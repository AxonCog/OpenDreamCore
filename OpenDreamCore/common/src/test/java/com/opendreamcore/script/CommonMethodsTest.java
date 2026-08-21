package com.opendreamcore.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通用方法命名空间（Math/Str/Time/UUID）测试。
 */
class CommonMethodsTest {

    static {
        CommonMethods.registerAll();
    }

    @Test
    void mathMethods() {
        Scope scope = new Scope();
        assertEquals(5.0, num(DreamLang.evaluate("Math.绝对值(-5)", scope)));
        assertEquals(10.0, num(DreamLang.evaluate("Math.最大(3, 10)", scope)));
        assertEquals(3.0, num(DreamLang.evaluate("Math.最小(3, 10)", scope)));
        assertEquals(4.0, num(DreamLang.evaluate("Math.向下取整(4.7)", scope)));
        assertEquals(5.0, num(DreamLang.evaluate("Math.四舍五入(4.5)", scope)));
        assertEquals(3.0, num(DreamLang.evaluate("Math.平方根(9)", scope)));
        assertEquals(8.0, num(DreamLang.evaluate("Math.幂(2, 3)", scope)));
        assertEquals(Math.PI, num(DreamLang.evaluate("Math.圆周率()", scope)));
    }

    @Test
    void strMethods() {
        Scope scope = new Scope();
        assertEquals(5.0, num(DreamLang.evaluate("Str.长度(\"你好abc\")", scope)));
        assertEquals("你好", DreamLang.evaluate("Str.截取(\"你好abc\", 0, 2)", scope));
        assertEquals("hxllo", DreamLang.evaluate("Str.替换(\"hello\", \"e\", \"x\")", scope));
        assertEquals("HELLO", DreamLang.evaluate("Str.大写(\"hello\")", scope));
        assertEquals(true, DreamLang.evaluate("Str.包含(\"hello world\", \"world\")", scope));
        assertEquals(true, DreamLang.evaluate("Str.开头是(\"opendreamcore\", \"open\")", scope));
        scope.assignVar("list", java.util.List.of("a", "b", "c"));
        assertEquals("a,b,c", DreamLang.evaluate("Str.拼接(vars.list, \",\")", scope));
    }

    @Test
    void splitReturnsList() {
        Scope scope = new Scope();
        Object result = DreamLang.evaluate("Str.分割(\"1,2,3\", \",\")", scope);
        assertTrue(result instanceof List<?>);
        assertEquals(3, ((List<?>) result).size());
    }

    @Test
    void strExtensions() {
        Scope scope = new Scope();
        assertEquals(2.0, num(DreamLang.evaluate("Str.索引(\"hello\", \"l\")", scope)));
        assertEquals(3.0, num(DreamLang.evaluate("Str.最后索引(\"hello\", \"l\")", scope)));
        assertEquals("abcabc", DreamLang.evaluate("Str.重复(\"abc\", 2)", scope));
        assertEquals("你好 玩家 等级3", DreamLang.evaluate("Str.格式化(\"你好 %s 等级%d\", \"玩家\", 3)", scope));
        assertEquals("Hello world", DreamLang.evaluate("Str.去颜色码(\"§aHello §lworld\")", scope));
        assertEquals(42.0, num(DreamLang.evaluate("Str.转整数(\"42\")", scope)));
        assertEquals(3.5, num(DreamLang.evaluate("Str.转小数(\"3.5\")", scope)));
        assertEquals(true, DreamLang.evaluate("Str.为空(\"\")", scope));
        assertEquals(true, DreamLang.evaluate("Str.为空白(\"   \")", scope));
        assertEquals("olleh", DreamLang.evaluate("Str.反转(\"hello\")", scope));
        assertEquals("00042", DreamLang.evaluate("Str.左填充(\"42\", 5, \"0\")", scope));
        assertEquals("42***", DreamLang.evaluate("Str.右填充(\"42\", 5, \"*\")", scope));
        assertEquals("b", DreamLang.evaluate("Str.字符(\"abc\", 1)", scope));
        assertEquals("heo", DreamLang.evaluate("Str.移除(\"hello\", \"l\")", scope));
        assertEquals("he-llo", DreamLang.evaluate("Str.插入(\"hello\", 2, \"-\")", scope));
        assertEquals(true, DreamLang.evaluate("Str.忽略大小写相等(\"ABC\", \"abc\")", scope));
        assertEquals("xello", DreamLang.evaluate("Str.替换首个(\"hello\", \"h\", \"x\")", scope));
        assertEquals(true, DreamLang.evaluate("Str.匹配(\"abc123\", \".*[0-9]+\")", scope));
        assertEquals("Hello", DreamLang.evaluate("Str.首字母大写(\"hello\")", scope));
    }

