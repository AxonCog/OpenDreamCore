package com.opendreamcore.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分片帧协议：专治 plugin message 那条 32767 的老线。
 *
 * Mojang 从 1.7 开始给上行 plugin message 画了 32767 字节的线，画完快十年
 * 没挪过窝（1.13 只是放开了下行到 1MB，上行纹丝不动）。编辑器随手存个正经
 * 的世界布局、云盘拖个大文件，分分钟撞线——撞了不是报错，是直接踢线，连句
 * 解释都没有。所以大件必须切块走，这层就是干这个的。
 *
 * 帧格式（收发两端同构，老版本单通道和新版本 chunk 通道共用这一套）：
 *   [int pathLen][path UTF-8][int transferId][int chunkIndex][int chunkTotal][int dataLen][data]
 *
 * chunkTotal==1 就是一帧完整消息，直接交付；>1 时按 transferId 和 path 聚齐再交付。
 * 同一组的 path 和总片数必须一致，重复片不改变组的计数。TCP 保序，不同组之间爱怎么
 * 交错怎么交错，各聚各的。
 */
public final class LegacyFraming {

    /** 上行单帧载荷上限：32767 减掉帧头再留点余量，别卡着线走。 */
    public static final int MAX_CHUNK_PAYLOAD = 30000;

    /**
     * 下行分片阈值：别被「1.13+ 有 1MB」诱惑——代理（Velocity/Bungee）把
     * plugin message 硬卡在 32766，实测 48146B 直接 MessageTooLargeException。
     * 统一按最窄的线切，全版本一个行为。
     */
    public static final int MAX_S2C_CHUNK_PAYLOAD = 30000;

    /** 重组等待窗口：超时没凑齐整组丢弃，半截传输别赖在内存里。 */
    public static final long ASSEMBLE_TIMEOUT_MS = 30000L;

    /** 同时在途的传输组上限。正常场景两三组封顶，超了准是对面抽风。 */
    public static final int MAX_TRANSFERS = 64;

    /** 单个整包和其分片数量的上限，避免恶意元数据和载荷占用过多内存。 */
    public static final int MAX_ASSEMBLED_PAYLOAD = 16 * 1024 * 1024;
    public static final int MAX_CHUNKS = 560;

    /** 分包时的传输组编号，收发两端各自从头计，谁也不欠谁的。 */
    private static final AtomicInteger NEXT_TRANSFER_ID = new AtomicInteger(1);

    private LegacyFraming() {
    }

    /**
     * 整包 → 帧序列（按默认上行块大小切）。
     * 单包装得下就是一帧，transferId 都懒得编（写 0）。
     */
    public static List<byte[]> frames(String path, byte[] payload) {
        return frames(path, payload, MAX_CHUNK_PAYLOAD);
    }

    /** 整包 → 帧序列，块大小必须为正且不得超过单帧传输上限。 */
    public static List<byte[]> frames(String path, byte[] payload, int maxChunkPayload) {
        if (path == null) {
            throw new IllegalArgumentException("path不能为空");
        }
        if (maxChunkPayload <= 0 || maxChunkPayload > MAX_CHUNK_PAYLOAD) {
            throw new IllegalArgumentException("maxChunkPayload超出范围");
        }
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        if (pathBytes.length > 256) {
            throw new IllegalArgumentException("path过长");
        }
        byte[] data = payload == null ? new byte[0] : payload;
        if (data.length > MAX_ASSEMBLED_PAYLOAD) {
            throw new IllegalArgumentException("payload过大");
        }
        long totalLong = (data.length + (long) maxChunkPayload - 1L) / maxChunkPayload;
        int total = (int) Math.max(1L, totalLong);
        if (totalLong > MAX_CHUNKS) {
            throw new IllegalArgumentException("分片数量过多");
        }
        List<byte[]> frames = new ArrayList<byte[]>(total);
        int transferId = total > 1 ? NEXT_TRANSFER_ID.incrementAndGet() : 0;
        for (int index = 0; index < total; index++) {
            int from = index * maxChunkPayload;
            int len = Math.min(maxChunkPayload, data.length - from);
            frames.add(frame(pathBytes, transferId, index, total, data, from, Math.max(0, len)));
        }
        return frames;
    }

