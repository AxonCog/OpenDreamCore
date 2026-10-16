package com.opendreamcore.ui;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Invocation isolation and binding write-path ownership regressions. */
class LayoutEngineIsolationTest {
    private static Page page(String yaml) {
        return PageSchema.build("isolation", new YamlParser().parse(yaml));
    }

    private static Page layoutPage(String gateId, int pixels) {
        return page("""
                shared:
                  type: layout
                  width: %spx
                  height: 10
                %s:
                  type: layout
                  x: 20px
                  width: 40px
                  height: 10
                dependent:
                  type: layout
                  width: shared.width
                  height: 10
                """.formatted(pixels, gateId));
    }

    private static Viewport doubledViewport() {
        return Viewport.ofTransform(320, 180, 2, 10, 0, "letterbox");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for deterministic layout rendezvous");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @Test
    void concurrentCallsKeepTheirViewportAndComputedFrames() throws Exception {
        Page first = layoutPage("first_gate", 80);
        Page second = layoutPage("second_gate", 120);
        CountDownLatch firstAtGate = new CountDownLatch(1);
        CountDownLatch secondAtGate = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        LayoutEngine.setStateLayoutResolver(id -> {
            if ("first_gate".equals(id)) {
                firstAtGate.countDown();
                await(secondAtGate);
            } else if ("second_gate".equals(id)) {
                secondAtGate.countDown();
                await(releaseSecond);
            }
            return Map.of();
        });
        try {
            Future<List<RenderNode>> firstResult = workers.submit(
                    () -> LayoutEngine.layout(first, 320, 180, null, doubledViewport()));
            // The first page has cached shared before the second invocation starts.
            await(firstAtGate);
            Future<List<RenderNode>> secondResult = workers.submit(
                    () -> LayoutEngine.layout(second, 320, 180, null, Viewport.IDENTITY));
            try {
                // Second remains inside its resolver until the first has finished.
                List<RenderNode> nodes = firstResult.get(10, TimeUnit.SECONDS);
                assertEquals(40, nodes.get(0).width());
                assertEquals(5, nodes.get(1).x());
                assertEquals(20, nodes.get(1).width());
                assertEquals(40, nodes.get(2).width());
            } finally {
                releaseSecond.countDown();
            }
            List<RenderNode> nodes = secondResult.get(10, TimeUnit.SECONDS);
            assertEquals(120, nodes.get(0).width());
            assertEquals(20, nodes.get(1).x());
            assertEquals(40, nodes.get(1).width());
            assertEquals(120, nodes.get(2).width());
        } finally {
            releaseSecond.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            LayoutEngine.setStateLayoutResolver(null);
        }
    }

    @Test
    void resolverCanRunNestedLayoutWithoutReplacingOuterContext() {
        Page outer = layoutPage("outer_gate", 80);
        Page inner = layoutPage("inner_gate", 120);
        AtomicReference<List<RenderNode>> nested = new AtomicReference<>();
        LayoutEngine.setStateLayoutResolver(id -> {
            if ("outer_gate".equals(id)) {
                nested.set(LayoutEngine.layout(inner, 320, 180, null, Viewport.IDENTITY));
            }
            return Map.of();
        });
        try {
            List<RenderNode> nodes = LayoutEngine.layout(outer, 320, 180, null, doubledViewport());
            assertNotNull(nested.get());
            assertEquals(120, nested.get().get(2).width());
            assertEquals(40, nested.get().get(1).width());
            assertEquals(5, nodes.get(1).x());
            assertEquals(20, nodes.get(1).width());
            assertEquals(40, nodes.get(2).width());
        } finally {
            LayoutEngine.setStateLayoutResolver(null);
        }
    }

    @Test
    void bindingCopiesOnlyWrittenPathsAndPreservesEarlierResults() {
        Page page = page("""
                name: first
                color: red
                label:
                  type: text
                  text:
                    content: original
                    style:
                      color: original-color
                      size: 12
                    untouched:
                      value: retained
                  bind:
                    text.content: vars.name
                    text.style.color: vars.color
                """);
        Map<?, ?> sourceText = (Map<?, ?>) page.elements().get(0).props().get("text");
        Map<?, ?> sourceStyle = (Map<?, ?>) sourceText.get("style");
        Map<?, ?> firstText = (Map<?, ?>) LayoutEngine.layout(page, 320, 180).get(0).props().get("text");
        page.variables().put("name", "second");
        page.variables().put("color", "blue");
        Map<?, ?> secondText = (Map<?, ?>) LayoutEngine.layout(page, 320, 180).get(0).props().get("text");

        assertEquals("original", sourceText.get("content"));
        assertEquals("original-color", sourceStyle.get("color"));
        assertEquals("first", firstText.get("content"));
        assertEquals("red", ((Map<?, ?>) firstText.get("style")).get("color"));
        assertEquals("second", secondText.get("content"));
        assertEquals("blue", ((Map<?, ?>) secondText.get("style")).get("color"));
        assertEquals(12, ((Number) ((Map<?, ?>) secondText.get("style")).get("size")).intValue());
    }
}
