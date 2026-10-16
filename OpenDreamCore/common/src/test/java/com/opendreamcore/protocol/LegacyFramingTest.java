package com.opendreamcore.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LegacyFramingTest {
    @Test
    void reassemblesOutOfOrderInterleavedAndDuplicateFrames() {
        byte[] first = new byte[9000];
        byte[] second = new byte[7000];
        for (int i = 0; i < first.length; i++) first[i] = (byte) i;
        for (int i = 0; i < second.length; i++) second[i] = (byte) (i * 3);
        List<byte[]> firstFrames = LegacyFraming.frames("a", first, 2000);
        List<byte[]> secondFrames = LegacyFraming.frames("b", second, 2000);
        List<byte[]> wire = new ArrayList<>();
        for (int i = 0; i < firstFrames.size(); i++) {
            wire.add(firstFrames.get(i));
            if (i < secondFrames.size()) wire.add(secondFrames.get(i));
        }
        Collections.reverse(wire);
        LegacyFraming.Assembler assembler = new LegacyFraming.Assembler();
        byte[] resultFirst = null;
        byte[] resultSecond = null;
        for (byte[] raw : wire) {
            LegacyFraming.Frame frame = LegacyFraming.parse(raw);
            byte[] result = assembler.offer(frame);
            if (result != null) {
                if (frame.path.equals("a")) resultFirst = result;
                else resultSecond = result;
            }
            assertNull(assembler.offer(frame));
        }
        assertArrayEquals(first, resultFirst);
        assertArrayEquals(second, resultSecond);
    }

    @Test
    void sameTransferIdDifferentPathDoesNotMix() {
        LegacyFraming.Assembler assembler = new LegacyFraming.Assembler();
        LegacyFraming.Frame a0 = new LegacyFraming.Frame("a", 77, 0, 2, new byte[]{1});
        LegacyFraming.Frame b1 = new LegacyFraming.Frame("b", 77, 1, 2, new byte[]{2});
        LegacyFraming.Frame a1 = new LegacyFraming.Frame("a", 77, 1, 2, new byte[]{3});
        assertNull(assembler.offer(a0));
        assertNull(assembler.offer(b1));
        assertArrayEquals(new byte[]{1, 3}, assembler.offer(a1));
    }

    @Test
    void rejectsInvalidFrameSizingAndClearDropsHalfPacket() {
        assertThrows(IllegalArgumentException.class, () -> LegacyFraming.frames("x", new byte[1], 0));
        assertThrows(IllegalArgumentException.class, () -> LegacyFraming.frames("x", new byte[1], -1));
        LegacyFraming.Assembler assembler = new LegacyFraming.Assembler();
        assembler.offer(new LegacyFraming.Frame("x", 9, 0, 2, new byte[]{1}));
        assertEquals(1, assembler.pending());
        assembler.clear();
        assertEquals(0, assembler.pending());
        assertNull(assembler.offer(new LegacyFraming.Frame("x", 9, 1, 2, new byte[]{2})));
    }

    @Test
    void concurrentOffersDoNotLoseChunks() throws Exception {
        List<byte[]> raws = LegacyFraming.frames("parallel", new byte[12000], 1000);
        LegacyFraming.Assembler assembler = new LegacyFraming.Assembler();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<byte[]> results = Collections.synchronizedList(new ArrayList<>());
        for (byte[] raw : raws) {
            pool.execute(() -> {
                LegacyFraming.Frame frame = LegacyFraming.parse(raw);
                byte[] result = assembler.offer(frame);
                if (result != null) results.add(result);
            });
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        assertEquals(1, results.size());
        assertEquals(12000, results.get(0).length);
    }
}
