package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 按键组合执行器三行为：combo 命中判定；when 条件拦截 + 失败提示一次；冷却窗口。
 */
class KeyComboExecutorTest {

    @Test
    void comboHitTriggersAction() {
        var hits = new AtomicInteger();
        var exec = new KeyComboExecutor(new KeyCombo("C+LEFT_SHIFT+左键"),
                0, () -> true, null,
                s -> hits.incrementAndGet());

        assertTrue(exec.feed(1000, Set.of("C", "LEFT_SHIFT", "左键"), m -> { }));
        assertEquals(1, hits.get());

        assertFalse(exec.feed(1000, Set.of("C", "LEFT_SHIFT"), m -> { }));
        assertEquals(1, hits.get());
    }

    @Test
    void conditionBlocksAndPromptsOnce() {
        var prompts = new AtomicInteger();
        boolean[] healthy = {false};
        var exec = new KeyComboExecutor(new KeyCombo("R"),
                0, () -> healthy[0], "状态不足",
                s -> { });

        // 不合格：拦截动作 + 提示一次
        assertFalse(exec.feed(1000, Set.of("R"), m -> prompts.incrementAndGet()));
        assertEquals(1, prompts.get());
        // 持续不合格：不重复刷屏
        exec.feed(1100, Set.of("R"), m -> prompts.incrementAndGet());
        assertEquals(1, prompts.get(), "条件持续不合格时不应反复提示");
        // 合格恢复后动作执行
        healthy[0] = true;
        assertTrue(exec.feed(1200, Set.of("R"), m -> { }));
    }

    @Test
    void cooldownWindowSuppressesRepeat() {
        var fires = new AtomicInteger();
        long[] clock = {10_000};
        var exec = new KeyComboExecutor(new KeyCombo("F"),
                500, () -> true, null,
                s -> fires.incrementAndGet());

        assertTrue(exec.feed(clock[0], Set.of("F"), m -> { }));
        assertEquals(1, fires.get());
        // 冷却窗口内：静默忽略（时钟没走）
        assertFalse(exec.feed(clock[0] + 200, Set.of("F"), m -> { }));
        assertEquals(1, fires.get());
        // 窗口过后再次命中
        clock[0] += 600;
        assertTrue(exec.feed(clock[0], Set.of("F"), m -> { }));
        assertEquals(2, fires.get());
    }
}
