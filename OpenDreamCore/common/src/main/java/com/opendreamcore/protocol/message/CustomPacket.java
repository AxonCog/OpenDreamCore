package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 自定义双向通道（C↔S，custom_packet）：任意通道名 + 字符串负载。
 * 客户端：Network.发送(通道, 内容) 上行；Network.订阅(通道, lambda) 接收服务端下行。
 * 服务端：CustomPacketRegistry.registerHandler(通道, 处理器) 接收上行；send(player, 通道, 内容) 下行。
 */
public final class CustomPacket implements Message {

    private final String channel;
    private final String payload;

    public CustomPacket(String channel, String payload) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("通道名不能为空");
        }
        this.channel = channel;
        this.payload = payload == null ? "" : payload;
    }

    public String channel() {
        return channel;
    }

    public String payload() {
        return payload;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(channel);
        buf.writeString(payload);
    }

    public static CustomPacket decode(OdcByteBuf buf) {
        return new CustomPacket(buf.readString(), buf.readString());
    }
}
