package com.opendreamcore.script;

import com.opendreamcore.script.antlr.DreamLangBaseVisitor;
import com.opendreamcore.script.antlr.DreamLangParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DreamLang 执行器：AST → 值。
 * 支持：分层表达式求值（三元/逻辑/相等/比较/加减/乘除/一元）、
 * var/const 声明、if/else、赋值、方法调用（方法.xxx）、成员/索引访问。
 * 完整语言特性（循环/类/模块/异常/异步）按需逐步补齐。
 */
public final class DreamLangExecutor extends DreamLangBaseVisitor<Object> {

    private Scope scope;
    /** 控制流信号：return/break/continue。 */
    private Signal signal;
    /** 用户函数表（函数 名称(参数) { ... }）。 */
    private final Map<String, Callable> functions = new java.util.LinkedHashMap<>();

    public DreamLangExecutor(Scope scope) {
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    // ---------- 函数定义 / Lambda ----------

    @Override
    public Object visitFunctionDefinitionStatement(DreamLangParser.FunctionDefinitionStatementContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        functions.put(name, new UserFunction(paramNames(ctx.parameterList()), ctx.blockStatement()));
        return null;
    }

    @Override
    public Object visitLambdaExpression(DreamLangParser.LambdaExpressionContext ctx) {
        return new LambdaFunction(paramNames(ctx.parameterList()), ctx.lambdaBody());
    }

    private static List<String> paramNames(DreamLangParser.ParameterListContext ctx) {
        List<String> names = new ArrayList<>();
        if (ctx != null) {
            for (var id : ctx.IDENTIFIER()) {
                names.add(id.getText());
            }
        }
        return names;
    }

    /** 用户函数：调用时开子作用域执行体。 */
    private final class UserFunction implements Callable {
        private final List<String> params;
        private final DreamLangParser.BlockStatementContext body;

        UserFunction(List<String> params, DreamLangParser.BlockStatementContext body) {
            this.params = params;
            this.body = body;
        }

        @Override
        public Object call(Object[] args) {
            Scope saved = scope;
            Scope child = scope.child();
            for (int i = 0; i < params.size(); i++) {
                child.assign(params.get(i), i < args.length ? args[i] : null);
            }
            return runIn(child, () -> visit(body));
        }

        @Override
        public Object member(String name) {
            throw new ScriptException("函数没有成员: " + name);
        }
    }

    /** Lambda 箭头函数：(x) => 表达式 / (x) => { 语句 }。 */
    private final class LambdaFunction implements Callable {
        private final List<String> params;
        private final DreamLangParser.LambdaBodyContext body;

        LambdaFunction(List<String> params, DreamLangParser.LambdaBodyContext body) {
            this.params = params;
            this.body = body;
        }

        @Override
        public Object call(Object[] args) {
            Scope saved = scope;
            Scope child = scope.child();
            for (int i = 0; i < params.size(); i++) {
                child.assign(params.get(i), i < args.length ? args[i] : null);
            }
            return runIn(child, () -> body.expression() != null
                    ? visit(body.expression()) : visit(body.blockStatement()));
        }

        @Override
        public Object member(String name) {
            throw new ScriptException("Lambda 没有成员: " + name);
        }
    }

    /** 在指定作用域执行并恢复（return 信号被消费为返回值）。 */
    private Object runIn(Scope child, java.util.function.Supplier<Object> body) {
        Scope saved = scope;
        Signal savedSignal = signal;
        signal = null;
        scope = child;
        try {
            Object result = body.get();
            if (signal != null && signal.type() == Signal.Type.RETURN) {
                result = signal.value();
            }
            return result;
        } finally {
            scope = saved;
            signal = savedSignal;
        }
    }

    // ---------- 语句 ----------

    @Override
    public Object visitProgram(DreamLangParser.ProgramContext ctx) {
        Object last = null;
        for (DreamLangParser.StatementContext stmt : ctx.statement()) {
            last = visit(stmt);
            if (signal != null) {
                break;
            }
        }
        return last;
    }

    @Override
    public Object visitStatement(DreamLangParser.StatementContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitVarDeclaration(DreamLangParser.VarDeclarationContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        Object value = ctx.expression() == null ? null : visit(ctx.expression());
        scope.declare(name, value);
        return value;
    }

    @Override
    public Object visitIfStatement(DreamLangParser.IfStatementContext ctx) {
        boolean cond = truthy(visit(ctx.expression()));
        if (cond) {
            return visit(ctx.statement(0));
        }
        if (ctx.ELSE() != null && ctx.statement().size() > 1) {
            return visit(ctx.statement(1));
        }
        return null;
    }

    @Override
    public Object visitReturnStatement(DreamLangParser.ReturnStatementContext ctx) {
        Object value = ctx.expression() == null ? null : visit(ctx.expression());
        signal = Signal.returning(value);
        return value;
    }

    // ---------- 循环 ----------

    @Override
    public Object visitWhileStatement(DreamLangParser.WhileStatementContext ctx) {
        Object last = null;
        while (truthy(visit(ctx.expression()))) {
            last = ctx.blockStatement() != null ? visit(ctx.blockStatement()) : visit(ctx.statement());
            if (signal != null) {
                if (signal.type() == Signal.Type.BREAK) {
                    signal = null;
                    break;
                }
                if (signal.type() == Signal.Type.CONTINUE) {
                    signal = null;
                    continue;
                }
                return last; // return 向上传
            }
        }
        return last;
    }

    @Override
    public Object visitForStatement(DreamLangParser.ForStatementContext ctx) {
        if (ctx.forInit() != null) {
            visit(ctx.forInit());
        }
        Object last = null;
        while (true) {
            if (ctx.expression(0) != null && !truthy(visit(ctx.expression(0)))) {
                break;
            }
            last = ctx.blockStatement() != null ? visit(ctx.blockStatement()) : visit(ctx.statement());
            if (signal != null) {
                if (signal.type() == Signal.Type.BREAK) {
                    signal = null;
                    break;
                }
                if (signal.type() == Signal.Type.CONTINUE) {
                    signal = null;
                    // 不退出，先跑 update
                } else {
                    return last;
                }
            }
            if (ctx.expression(1) != null) {
                visit(ctx.expression(1));
            }
        }
        return last;
    }

    @Override
    public Object visitLoopStatement(DreamLangParser.LoopStatementContext ctx) {
        Object countValue = visit(ctx.expression());
        int count = countValue instanceof Number n ? n.intValue() : 0;
        Object last = null;
        for (int i = 0; i < count; i++) {
            last = ctx.blockStatement() != null ? visit(ctx.blockStatement()) : visit(ctx.statement());
            if (signal != null) {
                if (signal.type() == Signal.Type.BREAK) {
                    signal = null;
                    break;
                }
                if (signal.type() == Signal.Type.CONTINUE) {
                    signal = null;
                    continue;
                }
                return last;
            }
        }
        return last;
    }

    /** foreach(集合, 索引变量名, 值变量名, ?, 块)：变量名按字符串求值。 */
    @Override
    public Object visitForEachStatement(DreamLangParser.ForEachStatementContext ctx) {
        Object data = visit(ctx.expression(0));
        String indexName = textOf(visit(ctx.expression(1)), "i");
        String valueName = textOf(visit(ctx.expression(2)), "v");
        if (!(data instanceof List<?> list)) {
            return null;
        }
        Object last = null;
        for (int i = 0; i < list.size(); i++) {
            scope.declare(indexName, (double) i);
            scope.declare(valueName, list.get(i));
            last = ctx.blockStatement() != null ? visit(ctx.blockStatement()) : visit(ctx.statement());
            if (signal != null) {
                if (signal.type() == Signal.Type.BREAK) {
                    signal = null;
                    break;
                }
                if (signal.type() == Signal.Type.CONTINUE) {
                    signal = null;
                    continue;
                }
                return last;
            }
        }
        return last;
    }

    @Override
    public Object visitBreakStatement(DreamLangParser.BreakStatementContext ctx) {
        signal = Signal.breaking();
        return null;
    }

    @Override
    public Object visitContinueStatement(DreamLangParser.ContinueStatementContext ctx) {
        signal = Signal.continuing();
        return null;
    }

    private static String textOf(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v);
    }

    // ---------- 分层表达式 ----------

    @Override
    public Object visitExpressionBody(DreamLangParser.ExpressionBodyContext ctx) {
        return visit(ctx.pipeExpression());
    }

    /** 管道：x | fn → fn(x)（右侧可调用对象：函数/Lambda/命名空间方法）。 */
    @Override
    public Object visitPipeExpression(DreamLangParser.PipeExpressionContext ctx) {
        Object value = visit(ctx.ternaryExpression());
        for (int i = 0; i < ctx.pipeTarget().size(); i++) {
            Object fn = visit(ctx.pipeTarget(i));
            if (fn instanceof Callable c) {
                value = c.call(new Object[]{value});
            } else {
                throw new ScriptException("管道右侧不是可调用对象: " + fn);
            }
        }
        return value;
    }

    /** 空合并：a ?? b → a 非 null 取 a，否则取 b；链式取首个非 null。 */
    @Override
    public Object visitNullCoalesceExpression(DreamLangParser.NullCoalesceExpressionContext ctx) {
        Object value = visit(ctx.orExpression(0));
        for (int i = 1; i < ctx.orExpression().size(); i++) {
            if (value == null) {
                value = visit(ctx.orExpression(i));
            } else {
                break;
            }
        }
        return value;
    }

    @Override
    public Object visitTernaryExpression(DreamLangParser.TernaryExpressionContext ctx) {
        Object cond = visit(ctx.nullCoalesceExpression());
        if (ctx.TERNARY() == null) {
            return cond;
        }
        if (truthy(cond)) {
            return visit(ctx.expression(0));
        }
        return ctx.COLON() != null ? visit(ctx.expression(1)) : null;
    }

    @Override
    public Object visitOrExpression(DreamLangParser.OrExpressionContext ctx) {
        return fold(ctx, DreamLangParser.AndExpressionContext.class);
    }

    @Override
    public Object visitAndExpression(DreamLangParser.AndExpressionContext ctx) {
        return fold(ctx, DreamLangParser.EqualityExpressionContext.class);
    }

    @Override
    public Object visitEqualityExpression(DreamLangParser.EqualityExpressionContext ctx) {
        return fold(ctx, DreamLangParser.RelationalExpressionContext.class);
    }

    @Override
    public Object visitRelationalExpression(DreamLangParser.RelationalExpressionContext ctx) {
        return fold(ctx, DreamLangParser.AdditiveExpressionContext.class);
    }

    @Override
    public Object visitAdditiveExpression(DreamLangParser.AdditiveExpressionContext ctx) {
        return fold(ctx, DreamLangParser.MultiplicativeExpressionContext.class);
    }

    @Override
    public Object visitMultiplicativeExpression(DreamLangParser.MultiplicativeExpressionContext ctx) {
        return fold(ctx, DreamLangParser.UnaryExpressionContext.class);
    }

    @Override
    public Object visitUnaryExpression(DreamLangParser.UnaryExpressionContext ctx) {
        if (ctx.unaryExpression() != null) {
            String op = ((TerminalNode) ctx.getChild(0)).getText();
            Object v = visit(ctx.unaryExpression());
            if ("!".equals(op)) {
                return !truthy(v);
            }
            return -num(v);
        }
        return visit(ctx.primary());
    }

    /** 按层折叠二元运算：操作数交替出现，操作符为中间 token。 */
    private Object fold(ParserRuleContext ctx, Class<?> operandClass) {
        Object result = null;
        String op = null;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (operandClass.isInstance(child)) {
                Object v = visit(child);
                if (result == null) {
                    result = v;
                } else {
                    result = applyBinary(op, result, v);
                }
            } else if (child instanceof TerminalNode t) {
                op = t.getText();
            }
        }
        return result;
    }

