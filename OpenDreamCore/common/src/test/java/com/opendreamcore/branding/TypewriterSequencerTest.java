package com.opendreamcore.branding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D1：打字机/轮播标题时序状态机（DreamCore ClientTitleManager 语义平移）。
 */
class TypewriterSequencerTest {

    private static final String LONG = "OpenDreamCore";

    @Test
    void staticTextPassthrough() {
        TitleConfig cfg = new TitleConfig();
        cfg.text = "梦想核心";
        var seq = new TypewriterSequencer(cfg);
        assertEquals("梦想核心", seq.tick(0));
        assertEquals("梦想核心", seq.tick(999_999));
        assertFalse(seq.isFinished(999_999), "静态单句且 loop=true 永不结束");
    }

    @Test
    void typewriterProgression() {
        TitleConfig cfg = new TitleConfig();
        cfg.text = "ABCD";
        cfg.typewriter = true;
        cfg.speed = 100;
        cfg.holdMs = 500;
        cfg.loop = false; // 单句打完即定格（loop=true 时会无限重打，属预期语义）
        var seq = new TypewriterSequencer(cfg);

        assertEquals("A", seq.tick(0));
        assertEquals("AB", seq.tick(120));
        assertEquals("ABC", seq.tick(250));
        assertEquals("ABCD", seq.tick(400));
        // 停留期内保持整句
        assertEquals("ABCD", seq.tick(800));
        assertTrue(seq.isFinished(900));
    }

    @Test
    void cycleRotatesTwoTitles() {
        TitleConfig cfg = new TitleConfig();
        cfg.titles.addAll(java.util.List.of("甲", "乙"));
        cfg.typewriter = true;
        cfg.speed = 10;   // 甲: 2字*10=20ms 打字 + 3000 hold
        var seq = new TypewriterSequencer(cfg);

        assertEquals("甲", seq.tick(0));
        long secondStart = 2L * 10 + 3000;
        assertEquals("乙", seq.tick(secondStart));
        // 循环回甲（周期 = (20+3000)*2）
        assertEquals("甲", seq.tick(secondStart * 2 + 5));
    }

    @Test
    void nonLoopClampsToLast() {
        TitleConfig cfg = new TitleConfig();
        cfg.text = "唯一";
        cfg.typewriter = true;
        cfg.speed = 50;
        cfg.holdMs = 100;
        cfg.loop = false;
        var seq = new TypewriterSequencer(cfg);
        assertTrue(seq.isFinished(1_000_000));
        assertEquals("唯一", seq.tick(1_000_000));
    }

    @Test
    void defaultsAndTolerance() {
        TitleConfig cfg = new TitleConfig();
        cfg.speed = -5;                 // 非法 → 回退 120
        cfg.holdMs = -1;                // 未设置 → interval
        cfg.interval = 2000;
        assertEquals(120, cfg.effectiveSpeed());
        assertEquals(2000, cfg.effectiveHoldMs());
        cfg.titles.add(null);           // null 条目被过滤
        assertEquals(0, cfg.sequence().size());
    }

    @Test
    void jsonLoad(@TempDir Path dir) throws Exception {
        Path json = dir.resolve("title.json");
        Files.writeString(json, """
                {"titles":["你好","世界"],"typewriter":true,"speed":80,"loop":false}
                """);
        TitleConfig cfg = TitleConfig.load(json);
        assertNotNull(cfg);
        assertTrue(cfg.typewriter);
        assertFalse(cfg.loop);
        assertEquals(80, cfg.speed);
        assertEquals(2, cfg.sequence().size());

        Files.writeString(json, "{ broken");
        assertNull(TitleConfig.load(json), "损坏 JSON 返回 null 回退 title.txt");
    }

    @Test
    void rotationSwitchesEveryInterval() {
        TitleConfig cfg = new TitleConfig();
        cfg.titles.addAll(java.util.List.of("甲", "乙"));
        cfg.typewriter = false;
        cfg.interval = 2000;
        var seq = new TypewriterSequencer(cfg);
        assertEquals("甲", seq.tick(0));
        assertEquals("甲", seq.tick(1999));
        assertEquals("乙", seq.tick(2000));
        assertEquals("乙", seq.tick(3999));
        assertEquals("甲", seq.tick(4000)); // loop 回卷
    }

    @Test
    void randomModeDeterministicAndBounded() {
        TitleConfig cfg = new TitleConfig();
        cfg.titles.addAll(java.util.List.of("甲", "乙", "丙"));
        cfg.random = true;
        cfg.interval = 1000;
        var seq = new TypewriterSequencer(cfg);

        var pool = java.util.List.of("甲", "乙", "丙");
        // 同一时间格内恒定（确定性）
        assertEquals(seq.tick(0), seq.tick(999));
        // 跨格仍属序列集合
        for (long t = 0; t <= 5000; t += 700) {
            assertTrue(pool.contains(seq.tick(t)), "t=" + t + " 应为序列成员");
        }
        // 随机模式永不结束
        assertFalse(seq.isFinished(1_000_000_000L));
        // 单句时 random 不生效（回退静态）
        TitleConfig single = new TitleConfig();
        single.text = "唯一";
        single.random = true;
        assertEquals("唯一", new TypewriterSequencer(single).tick(12345));
    }
}
