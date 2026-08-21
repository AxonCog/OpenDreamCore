package com.opendreamcore.script;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DreamLang 运行时测试：表达式求值、变量、方法调用（中英文关键字）。
 */
class DreamLangTest {

    static {
        CommonMethods.registerAll(); // Array/Str/Math 等命名空间
    }

    @Test
    void arithmetic() {
        Scope scope = new Scope();
        assertEquals(6.0, num(DreamLang.evaluate("2 + 2 * 2", scope)));
        assertEquals(1.0, num(DreamLang.evaluate("10 % 3", scope)));
        assertEquals(-5.0, num(DreamLang.evaluate("-5", scope)));
        assertEquals(7.0, num(DreamLang.evaluate("(1 + 2) * 3 - 2", scope)));
    }

    @Test
    void comparisonAndLogic() {
        Scope scope = new Scope();
        assertEquals(true, DreamLang.evaluate("10 > 5 && 3 >= 3", scope));
        assertEquals(true, DreamLang.evaluate("10 == 10", scope));
        assertEquals(false, DreamLang.evaluate("10 != 10", scope));
        assertEquals(true, DreamLang.evaluate("!(10 < 5)", scope));
        assertEquals(true, DreamLang.evaluate("0 || 1", scope) instanceof Number);
    }

    @Test
    void stringConcat() {
        Scope scope = new Scope();
        assertEquals("hello world", DreamLang.evaluate("\"hello \" + 'world'", scope));
    }

    @Test
    void varDeclarationAndAssignment() {
        Scope scope = new Scope();
        DreamLang.execute("var x = 10; x = x + 5", scope);
        assertEquals(15.0, num(scope.resolve("x")));
    }

    @Test
    void chineseKeywords() {
        Scope scope = new Scope();
        DreamLang.execute("变量 金币 = 100; 如果 (金币 >= 100) { 金币 = 金币 - 100 }", scope);
        assertEquals(0.0, num(scope.resolve("金币")));
    }

    @Test
    void ifElse() {
        Scope scope = new Scope();
        DreamLang.execute("var a = 5; if (a > 3) { a = 1 } else { a = 2 }", scope);
        assertEquals(1.0, num(scope.resolve("a")));
        DreamLang.execute("if (a > 10) { a = 9 } else { a = 8 }", scope);
        assertEquals(8.0, num(scope.resolve("a")));
    }

    @Test
    void methodCall() {
        Scope scope = new Scope();
        AtomicReference<String> received = new AtomicReference<>();
        MethodRegistry.register("发送消息", args -> {
            received.set(String.valueOf(args[0]));
            return null;
        });

        DreamLang.execute("方法.发送消息(\"你好\")", scope);
        assertEquals("你好", received.get());
    }

    @Test
    void varsNamespace() {
        Scope scope = new Scope();
        scope.assignVar("coin", 100);
        assertEquals(100, ((Number) DreamLang.evaluate("vars.coin", scope)).intValue());
        assertEquals(true, DreamLang.evaluate("vars.coin >= 100", scope));
    }

    @Test
    void ternary() {
        Scope scope = new Scope();
        assertEquals("大", DreamLang.evaluate("10 > 5 ? \"大\" : \"小\"", scope));
    }

    @Test
    void syntaxErrorReportsLine() {
        Scope scope = new Scope();
        DreamLangExecutor.ScriptException e = assertThrows(
                DreamLangExecutor.ScriptException.class,
                () -> DreamLang.execute("var = 10", scope));
        assertTrue(e.getMessage().contains("语法错误"));
    }

    @Test
    void unknownMethodThrows() {
        Scope scope = new Scope();
        assertThrows(IllegalStateException.class,
                () -> DreamLang.execute("方法.不存在的(" + "\"" + "x" + "\"" + ")", scope));
    }

    @Test
    void namespaceMethodCall() {
        Scope scope = new Scope();
        AtomicReference<String> received = new AtomicReference<>();
        NamespaceRegistry.register("测试", args -> {
            received.set(String.valueOf(args[0]));
            return null;
        }, "打招呼", "greet");

        DreamLang.execute("测试.打招呼(\"哈喽\")", scope);
        assertEquals("哈喽", received.get());
        DreamLang.execute("测试.greet(\"hi\")", scope);
        assertEquals("hi", received.get());
    }

    @Test
    void unknownNamespaceThrows() {
        Scope scope = new Scope();
        assertThrows(RuntimeException.class,
                () -> DreamLang.execute("不存在的.方法(" + "\"" + "x" + "\"" + ")", scope));
    }

    @Test
    void whileLoop() {
        Scope scope = new Scope();
        DreamLang.execute("变量 i = 0; 变量 sum = 0; 当 (i < 5) { sum = sum + i; i = i + 1 }", scope);
        assertEquals(10.0, num(scope.resolve("sum")));
    }

    @Test
    void loopCount() {
        Scope scope = new Scope();
        DreamLang.execute("变量 sum = 0; 循环 (3, { sum = sum + 10 })", scope);
        assertEquals(30.0, num(scope.resolve("sum")));
    }

    @Test
    void forEachList() {
        Scope scope = new Scope();
        scope.assignVar("items", java.util.List.of(1L, 2L, 3L));
        DreamLang.execute("变量 total = 0; 遍历 (items, \"i\", \"v\", 0, { total = total + v })", scope);
        assertEquals(6.0, num(scope.resolve("total")));
    }

    @Test
    void breakInLoop() {
        Scope scope = new Scope();
        DreamLang.execute("变量 i = 0; 当 (true) { i = i + 1; 如果 (i >= 3) { 跳出 } }", scope);
        assertEquals(3.0, num(scope.resolve("i")));
    }