    @Test
    void arrayMethods() {
        Scope scope = new Scope();
        scope.assignVar("list", java.util.List.of(1, 2, 3));
        assertEquals(3.0, num(DreamLang.evaluate("Array.大小(vars.list)", scope)));
        assertEquals(2.0, num(DreamLang.evaluate("Array.获取(vars.list, 1)", scope)));
        assertEquals(3.0, num(DreamLang.evaluate("Array.获取(vars.list, -1)", scope)));
        assertEquals(true, DreamLang.evaluate("Array.包含(vars.list, 2)", scope));
        assertEquals("1,2,3", DreamLang.evaluate("Array.拼接(vars.list, \",\")", scope));
        assertEquals(3.0, num(DreamLang.evaluate("Array.弹出(vars.list)", scope)));
        assertEquals(1.0, num(DreamLang.evaluate("Array.首个(vars.list)", scope)));
        assertEquals(0.0, num(DreamLang.evaluate("Array.索引(vars.list, 1)", scope)));

        // 返回新列表的操作（DreamLang 数字字面量为 Long，与 Java Integer 列表比较用字符串形式）
        scope.assignVar("a", java.util.List.of(1, 2));
        assertEquals("[1, 2, 3]", String.valueOf(DreamLang.evaluate("Array.添加(vars.a, 3)", scope)));
        assertEquals("[1]", String.valueOf(DreamLang.evaluate("Array.移除(vars.a, 1)", scope)));
        assertEquals("[2, 1]", String.valueOf(DreamLang.evaluate("Array.反转(vars.a)", scope)));
        assertEquals("[1, 2]", String.valueOf(DreamLang.evaluate("Array.切片(vars.a, 0, 3)", scope)));
        assertEquals("[2]", String.valueOf(DreamLang.evaluate("Array.切片(vars.a, -1, 3)", scope)));
        scope.assignVar("dup", java.util.List.of(1, 1, 2, 2, 3));
        assertEquals("[1, 2, 3]", String.valueOf(DreamLang.evaluate("Array.去重(vars.dup)", scope)));
        assertEquals("[1, 2, 2, 3]", String.valueOf(DreamLang.evaluate("Array.合并(vars.a, 2, 3)", scope)));
        scope.assignVar("mix", java.util.List.of(3, 1, 2));
        assertEquals("[1, 2, 3]", String.valueOf(DreamLang.evaluate("Array.排序(vars.mix)", scope)));
        assertEquals(0, ((List<?>) DreamLang.evaluate("Array.清空(vars.a)", scope)).size());
    }

    @Test
    void timeAndUuid() {
        Scope scope = new Scope();
        assertTrue(num(DreamLang.evaluate("Time.当前时间戳()", scope)) > 1_000_000_000);
        assertTrue(((String) DreamLang.evaluate("UUID.随机()", scope)).length() == 36);
    }

    @Test
    void eventBusPublishSubscribe() {
        EventBus.clearAll();
        Scope scope = new Scope();
        // 订阅 + 发布：参数透传进 Lambda
        DreamLang.execute("""
                变量 hits = Array.清空()
                Event.订阅("ping", (msg) => { hits = Array.添加(hits, msg) })
                Event.订阅("ping", (msg) => { hits = Array.添加(hits, msg + "!") })
                Event.发布("ping", "hi")
                """, scope);
        Object hits = scope.resolve("hits");
        assertEquals("[hi, hi!]", String.valueOf(hits));
        assertEquals(2, EventBus.handlerCount("ping"));

        // 取消订阅后不再触发
        DreamLang.execute("""
                变量 id = Event.订阅("once", () => { 返回 1 })
                Event.取消订阅(id)
                Event.发布("once")
                """, scope);
        assertEquals(0, EventBus.handlerCount("once"));

        // 跨脚本（不同 execute 调用）订阅仍可被触发
        DreamLang.execute("Event.订阅(" + "\"cross\"" + ", () => { 变量 got = 42 })", scope);
        Object result = DreamLang.evaluate("Event.发布(" + "\"cross\"" + ")", scope);
        assertEquals(null, result);
        EventBus.clearAll();
        assertEquals(0, EventBus.handlerCount("cross"));
    }

    private static double num(Object v) {
        return ((Number) v).doubleValue();
    }
}
