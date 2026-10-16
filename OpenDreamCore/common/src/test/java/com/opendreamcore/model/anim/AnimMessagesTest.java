package com.opendreamcore.model.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 载荷编解码：构造→解析往返 + 坏包安静丢。 */
class AnimMessagesTest {

    @Test
    void 触发往返() {
        AnimMessages.Msg m = AnimMessages.parse(AnimMessages.trigger(42, "cast"));
        assertEquals("t", m.kind);
        assertEquals(42, m.entityId);
        assertEquals("cast", m.name);
    }

    @Test
    void 自己播放带层往返() {
        AnimMessages.Msg m = AnimMessages.parse(AnimMessages.play(-1, "walk", "特效"));
        assertEquals("p", m.kind);
        assertEquals(-1, m.entityId);
        assertEquals("walk", m.name);
        assertEquals("特效", m.layer);
    }

    @Test
    void 无层播放和停止() {
        AnimMessages.Msg p = AnimMessages.parse(AnimMessages.play(7, "idle", null));
        assertNull(p.layer);
        AnimMessages.Msg s = AnimMessages.parse(AnimMessages.stop(7));
        assertEquals("s", s.kind);
        assertEquals(7, s.entityId);
    }

    @Test
    void 坏包安静丢() {
        assertNull(AnimMessages.parse(null));
        assertNull(AnimMessages.parse(""));
        assertNull(AnimMessages.parse("x|1"));
        assertNull(AnimMessages.parse("t|abc|cast"));
        assertNull(AnimMessages.parse("t|1"));
        assertNull(AnimMessages.parse("q|1|cast"));
    }

    @Test
    void 名字里的竖线被替换() {
        AnimMessages.Msg m = AnimMessages.parse(AnimMessages.trigger(1, "a|b"));
        assertEquals("a/b", m.name);
        assertTrue(AnimMessages.parse(AnimMessages.trigger(1, "a/b")).name.equals("a/b"));
    }
}