    private Object applyBinary(String op, Object l, Object r) {
        switch (op) {
            case "+":
                return (l instanceof String || r instanceof String)
                        ? String.valueOf(l) + String.valueOf(r) : num(l) + num(r);
            case "-":
                return num(l) - num(r);
            case "*":
                return num(l) * num(r);
            case "/":
                return num(l) / num(r);
            case "%":
                return num(l) % num(r);
            case "==":
                return java.util.Objects.equals(normalize(l), normalize(r));
            case "!=":
                return !java.util.Objects.equals(normalize(l), normalize(r));
            case ">":
                return num(l) > num(r);
            case ">=":
                return num(l) >= num(r);
            case "<":
                return num(l) < num(r);
            case "<=":
                return num(l) <= num(r);
            case "&&":
                return truthy(l) ? r : l;
            case "||":
                return truthy(l) ? l : r;
            default:
                throw new ScriptException("未知运算符: " + op);
        }
    }

    // ---------- primary（原子 + 后缀）----------

    @Override
    public Object visitPrimaryExpression(DreamLangParser.PrimaryExpressionContext ctx) {
        Object target = visit(ctx.atom());
        for (DreamLangParser.SuffixContext suffix : ctx.suffix()) {
            target = visitSuffix(target, suffix);
        }
        if (ctx.expression() != null) {
            Object value = visit(ctx.expression());
            if (!ctx.suffix().isEmpty()) {
                assignSuffixTarget(ctx, value);
            } else {
                assignTarget(ctx.atom(), value);
            }
            return value;
        }
        return target;
    }

