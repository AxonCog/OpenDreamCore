package com.opendreamcore.client.controller;

import com.opendreamcore.page.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动画变量服务（Var.动画值 / 设置动画值 / 动画到：数值补间每帧写入页面变量）。
 *
 * <p>从 ClientController 抽出的纯补间逻辑；与控制器的交互（当前页/按 id 查页/
 * 立即写变量/屏幕刷新）经 {@link Host} 回调，保持零 MC 依赖可单测。</p>
 */
public final class AnimateVarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnimateVarService.class);

    /** 宿主回调。 */
    public interface Host {
        /** 任意当前活动页（屏幕 > HUD > 世界；无则 null）。 */
        Page anyCurrentPage();

        /** 按 id 查页面。 */
        Page pageById(String pageId);

        /** 立即写页面变量（绕过补间）。 */
        void setPageVarAny(String name, Object value);

        /** 若该页正显示在屏幕上则刷新（补间帧写变量后的视觉同步）。 */
        void refreshScreenIfPage(Page page);
    }

    /** 动画变量补间（起点 → 终点，durationMs 内按缓动推进）。 */
    private static final class Tween {
        final String pageId;
        final String name;
        final double from;
        final double to;
        final long startMs;
        final long durationMs;
        final com.opendreamcore.script.Easing.Type easing;

        Tween(String pageId, String name, double from, double to, long startMs, long durationMs,
              com.opendreamcore.script.Easing.Type easing) {
            this.pageId = pageId;
            this.name = name;
            this.from = from;
            this.to = to;
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.easing = easing;
        }

        double valueAt(long nowMs) {
            double p = durationMs <= 0 ? 1 : Math.min(1, (nowMs - startMs) / (double) durationMs);
            double e = com.opendreamcore.script.Easing.apply(easing, p);
            return from + (to - from) * e;
        }

        boolean finishedAt(long nowMs) {
            return nowMs >= startMs + durationMs;
        }
    }

    private final Map<String, Tween> tweens = new ConcurrentHashMap<>();

    private final Host host;

    public AnimateVarService(Host host) {
        this.host = host;
    }

    /** 设置动画变量（立即写入页面变量并清除补间；Var.设置动画值）。 */
    public boolean setAnimateValue(String name, Object value) {
        tweens.remove(name);
        double nv = value instanceof Number n ? n.doubleValue() : 0;
        host.setPageVarAny(name, nv);
        return true;
    }

    /** 动画到：durationMs 内从当前值缓动到目标值。 */
    public boolean animateValueTo(String name, double to, long durationMs,
                                  com.opendreamcore.script.Easing.Type easing) {
        Page page = host.anyCurrentPage();
        if (page == null || name == null) {
            return false;
        }
        Object cur = page.variables().get(name);
        double from = cur instanceof Number n ? n.doubleValue() : 0;
        if (durationMs <= 0) {
            tweens.remove(name);
            host.setPageVarAny(name, to);
            return true;
        }
        tweens.put(name, new Tween(page.id(), name, from, to,
                System.currentTimeMillis(), durationMs, easing));
        return true;
    }

    /** 动画变量当前值（活动补间取插值；否则读页面变量；不存在 0。Var.动画值）。 */
    public double getAnimateValue(String name) {
        Tween t = name == null ? null : tweens.get(name);
        if (t != null) {
            long now = System.currentTimeMillis();
            double v = t.valueAt(now);
            if (t.finishedAt(now)) {
                tweens.remove(name);
            }
            return v;
        }
        Page page = host.anyCurrentPage();
        if (page == null || name == null) {
            return 0;
        }
        Object cur = page.variables().get(name);
        return cur instanceof Number n ? n.doubleValue() : 0;
    }

    /** 每帧推进：活动补间写入其归属页面变量（HUD/世界每帧读变量 → 数值天然动画；屏幕补刷新）。 */
    public void tick() {
        if (tweens.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        java.util.List<String> done = new java.util.ArrayList<>();
        for (Tween t : tweens.values()) {
            double v = t.valueAt(now);
            Page page = host.pageById(t.pageId);
            if (page != null) {
                page.variables().put(t.name, v);
                host.refreshScreenIfPage(page);
            }
            if (t.finishedAt(now)) {
                done.add(t.name);
            }
        }
        done.forEach(tweens::remove);
    }
}