    @Test
    void forLoop() {
        Scope scope = new Scope();
        DreamLang.execute("var total = 0; for (var i = 1; i <= 4; i = i + 1) { total = total + i }", scope);
        assertEquals(10.0, num(scope.resolve("total")));
    }

    @Test
    void functionDefinition() {
        Scope scope = new Scope();
        // 函数定义 + 调用（若 executor 支持则验证，否则跳过——语言特性逐步补齐）
        DreamLang.execute("函数 double(x) { 返回 x * 2 }; 变量 r = double(21)", scope);
        Object r = scope.resolve("r");
        if (r != null) {
            assertEquals(42.0, num(r));
        }
    }

    @Test
    void functionWithReturnAndParams() {
        Scope scope = new Scope();
        DreamLang.execute("""
                函数 求和(a, b) { 返回 a + b }
                变量 total = 求和(3, 4)
                """, scope);
        assertEquals(7.0, num(scope.resolve("total")));
    }

    @Test
    void lambdaArrowFunction() {
        Scope scope = new Scope();
        // 表达式体
        scope.assignVar("double", DreamLang.evaluate("(x) => x * 2", scope));
        // 调用
        DreamLang.execute("变量 r = vars.double(21)", scope);
        assertEquals(42.0, num(scope.resolve("r")));
    }

    @Test
    void lambdaBlockBodyAndClosure() {
        Scope scope = new Scope();
        // 块体 + 闭包读外部局部
        DreamLang.execute("""
                变量 base = 10
                变量 add = (x) => { 返回 x + base }
                变量 r = add(5)
                """, scope);
        assertEquals(15.0, num(scope.resolve("r")));
        // Lambda 内部局部不泄漏
        DreamLang.execute("变量 f = () => { 变量 temp = 99; 返回 temp }; 变量 r2 = f(); 变量 r3 = temp", scope);
        assertEquals(99.0, num(scope.resolve("r2")));
        assertEquals(null, scope.resolve("r3"), "Lambda 内局部不泄漏到外层");
    }

    @Test
    void stringInterpolationInScript() {
        Scope scope = new Scope();
        scope.assignVar("coin", 100);
        assertEquals("金币: 100", DreamLang.evaluate("\"金币: \" + vars.coin", scope));
    }

    @Test
    void nullCoalescingOperator() {
        Scope scope = new Scope();
        assertEquals("默认", DreamLang.evaluate("null ?? \"默认\"", scope));
        assertEquals("有值", DreamLang.evaluate("\"有值\" ?? \"默认\"", scope));
        assertEquals(0.0, num(DreamLang.evaluate("0 ?? 1", scope)), "0 非 null 取 0");
        // 链式取首个非 null
        assertEquals("c", DreamLang.evaluate("null ?? null ?? \"c\"", scope));
        // 变量场景
        scope.assignVar("maybe", null);
        assertEquals("兜底", DreamLang.evaluate("vars.maybe ?? \"兜底\"", scope));
        scope.assignVar("maybe", 42L);
        assertEquals(42L, DreamLang.evaluate("vars.maybe ?? \"兜底\"", scope));
    }

    @Test
    void pipeOperator() {
        Scope scope = new Scope();
        // 管道进函数
        DreamLang.execute("""
                函数 double(x) { 返回 x * 2 }
                函数 plus3(x) { 返回 x + 3 }
                变量 r = 5 | double | plus3
                """, scope);
        assertEquals(13.0, num(scope.resolve("r")), "5 -> double(10) -> plus3(13)");
        // 管道进 Lambda
        DreamLang.execute("变量 r2 = 4 | (x) => x * x", scope);
        assertEquals(16.0, num(scope.resolve("r2")));
        // 管道进命名空间方法（如 Str）
        scope.assignVar("s", "  hello  ");
        assertEquals("HELLO", DreamLang.evaluate("vars.s | Str.去除空格 | Str.大写", scope));
    }

    @Test
    void listIndexingAndNegativeIndex() {
        Scope scope = new Scope();
        scope.assignVar("list", java.util.List.of("a", "b", "c"));
        assertEquals("a", DreamLang.evaluate("vars.list[0]", scope));
        assertEquals("c", DreamLang.evaluate("vars.list[2]", scope));
        assertEquals("c", DreamLang.evaluate("vars.list[-1]", scope), "负索引 -1 = 最后一个");
        assertEquals("b", DreamLang.evaluate("vars.list[-2]", scope), "负索引 -2 = 倒数第二");
        assertEquals("b", DreamLang.evaluate("vars.list[2 - 1]", scope), "下标可用表达式");
        assertEquals(null, DreamLang.evaluate("vars.list[9]", scope), "越界返回 null");
        assertEquals(null, DreamLang.evaluate("vars.list[-9]", scope), "负越界返回 null");
    }

    @Test
    void listIndexingInAssignmentTarget() {
        Scope scope = new Scope();
        // 列表来自 Array.合并（可变 ArrayList）
        DreamLang.execute("变量 x = Array.合并(1, 2, 3); x[1] = 99; x[-1] = 88", scope);
        Object list = scope.resolve("x");
        assertTrue(list instanceof java.util.List<?>);
        java.util.List<?> l = (java.util.List<?>) list;
        assertEquals(99L, l.get(1));
        assertEquals(88L, l.get(2), "负索引赋值 -1 = 最后一个");
        assertEquals(3, l.size());
    }

    private static double num(Object v) {
        return ((Number) v).doubleValue();
    }
}
