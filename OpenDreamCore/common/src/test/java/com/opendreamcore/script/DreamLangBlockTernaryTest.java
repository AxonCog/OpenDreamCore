package com.opendreamcore.script;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 龙核（DreamCore）块三元方言兼容测试：
 * (条件)?{语句块}:值 —— 菜单.yml 的 Functions.keyPress 等大量使用。
 */
class DreamLangBlockTernaryTest {

    static final List<String> LOG = new ArrayList<>();

    static {
        CommonMethods.registerAll();
        MethodRegistry.registerOrReplace("测试记录", args -> {
            LOG.add(String.valueOf(args.length > 0 ? args[0] : ""));
            return null;
        });
    }

    private void reset() {
        LOG.clear();
    }

    /** 基础形态：(cond)?{stmts}:0 —— 条件真执行块。 */
    @Test
    void blockThenExecutesWhenTrue() {
        reset();
        Scope scope = new Scope();
        Object r = assertDoesNotThrow(() ->
                DreamLang.execute("(1 > 0)?{方法.测试记录('A');}:0", scope));
        assertEquals("A", String.join("", LOG));
        assertNull(r);
    }

    /** 条件假跳过块，取 else 值 0。 */
    @Test
    void skippedWhenFalse() {
        reset();
        Scope scope = new Scope();
        Object r = DreamLang.execute("(1 > 2)?{方法.测试记录('A');}:0", scope);
        assertTrue(LOG.isEmpty());
        assertEquals(0.0, ((Number) r).doubleValue());
    }

    /** 菜单.yml 实际写法：嵌套方法调用 + 字符串比较 + || 。 */
    @Test
    void menuYmlKeyPressForm() {
        reset();
        NamespaceRegistry.register("方法", args -> "ESCAPE");
        MethodRegistry.registerOrReplace("取当前按下键", args -> "ESCAPE");
        Scope scope = new Scope();
        String script = "(方法.测试记录('X')=='E' || 'ESCAPE'=='ESCAPE')?{方法.测试记录('关闭界面');}:0";
        assertDoesNotThrow(() -> DreamLang.execute(script, scope));
        assertTrue(LOG.contains("关闭界面"), "应执行块内语句, 实际: " + LOG);
    }

    /** 无冒号省略 else：cond?{stmts} */
    @Test
    void blockWithoutElse() {
        reset();
        Scope scope = new Scope();
        Object r = assertDoesNotThrow(() ->
                DreamLang.execute("(5 == 5)?{方法.测试记录('hit');}", scope));
        assertEquals("hit", String.join("", LOG));
        assertNull(r);
    }

    /** 多语句块：最后一条的值不外泄也不影响执行。 */
    @Test
    void multiStatementBlock() {
        reset();
        Scope scope = new Scope();
        assertDoesNotThrow(() -> DreamLang.execute(
                "(true)?{var a = 1; 方法.测试记录('s1'); 方法.测试记录('s2');}:0", scope));
        assertEquals("s1s2", String.join("", LOG));
    }
}
