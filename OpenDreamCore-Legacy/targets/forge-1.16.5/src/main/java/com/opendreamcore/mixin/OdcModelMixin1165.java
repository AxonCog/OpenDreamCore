package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.opendreamcore.model1165.ModelRenderer1165;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体静态模型替换（1.16.5）：LivingRenderer.render（SRG func_225623_a_）HEAD 可取消，
 * 命中 Model 规则就整体接管（画烘焙模型并取消原版）。
 *
 * <p>方法名按 1.16.5 惯例直接写 SRG（本 target 的 mixin 不走 refmap，见 MixinNameTag 注释）；
 * 参数类型写 official 名（编译期由重混淆处理）。
 */
@Mixin(LivingRenderer.class)
public abstract class OdcModelMixin1165<T extends LivingEntity, M extends net.minecraft.client.renderer.entity.model.EntityModel<T>> {

    @Inject(method = "func_225623_a_(Lnet/minecraft/entity/Entity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(Entity entity, float entityYaw, float partialTicks,
                                  MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight,
                                  CallbackInfo ci) {
        if (ModelRenderer1165.tryRender(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight)) {
            ci.cancel();
        }
    }
}
