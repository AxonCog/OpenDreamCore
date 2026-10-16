package com.opendreamcore.mixin;

import com.opendreamcore.model1710.LegacyItemModel1710;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dropped stacks have their own renderer in 1.7; preserve its world-space bob and rotation. */
@Mixin(RenderItem.class)
public abstract class LegacyItemModelDrop1710 {
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void odc$itemModelDrop(EntityItem entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y + Math.sin((entity.age + partialTicks) / 10.0 + entity.hoverStart) * 0.1 + 0.1, z);
            GL11.glRotatef(((entity.age + partialTicks) / 20.0F + entity.hoverStart) * 57.29578F, 0.0F, 1.0F, 0.0F);
            if (LegacyItemModel1710.tryWorld(entity.getEntityItem())) ci.cancel();
        } finally { GL11.glPopMatrix(); }
    }
}
