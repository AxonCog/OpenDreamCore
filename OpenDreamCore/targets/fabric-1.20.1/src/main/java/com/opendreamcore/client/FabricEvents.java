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

    private FabricEvents() {
    }

    public static void register() {
        // 网络发送：Fabric 走旧版 channel API
        ClientController.get().setSender(com.opendreamcore.network.FabricChannel::send);

        // 进服：本地页面 + 位置记忆 + HUD 挂载 + ready + tooltip 拉取
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Path uiDir = client.gameDirectory.toPath().resolve("OpenDreamCore").resolve("UI");
            ClientController.get().localPages().load(uiDir);
            ClientController.get().loadPositions();
            ClientController.get().elementEdits().load();
            ClientController.get().markLogin();
            ClientController.get().autoMountHud();
            ClientController.get().sendReady();
            ClientController.get().requestTooltips();
        });

        // HUD 常驻渲染
        HudRenderCallback.EVENT.register((g, tickDelta) -> ClientController.get().renderHud(g));

        // 世界全息（实体渲染后）
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                ClientController.get().renderWorld(context.camera(), context.tickDelta()));

        // 容器替换：原版容器打开后，命中本地 match 就换掉
        ScreenEvents.AFTER_INIT.register(ScreenEvents.ANY, (screen, scaledWidth, scaledHeight, mouseX, mouseY) -> {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen cs) {
                String target;
                if (cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                    target = "inventory";
                } else {
                    var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(cs.getMenu().getType());
                    target = id == null ? null : id.toString();
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
            dispatcher.register(ClientCommandManager.literal("odc")
                    .then(ClientCommandManager.literal("open")
                            .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String id = StringArgumentType.getString(ctx, "page");
                                        Page page = ClientController.get().localPages().get(id);
                                        if (page == null) {
                                            ctx.getSource().sendError(Component.literal("没有这个页面: " + id));
                                            return 0;
                                        }
                                        ClientController.get().open(page);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("close")
                            .executes(ctx -> {
                                ClientController.get().close();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("hud")
                            .executes(ctx -> {
                                var controller = ClientController.get();
                                if (controller.isHudOpen()) {
                                    controller.closeHud();
                                    ctx.getSource().sendFeedback(Component.literal("HUD 已关闭"));
                                } else {
                                    controller.autoMountHud();
                                    ctx.getSource().sendFeedback(Component.literal(
                                            controller.isHudOpen() ? "HUD 已挂载" : "没有 match: hud 的本地页面"));
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("edit")
                            .then(ClientCommandManager.literal("on")
                                    .executes(ctx -> {
                                        ClientController.get().toggleEdit(true);
                                        ctx.getSource().sendFeedback(Component.literal("编辑模式已开启（拖动元素改位置）"));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("off")
                                    .executes(ctx -> {
                                        ClientController.get().toggleEdit(false);
                                        ctx.getSource().sendFeedback(Component.literal("编辑模式已关闭"));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("save")
                                    .executes(ctx -> {
                                        ClientController.get().saveEdits();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("lease")
                                    .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                ClientController.get().requestLease(StringArgumentType.getString(ctx, "page"));
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("release")
                                    .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                ClientController.get().releaseLease(StringArgumentType.getString(ctx, "page"));
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("list")
                                    .executes(ctx -> {
                                        var ids = ClientController.get().localPages().ids();
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "本地页面 (" + ids.size() + "): " + String.join(", ", ids)));
                                        return 1;
                                    }))
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "用法: /odc open <页面id> | close | hud | edit | list"));
                                return 1;
                            }));
        });
    }
}
