package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界全息 + 名牌渲染钩子（1.21.9+ Fabric）。
 *
 * <p>Fabric API 的 WorldRenderEvents 在 1.21.9+ 被移除（世界渲染重构为提交管线），
 * Mixin 注入 GameRenderer.renderLevel HEAD，时点与 NeoForge 侧 RenderLevelStageEvent 一致。
 * 与 NeoForge 侧 RenderLevelStageEvent.AfterEntities 时点等价。</p>
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class MixinWorldRender {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void opendreamcore$onRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        GameRenderer self = (GameRenderer) (Object) this;
        var camera = self.getMainCamera();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        ClientController.get().renderWorld(camera, partialTick);
        ClientController.get().renderNameTags(camera, partialTick);
    }
}
