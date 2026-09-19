package com.opendreamcore.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * 裸字节载荷（path+bytes 两段式）。
 * 接收后转交 ClientEvents.onServerMessage 按路径分发。
 */
public class RawPayload {
    public String path;
    public byte[] data;

    public RawPayload(String path, byte[] data) {
        this.path = path;
        this.data = data == null ? new byte[0] : data;
    }

    public static void encode(RawPayload msg, PacketBuffer buf) {
        byte[] p = msg.path.getBytes(StandardCharsets.UTF_8);
        buf.writeVarInt(p.length);
        buf.writeBytes(p);
        buf.writeVarInt(msg.data.length);
        buf.writeBytes(msg.data);
    }

    public static RawPayload decode(PacketBuffer buf) {
        int pl = buf.readVarInt();
        byte[] p = new byte[pl];
        buf.readBytes(p);
        int dl = buf.readVarInt();
        byte[] d = new byte[dl];
        buf.readBytes(d);
        return new RawPayload(new String(p, StandardCharsets.UTF_8), d);
    }

    public static void handle(RawPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!ctx.get().getDirection().getReceptionSide().isServer()) {
                // 1.16.5 最小 Shim：ClientEvents 为现代层（GuiGraphics），此处仅日志占位
                System.out.println("[ODC] RawPayload " + msg.path + " (" + msg.data.length + " bytes)");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
