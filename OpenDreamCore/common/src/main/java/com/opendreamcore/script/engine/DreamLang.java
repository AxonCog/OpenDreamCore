package com.opendreamcore.script.engine;

import com.opendreamcore.script.engine.antlr.DreamLangLexer;
import com.opendreamcore.script.engine.antlr.DreamLangParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * DreamLang 入口：解析 + 执行。
 */
public final class DreamLang {

    private DreamLang() {
    }

    /** 执行脚本，返回最后一条语句的值。 */
    public static Object execute(String script, Scope scope) {
        DreamLangParser.ProgramContext tree = parse(script);
        DreamLangExecutor executor = new DreamLangExecutor(scope);
        return executor.visitProgram(tree);
    }

    /** Host-owned asynchronous AST execution. Cancellation belongs to host scheduler. */
    public static void executeAsync(String script, Scope scope, AsyncScheduler scheduler) {
        if (script == null || scope == null || scheduler == null) {
            throw new IllegalArgumentException("script, scope and scheduler are required");
        }
        new DreamLangExecutor(scope).executeAsync(parse(script), scheduler);
    }

    /** Return value used by a host method to suspend the current AST. */
    public static PauseRequest pause(long delayMs) {
        return new PauseRequest(Math.max(0L, delayMs));
    }

    /** Host method marker for a script-backed function call. */
    public static ScriptCallRequest call(String script, Scope scope) {
        if (script == null || scope == null) {
            throw new IllegalArgumentException("script and scope are required");
        }
        return new ScriptCallRequest(script, scope);
    }

    public static final class PauseRequest {
        private final long delayMs;
        private PauseRequest(long delayMs) { this.delayMs = delayMs; }
        public long delayMs() { return delayMs; }
    }

    public static final class ScriptCallRequest {
        private final String script;
        private final Scope scope;
        private ScriptCallRequest(String script, Scope scope) {
            this.script = script;
            this.scope = scope;
        }
        public String script() { return script; }
        public Scope scope() { return scope; }
    }


    private static final ThreadLocal<Scope> CURRENT_SCOPE = new ThreadLocal<>();

    /** Scope visible to a host method while the current AST expression runs. */
    public static Scope currentScope() {
        return CURRENT_SCOPE.get();
    }

    static Scope enterScope(Scope scope) {
        Scope previous = CURRENT_SCOPE.get();
        CURRENT_SCOPE.set(scope);
        return previous;
    }

    static void leaveScope(Scope previous) {
        if (previous == null) CURRENT_SCOPE.remove();
        else CURRENT_SCOPE.set(previous);
    }
    /** 只求值表达式（布局公式/条件表达式用）。 */
    public static Object evaluate(String expression, Scope scope) {
        DreamLangParser.ProgramContext tree = parse(expression);
        DreamLangExecutor executor = new DreamLangExecutor(scope);
        Object result = null;
        for (DreamLangParser.StatementContext stmt : tree.statement()) {
            result = executor.visit(stmt);
        }
        return result;
    }

    /**
     * 结果缓存求值：布局热路径专用（宽度/高度/锚点这类纯数值表达式）。
     *
     * <p>parse 缓存只省了词法/语法，AST 遍历仍在每帧跑（节点数 × 实体数 × 帧率）。
     * 实体站桩、血量不变时同一表达式在同样变量值下结果永远一致，于是按
     * 「表达式 + 变量赋值序列」做键缓存求值结果——变量没变就零计算。
     * 血量/位置一变，键变了，自然落到实算，功能无损。
     *
     * <p>只给布局数值站点用（{@code evaluate} 仍供脚本/条件原样调用）：
     * 条件表达式可能调用带副作用的方法（如 Screen.设置元素），缓存会改变语义。
     * 布局公式是纯算术，无此风险。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> EVAL_CACHE =
            new java.util.concurrent.ConcurrentHashMap<String, Object>();

    private static final int EVAL_CACHE_MAX = 2048;

    public static Object evaluateCached(String expression, java.util.Map<String, Object> vars) {
        if (expression == null) {
            return null;
        }
        StringBuilder key = new StringBuilder(expression.length() + 64);
        key.append(expression);
        if (vars != null && !vars.isEmpty()) {
            // 排序保证同一组赋值总是同一个键（vars 是 LinkedHashMap，顺序稳定，但保险起见）
            java.util.List<String> names = new java.util.ArrayList<String>(vars.keySet());
            java.util.Collections.sort(names);
            for (String name : names) {
                key.append('\u0001').append(name).append('=').append(vars.get(name));
            }
        }
        String k = key.toString();
        Object hit = EVAL_CACHE.get(k);
        if (hit != null) {
            return hit;
        }
        Scope scope = new Scope();
        if (vars != null) {
            vars.forEach(scope::assignVar);
        }
        Object r = evaluate(expression, scope);
        if (EVAL_CACHE.size() < EVAL_CACHE_MAX) {
            EVAL_CACHE.put(k, r);
        }
        return r;
    }

    /** 规则集变化后清结果缓存（与 clearParseCache 同步调用）。 */
    public static void clearEvalCache() {
        EVAL_CACHE.clear();
    }

    /**
     * 解析结果缓存：表达式字符串 → 语法树。
     *
     * <p>为什么要缓存：{@link #evaluate} 在渲染热路径上按「节点数 × 实体数 × 每帧」调用，
     * 每次都跑一遍 ANTLR 词法+语法分析，实测头顶页面一开就卡（同一句表达式被反复解析几百次）。
     * 语法树只读（执行器不改写 context），跨作用域复用安全，所以解析一次就够。
     *
     * <p>上限 512 条：用户表达式来自配置，数量有界；超限后不再收新条目（老条目继续命中）。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, DreamLangParser.ProgramContext> PARSE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<String, DreamLangParser.ProgramContext>();

    private static final int PARSE_CACHE_MAX = 512;

    public static DreamLangParser.ProgramContext parse(String script) {
        DreamLangParser.ProgramContext hit = PARSE_CACHE.get(script);
        if (hit != null) {
            return hit;
        }
        DreamLangLexer lexer = new DreamLangLexer(CharStreams.fromString(script));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DreamLangParser parser = new DreamLangParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ThrowingErrorListener());
        DreamLangParser.ProgramContext tree = parser.program();
        if (PARSE_CACHE.size() < PARSE_CACHE_MAX) {
            PARSE_CACHE.put(script, tree);
        }
        return tree;
    }

    /** 清空解析缓存（脚本热重载/内存压力时调；不清也能跑，只是重新解析一遍）。 */
    public static void clearParseCache() {
        PARSE_CACHE.clear();
    }

    /** 缓存条目数（诊断用）。 */
    public static int parseCacheSize() {
        return PARSE_CACHE.size();
    }
}
