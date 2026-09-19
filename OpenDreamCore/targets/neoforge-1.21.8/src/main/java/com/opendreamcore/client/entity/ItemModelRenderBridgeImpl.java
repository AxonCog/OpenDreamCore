package com.opendreamcore.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * 1.21.4/1.21.8 物品 3D 展示桥：renderStatic(FIXED) + PoseStack 旋转。
 * 注册表取物品走反射（1.21.2+ ITEM.get 返回 Optional），缓冲提交 flush/endBatch 都试。
 */
public final class ItemModelRenderBridgeImpl implements ItemModelRenderBridge {

    @Override
    public void drawItemModel(Object g0, int cx, int cy, float scale, float yaw, float pitch,
                              String itemId, int alpha) {
        GuiGraphics g = (GuiGraphics) g0;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        ItemRenderer ir = mc.getItemRenderer();
        ItemStack stack = resolveItem(itemId);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(cx, cy, 100.0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0.0F));
        pose.translate(-0.5F, -0.5F, 0.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ir.renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, 0, pose, buffers, mc.level, 0);
        try {
            buffers.getClass().getMethod("flush").invoke(buffers);
        } catch (Exception e) {
            try {
                buffers.getClass().getMethod("endBatch").invoke(buffers);
            } catch (Exception e2) {
            }
        }
    }

    /** 反射解析物品：1.21.2+ BuiltInRegistries.ITEM.get 返回 Optional<Reference>，编译期类型漂移绕开。 */
    private static ItemStack resolveItem(String id) {
        try {
            Class<?> regClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object reg = regClass.getField("ITEM").get(null);
            Object rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            Object item = reg.getClass().getMethod("get", Object.class).invoke(reg, rl);
            if (item instanceof java.util.Optional<?> opt) {
                item = opt.map(o -> {
                    try {
                        return o.getClass().getMethod("value").invoke(o);
                    } catch (Exception e) {
                        return null;
                    }
                }).orElse(null);
            }
            if (item == null) {
                return ItemStack.EMPTY;
            }
            Class<?> isClass = Class.forName("net.minecraft.world.item.ItemStack");
            return (ItemStack) isClass.getConstructor(Class.forName("net.minecraft.world.item.Item"))
                    .newInstance(item);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}