package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.LegacyVisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（1.7.10）：Render.renderLivingLabel 出点拦下，命中规则就
 * GL 自绘名牌顶掉原版。相机朝向复用 RenderManager.playerViewX/Y。
 */
@Mixin(Render.class)
public abstract class MixinNameTag {

    @Shadow
    protected RenderManager renderManager;

    @Inject(method = "renderLivingLabel(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(Entity entity, String str, double x, double y, double z,
                                              int maxDistance, CallbackInfo ci) {
        String type = EntityList.getEntityString(entity);
        LegacyVisualNameTags.TagStyle style = LegacyVisualNameTags.styleFor(type == null ? "" : type, str);
        if (style == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-this.renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(this.renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-0.025F, -0.025F, 0.025F);
        GL11.glDisable(GL11.GL_LIGHTING);
        int i = Minecraft.getMinecraft().fontRenderer.getStringWidth(str) / 2;
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(str, -i, -4, style.textColor);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
        ci.cancel();
    }
}