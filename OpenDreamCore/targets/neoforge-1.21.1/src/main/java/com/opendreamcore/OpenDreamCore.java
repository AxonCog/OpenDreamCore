package com.opendreamcore;

import com.mojang.logging.LogUtils;
import com.opendreamcore.network.UiChannel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * OpenDreamCore 客户端模组入口。
 */
@Mod(OpenDreamCore.MODID)
public final class OpenDreamCore {

    public static final String MODID = "opendreamcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OpenDreamCore(IEventBus bus) {
        bus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional() 使 payload 不要求服务端必须运行 NeoForge（可连接原版/Bukkit/Paper 服务端）
        var registrar = event.registrar(String.valueOf(com.opendreamcore.protocol.Protocol.VERSION))
                .optional();
        UiChannel.registerPayloads(registrar);
    }
}
