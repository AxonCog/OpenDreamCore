package com.opendreamcore.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

/**
 * 1.20.1 Forge 物品 3D 展示桥：ItemRenderer.render(FIXED) + PoseStack 旋转。
 * 注册表走 ForgeRegistries.ITEMS（与 forge 侧实体桥同源）。
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
        var model = ir.getModel(stack, mc.level, mc.player, 0);
        PoseStack pose = new PoseStack();
        pose.translate(cx, cy, 100.0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0.0F));
        pose.translate(-0.5F, -0.5F, 0.0F);
        // 独立批次：以前借原版共享源再 endBatch()，会把原版正在攒的实体/粒子批次提前送走；
        // 开光影（Iris/Oculus 按渲染类型重绘世界）时几何会落错 gbuffer 阶段，表现为物品部分透明。
        com.opendreamcore.client.CompatRender.WorldBatch worldBatch =
                com.opendreamcore.client.CompatRender.beginWorldBatch();
        MultiBufferSource buffers = worldBatch.source() != null
                ? (MultiBufferSource) worldBatch.source()
                : mc.renderBuffers().bufferSource();
        try {
            ir.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, 0xF000F0, 0, model);
        } finally {
            worldBatch.close();
        }
    }

    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        var item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public java.util.List<Object> loreLines(Object stack) {
        net.minecraft.world.item.ItemStack s = (net.minecraft.world.item.ItemStack) stack;
        net.minecraft.nbt.CompoundTag tag = s.getTag();
        if (tag == null || !tag.contains("display", 10)) {
            return java.util.List.of();
        }
        net.minecraft.nbt.CompoundTag display = tag.getCompound("display");
        if (!display.contains("Lore", 9)) {
            return java.util.List.of();
        }
        net.minecraft.nbt.ListTag list = display.getList("Lore", 8);
        java.util.List<Object> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.network.chat.Component line =
                    net.minecraft.network.chat.Component.Serializer.fromJson(list.getString(i));
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    @Override
    public java.util.List<Object> tooltipLines(Object stack, Object level, Object player, Object flag) {
        return new java.util.ArrayList<Object>(((net.minecraft.world.item.ItemStack) stack).getTooltipLines(
                (net.minecraft.world.entity.player.Player) player,
                (net.minecraft.world.item.TooltipFlag) flag));
    }

    @Override
    public int packFormat() {
        return net.minecraft.SharedConstants.getCurrentVersion()
                .getPackVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
    }

}