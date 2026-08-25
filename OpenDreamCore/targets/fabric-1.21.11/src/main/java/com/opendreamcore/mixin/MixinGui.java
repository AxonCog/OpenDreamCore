package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hideVanilla 页面选项：
 * - all/true → 整层跳过原版 HUD（HEAD 取消 Gui.render，先画我们的 HUD）。
 * - 列表 → 逐层取消（层名 = 控制器 VANILLA_LAYER_NAMES 全名；方法名/签名按 fabric named jar 1.21.1
 *   核实：hotbar = renderItemHotbar、food = renderFood、无独立 air/subtitle 方法 → air 随
 *   player_health、subtitle 走 MixinSubtitleOverlay；boss_overlay/debug_overlay 无 Gui 方法仅 NeoForge）。
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

    // ---- 逐层（hideVanilla: [层列表]）----
    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void odc$hideHotbar(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:hotbar", ci);
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

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void odc$hideExperienceBar(GuiGraphics g, int i, CallbackInfo ci) {
        cancelIf("minecraft:experience_bar", ci);
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void odc$hideJumpMeter(PlayerRideableJumping rideable, GuiGraphics g, int i, CallbackInfo ci) {
        cancelIf("minecraft:jump_meter", ci);
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
}
