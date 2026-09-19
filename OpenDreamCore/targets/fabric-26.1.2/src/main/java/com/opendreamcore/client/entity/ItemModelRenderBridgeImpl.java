package com.opendreamcore.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;

/**
 * 26.1.2 物品 3D 展示桥：GUI 进了 submit 式渲染管线，物品不再即时画，
 * 而是填充 ItemStackRenderState 后经 GuiRenderState.addItem 收进渲染节点。
 *
 * 能力边界先说清楚：这代 GUI 矩阵是 Matrix3x2f（纯 2D），3D 旋转在 GUI 里
 * 物理上不成立，yaw/pitch 只能登记进 SYNC-MATRIX 记为管线限制；
 * 缩放和居中做到位，物品本体是真 3D 模型（不是 2D 图标）。
 */
public final class ItemModelRenderBridgeImpl implements ItemModelRenderBridge {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("OpenDreamCore");

    @Override
    public void drawItemModel(Object g, int cx, int cy, float scale, float yaw, float pitch,
                              String itemId, int alpha) {
        if (!(g instanceof GuiGraphicsExtractor gge)) {
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
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI,
                mc.level, mc.player, 0);
        if (state.isEmpty()) {
            return;
        }
        var gui = ((com.opendreamcore.mixin.MixinGuiGraphicsExtractor) gge)
                .opendreamcore$getGuiRenderState();
        if (gui == null) {
            return;
        }
        float s = scale <= 0 ? 1.0F : scale;
        Matrix3x2f pose = new Matrix3x2f(gge.pose());
        pose.translate(cx - 8 * s, cy - 8 * s);
        pose.scale(s, s);
        gui.addItem(new GuiItemRenderState(pose, state, cx, cy,
                new ScreenRectangle(0, 0, Math.max(1, gge.guiWidth()), Math.max(1, gge.guiHeight()))));
    }

    private static ItemStack resolveItem(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            rl = Identifier.withDefaultNamespace(id);
        }
        // 26.1.2 的注册表 get 返回 Optional<Reference<Item>>，value() 走反射解包
        // （Reference 类的具体形态每个小版本都在动，反射最不怕它改）
        var got = BuiltInRegistries.ITEM.get(rl);
        if (got == null || !got.isPresent()) {
            return ItemStack.EMPTY;
        }
        Object ref = got.get();
        try {
            java.lang.reflect.Method value = ref.getClass().getMethod("value");
            Item item = (Item) value.invoke(ref);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Throwable t) {
            LOGGER.warn("物品 id 解包失败（{}）: {}", id, t.toString());
            return ItemStack.EMPTY;
        }
    }
}
