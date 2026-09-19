package com.opendreamcore.client;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.Ready;

/**
 * 老版本客户端的主控口子：发送器的注入、握手、命令转发都在这。
 * 真正的网络发送是各版本自己的事（1.7.10 走 Netty channel，1.6.4 走
 * Packet250），所以这里只认 setSender 注入进来的三参发送函数。
 */
public final class ClientControllerLegacy {

    /** (channel, path, data) -> 网络发送。各版本壳里用 lambda 交进来。 */
    public interface Sender {
        void send(String channel, String path, byte[] data);
    }

    private static volatile Sender sender;

    private ClientControllerLegacy() { }

    /** 网络层就绪后调，把发送函数交进来。没注入前所有发送都会拒绝。 */
    public static void setSender(Sender s) {
        sender = s;
    }

    /** 连接是否可用：发送器在就算在。转发前都先过这道检查。 */
    public static boolean isConnected() {
        return sender != null;
    }

    /** 握手：连接刚建立时发一次 ready。版本号和能力位由各壳传入。 */
    public static boolean sendReady(String modVersion, int capabilities) {
        Sender s = sender;
        if (s == null) {
            return false;
        }
        Ready ready = new Ready(Protocol.VERSION, modVersion, capabilities);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        ready.encode(buf);
        s.send("ODC", "ready", buf.toByteArray());
        return true;
    }

    /** 包序自增：服务器拿它去重旧事件。 */
    private static final java.util.concurrent.atomic.AtomicLong UI_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    /** 云缓存专用：拿发送器直接发（sendRaw 那套语义对齐，不另开口子）。 */
    public static boolean sendRawForCloud(String path, byte[] data) {
        Sender s = sender;
        if (s == null) {
            return false;
        }
        s.send("ODC", path, data);
        return true;
    }

    /**
     * 把一次界面交互（点击/拖拽/键入）回传服务器。
     * sender 未注入（单人存档/未连上）就静默丢弃：本地状态已经改了，
     * 没网就不硬报错，和命令转发的本地兜底一个思路。
     */
    public static void sendUiEvent(String sessionId, String elementId,
                                   com.opendreamcore.protocol.message.UiEvent.Trigger trigger,
                                   String data) {
        Sender s = sender;
        if (s == null || elementId == null || elementId.isEmpty()) {
            return;
        }
        com.opendreamcore.protocol.OdcByteArrayBuf buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.UiEvent(
                sessionId == null ? "" : sessionId, elementId, trigger,
                UI_SEQ.incrementAndGet(), data).encode(buf);
        s.send("ODC", com.opendreamcore.protocol.Protocol.UI_EVENT, buf.toByteArray());
    }

    /** ESC 关页通知：现代端 onClose 同款（服务端清会话/容器绑定）。 */
    public static void sendPageClose(String sessionId) {
        Sender s = sender;
        if (s == null || sessionId == null || sessionId.isEmpty()) {
            return;
        }
        com.opendreamcore.protocol.OdcByteArrayBuf buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.PageClose(sessionId).encode(buf);
        s.send("ODC", com.opendreamcore.protocol.Protocol.PAGE_CLOSE, buf.toByteArray());
    }

    /**
     * 上行：客户端 → 服务端自定义通道（custom_packet）。
     * 没注入发送器（没连服）就返回 false，不报错。附属模组直接调这里，
     * 脚本侧走 Network.发送 同一个口子。
     */
    public static boolean sendCustomPacket(String channel, String payload) {
        Sender s = sender;
        if (s == null || channel == null || channel.trim().isEmpty()) {
            return false;
        }
        com.opendreamcore.protocol.OdcByteArrayBuf buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.CustomPacket(channel, payload == null ? "" : payload)
                .encode(buf);
        s.send("ODC", com.opendreamcore.protocol.Protocol.CUSTOM_PACKET, buf.toByteArray());
        return true;
    }
}