    /** 赋值目标：裸标识符 → 局部/页面变量。 */
    private void assignTarget(DreamLangParser.AtomContext atom, Object value) {
        if (atom instanceof DreamLangParser.IdentifierAtomContext id) {
            String name = id.IDENTIFIER().getText();
            if ("vars".equals(name) || "global".equals(name) || "player".equals(name) || "papi".equals(name)) {
                throw new ScriptException(name + " 是命名空间，不能直接赋值");
            }
            scope.assign(name, value);
            return;
        }
        throw new ScriptException("暂不支持的赋值目标: " + atom.getText());
    }

    /** 后缀赋值：list[i] = v / map[key] = v（最后一个后缀必须是下标访问）。 */
    @SuppressWarnings("unchecked")
    private void assignSuffixTarget(DreamLangParser.PrimaryExpressionContext ctx, Object value) {
        Object target = visit(ctx.atom());
        List<DreamLangParser.SuffixContext> suffixes = ctx.suffix();
        for (int i = 0; i < suffixes.size() - 1; i++) {
            target = visitSuffix(target, suffixes.get(i));
        }
        DreamLangParser.SuffixContext last = suffixes.get(suffixes.size() - 1);
        if (last instanceof DreamLangParser.ArrayAccessSuffixContext arr) {
            Object index = visit(arr.expression());
            if (target instanceof java.util.List<?> list && index instanceof Number n) {
                int i = n.intValue();
                if (i < 0) {
                    i = list.size() + i; // 负索引赋值
                }
                if (i >= 0 && i < list.size()) {
                    ((java.util.List<Object>) list).set(i, value);
                    return;
                }
                throw new ScriptException("列表下标越界: " + i);
            }
            if (target instanceof java.util.Map<?, ?> m && index != null) {
                ((java.util.Map<Object, Object>) m).put(String.valueOf(index), value);
                return;
            }
            throw new ScriptException("暂不支持的赋值目标: " + ctx.getText());
        }
        throw new ScriptException("暂不支持的赋值目标: " + ctx.getText());
    }

