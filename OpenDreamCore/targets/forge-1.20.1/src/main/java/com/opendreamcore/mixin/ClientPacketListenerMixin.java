package com.opendreamcore.mixin;

import com.opendreamcore.network.ForgeChannel;
import com.opendreamcore.protocol.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截客户端 custom payload：opendreamcore:* 通道不经 Forge 网络协商直接分发
 * （等价于 Fabric 的 registerGlobalReceiver；Paper 服务端下发的通道不在 FML 协商表里，
 * Forge 默认会静默丢弃，这里在 HEAD 截获并取消）。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"), cancellable = true)
    public void opendreamcore$handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ResourceLocation id = packet.getIdentifier();
        if (id == null || !Protocol.NAMESPACE.equals(id.getNamespace())) {
            return;
        }
        // 缓冲必须在网络线程同步读完，处理丢到客户端主线程（与 fabric 壳同策略）
        FriendlyByteBuf data = packet.getData();
        byte[] bytes = new byte[data.readableBytes()];
        data.readBytes(bytes);
        String path = id.getPath();
        Minecraft.getInstance().execute(() -> ForgeChannel.dispatch(path, bytes));
        ci.cancel();
    }
}
