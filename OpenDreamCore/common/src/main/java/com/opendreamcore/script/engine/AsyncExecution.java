package com.opendreamcore.script.engine;

import com.opendreamcore.script.engine.antlr.DreamLangParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Explicit AST activation stack. No Java stack, thread, or replay survives a pause. */
final class AsyncExecution {
    private final DreamLangExecutor executor;
    private final AsyncScheduler scheduler;
    private final Thread owner = Thread.currentThread();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private Flow flow;
    private Object flowValue;
    private Long delay;
    private boolean running;

    private enum Flow { RETURN, BREAK, CONTINUE }

    private static final class Frame {
        final ParseTree node;
        final Scope scope;
        int pc;
        int index;
        int count;
        Object input;
        Object value;
        Object target;
        Object key;
        boolean hasValue;
        String operator;
        String indexName;
        String valueName;
        List<?> items;
        List<Object> args;
        List<ParseTree> children;
        Flow savedFlow;
        Object savedValue;
        RuntimeException error;
        boolean callBoundary;
        boolean discardImplicitValue;
        Frame(ParseTree node, Scope scope) { this.node = node; this.scope = scope; }
    }

    AsyncExecution(DreamLangExecutor executor, DreamLangParser.ProgramContext program, AsyncScheduler scheduler) {
        this.executor = executor;
        this.scheduler = scheduler;
        stack.push(new Frame(program, executor.scope()));
    }