    private Object visitSuffix(Object target, DreamLangParser.SuffixContext ctx) {
        if (ctx instanceof DreamLangParser.FunctionCallWithArgsSuffixContext call) {
            List<Object> args = new ArrayList<>();
            for (DreamLangParser.ExpressionContext e : call.expression()) {
                args.add(visit(e));
            }
            return invoke(target, args.toArray());
        }
        if (ctx instanceof DreamLangParser.FunctionCallSuffixContext) {
            return invoke(target, new Object[0]);
        }
        if (ctx instanceof DreamLangParser.DotAccessSuffixContext dot) {
            return access(target, dot.IDENTIFIER().getText());
        }
        if (ctx instanceof DreamLangParser.DotAccessNumberSuffixContext dotNum) {
            return access(target, dotNum.NUMBER().getText());
        }
        if (ctx instanceof DreamLangParser.ArrayAccessSuffixContext arr) {
            Object index = visit(arr.expression());
            return index(target, index);
        }
        return null;
    }

    /** 方法调用：方法.xxx(...) / 函数(...) / 对象.方法(...)。 */
    private Object invoke(Object target, Object[] args) {
        if (target instanceof Callable callable) {
            return callable.call(args);
        }
        throw new ScriptException("不可调用: " + target);
    }

    private Object access(Object target, String member) {
        if (target instanceof Callable c) {
            return c.member(member);
        }
        if (target instanceof MapLike map) {
            return map.get(member);
        }
        if (target instanceof java.util.Map<?, ?> m) {
            return m.get(member);
        }
        throw new ScriptException("成员访问失败: " + target + "." + member);
    }

