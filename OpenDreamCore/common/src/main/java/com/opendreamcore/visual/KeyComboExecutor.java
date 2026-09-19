package com.opendreamcore.visual;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 按键组合执行器。
 *
 * 服务端持有每个玩家的按下集合；KEY 上报到达时喂进来，
 * 组合命中即触发动作。内置冷却窗口与条件拦截：
 *
 *   when 表达式不满足 → 触发 failMessage 回调（提示一次），动作不放行；
 *   冷却期内 → 静默忽略；冷却由调用方传入时钟推进，便于单测。
 */
public final class KeyComboExecutor {

    private final KeyCombo combo;
    private final long cooldownMs;
    private final BooleanSupplier condition;      // when 表达式的求值包装
    private final String failMessage;             // 条件失败提示（null = 不提示）
    private final Consumer<String> action;        // 命中回调（参数=玩家名等上下文由调用方绑定）

    private long lastFiredAt = Long.MIN_VALUE;
    private boolean lastConditionPassed = true;

    public KeyComboExecutor(KeyCombo combo, long cooldownMs,
                            BooleanSupplier condition, String failMessage,
                            Consumer<String> action) {
        this.combo = combo;
        this.cooldownMs = Math.max(0, cooldownMs);
        this.condition = condition;
        this.failMessage = failMessage;
        this.action = action;
    }

    /**
     * 喂入当前时刻的按键状态。
     *
     * nowMs：当前毫秒（调用方的时钟）
     * pressed：玩家当前按下的键集合
     * onFail：条件不满足时的提示回调（可 null）
     * 返回：true = 本次命中并执行了动作
     */
    public boolean feed(long nowMs, Set<String> pressed, java.util.function.Consumer<String> onFail) {
        if (!combo.matches(pressed)) {
            lastConditionPassed = true; // 松开就复位，下次按下重新提示
            return false;
        }
        if (nowMs - lastFiredAt < cooldownMs && lastFiredAt != Long.MIN_VALUE) {
            return false;               // 冷却中：静默
        }
        if (!condition.getAsBoolean()) {
            if (lastConditionPassed && failMessage != null && onFail != null) {
                onFail.accept(failMessage);  // 只在"刚变不合格"时提示一次，避免刷屏
            }
            lastConditionPassed = false;
            return false;
        }
        lastFiredAt = nowMs;
        lastConditionPassed = true;
        action.accept(combo.toString());
        return true;
    }

    /** 仅供测试观察：上次是否处于条件合格状态。 */
    boolean lastConditionPassed() {
        return lastConditionPassed;
    }

    /** 多组合注册表：combo 字符串 → 执行器（服务端 KeyConfig 装载用）。 */
    public static class Registry {
        private final Map<String, KeyComboExecutor> executors = new HashMap<>();

        public void register(String comboText, long cooldownMs,
                             BooleanSupplier condition, String failMessage,
                             Consumer<String> action) {
            executors.put(comboText, new KeyComboExecutor(new KeyCombo(comboText),
                    cooldownMs, condition, failMessage, action));
        }

        /** 喂入按键状态给所有组合；返回是否有任一命中执行。 */
        public boolean feed(long nowMs, Set<String> pressed, Consumer<String> onFail) {
            boolean any = false;
            for (KeyComboExecutor e : executors.values()) {
                if (e.feed(nowMs, pressed, onFail)) {
                    any = true;
                }
            }
            return any;
        }

        public int size() {
            return executors.size();
        }
    }
}
