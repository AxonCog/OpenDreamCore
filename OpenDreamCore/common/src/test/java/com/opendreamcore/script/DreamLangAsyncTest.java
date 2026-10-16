package com.opendreamcore.script;

import com.opendreamcore.script.engine.AsyncScheduler;
import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.DreamLangExecutor;
import com.opendreamcore.script.engine.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Manual clock: no sleeps, background workers, or Minecraft dependencies. */
class DreamLangAsyncTest {
    private static final String DELAY = "__async_test_delay__";

    private static final class ManualScheduler implements AsyncScheduler {
        private static final class Scheduled {
            final long due;
            final Runnable continuation;
            Scheduled(long due, Runnable continuation) { this.due = due; this.continuation = continuation; }
        }
        final Deque<Scheduled> queue = new ArrayDeque<>();
        long now;
        @Override public void schedule(long delayMs, Runnable continuation) {
            queue.addLast(new Scheduled(now + delayMs, continuation));
        }
        void advance(long milliseconds) {
            now += milliseconds;
            while (!queue.isEmpty() && queue.peekFirst().due <= now) queue.removeFirst().continuation.run();
        }
        void finish() {
            int remaining = 100;
            while (!queue.isEmpty()) {
                assertTrue(remaining-- > 0, "unexpected infinite continuation");
                advance(queue.peekFirst().due - now);
            }
        }
    }

    private static void withDelay(Runnable test) {
        MethodRegistry.registerOrReplace(DELAY, args -> DreamLang.pause(((Number) args[0]).longValue()));
        try { test.run(); } finally { MethodRegistry.unregister(DELAY); }
    }
    private static String pause(long milliseconds) { return "方法." + DELAY + "(" + milliseconds + ")"; }
    private static int number(Scope scope, String name) { return ((Number) scope.resolve(name)).intValue(); }

    @Test void sideEffectsRunOnceAndIfResumesInsideBlock() {
        withDelay(() -> {
            Scope scope = new Scope();
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync("var hits = 0; if (true) { hits = hits + 1; " + pause(500)
                    + "; hits = hits + 10 }; hits = hits + 100", scope, scheduler);
            assertEquals(1, number(scope, "hits"));
            assertEquals(1, scheduler.queue.size());
            Runnable duplicate = scheduler.queue.peekFirst().continuation;
            scheduler.advance(499);
            assertEquals(1, number(scope, "hits"));
            scheduler.advance(1);
            assertEquals(111, number(scope, "hits"));
            duplicate.run();
            assertEquals(111, number(scope, "hits"));
        });
    }

    @Test void nestedLoopsBreakContinueAndDelayedFunctionResults() {
        withDelay(() -> {
            Scope scope = new Scope();
            ManualScheduler scheduler = new ManualScheduler();
            String script = "function add(x) { var local = x + 1; " + pause(10)
                    + "; return local * 2; vars.unreachable = 1 }; var total = 0; "
                    + "for (var i = 0; i < 3; i = i + 1) { loop(2, { " + pause(1)
                    + "; if (i == 1) { continue }; total = total + add(i); if (i == 2) { break } }) }; vars.done = total";
            DreamLang.executeAsync(script, scope, scheduler);
            assertNull(scope.resolve("vars", "done"));
            scheduler.finish();
            assertEquals(10, number(scope, "total"));
            assertEquals(10, ((Number) scope.resolve("vars", "done")).intValue());
            assertNull(scope.resolve("vars", "unreachable"));
            assertNull(scope.resolve("local"));
            assertEquals(3, number(scope, "i"));
        });
    }

    @Test void whileForEachLambdaAndPipeResume() {
        withDelay(() -> {
            Scope scope = new Scope();
            scope.assignVar("items", List.of(2L, 3L));
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync("var total = 0; var fn = (x) => { " + pause(2)
                    + "; return x + 1 }; for_each(items, 'idx', 'item', 0, { total = total + fn(item) }); "
                    + "var i = 0; while(i < 2) { " + pause(3)
                    + "; i = i + 1; total = total + 1 }; vars.pipe = total | fn", scope, scheduler);
            scheduler.finish();
            assertEquals(9, number(scope, "total"));
            assertEquals(10, ((Number) scope.resolve("vars", "pipe")).intValue());
        });
    }

    @Test void scriptBackedPageCallsShareCallerAndIsolateLocals() {
        withDelay(() -> {
            Scope scope = new Scope();
            Map<String, Object> component = new java.util.LinkedHashMap<>();
            component.put("value", 1L);
            scope.declare("当前组件", component);
            scope.declare("pageFn", (DreamLangExecutor.Callable) args -> {
                Scope caller = DreamLang.currentScope();
                assertNotNull(caller);
                Scope child = caller.child();
                child.declare("arg", args[0]);
                return DreamLang.call("var saved = arg; 当前组件.value = 当前组件.value + saved; "
                        + pause(7) + "; return saved + 当前组件.value", child);
            });
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync("var result = 5 + pageFn(3) * 2; vars.done = result", scope, scheduler);
            assertEquals(4, ((Number) component.get("value")).intValue());
            assertNull(scope.resolve("result"));
            scheduler.finish();
            assertEquals(19, number(scope, "result"));
            assertNull(scope.resolve("saved"));
            assertNull(scope.resolve("arg"));
            assertNull(DreamLang.currentScope());
        });
    }

