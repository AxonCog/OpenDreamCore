package com.opendreamcore.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 原始字节 payload：协议消息整体编解码（type 在构造时指定）。
 * 同一个字节容器复用于所有协议通道，type 区分通道。
 * 关键：type() 必须始终返回非 null 的 Type，否则 NeoForge NetworkRegistry.isModdedPayload()
 * 调用时会抛异常导致连接断开。
 */
public record RawPayload(Type<? extends CustomPacketPayload> type, byte[] bytes) implements CustomPacketPayload {

    /** 发消息时（type 由通道常量定死）。 */
    public static RawPayload of(Type<? extends CustomPacketPayload> type, byte[] bytes) {
        return new RawPayload(type, bytes);
    }

    /** 收消息时（type 由 codec 在解码时注入）。 */
    public static RawPayload received(Type<? extends CustomPacketPayload> type, byte[] bytes) {
        return new RawPayload(type, bytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
