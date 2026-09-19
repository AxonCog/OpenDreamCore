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
 * chunkTotal==1 就是一帧完整消息，直接交付，白忙活的部分一概没有；>1 时按
 * transferId 聚齐再交付。每帧都带 path（同一组内相同），凑齐后拿哪帧的
 * path 都行。TCP 保序，不同组之间爱怎么交错怎么交错，各聚各的。
 */
public final class LegacyFraming {

    /** 上行单帧载荷上限：32767 减掉帧头再留点余量，别卡着线走。 */
    public static final int MAX_CHUNK_PAYLOAD = 30000;

    /** 下行 1.13+ 的线是 1MB，插件端切 500KB 一块，两三刀之内解决战斗。 */
    public static final int MAX_S2C_CHUNK_PAYLOAD = 500000;

    /** 重组等待窗口：超时没凑齐整组丢弃，半截传输别赖在内存里。 */
    public static final long ASSEMBLE_TIMEOUT_MS = 30000L;

    /** 同时在途的传输组上限。正常场景两三组封顶，超了准是对面抽风。 */
    public static final int MAX_TRANSFERS = 64;

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

    /** 整包 → 帧序列，块大小调用方说了算（下行 1MB 线和上行 32KB 线不一样宽）。 */
    public static List<byte[]> frames(String path, byte[] payload, int maxChunkPayload) {
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        byte[] data = payload == null ? new byte[0] : payload;
        int total = (data.length + maxChunkPayload - 1) / maxChunkPayload;
        if (total < 1) {
            total = 1;
        }
        List<byte[]> frames = new ArrayList<byte[]>(total);
        int transferId = total > 1 ? NEXT_TRANSFER_ID.incrementAndGet() : 0;
        for (int index = 0; index < total; index++) {
            int from = index * maxChunkPayload;
            int len = Math.min(maxChunkPayload, data.length - from);
            byte[] chunk = new byte[len > 0 ? len : 0];
            System.arraycopy(data, from, chunk, 0, chunk.length);
            frames.add(frame(pathBytes, transferId, index, total, chunk));
        }
        return frames;
    }

    /** 编一帧：帧头 + 载荷，完事。 */
    private static byte[] frame(byte[] pathBytes, int transferId, int index, int total, byte[] chunk) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pathBytes.length + chunk.length + 24);
        DataOutputStream buf = new DataOutputStream(out);
        try {
            buf.writeInt(pathBytes.length);
            buf.write(pathBytes);
            buf.writeInt(transferId);
            buf.writeInt(index);
            buf.writeInt(total);
            buf.writeInt(chunk.length);
            buf.write(chunk);
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
            if (chunkTotal < 1 || chunkIndex < 0 || chunkIndex >= chunkTotal) {
                return null;
            }
            if (buf.remaining() < 4) {
                return null;
            }
            int dataLen = buf.getInt();
            if (dataLen < 0 || buf.remaining() < dataLen) {
                return null;
            }
            byte[] data = new byte[dataLen];
            buf.get(data);
            return new Frame(new String(pathBytes, StandardCharsets.UTF_8),
                    transferId, chunkIndex, chunkTotal, data);
        } catch (RuntimeException e) {
            // 任何越界都说明这不是我们的包，或者对面在搞事，两种情况都不接
            return null;
        }
    }

    /**
     * 重组器：喂帧进去，凑齐一包返回整包载荷，没凑齐返回 null。
     * 单帧直接透传 data，一趟都不用排队。纯状态机不碰网络——插件端每个
     * 玩家挂一个（半截传输互不打架），模组端一条连接挂一个。生命周期
     * 宿主自己管，别指望它自己长眼睛。
     */
    public static final class Assembler {

        /** 插件端收包在 netty 线程、NeoForge 端在网络线程，多线程喂帧是常态，老老实实上并发容器。 */
        private final Map<Integer, Transfer> transfers =
                new java.util.concurrent.ConcurrentHashMap<Integer, Transfer>();

        /** 喂一帧；凑齐返回整包，未齐/坏帧返回 null。交付用 frame.path。 */
        public byte[] offer(Frame frame) {
            sweep();
            if (frame.chunkTotal == 1) {
                return frame.data;
            }
            Transfer transfer = transfers.get(Integer.valueOf(frame.transferId));
            if (transfer == null) {
                if (transfers.size() >= MAX_TRANSFERS) {
                    evictOldest();
                }
                transfer = new Transfer(frame.chunkTotal);
                transfers.put(Integer.valueOf(frame.transferId), transfer);
            }
            if (transfer.total != frame.chunkTotal) {
                // 组号对不上总片数——编号撞车了，整组作废，让对面重发去
                transfers.remove(Integer.valueOf(frame.transferId));
                return null;
            }
            if (transfer.chunks[frame.chunkIndex] == null) {
                transfer.received++;
            }
            transfer.chunks[frame.chunkIndex] = frame.data;
            transfer.lastTouch = System.currentTimeMillis();
            if (transfer.received < transfer.total) {
                return null;
            }
            transfers.remove(Integer.valueOf(frame.transferId));
            int size = 0;
            for (byte[] chunk : transfer.chunks) {
                size += chunk.length;
            }
            byte[] payload = new byte[size];
            int offset = 0;
            for (byte[] chunk : transfer.chunks) {
                System.arraycopy(chunk, 0, payload, offset, chunk.length);
                offset += chunk.length;
            }
            return payload;
        }

        /** 在途组数（监控/测试用，平时没人看）。 */
        public int pending() {
            return transfers.size();
        }

        /** 清掉超时的半截传输，主要是给断线重连擦屁股。 */
        private void sweep() {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, Transfer>> it = transfers.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().lastTouch > ASSEMBLE_TIMEOUT_MS) {
                    it.remove();
                }
            }
        }

        /** 空间不够时丢最老的一组，谁最久没动静谁背锅。 */
        private void evictOldest() {
            Integer oldest = null;
            long oldestTouch = Long.MAX_VALUE;
            for (Map.Entry<Integer, Transfer> e : transfers.entrySet()) {
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
            final int total;
            final byte[][] chunks;
            int received;
            long lastTouch = System.currentTimeMillis();

            Transfer(int total) {
                this.total = total;
                this.chunks = new byte[total][];
            }
        }
    }
}
