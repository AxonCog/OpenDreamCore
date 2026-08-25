package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hideVanilla 页面选项（1.21.9+ 提交管线版）。
 *
 * <p>26.x 的 Gui 从 render(Xxx) 族整体改名为 extractXxx(GuiGraphicsExtractor, DeltaTracker)；
 * 血条/饥饿/载具/经验/跳跃条已并入 ContextualBarRenderer 体系，无独立方法——这些层在 26.x 上
 * 暂随 hotbar 装饰层（extractHotbarAndDecorations）一并抑制，粒度损失已记录 HANDOFF。</p>
 */
@Mixin(Gui.class)
public abstract class MixinGui {

    private static void cancelIf(String layerName, CallbackInfo ci) {
        if (ClientController.get().isVanillaLayerHidden(layerName)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void opendreamcore$hideVanilla(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // 容器替换页（CONTAINER）打开 → 整个原版 HUD 取消，
        // 随后由 OdcScreen 自行绘制；或 hideVanilla: all 时先画我们的 HUD 再取消原版
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

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void odc$hideItemHotbar(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:hotbar", ci);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void odc$hideHotbarDecorations(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        // 26.x：player_health/armor_level/air_level/food_level/vehicle_health/
        // experience_bar/jump_meter 并入装饰层体系，随本入口一并抑制
        for (String layer : new String[]{
                "minecraft:player_health", "minecraft:armor_level", "minecraft:air_level",
                "minecraft:food_level", "minecraft:vehicle_health", "minecraft:experience_bar",
                "minecraft:jump_meter"}) {
            if (ClientController.get().isVanillaLayerHidden(layer)) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void odc$hideCrosshair(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:crosshair", ci);
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void odc$hideChat(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:chat", ci);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void odc$hideEffects(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:effects", ci);
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void odc$hideScoreboard(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:scoreboard_sidebar", ci);
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void odc$hideItemName(GuiGraphicsExtractor g, CallbackInfo ci) {
        cancelIf("minecraft:selected_item_name", ci);
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void odc$hideTitle(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:title", ci);
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void odc$hideOverlayMessage(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:overlay_message", ci);
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void odc$hideTabList(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:tab_list", ci);
    }

    @Inject(method = "extractCameraOverlays", at = @At("HEAD"), cancellable = true)
    private void odc$hideCameraOverlays(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:camera_overlays", ci);
    }

    @Inject(method = "extractSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void odc$hideSleepOverlay(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:sleep_overlay", ci);
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void odc$hideBossOverlay(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        cancelIf("minecraft:boss_overlay", ci);
    }

    @Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)
    private void odc$hideDebugOverlay(GuiGraphicsExtractor g, CallbackInfo ci) {
        cancelIf("minecraft:debug_overlay", ci);
    }
}
