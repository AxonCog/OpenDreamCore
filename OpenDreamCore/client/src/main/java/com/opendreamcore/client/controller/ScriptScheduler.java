package com.opendreamcore.client.controller;

import com.opendreamcore.page.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 脚本调度器（Script.延迟执行 / 计划执行 / 防抖 / 节流 / Screen.延迟变量）。
 *
 * <p>从 ClientController 抽出的纯调度逻辑：任务存储、到期判定、防抖/节流合并、
 * 页面级清理。与控制器状态的交互经 {@link Host} 回调，保持本类零 MC 依赖可单测。</p>
 */
public final class ScriptScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptScheduler.class);

    /** 宿主回调：调度器执行任务时需要的控制器能力。 */
    public interface Host {
        /** 当前页面 id（屏幕优先；无则为 null）。 */
        String currentPageId();

        /** 当前页面（无则为 null）。 */
        Page currentPage();

        /** 按 id 查页面（含 HUD/世界页；无则为 null）。 */
        Page pageById(String pageId);

        /** 本地执行脚本。 */
        void runLocalAction(Page page, String script);

        /**
         * 应用延迟变量（Screen.延迟设置变量到期）：写 page.variables 并按展示形态刷新
         * （屏幕/HUD/世界分支由宿主处理——依赖布局与界面状态）。
         */
        void applyDelayedVar(String pageId, String varName, Object value);
    }

    /** 脚本定时任务。kind：0 一次性 / 1 循环 / 2 防抖 / 3 节流 / 4 延迟变量。 */
    private static final class ScriptTask {
        final long id;
        final String script;
        final int kind;
        final String key;     // 防抖/节流/延迟变量同名键（缺省 = 脚本文本）；其它 kind 为 null
        final String pageId;  // 调度时页面 id（页面关闭时清理）
        final long periodMs;  // 循环/防抖/节流周期；一次性 = 0
        final Consumer<Page> action; // 非脚本任务回调（延迟变量用；null = 执行脚本）
        long dueAt;
        boolean pending;      // 节流：周期内再次调用 → 周期末补跑一次（合并）

        ScriptTask(long id, String script, int kind, String key, String pageId, long periodMs, long dueAt,
                   Consumer<Page> action) {
            this.id = id;
            this.script = script;
            this.kind = kind;
            this.key = key;
            this.pageId = pageId;
            this.periodMs = periodMs;
            this.dueAt = dueAt;
            this.action = action;
        }
    }

    /** 脚本任务上限（防失控：防抖/节流/循环全算）。 */
    private static final int MAX_SCRIPT_TASKS = 64;

    private final Map<Long, ScriptTask> tasks = new ConcurrentHashMap<>();
    private long nextTaskId = 1;

    private final Host host;

    public ScriptScheduler(Host host) {
        this.host = host;
    }

    /** 调度脚本执行；intervalMs = 0 一次性，&gt;0 循环。返回任务 id（任务数超上限返回 -1）。 */
    public long scheduleScript(String script, long delayMs, long intervalMs) {
        return scheduleTask(0, script, delayMs, intervalMs, null, null);
    }

    /** 防抖：同名键（缺省 = 脚本文本）重置计时，安静 ms 毫秒后执行一次。返回任务 id。 */
    public long debounceScript(String script, long ms, String key) {
        String k = key == null || key.isBlank() ? script : key;
        String pid = host.currentPageId();
        tasks.values().removeIf(t -> t.kind == 2 && java.util.Objects.equals(t.key, k)
                && java.util.Objects.equals(t.pageId, pid));
        return scheduleTask(2, script, ms, ms, k, null);
    }

    /** 节流：周期内最多执行一次（周期内再次调用合并为周期末一次尾调用）。返回任务 id。 */
    public long throttleScript(String script, long ms, String key) {
        String k = key == null || key.isBlank() ? script : key;
        String pid = host.currentPageId();
        for (ScriptTask t : tasks.values()) {
            if (t.kind == 3 && java.util.Objects.equals(t.key, k)
                    && java.util.Objects.equals(t.pageId, pid)) {
                t.pending = true;
                t.dueAt = Math.min(t.dueAt, System.currentTimeMillis() + ms);
                return t.id;
            }
        }
        return scheduleTask(3, script, ms, ms, k, null);
    }

    /** 统一调度入口；任务数超上限返回 -1。action 非空 = 非脚本任务（延迟变量等）。 */
    private long scheduleTask(int kind, String script, long delayMs, long periodMs, String key,
                              Consumer<Page> action) {
        if (tasks.size() >= MAX_SCRIPT_TASKS) {
            LOGGER.warn("脚本任务数超上限 {}，拒绝调度", MAX_SCRIPT_TASKS);
            return -1;
        }
        long id = nextTaskId++;
        Page page = host.currentPage();
        tasks.put(id, new ScriptTask(id, script, kind, key,
                page == null ? null : page.id(), periodMs,
                System.currentTimeMillis() + Math.max(0, delayMs), action));
        return id;
    }

    /** 延迟设置页面变量（Screen.延迟设置变量）：到期写 page.variables 并按展示形态刷新；同名挂起任务先取消。 */
    public long delaySetPageVar(String varName, Object value, long ms) {
        String pid = host.currentPageId();
        if (pid == null || varName == null) {
            return -1;
        }
        cancelDelayedVar(pid, varName);
        return scheduleTask(4, null, ms, ms, varName, page ->
                host.applyDelayedVar(pid, varName, value));
    }

    /** 取消页面挂起的延迟变量（不存在返回 false）。 */
    public boolean cancelDelayedVar(String pageId, String varName) {
        if (pageId == null || varName == null) {
            return false;
        }
        return tasks.values().removeIf(t -> t.kind == 4 && varName.equals(t.key)
                && pageId.equals(t.pageId));
    }

    /** 延迟变量剩余毫秒（无挂起任务返回 -1）。 */
    public double delayedVarRemaining(String pageId, String varName) {
        if (pageId == null || varName == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        for (ScriptTask t : tasks.values()) {
            if (t.kind == 4 && varName.equals(t.key) && pageId.equals(t.pageId)) {
                return Math.max(0, t.dueAt - now);
            }
        }
        return -1;
    }

    /** 取消脚本任务（不存在返回 false）。 */
    public boolean cancelScript(long id) {
        return tasks.remove(id) != null;
    }

    /** 页面关闭清理：该页调度/归属的全部任务取消（防抖/节流/循环/一次性）。 */
    public void cancelScriptsForPage(String pageId) {
        if (pageId == null) {
            return;
        }
        tasks.values().removeIf(t -> pageId.equals(t.pageId));
    }

    /** 渲染线程 tick：到期任务执行（循环/节流重置计时，防抖/延迟变量/一次性移除）。 */
    public void tick() {
        if (tasks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ScriptTask task : tasks.values()) {
            if (now < task.dueAt) {
                continue;
            }
            Page page = host.currentPage() != null ? host.currentPage() : host.pageById(task.pageId);
            if (page != null) {
                if (task.action != null) {
                    task.action.accept(page); // 非脚本任务（延迟变量等）
                } else {
                    host.runLocalAction(page, task.script);
                }
            }
            switch (task.kind) {
                case 1 -> task.dueAt = now + task.periodMs;                    // 循环
                case 2 -> tasks.remove(task.id);                               // 防抖：一次
                case 3 -> {                                                    // 节流：常驻，周期末补跑合并
                    task.dueAt = now + task.periodMs;
                    task.pending = false;
                }
                case 4 -> tasks.remove(task.id);                               // 延迟变量：一次
                default -> tasks.remove(task.id);                              // 一次性
            }
        }
    }
}