    @Test void exceptionAfterPauseRunsCatchAndFinallyWithoutReplaying() {
        withDelay(() -> {
            Scope scope = new Scope();
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync("var count = 0; try { count = count + 1; " + pause(1)
                    + "; throw 'failure' } catch(error) { count = count + 10; " + pause(2)
                    + "; count = count + 100 } finally { " + pause(3)
                    + "; count = count + 1000 }; vars.done = count", scope, scheduler);
            assertEquals(1, number(scope, "count"));
            scheduler.finish();
            assertEquals(1111, number(scope, "count"));
            assertEquals(1111, ((Number) scope.resolve("vars", "done")).intValue());
            assertNull(DreamLang.currentScope());
        });
    }

    @Test void uncaughtFailureStopsRemainingStatementsAndRestoresContext() {
        withDelay(() -> {
            Scope scope = new Scope();
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync(pause(1) + "; 方法.__async_missing_method__(); vars.unreachable = 1", scope, scheduler);
            assertThrows(IllegalStateException.class, () -> scheduler.advance(1));
            assertNull(scope.resolve("vars", "unreachable"));
            assertTrue(scheduler.queue.isEmpty());
            assertNull(DreamLang.currentScope());
        });
    }

    @Test void hostOwnsCancellationAndWrongThreadCannotConsumeContinuation() throws InterruptedException {
        withDelay(() -> {
            Scope scope = new Scope();
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync("var count = 1; " + pause(1) + "; count = 2", scope, scheduler);
            Runnable continuation = scheduler.queue.peekFirst().continuation;
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try { continuation.run(); } catch (Throwable error) { failure.set(error); }
            });
            thread.start();
            try { thread.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertEquals(1, number(scope, "count"));
            scheduler.queue.clear(); // host page-close cancellation: never dispatch the continuation
            scheduler.advance(100);
            assertEquals(1, number(scope, "count"));
        });
    }

    @Test void synchronousOrdinaryExecutionAndPageCallRemainSynchronous() {
        Scope scope = new Scope();
        scope.declare("pageFn", (DreamLangExecutor.Callable) args -> DreamLang.call("return 21", DreamLang.currentScope().child()));
        DreamLang.execute("var result = pageFn() * 2; if (true) { result = result + 1; result = result + 1 }", scope);
        assertEquals(44, number(scope, "result"));
        assertNull(DreamLang.currentScope());
        withDelay(() -> assertThrows(DreamLangExecutor.ScriptException.class,
                () -> DreamLang.execute(pause(0) + "; vars.unreachable = 1", scope)));
        assertNull(scope.resolve("vars", "unreachable"));
    }

    @Test void expressionOperandsArgumentsAndAssignmentTargetsAreNotReplayed() {
        withDelay(() -> {
            Scope scope = new Scope();
            List<String> effects = new ArrayList<>();
            scope.declare("effect", (DreamLangExecutor.Callable) args -> { effects.add(String.valueOf(args[0])); return args[1]; });
            scope.declare("result", new ArrayList<>(List.of(0L)));
            ManualScheduler scheduler = new ManualScheduler();
            DreamLang.executeAsync("function delayed(x) { " + pause(1) + "; return x }; "
                    + "result[effect('index', 0)] = effect('left', 5) + delayed(effect('arg', 7))", scope, scheduler);
            assertEquals(List.of("index", "left", "arg"), effects);
            scheduler.finish();
            assertEquals(List.of("index", "left", "arg"), effects);
            assertEquals(12, ((Number) ((List<?>) scope.resolve("result")).get(0)).intValue());
        });
    }

    @Test void asyncLogicalOperatorsShortCircuitSideEffects() {
        Scope scope = new Scope();
        List<String> calls = new ArrayList<>();
        scope.declare("sideEffect", (DreamLangExecutor.Callable) args -> {
            calls.add(String.valueOf(args[0]));
            return true;
        });
        ManualScheduler scheduler = new ManualScheduler();
        DreamLang.executeAsync("vars.a = false && sideEffect('and'); vars.b = true || sideEffect('or'); vars.c = true && sideEffect('run')", scope, scheduler);
        assertEquals(List.of("run"), calls);
        assertEquals(false, scope.resolve("vars", "a"));
        assertEquals(true, scope.resolve("vars", "b"));
        assertEquals(true, scope.resolve("vars", "c"));
    }
}
