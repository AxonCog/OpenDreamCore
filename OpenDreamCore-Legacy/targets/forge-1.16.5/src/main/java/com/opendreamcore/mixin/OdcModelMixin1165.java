package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.opendreamcore.model1165.ModelRenderer1165;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the EntityModel body call; render continues with every later layer. */
@Mixin(LivingRenderer.class)
public abstract class OdcModelMixin1165<T extends LivingEntity, M extends net.minecraft.client.renderer.entity.model.EntityModel<T>> {
    private static final ThreadLocal<BodyContext> ODC_BODY = new ThreadLocal<BodyContext>();

    private static final class BodyContext {
        final LivingEntity entity;
        final float partialTicks;
        final IRenderTypeBuffer buffer;
        final int packedLight;

        BodyContext(LivingEntity entity, float partialTicks, IRenderTypeBuffer buffer, int packedLight) {
            this.entity = entity;
            this.partialTicks = partialTicks;
            this.buffer = buffer;
            this.packedLight = packedLight;
        }
    }

    @Inject(method = "func_225623_a_(Lnet/minecraft/entity/LivingEntity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V",
            at = @At("HEAD"))
    private void odc$begin(LivingEntity entity, float entityYaw, float partialTicks,
                           MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight,
                           CallbackInfo ci) {
        if (entity instanceof LivingEntity) {
            ODC_BODY.set(new BodyContext((LivingEntity) entity, partialTicks, buffer, packedLight));
        }
    }

    @Inject(method = "func_225623_a_(Lnet/minecraft/entity/LivingEntity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V",
            at = @At("RETURN"))
    private void odc$end(LivingEntity entity, float entityYaw, float partialTicks,
                         MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight,
                         CallbackInfo ci) {
        ODC_BODY.remove();
    }

    @Redirect(method = "func_225623_a_(Lnet/minecraft/entity/LivingEntity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/model/Model;func_225598_a_(Lcom/mojang/blaze3d/matrix/MatrixStack;Lcom/mojang/blaze3d/vertex/IVertexBuilder;IIIFFFF)V"))
    private void odc$body(net.minecraft.client.renderer.model.Model model, MatrixStack stack,
                          IVertexBuilder original, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        BodyContext ctx = ODC_BODY.get();
        if (ctx == null || !ModelRenderer1165.renderBody(ctx.entity, ctx.partialTicks,
                stack, ctx.buffer, ctx.packedLight)) {
            model.renderToBuffer(stack, original, packedLight, packedOverlay,
                    red, green, blue, alpha);
        }
    }
}
