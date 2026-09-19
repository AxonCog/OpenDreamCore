package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hideVanilla 页面选项：
 * all/true → 整层跳过原版 HUD（HEAD 取消 Gui.render，先画我们的 HUD）。
 * 列表 → 逐层取消（层名 = 控制器 VANILLA_LAYER_NAMES 全名）。
 * 注入目标按 fabric named jar 1.21.8 的 Gui 真实方法名逐个 javap 核对过：
 *   这版体验条（experience_bar）和跳跃计（jump_meter）并进 renderHotbarAndDecorations 里了，
 *   Gui 上已经没有独立方法，想按层关就得注那个合并方法（见下）；
 *   boss/debug/subtitle 三层从 1.21.8 起有自己的渲染方法（均为 private），直接注；
 *   没有独立方法的：air_level 气泡随 player_health 关（renderAirBubbles 归在血条族里）。
 */
@Mixin(Gui.class)
public abstract class MixinGui {

    private static void cancelIf(String layerName, CallbackInfo ci) {
        if (ClientController.get().isVanillaLayerHidden(layerName)) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void opendreamcore$hideVanilla(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // 容器替换页（CONTAINER）打开 → 整个原版 HUD 取消（与 NeoForge onRenderGui 对齐；
        // 快捷栏/背包物品贴图不再穿透自定义 UI），随后由 OdcScreen 自行绘制
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof com.opendreamcore.client.OdcScreen odc
                && odc.page().displayMode() == com.opendreamcore.page.DisplayMode.CONTAINER) {
            ci.cancel();
            return;
        }
        if (ClientController.get().vanillaHudHidden()) {
            ClientController.get().renderHud(guiGraphics);
            ClientController.get().renderWorldArrows(guiGraphics,
                    Minecraft.getInstance().gameRenderer.getMainCamera());
            ci.cancel();
        }
    }

    // 逐层（hideVanilla: [层列表]）
    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void odc$hideHotbar(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:hotbar", ci);
    }

    /** 体验条/跳跃计在 1.21.8 挪进这个合并方法了，要关这俩层就关它（顺带把装饰一起掐了，能接受）。 */
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void odc$hideHotbarDecorations(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        if (ClientController.get().isVanillaLayerHidden("minecraft:experience_bar")
                || ClientController.get().isVanillaLayerHidden("minecraft:jump_meter")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void odc$hideCrosshair(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:crosshair", ci);
    }

    /** 玩家血条：player_health + armor_level（armor 无独立方法）+ air_level（气泡无独立方法）。 */
    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void odc$hidePlayerHealth(GuiGraphics g, CallbackInfo ci) {
        if (ClientController.get().isVanillaLayerHidden("minecraft:player_health")
                || ClientController.get().isVanillaLayerHidden("minecraft:armor_level")
                || ClientController.get().isVanillaLayerHidden("minecraft:air_level")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void odc$hideFoodLevel(GuiGraphics g, Player player, int i, int j, CallbackInfo ci) {
        cancelIf("minecraft:food_level", ci);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void odc$hideVehicleHealth(GuiGraphics g, CallbackInfo ci) {
        cancelIf("minecraft:vehicle_health", ci);
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void odc$hideChat(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:chat", ci);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void odc$hideEffects(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:effects", ci);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void odc$hideScoreboard(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:scoreboard_sidebar", ci);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void odc$hideItemName(GuiGraphics g, CallbackInfo ci) {
        cancelIf("minecraft:selected_item_name", ci);
    }

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    private void odc$hideTitle(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:title", ci);
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void odc$hideOverlayMessage(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:overlay_message", ci);
    }

    @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true)
    private void odc$hideTabList(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:tab_list", ci);
    }

    @Inject(method = "renderCameraOverlays", at = @At("HEAD"), cancellable = true)
    private void odc$hideCameraOverlays(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:camera_overlays", ci);
    }

    @Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void odc$hideSleepOverlay(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:sleep_overlay", ci);
    }

    @Inject(method = "renderDemoOverlay", at = @At("HEAD"), cancellable = true)
    private void odc$hideDemoOverlay(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:demo_overlay", ci);
    }

    @Inject(method = "renderSavingIndicator", at = @At("HEAD"), cancellable = true)
    private void odc$hideSavingIndicator(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:saving_indicator", ci);
    }

    // 1.21.8 起从 Gui.render 里拆出来的独立覆盖层
    @Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true)
    private void odc$hideBossOverlay(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:boss_overlay", ci);
    }

    // debug_overlay 不注：1.21.11 签名变了（见 1.21.11 版说明），调试栏不藏也罢；
    // subtitle_overlay 不注：已有 MixinSubtitleOverlay 在 SubtitleOverlay.render 里关，避免重复
}
