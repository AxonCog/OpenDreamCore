package com.opendreamcore.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.opendreamcore.page.Page;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * Fabric 1.20.1 侧事件：网络发送注入、进服握手、本地页面、HUD/全息渲染、容器替换、/odc 命令。
 */
public final class FabricEvents {

    static {
        com.opendreamcore.client.ClientController.setClientVersion("0.1.1");
    }

    private FabricEvents() {
    }

    public static void register() {
        // 网络发送：Fabric 走旧版 channel API
        ClientController.get().setSender(com.opendreamcore.network.FabricChannel::send);

        // 每帧标题推进（主菜单也刷新）；键鼠绑定边沿检测仅在进世界后
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                com.opendreamcore.client.WindowBranding.tick();
            } catch (Exception ignored) {
            }
            if (client.player != null) {
                try {
                    ClientController.get().tickBindings();
                } catch (Exception ignored) {
                }
            }
        });

        // 进服：本地页面 + 位置记忆 + HUD 挂载 + ready + tooltip 拉取
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Path uiDir = client.gameDirectory.toPath().resolve("OpenDreamCore").resolve("UI");
            ClientController.get().localPages().load(uiDir);
            ClientController.get().loadPositions();
            ClientController.get().elementEdits().load();
            ClientController.get().markLogin();
            // 服务端标题缓存预载（首包前生效，消除空窗）
            ClientController.get().preloadServerTitle();
            ClientController.get().autoMountHud();
            ClientController.get().sendReady();
            ClientController.get().requestTooltips();
        });

        // 断线：解除服务端标题覆盖，还原本地 branding
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientController.get().clearServerTitle());

        // HUD 常驻渲染
        HudRenderCallback.EVENT.register((g, tickDelta) -> ClientController.get().renderHud(g));

        // 世界全息（实体渲染后）
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                ClientController.get().renderWorld(context.camera(), context.tickDelta()));

        // 容器替换：原版容器打开后，命中本地 match 就换掉（1.20.1 fabric-api 签名：client+screen+w+h）
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen cs) {
                String target;
                if (cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                    target = "inventory";
                } else {
try {
                        var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(cs.getMenu().getType());
                        target = id == null ? null : id.toString();
                    } catch (Throwable menuTypeFail) {
                        // 部分模组容器未注册菜单类型，getType 会抛异常；只按标题匹配
                        target = null;
                    }
                }
                String title = cs.getTitle().getString();
                if (target == null) {
                    return;
                }
                Page page = ClientController.get().localPages()
                        .match(target, title, com.opendreamcore.page.DisplayMode.CONTAINER);
                if (page == null && title != null) {
                    page = ClientController.get().localPages()
                            .match(title, title, com.opendreamcore.page.DisplayMode.CONTAINER);
                }
                if (page != null) {
                    Minecraft.getInstance().setScreen(null);
                    ClientController.get().open(page);
                }
            }
        });

        // /odc 客户端命令
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register((com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>)
                    com.opendreamcore.client.OdcCommands.buildRoot());
        });
    }

    /**
     * 连接服务器时将 /odc 命令转发到服务端执行（单人世界返回 false 走本地逻辑）。
     * 直接发送命令协议包绕过客户端命令调度器，防止 /odc 自匹配导致无限递归。
     */
    private static boolean forwardToServerIfConnected(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                                      String subCommand)  {
        // 一链路：转发实现在共享树 ClientController，版本差异由其内部反射吸收
        return ClientController.get().tryForwardOdcCommand(subCommand);
    }
}