    private Object index(Object target, Object index) {
        if (target instanceof MapLike map && index != null) {
            return map.get(String.valueOf(index));
        }
        if (target instanceof java.util.Map<?, ?> m && index != null) {
            return m.get(String.valueOf(index));
        }
        if (target instanceof java.util.List<?> list && index instanceof Number n) {
            int i = n.intValue();
            if (i < 0) {
                i = list.size() + i; // 负索引：-1 = 最后一个
            }
            if (i >= 0 && i < list.size()) {
                return list.get(i);
            }
            return null; // 越界返回 null（不抛错，脚本可判空）
        }
        throw new ScriptException("索引访问失败: " + target + "[" + index + "]");
    }

    // ---------- 原子与字面量 ----------

    @Override
    public Object visitNumberAtom(DreamLangParser.NumberAtomContext ctx) {
        String text = ctx.NUMBER().getText();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return Double.parseDouble(text);
        }
    }

    @Override
    public Object visitStringAtom(DreamLangParser.StringAtomContext ctx) {
        return unquote(ctx.getText());
    }

    @Override
    public Object visitBooleanAtom(DreamLangParser.BooleanAtomContext ctx) {
        return Boolean.parseBoolean(ctx.getText());
    }

    @Override
    public Object visitNullAtom(DreamLangParser.NullAtomContext ctx) {
        return null;
    }

    @Override
    public Object visitIdentifierAtom(DreamLangParser.IdentifierAtomContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        if ("方法".equals(name)) {
            return new MethodNamespace();
        }
        if (NamespaceRegistry.containsNamespace(name)) {
            return new NamespaceObject(name);
        }
        Callable fn = functions.get(name);
        if (fn != null) {
            return fn;
        }
        return scope.resolve(name);
    }

    @Override
    public Object visitParenAtom(DreamLangParser.ParenAtomContext ctx) {
        return visit(ctx.expression());
    }

    // ---------- 方法命名空间 ----------

    /** 方法命名空间对象：方法.xxx(...) → 方法注册表。 */
    private final class MethodNamespace implements Callable {
        @Override
        public Object call(Object[] args) {
            throw new ScriptException("方法 是命名空间，需要 .方法名(...)");
        }

        @Override
        public Object member(String name) {
            return new BoundMethod(name);
        }
    }

    /** 已绑定方法名：调用时查注册表。 */
    private final class BoundMethod implements Callable {
        private final String name;

        BoundMethod(String name) {
            this.name = name;
        }

        @Override
        public Object call(Object[] args) {
            MethodRegistry.Handler handler = MethodRegistry.require(name);
            return handler.invoke(args);
        }
    }

    /** 命名空间对象（Player/Chat/Display...）：ns.方法(...) → 命名空间注册表。 */
    private final class NamespaceObject implements Callable {
        private final String ns;

        NamespaceObject(String ns) {
            this.ns = ns;
        }

        @Override
        public Object call(Object[] args) {
            throw new ScriptException(ns + " 是命名空间，需要 .方法名(...)");
        }

        @Override
        public Object member(String name) {
            return new BoundNsMethod(ns, name);
        }
    }

    /** 已绑定命名空间方法：调用时查注册表。 */
    private final class BoundNsMethod implements Callable {
        private final String ns;
        private final String name;

        BoundNsMethod(String ns, String name) {
            this.ns = ns;
            this.name = name;
        }

        @Override
        public Object call(Object[] args) {
            return NamespaceRegistry.require(ns, name).invoke(args);
        }
    }

    /** 可调用对象（方法/函数）。 */
    public interface Callable {
        Object call(Object[] args);

        default Object member(String name) {
            throw new ScriptException("不可访问成员");
        }
    }

    /** Map 访问适配。 */
    public interface MapLike {
        Object get(String key);
    }

    /** 包装 java.util.Map 为 MapLike。 */
    public static MapLike wrapMap(java.util.Map<String, Object> map) {
        return map::get;
    }

    private static Object normalize(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return v;
    }

    private static double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return 0;
        }
        throw new ScriptException("不是数字: " + v);
    }

    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    private static String unquote(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    /** 控制流信号。 */
    private record Signal(Type type, Object value) {
        static Signal returning(Object value) {
            return new Signal(Type.RETURN, value);
        }

        static Signal breaking() {
            return new Signal(Type.BREAK, null);
        }

        static Signal continuing() {
            return new Signal(Type.CONTINUE, null);
        }

        enum Type { RETURN, BREAK, CONTINUE }
    }

    public static final class ScriptException extends RuntimeException {
        public ScriptException(String message) {
            super(message);
        }
    }
}
