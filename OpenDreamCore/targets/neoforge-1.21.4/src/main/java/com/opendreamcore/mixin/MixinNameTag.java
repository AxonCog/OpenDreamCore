package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（1.21.4）：这代 renderNameTag 已挪到 EntityRenderer 且首参是
 * EntityRenderState（javap 实测 LivingEntityRenderer 已无 renderNameTag）。
 * state 里没有实体类型，实体在 extractRenderState(T,S,float) 才有——
 * 在这里把类型记进 ThreadLocal，renderNameTag 命中规则时读它。
 * 命中规则就自绘顶掉原版；没规则原版照旧。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinNameTag {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("HEAD"))
    private void opendreamcore$captureEntityType(Entity entity, EntityRenderState state,
                                                 float partialTick, CallbackInfo ci) {
        VisualNameTags.setCurrentEntity(entity); // state 体系：实体经这里传给 hitFor（血量/距离/准星判定都要实体本体）
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lnet/minecraft/network/chat/Component;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(EntityRenderState state, Component displayName,
                                              PoseStack pose, MultiBufferSource buffer,
                                              int light, CallbackInfo ci) {
        Entity current = VisualNameTags.currentEntity();
        VisualNameTags.Hit hit = current == null ? null : VisualNameTags.hitFor(current);
        if (hit == null) {
            return; // 没规则命中，原版名牌照旧
        }
        // 名字条一律就地画（HEAD 起就是这条路径）：整页内容只**叠加**在名牌之上。
        // 以前只在 fullHud=false 时画 —— 整页路径一旦构建/布局/上屏任一步失败
        // （页面元素没写世界坐标 hologram、布局落在画布外、渲染时机不对…），
        // 原版名牌已被 cancel、页面又画不出来，名牌就整体消失。实机回归的根因。
        drawTag(state, displayName, pose, buffer, light, hit.style());
        ci.cancel();
    }

    /** 名牌自绘：实体头顶 billboard，文字带背景色（样式取规则）。 */
    private static void drawTag(EntityRenderState state, Component name, PoseStack pose,
                                MultiBufferSource buffer, int light,
                                VisualNameTags.TagStyle style) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0.0F, (float) (state.y + 0.5F), 0.0F);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025F;
        pose.scale(-s, -s, s);
        String text = name.getString();
        int tw = font.width(text);
        var matrix = pose.last().pose();
        font.drawInBatch(text, -tw / 2.0F, -font.lineHeight / 2.0F,
                style.textColor(), false, matrix, buffer, Font.DisplayMode.NORMAL,
                style.bgColor(), light);
        pose.popPose();
    }
}
