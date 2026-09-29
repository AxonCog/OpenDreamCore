package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.opendreamcore.client.visual.LegacyVisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（1.16.5）：EntityRenderer.renderNameTag 出点拦下，命中规则就
 * billboard 自绘文字顶掉原版（颜色生效；背景简化版不做，见 SYNC-MATRIX）。
 *
 * 两类名字要分清：Java 代码里引用的类/方法写源码名（编译期是 mojmap，重混淆会改字节码引用）；
 * 而 @Inject 里那个字符串重混淆不管，运行时又没有任何东西把它翻成 srg——所以它必须直接写 srg 名。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinNameTag {

    @Inject(method = "func_225629_a_(Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/util/text/ITextComponent;"
            + "Lcom/mojang/blaze3d/matrix/MatrixStack;"
            + "Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(Entity entity, ITextComponent name, MatrixStack pose,
                                              IRenderTypeBuffer buffer, int light, CallbackInfo ci) {
        if (entity == null || name == null) {
            return;
        }
        String type = ForgeRegistries.ENTITIES.getKey(entity.getType()) == null ? ""
                : ForgeRegistries.ENTITIES.getKey(entity.getType()).toString();
        LegacyVisualNameTags.TagStyle style = LegacyVisualNameTags.styleFor(type, name.getString());
        if (style == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ActiveRenderInfo camera = mc.gameRenderer.getMainCamera();
        pose.pushPose();
        pose.translate(0.0D, entity.getDimensions(entity.getPose()).height + 0.5D, 0.0D);
        pose.mulPose(camera.rotation());
        float s = 0.025F;
        pose.scale(-s, -s, s);
        String text = name.getString();
        int tw = mc.font.width(text);
        mc.font.draw(pose, text, -tw / 2.0F, -mc.font.lineHeight / 2.0F, style.textColor);
        pose.popPose();
        ci.cancel();
    }
}