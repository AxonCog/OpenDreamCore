package com.opendreamcore.mixin;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 通道过滤器注入幂等化：重连 Paper 等非 NeoForge 服务端时，
 * 原实现会对已有同名过滤器的管线再次 addAfter 抛 Duplicate handler 断连。
 * 这里拦截 addAfter——管线中已存在同名处理器则直接跳过。
 */
@Mixin(value = net.neoforged.neoforge.network.filters.NetworkFilters.class, remap = false)
public abstract class MixinNetworkFilters {

    @Redirect(
            method = "lambda$injectIfNecessary$1",
            at = @At(value = "INVOKE",
                     target = "Lio/netty/channel/ChannelPipeline;addAfter(Ljava/lang/String;Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;",
                     remap = false),
            remap = false)
    private static ChannelPipeline odc$safeAddAfter(io.netty.channel.ChannelPipeline pipeline, String beforeName,
                                                    String name, ChannelHandler handler) {
        if (pipeline.get(name) != null) {
            return pipeline; // 幂等：已存在即跳过
        }
        return pipeline.addAfter(beforeName, name, handler);
    }
}
