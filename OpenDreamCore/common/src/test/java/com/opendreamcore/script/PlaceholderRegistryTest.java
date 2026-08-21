package com.opendreamcore.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 占位符注册表测试：分类注册/替换/未知保留/单键解析。
 */
class PlaceholderRegistryTest {

    @Test
    void registerAndResolve() {
        PlaceholderRegistry.register("player", key -> switch (key) {
            case "name" -> "张三";
            case "health" -> 20.0;
            default -> null;
        });
        try {
            assertEquals("你好 张三", PlaceholderRegistry.resolve("你好 {player.name}"));
            assertEquals("血量 20.0", PlaceholderRegistry.resolve("血量 {player.health}"));
        } finally {
            PlaceholderRegistry.unregister("player");
        }
    }

    @Test
    void unknownPlaceholderKept() {
        assertEquals("未知 {player.xxx}", PlaceholderRegistry.resolve("未知 {player.xxx}"));
        assertEquals("没有 {nope.key} 的分类", PlaceholderRegistry.resolve("没有 {nope.key} 的分类"));
        assertEquals("没有分类 {nokey}", PlaceholderRegistry.resolve("没有分类 {nokey}"));
    }

    @Test
    void mixedAndMultiple() {
        PlaceholderRegistry.register("color", key -> "red".equals(key) ? "#FF0000" : null);
        try {
            assertEquals("颜色 #FF0000 #FF0000 测试", PlaceholderRegistry.resolve("颜色 {color.red} {color.red} 测试"));
        } finally {
            PlaceholderRegistry.unregister("color");
        }
    }

    @Test
    void emptyAndNull() {
        assertNull(PlaceholderRegistry.resolve(null));
        assertEquals("", PlaceholderRegistry.resolve(""));
        assertEquals("无花括号", PlaceholderRegistry.resolve("无花括号"));
    }

    @Test
    void resolverThrowKeepsOriginal() {
        PlaceholderRegistry.register("bad", key -> {
            throw new IllegalStateException("boom");
        });
        try {
            assertEquals("{bad.x}", PlaceholderRegistry.resolve("{bad.x}"));
        } finally {
            PlaceholderRegistry.unregister("bad");
        }
    }

    @Test
    void resolveOne() {
        PlaceholderRegistry.register("system", key -> "millis".equals(key) ? 123L : null);
        try {
            assertEquals(123L, PlaceholderRegistry.resolveOne("system", "millis"));
            assertNull(PlaceholderRegistry.resolveOne("system", "nope"));
            assertNull(PlaceholderRegistry.resolveOne("nope", "x"));
        } finally {
            PlaceholderRegistry.unregister("system");
        }
    }
}