    void run() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("DreamLang continuation must run on its original script thread");
        }
        if (running) throw new IllegalStateException("DreamLang scheduler must defer continuations");
        running = true;
        Scope savedScope = executor.scope();
        Scope previous = DreamLang.enterScope(savedScope);
        try {
            while (!stack.isEmpty() && delay == null) {
                Frame frame = stack.peek();
                executor.scope(frame.scope);
                DreamLang.enterScope(frame.scope);
                try {
                    step(frame);
                } catch (RuntimeException error) {
                    if (!handleError(error)) throw error;
                }
            }
        } catch (RuntimeException error) {
            stack.clear();
            throw error;
        } finally {
            executor.scope(savedScope);
            DreamLang.leaveScope(previous);
            running = false;
        }
        if (delay != null) {
            long milliseconds = delay;
            delay = null;
            // One-shot continuation: a duplicate host dispatch cannot replay effects.
            scheduler.schedule(milliseconds, new Runnable() {
                private boolean consumed;
                @Override public void run() {
                    if (Thread.currentThread() != owner) {
                        throw new IllegalStateException("DreamLang continuation must run on its original script thread");
                    }
                    if (consumed) return;
                    consumed = true;
                    AsyncExecution.this.run();
                }
            });
        }
    }

    private void push(ParseTree node, Scope scope) { stack.push(new Frame(node, scope)); }
    private void done(Object value) {
        Frame frame = stack.pop();
        if (frame.callBoundary) {
            if (flow == Flow.RETURN) value = flowValue;
            else if (frame.discardImplicitValue) value = null;
            flow = null;
            flowValue = null;
        }
        if (!stack.isEmpty()) stack.peek().input = value;
    }
    private void callBody(Frame caller, ParseTree body, Scope scope) {
        caller.pc = 3;
        Frame frame = new Frame(body, scope);
        frame.callBoundary = true;
        frame.discardImplicitValue = body instanceof DreamLangParser.LambdaBodyContext lambda
                && lambda.blockStatement() != null;
        stack.push(frame);
    }

    private void step(Frame f) {
        ParseTree node = f.node;
        if (node == null) {
            if (f.pc == 3) done(f.input);
            else invoke(f, f.target, f.args.toArray());
            return;
        }
        if (node instanceof DreamLangParser.ProgramContext p) { sequence(f, p.statement()); return; }
        if (node instanceof DreamLangParser.BlockStatementContext b) { sequence(f, b.statement()); return; }
        if (node instanceof DreamLangParser.VarDeclarationContext v) {
            if (f.pc++ == 0 && v.expression() != null) { push(v.expression(), f.scope); return; }
            f.scope.declare(v.IDENTIFIER().getText(), f.input); done(f.input); return;
        }
        if (node instanceof DreamLangParser.IfStatementContext i) {
            if (f.pc == 0) { f.pc = 1; push(i.expression(), f.scope); return; }
            if (f.pc == 1) {
                f.pc = 2;
                int branch = DreamLangExecutor.truthy(f.input) ? 0 : 1;
                if (branch < i.statement().size()) { push(i.statement(branch), f.scope); return; }
                done(null); return;
            }
            done(f.input); return;
        }
        if (node instanceof DreamLangParser.ReturnStatementContext r) {
            if (f.pc++ == 0 && r.expression() != null) { push(r.expression(), f.scope); return; }
            flow = Flow.RETURN; flowValue = f.input; done(f.input); return;
        }
        if (node instanceof DreamLangParser.BreakStatementContext) { flow = Flow.BREAK; done(null); return; }
        if (node instanceof DreamLangParser.ContinueStatementContext) { flow = Flow.CONTINUE; done(null); return; }
        if (node instanceof DreamLangParser.WhileStatementContext w) { whileLoop(f, w); return; }
        if (node instanceof DreamLangParser.ForStatementContext loop) { forLoop(f, loop); return; }
        if (node instanceof DreamLangParser.LoopStatementContext loop) { countedLoop(f, loop); return; }
        if (node instanceof DreamLangParser.ForEachStatementContext loop) { forEach(f, loop); return; }
        if (node instanceof DreamLangParser.TryStatementContext t) { tryStatement(f, t); return; }
        if (node instanceof DreamLangParser.ThrowStatementContext t) {
            if (f.pc++ == 0) { push(t.expression(), f.scope); return; }
            if (f.input instanceof RuntimeException error) throw error;
            throw new DreamLangExecutor.ScriptException(String.valueOf(f.input));
        }
        if (node instanceof DreamLangParser.AwaitStatementContext a) {
            if (f.pc == 0) { f.pc = 1; push(a.expression(), f.scope); return; }
            if (f.pc == 1) { f.pc = 2; delay = Math.max(0L, (long) DreamLangExecutor.num(f.input)); return; }
            if (f.pc == 2) { f.pc = 3; push(a.blockStatement(), f.scope); return; }
            done(f.input); return;
        }
        if (node instanceof DreamLangParser.PrimaryExpressionContext p) { primary(f, p); return; }
        if (node instanceof DreamLangParser.SuffixContext s) { suffix(f, s); return; }
        if (node instanceof DreamLangParser.PipeExpressionContext p) { pipe(f, p); return; }
        if (node instanceof DreamLangParser.TernaryExpressionContext t) {
            if (f.pc == 0) { f.pc = 1; push(t.nullCoalesceExpression(), f.scope); return; }
            if (f.pc == 1 && t.TERNARY() != null) {
                f.pc = 2;
                int branch = DreamLangExecutor.truthy(f.input) ? 0 : 1;
                if (branch < t.expression().size()) { push(t.expression(branch), f.scope); return; }
                done(null); return;
            }
            done(f.input); return;
        }
        if (node instanceof DreamLangParser.NullCoalesceExpressionContext c) {
            if (f.index == 0 || (f.input == null && f.index < c.orExpression().size())) {
                push(c.orExpression(f.index++), f.scope); return;
            }
            done(f.input); return;
        }
        if (node instanceof DreamLangParser.OrExpressionContext
                || node instanceof DreamLangParser.AndExpressionContext
                || node instanceof DreamLangParser.EqualityExpressionContext
                || node instanceof DreamLangParser.RelationalExpressionContext
                || node instanceof DreamLangParser.AdditiveExpressionContext
                || node instanceof DreamLangParser.MultiplicativeExpressionContext) { fold(f); return; }
        if (node instanceof DreamLangParser.UnaryExpressionContext u) {
            if (f.pc++ == 0) { push(u.unaryExpression() == null ? u.primary() : u.unaryExpression(), f.scope); return; }
            if (u.unaryExpression() == null) done(f.input);
            else done("!".equals(u.getChild(0).getText()) ? !DreamLangExecutor.truthy(f.input) : -DreamLangExecutor.num(f.input));
            return;
        }
        if (node instanceof DreamLangParser.IdentifierAtomContext
                || node instanceof DreamLangParser.NumberAtomContext
                || node instanceof DreamLangParser.StringAtomContext
                || node instanceof DreamLangParser.BooleanAtomContext
                || node instanceof DreamLangParser.NullAtomContext
                || node instanceof DreamLangParser.LambdaExpressionContext
                || node instanceof DreamLangParser.FunctionDefinitionStatementContext) {
            done(executor.visit(node)); return;
        }
        // Structural wrappers (Statement/ExpressionBody/ParenAtom/BlockAtom/
        // ForInit/LambdaBody/PipeTarget) retain child results and control flow.
        if (node instanceof TerminalNode) { done(null); return; }
        if (f.children == null) {
            f.children = new ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                if (!(child instanceof TerminalNode)) f.children.add(child);
            }
        }
        sequence(f, f.children);
    }

    private void sequence(Frame f, List<? extends ParseTree> children) {
        if (f.index > 0) f.value = f.input;
        if (flow != null || f.index >= children.size()) { done(f.value); return; }
        push(children.get(f.index++), f.scope);
    }

    private boolean loopFlow(Frame f) {
        if (flow == Flow.RETURN) { done(f.value); return true; }
        if (flow == Flow.BREAK) { flow = null; done(f.value); return true; }
        if (flow == Flow.CONTINUE) flow = null;
        return false;
    }

    private void whileLoop(Frame f, DreamLangParser.WhileStatementContext loop) {
        if (f.pc == 0) { f.pc = 1; push(loop.expression(), f.scope); return; }
        if (f.pc == 1) {
            if (!DreamLangExecutor.truthy(f.input)) { done(f.value); return; }
            f.pc = 2; push(loop.blockStatement() != null ? loop.blockStatement() : loop.statement(), f.scope); return;
        }
        f.value = f.input;
        if (!loopFlow(f)) f.pc = 0;
    }

    private void forLoop(Frame f, DreamLangParser.ForStatementContext loop) {
        // Locate condition/update by semicolon positions: expression(0) is the
        // update when the optional condition is absent.
        if (f.children == null) {
            f.children = new ArrayList<>();
            f.children.add(null); f.children.add(null);
            int part = -1;
            for (int i = 0; i < loop.getChildCount(); i++) {
                ParseTree child = loop.getChild(i);
                if (";".equals(child.getText())) part++;
                else if (child instanceof DreamLangParser.ExpressionContext && part >= 0 && part < 2) f.children.set(part, child);
            }
        }
        if (f.pc == 0) { f.pc = 1; if (loop.forInit() != null) { push(loop.forInit(), f.scope); return; } }
        if (f.pc == 1) { f.pc = 2; if (f.children.get(0) != null) { push(f.children.get(0), f.scope); return; } f.input = true; }
        if (f.pc == 2) {
            if (!DreamLangExecutor.truthy(f.input)) { done(f.value); return; }
            f.pc = 3; push(loop.blockStatement() != null ? loop.blockStatement() : loop.statement(), f.scope); return;
        }
        if (f.pc == 3) {
            f.value = f.input;
            if (loopFlow(f)) return;
            f.pc = 4;
            if (f.children.get(1) != null) { push(f.children.get(1), f.scope); return; }
        }
        f.pc = 1;
    }

    private void countedLoop(Frame f, DreamLangParser.LoopStatementContext loop) {
        if (f.pc == 0) { f.pc = 1; push(loop.expression(), f.scope); return; }
        if (f.pc == 1) { f.count = f.input instanceof Number ? ((Number) f.input).intValue() : 0; f.pc = 2; }
        if (f.pc == 3) { f.value = f.input; if (loopFlow(f)) return; f.pc = 2; }
        if (f.index++ >= f.count) { done(f.value); return; }
        f.pc = 3; push(loop.blockStatement() != null ? loop.blockStatement() : loop.statement(), f.scope);
    }

    private void forEach(Frame f, DreamLangParser.ForEachStatementContext loop) {
        if (f.pc == 0) { f.pc = 1; push(loop.expression(0), f.scope); return; }
        if (f.pc == 1) { f.items = f.input instanceof List<?> ? (List<?>) f.input : null; f.pc = 2; push(loop.expression(1), f.scope); return; }
        if (f.pc == 2) { f.indexName = f.input == null ? "i" : String.valueOf(f.input); f.pc = 3; push(loop.expression(2), f.scope); return; }
        if (f.pc == 3) { f.valueName = f.input == null ? "v" : String.valueOf(f.input); f.pc = 4; }
        if (f.pc == 5) { f.value = f.input; if (loopFlow(f)) return; f.pc = 4; }
        if (f.items == null || f.index >= f.items.size()) { done(f.value); return; }
        f.scope.declare(f.indexName, (double) f.index);
        f.scope.declare(f.valueName, f.items.get(f.index++));
        f.pc = 5; push(loop.blockStatement() != null ? loop.blockStatement() : loop.statement(), f.scope);
    }

    private void fold(Frame f) {
        if (f.pc == 1) {
            if (!f.hasValue) {
                f.value = f.input;
                f.hasValue = true;
            } else {
                f.value = DreamLangExecutor.applyBinary(f.operator, f.value, f.input);
            }
            f.pc = 0;
        }
        while (f.index < f.node.getChildCount()) {
            ParseTree child = f.node.getChild(f.index++);
            if (child instanceof TerminalNode) {
                f.operator = child.getText();
                if (isShortCircuit(f.node, f.operator)
                        && ("&&".equals(f.operator)
                        ? !DreamLangExecutor.truthy(f.value)
                        : DreamLangExecutor.truthy(f.value))) {
                    done(f.value);
                    return;
                }
                continue;
            }
            f.pc = 1;
            push(child, f.scope);
            return;
        }
        done(f.value);
    }

    private static boolean isShortCircuit(ParseTree node, String op) {
        return (node instanceof DreamLangParser.AndExpressionContext && "&&".equals(op))
                || (node instanceof DreamLangParser.OrExpressionContext && "||".equals(op));
    }

    private void primary(Frame f, DreamLangParser.PrimaryExpressionContext primary) {
        if (f.pc == 0) { f.pc = 1; push(primary.atom(), f.scope); return; }
        if (f.pc == 1) {
            f.target = f.input;
            if (f.index < primary.suffix().size()) {
                DreamLangParser.SuffixContext suffix = primary.suffix(f.index++);
                boolean assignment = primary.expression() != null && f.index == primary.suffix().size();
                if (assignment && suffix instanceof DreamLangParser.DotAccessSuffixContext dot) {
                    f.key = dot.IDENTIFIER().getText(); f.pc = 2; push(primary.expression(), f.scope); return;
                }
                if (assignment && suffix instanceof DreamLangParser.ArrayAccessSuffixContext array) {
                    f.pc = 4; push(array.expression(), f.scope); return;
                }
                if (assignment) throw new DreamLangExecutor.ScriptException("不可赋值: " + suffix.getText());
                Frame child = new Frame(suffix, f.scope); child.target = f.target; stack.push(child); return;
            }
            if (primary.expression() != null) { f.pc = 2; push(primary.expression(), f.scope); return; }
            done(f.target); return;
        }
        if (f.pc == 4) { f.key = f.input; f.pc = 2; push(primary.expression(), f.scope); return; }
        if (primary.suffix().isEmpty()) executor.assignTarget(primary.atom(), f.input);
        else assign(f.target, f.key, f.input);
        done(f.input);
    }

    @SuppressWarnings("unchecked")
    private static void assign(Object target, Object key, Object value) {
        if (target instanceof List<?> list && key instanceof Number number) {
            int index = number.intValue(); if (index < 0) index += list.size();
            if (index < 0 || index >= list.size()) throw new DreamLangExecutor.ScriptException("列表下标越界: " + index);
            ((List<Object>) list).set(index, value); return;
        }
        if (target instanceof Map<?, ?> map && key != null) { ((Map<Object, Object>) map).put(String.valueOf(key), value); return; }
        if (target instanceof DreamLangExecutor.MapLike map && key != null) { map.put(String.valueOf(key), value); return; }
        throw new DreamLangExecutor.ScriptException("不可赋值: " + target);
    }

    private void suffix(Frame f, DreamLangParser.SuffixContext suffix) {
        if (suffix instanceof DreamLangParser.DotAccessSuffixContext dot) { done(DreamLangExecutor.access(f.target, dot.IDENTIFIER().getText())); return; }
        if (suffix instanceof DreamLangParser.DotAccessNumberSuffixContext dot) { done(DreamLangExecutor.access(f.target, dot.NUMBER().getText())); return; }
        if (suffix instanceof DreamLangParser.ArrayAccessSuffixContext array) {
            if (f.pc++ == 0) { push(array.expression(), f.scope); return; }
            done(DreamLangExecutor.index(f.target, f.input)); return;
        }
        if (f.pc == 3) { done(f.input); return; }
        if (f.args == null) f.args = new ArrayList<>();
        if (f.pc == 1) { f.args.add(f.input); f.pc = 0; }
        if (suffix instanceof DreamLangParser.FunctionCallWithArgsSuffixContext call && f.index < call.expression().size()) {
            f.pc = 1; push(call.expression(f.index++), f.scope); return;
        }
        invoke(f, f.target, f.args.toArray());
    }

    private void invoke(Frame f, Object target, Object[] args) {
        if (target instanceof DreamLangExecutor.ScriptCallable function) {
            callBody(f, function.scriptBody(), function.callScope(f.scope, args)); return;
        }
        if (!(target instanceof DreamLangExecutor.Callable callable)) throw new DreamLangExecutor.ScriptException("不可调用: " + target);
        Object value = callable.call(args);
        if (value instanceof DreamLang.ScriptCallRequest call) { callBody(f, DreamLang.parse(call.script()), call.scope()); return; }
        if (value instanceof DreamLang.PauseRequest pause) { done(null); delay = pause.delayMs(); return; }
        done(value);
    }

    private void pipe(Frame f, DreamLangParser.PipeExpressionContext pipe) {
        if (f.pc == 0) { f.pc = 1; push(pipe.ternaryExpression(), f.scope); return; }
        if (f.pc == 1 || f.pc == 3) {
            f.value = f.input;
            if (f.index >= pipe.pipeTarget().size()) { done(f.value); return; }
            f.pc = 2;
            push(pipe.pipeTarget(f.index++), f.scope);
            return;
        }
        // An invocation-only activation retains the surrounding pipe chain.
        Frame call = new Frame(null, f.scope);
        call.target = f.input;
        call.args = new ArrayList<>();
        call.args.add(f.value);
        f.pc = 3;
        stack.push(call);
    }

    private void tryStatement(Frame f, DreamLangParser.TryStatementContext statement) {
        if (f.pc == 0) { f.pc = 1; push(statement.blockStatement(0), f.scope); return; }
        if (f.pc == 1 || f.pc == 2) {
            f.value = f.input;
            f.savedFlow = flow; f.savedValue = flowValue; flow = null; flowValue = null;
            f.pc = 3;
            if (statement.FINALLY() != null) { push(statement.blockStatement(statement.blockStatement().size() - 1), f.scope); return; }
        }
        if (flow == null) { flow = f.savedFlow; flowValue = f.savedValue; }
        if (f.error != null && flow == f.savedFlow) throw f.error;
        done(f.value);
    }

    private boolean handleError(RuntimeException error) {
        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            if (frame.node instanceof DreamLangParser.TryStatementContext statement && frame.pc < 3) {
                flow = null; flowValue = null;
                if (frame.pc == 1 && statement.catchClause() != null) {
                    Scope caught = frame.scope.child();
                    caught.declare(statement.catchClause().IDENTIFIER().getText(), error);
                    frame.pc = 2; push(statement.catchClause().blockStatement(), caught); return true;
                }
                frame.error = error; frame.savedFlow = null; frame.savedValue = null; frame.pc = 3;
                if (statement.FINALLY() != null) { push(statement.blockStatement(statement.blockStatement().size() - 1), frame.scope); return true; }
            }
            stack.pop();
        }
        return false;
    }
}
