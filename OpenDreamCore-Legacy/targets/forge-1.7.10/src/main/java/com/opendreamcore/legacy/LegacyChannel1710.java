package com.opendreamcore.legacy;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

/**
 * 1.7.10 通道：path+bytes 两段式与 1.16.5/1.12.2 完全同构。
 * SimpleNetworkWrapper 单类型注册，方向由发送方决定。
 */
public final class LegacyChannel1710 {
    private static boolean registered;

    private LegacyChannel1710() {
    }

    public static synchronized void init(SimpleNetworkWrapper channel) {
        if (registered) {
            return;
        }
        registered = true;
        channel.registerMessage(RawHandler.class, RawMessage.class, 0, Side.CLIENT);
    }

    /** 客户端 → 服务端。 */
    public static void sendToServer(SimpleNetworkWrapper channel, String path, byte[] data) {
        channel.sendToServer(new RawMessage(path, data));
    }

    public static class RawMessage implements IMessage {
        public String path;
        public byte[] data;

        public RawMessage() { }

        public RawMessage(String path, byte[] data) {
            this.path = path;
            this.data = data == null ? new byte[0] : data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int pl = buf.readInt();
            byte[] p = new byte[pl];
            buf.readBytes(p);
            int dl = buf.readInt();
            byte[] d = new byte[dl];
            buf.readBytes(d);
            this.path = new String(p, StandardCharsets.UTF_8);
            this.data = d;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            byte[] p = path.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(p.length);
            buf.writeBytes(p);
            buf.writeInt(data.length);
            buf.writeBytes(data);
        }
    }

    public static class RawHandler implements IMessageHandler<RawMessage, IMessage> {
        @Override
        public IMessage onMessage(RawMessage msg, MessageContext ctx) {
            ClientHooks1710.onServerMessage(msg.path, msg.data);
            return null;
        }
    }
}
