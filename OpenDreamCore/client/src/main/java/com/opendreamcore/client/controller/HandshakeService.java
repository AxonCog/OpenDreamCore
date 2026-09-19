package com.opendreamcore.client.controller;

import com.opendreamcore.protocol.message.Ready;

/**
 * 进服握手的两个原子动作：发 ready、记服务端版本。
 * 分到独立类是为了让控制器瘦身；ready_ack 的版本对比面板与本地 UI 加载、
 * 云资源靠接纠缠，仍留在控制器处理。
 */
public final class HandshakeService {

    /** 控制器回传：版本号来源与原始发包口。 */
    public interface Host {
        String clientVersion();

        void sendRaw(String channelPath, byte[] bytes);
    }

    private final Host host;
    private String serverVersion = "";
    private int serverProtocol = -1;

    public HandshakeService(Host host) {
        this.host = host;
    }

    /** 进服时发送 ready。 */
    public void sendReady() {
        Ready ready = new Ready(com.opendreamcore.protocol.Protocol.VERSION, host.clientVersion(),
                com.opendreamcore.protocol.Protocol.CAPABILITY_LOCAL_UI | com.opendreamcore.protocol.Protocol.CAPABILITY_CLOUD);
        // 先声明下行通道（minecraft:register）：必须早于 READY——服务端收到 READY 即刻下发
        // PAGE_SYNC，晚于声明会被 Paper 静默丢弃（首包竞态，1.20.1 实机实证）。
        host.sendRaw("minecraft:register", com.opendreamcore.protocol.Protocol.clientboundRegisterPayload());
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        ready.encode(buf);
        host.sendRaw(com.opendreamcore.protocol.Protocol.READY, buf.toByteArray());
    }

    /** ready_ack 到达时记录服务端版本/协议（面板判定在 controller）。 */
    public void record(String modVersion, int protocolVersion) {
        serverVersion = modVersion == null ? "" : modVersion;
        serverProtocol = protocolVersion;
    }

    /** 服务端 mod 版本（未握手前空串）。 */
    public String serverVersion() {
        return serverVersion;
    }

    /** 服务端协议版本（未握手前 -1）。 */
    public int serverProtocol() {
        return serverProtocol;
    }
}