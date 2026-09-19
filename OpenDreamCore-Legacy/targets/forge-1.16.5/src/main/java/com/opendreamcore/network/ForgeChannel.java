package com.opendreamcore.network;

import com.opendreamcore.protocol.Protocol;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/**
 * 1.16.5 Forge 协议通道（对齐 forge-1.20.1 架构）：
 * SimpleChannel 单类型 RawPayload 承载全部消息，handle 后按路径分发到 ClientEvents。
 */
public final class ForgeChannel {

    private static final String VERSION = String.valueOf(Protocol.VERSION);

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Protocol.NAMESPACE, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean registered;

    private ForgeChannel() {
    }

    /** 同步入口：mod 构造期调用一次。 */
    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, RawPayload.class,
                RawPayload::encode,
                RawPayload::decode,
                RawPayload::handle);
    }

    /** 注册路径处理器（ClientEvents 静态块调用；分发走 dispatcher）。 */
    public static void register(String path, java.util.function.Consumer<byte[]> handler) {
        // 1.16.5 SimpleChannel 单类型已注册全部消息，这里仅保留路径集合供路由判断
        // 实际分发在 RawPayload.handle → ClientEvents.onServerMessage
    }

    /** 客户端 → 服务端。 */
    public static void sendToServer(String path, byte[] data) {
        CHANNEL.sendToServer(new RawPayload(path, data));
    }
}