    /** 编一帧：帧头 + 载荷，完事。 */
    private static byte[] frame(byte[] pathBytes, int transferId, int index, int total,
            byte[] data, int offset, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pathBytes.length + length + 24);
        DataOutputStream buf = new DataOutputStream(out);
        try {
            buf.writeInt(pathBytes.length);
            buf.write(pathBytes);
            buf.writeInt(transferId);
            buf.writeInt(index);
            buf.writeInt(total);
            buf.writeInt(length);
            buf.write(data, offset, length);
        } catch (IOException e) {
            // 内存流不会抛 IOException，这行 catch 纯属给编译器看的
            throw new IllegalStateException("帧编码失败", e);
        }
        return out.toByteArray();
    }


    /** 解出来的一帧，四个编号字段加一段载荷，没有秘密。 */
    public static final class Frame {
        public final String path;
        public final int transferId;
        public final int chunkIndex;
        public final int chunkTotal;
        public final byte[] data;

        Frame(String path, int transferId, int chunkIndex, int chunkTotal, byte[] data) {
            this.path = path;
            this.transferId = transferId;
            this.chunkIndex = chunkIndex;
            this.chunkTotal = chunkTotal;
            this.data = data;
        }
    }

    /** 从裸载荷解一帧。格式不对（长度溢出/字段抽风）一律当垃圾包返回 null，不惯着。 */
    public static Frame parse(byte[] raw) {
        if (raw == null || raw.length < 24) {
            return null;
        }
        try {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(raw);
            int pathLen = buf.getInt();
            if (pathLen < 0 || pathLen > 256 || buf.remaining() < pathLen + 12) {
                return null;
            }
            byte[] pathBytes = new byte[pathLen];
            buf.get(pathBytes);
            int transferId = buf.getInt();
            int chunkIndex = buf.getInt();
            int chunkTotal = buf.getInt();
            if (chunkTotal < 1 || chunkTotal > MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= chunkTotal) {
                return null;
            }
            if (buf.remaining() < 4) {
                return null;
            }
            int dataLen = buf.getInt();
            if (dataLen < 0 || dataLen > MAX_CHUNK_PAYLOAD || dataLen > MAX_ASSEMBLED_PAYLOAD
                    || buf.remaining() < dataLen) {
                return null;
            }
            byte[] data = new byte[dataLen];
            buf.get(data);
            return new Frame(new String(pathBytes, StandardCharsets.UTF_8),
                    transferId, chunkIndex, chunkTotal, data);
        } catch (RuntimeException e) {
            // 截断或越界数据不构成有效帧。
            return null;
        }
    }

    /**
     * 每个连接使用一个重组器。重组状态由同一把锁保护，断线时由宿主调用 clear。
     * 同组分片必须保持 path 和总片数一致；重复片保留首次收到的载荷。
     */
    public static final class Assembler {

        private final Map<TransferKey, Transfer> transfers = new HashMap<TransferKey, Transfer>();
        // 短期记录已完成编号，避免迟到的重复片重新开启同一组。
        private final Map<TransferKey, Long> completed = new java.util.LinkedHashMap<TransferKey, Long>();

        public synchronized byte[] offer(Frame frame) {
            if (frame == null || frame.path == null || frame.data == null
                    || frame.chunkTotal < 1 || frame.chunkTotal > MAX_CHUNKS
                    || frame.chunkIndex < 0 || frame.chunkIndex >= frame.chunkTotal
                    || (frame.chunkTotal > 1 && frame.transferId == 0)
                    || frame.data.length > MAX_CHUNK_PAYLOAD
                    || frame.data.length > MAX_ASSEMBLED_PAYLOAD) {
                return null;
            }
            sweep();
            if (frame.chunkTotal == 1) {
                return frame.data;
            }
            TransferKey key = new TransferKey(frame.path, frame.transferId);
            if (completed.containsKey(key)) {
                return null;
            }
            Transfer transfer = transfers.get(key);
            if (transfer == null) {
                if (transfers.size() >= MAX_TRANSFERS) {
                    evictOldest();
                }
                transfer = new Transfer(frame.path, frame.chunkTotal);
                transfers.put(key, transfer);
            } else if (transfer.total != frame.chunkTotal || !transfer.path.equals(frame.path)) {
                // 碰撞帧不覆盖已有传输，也不参与其计数或载荷。
                return null;
            }
            if (transfer.chunks[frame.chunkIndex] != null) {
                return null;
            }
            if (transfer.receivedBytes > MAX_ASSEMBLED_PAYLOAD - frame.data.length) {
                transfers.remove(key);
                return null;
            }
            transfer.chunks[frame.chunkIndex] = frame.data;
            transfer.received++;
            transfer.receivedBytes += frame.data.length;
            transfer.lastTouch = System.currentTimeMillis();
            if (transfer.received < transfer.total) {
                return null;
            }
            transfers.remove(key);
            if (completed.size() >= MAX_TRANSFERS) {
                completed.remove(completed.keySet().iterator().next());
            }
            completed.put(key, Long.valueOf(transfer.lastTouch));
            byte[] payload = new byte[transfer.receivedBytes];
            int offset = 0;
            for (byte[] chunk : transfer.chunks) {
                System.arraycopy(chunk, 0, payload, offset, chunk.length);
                offset += chunk.length;
            }
            return payload;
        }

        /** 当前尚未完成的传输组数。 */
        public synchronized int pending() {
            return transfers.size();
        }

        /** 丢弃当前连接的半包和重复片记录。 */
        public synchronized void clear() {
            transfers.clear();
            completed.clear();
        }

        /** 丢弃超时半包和已完成编号。调用方持有重组器锁。 */
        private void sweep() {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<TransferKey, Transfer>> it = transfers.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().lastTouch > ASSEMBLE_TIMEOUT_MS) {
                    it.remove();
                }
            }
            Iterator<Map.Entry<TransferKey, Long>> done = completed.entrySet().iterator();
            while (done.hasNext()) {
                if (now - done.next().getValue().longValue() > ASSEMBLE_TIMEOUT_MS) {
                    done.remove();
                }
            }
        }

        private static final class TransferKey {
            final String path;
            final int transferId;

            TransferKey(String path, int transferId) {
                this.path = path;
                this.transferId = transferId;
            }

            @Override
            public boolean equals(Object other) {
                if (!(other instanceof TransferKey)) {
                    return false;
                }
                TransferKey that = (TransferKey) other;
                return transferId == that.transferId && path.equals(that.path);
            }

            @Override
            public int hashCode() {
                return 31 * path.hashCode() + transferId;
            }
        }

        /** 空间不够时淘汰最久未使用的一组。 */
        private void evictOldest() {
            TransferKey oldest = null;
            long oldestTouch = Long.MAX_VALUE;
            for (Map.Entry<TransferKey, Transfer> e : transfers.entrySet()) {
                if (e.getValue().lastTouch < oldestTouch) {
                    oldestTouch = e.getValue().lastTouch;
                    oldest = e.getKey();
                }
            }
            if (oldest != null) {
                transfers.remove(oldest);
            }
        }

        private static final class Transfer {
            final String path;
            final int total;
            final byte[][] chunks;
            int received;
            int receivedBytes;
            long lastTouch = System.currentTimeMillis();

            Transfer(String path, int total) {
                this.path = path;
                this.total = total;
                this.chunks = new byte[total][];
            }
        }
    }
}
