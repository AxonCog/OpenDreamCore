package com.opendreamcore.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 1.21.11 物品 3D 展示桥：这版管线往 26.x 靠——renderStatic 删了、ResourceLocation
 * 改名 Identifier，GUI 物品只有 GuiGraphics.renderItem（平图入口）。
 * 旋转登记 SYNC-MATRIX 为管线限制（GUI 矩阵 2D）；缩放/居中生效，物品是真 3D 模型。
 */
public final class ItemModelRenderBridgeImpl implements ItemModelRenderBridge {

    @Override
    public void drawItemModel(Object g0, int cx, int cy, float scale, float yaw, float pitch,
                              String itemId, int alpha) {
        if (!(g0 instanceof GuiGraphics g)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ItemStack stack = resolveItem(itemId);
        if (stack.isEmpty()) {
            return;
        }
        float s = scale <= 0 ? 1.0F : scale;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx - 8 * s, cy - 8 * s);
        pose.scale(s, s);
        g.renderItem(stack, 0, 0);
        pose.popMatrix();
    }

    private static ItemStack resolveItem(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            rl = Identifier.withDefaultNamespace(id);
        }
        var got = BuiltInRegistries.ITEM.get(rl);
        Item item;
        if (got instanceof java.util.Optional<?> opt) {
            item = unwrap(opt.orElse(null));
        } else {
            item = unwrap(got);
        }
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Reference/Reference2 这类注册表引用统一解包到 value()（版本细节别抠死）。 */
    private static Item unwrap(Object ref) {
        if (ref instanceof Item i) {
            return i;
        }
        try {
            java.lang.reflect.Method value = ref.getClass().getMethod("value");
            Object v = value.invoke(ref);
            return v instanceof Item i ? i : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
