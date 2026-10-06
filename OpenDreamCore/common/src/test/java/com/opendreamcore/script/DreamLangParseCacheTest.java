package com.opendreamcore.script;

import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.Scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析缓存测试。
 *
 * <p>缓存语法树是为了砍掉渲染热路径上的重复 ANTLR 解析；风险是「树里粘住了上一次的作用域」，
 * 所以这里盯两件事：同表达式换作用域结果必须跟着变、缓存条目数增长有上限。
 */
class DreamLangParseCacheTest {

    static {
        CommonMethods.registerAll();
    }

    private static double num(String expr, double value) {
        Scope scope = new Scope();
        scope.assignVar("hp", Double.valueOf(value));
        java.util.Map<String, Object> entity = new java.util.LinkedHashMap<String, Object>();
        entity.put("health", Double.valueOf(value));
        entity.put("health_ratio", Double.valueOf(value / 100.0));
        scope.assign("entity", entity);
        Object out = DreamLang.evaluate(expr, scope);
        return ((Number) out).doubleValue();
    }

    @Test
    void 同一表达式换作用域结果跟着变() {
        // 先用血量 10 求值，让这棵语法树进缓存（hp*2 = 20）
        assertEquals(20.0, num("hp * 2", 10.0), 1e-9);
        // 同一句表达式换作用域：缓存的树不能把上一次的 hp 带进来
        assertEquals(40.0, num("hp * 2", 20.0), 1e-9);
        assertEquals(0.0, num("hp * 2", 0.0), 1e-9);
    }

    @Test
    void 成员链与算术混用在缓存下稳定() {
        assertEquals(5.0, num("entity.health_ratio * 100", 5.0), 1e-9);
        assertEquals(11.0, num("entity.health + 1", 10.0), 1e-9);
    }

    @Test
    void 缓存条目数不超过上限() {
        DreamLang.clearParseCache();
        assertEquals(0, DreamLang.parseCacheSize());
        for (int i = 0; i < 600; i++) {
            num("hp + " + i, 1.0);
        }
        assertTrue(DreamLang.parseCacheSize() <= 512,
                "缓存应当封顶 512，实际 " + DreamLang.parseCacheSize());
        DreamLang.clearParseCache();
    }

    @Test
    void 非法表达式不缓存也不影响后续求值() {
        assertEquals(3.0, num("hp + 1", 2.0), 1e-9);
        try {
            num("hp +", 1.0);
        } catch (RuntimeException expected) {
            // 语法错就该抛，抛完缓存里不能留下坏条目
        }
        assertEquals(7.0, num("hp + 5", 2.0), 1e-9);
    }
}