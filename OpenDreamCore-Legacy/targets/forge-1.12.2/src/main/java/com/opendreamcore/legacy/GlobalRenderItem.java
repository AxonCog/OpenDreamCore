package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyVisualItemEffects;
import com.opendreamcore.client.visual.LegacyVisualItemIcons;
import com.opendreamcore.model1212.LegacyItemModel1212;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 全局物品图标覆写（1.12.2）：老版本没有 mixin，跟 GlobalFontRenderer 一个
 * 套路——继承原版 RenderItem 换掉 Minecraft.renderItem 实例。画图标前查
 * ItemIcon 规则，命中就盖自定义贴图，不命中原版照画。
 */
public class GlobalRenderItem extends RenderItem {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    public GlobalRenderItem(TextureManager textureManager, ModelManager modelManager,
                            net.minecraft.client.renderer.color.ItemColors itemColors) {
        super(textureManager, modelManager, itemColors);
    }

    @Override
    public void renderItemAndEffectIntoGUI(ItemStack stack, int x, int y) {
        String id = "";
        if (stack != null && !stack.isEmpty()) {
        if (LegacyItemModel1212.tryGui(this, stack, x, y)) {
            return;
        }
            id = stack.getItem().getRegistryName() == null
                    ? "" : stack.getItem().getRegistryName().toString();
            LegacyVisualItemIcons.Selection selected =
                    LegacyVisualItemIcons.selectionForStack(id, stack);
            if (selected != null && drawIcon(selected, x, y)) {
                drawEffect(id, x, y);
                return;
            }
        }
        super.renderItemAndEffectIntoGUI(stack, x, y);
        drawEffect(id, x, y);
    }

    /** 物品特效：super 画完图标叠半透明发光层（色块随 alpha 递减）。 */
    private static void drawEffect(String id, int x, int y) {
        LegacyVisualItemEffects.Effect fx = LegacyVisualItemEffects.effectFor(id);
        if (fx == null) {
            return;
        }
        int c = fx.color;
        float a = ((c >>> 24) & 0xFF) / 255.0F;
        float r = ((c >>> 16) & 0xFF) / 255.0F;
        float g = ((c >>> 8) & 0xFF) / 255.0F;
        float b = (c & 0xFF) / 255.0F;
        float alpha = Math.max(0.05F, a * 0.20F);
        GlStateManager.enableBlend();
        GlStateManager.color(r, g, b, alpha);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        bb.pos(x - 3, y - 3, 0.0D).endVertex();
        bb.pos(x - 3, y + 19, 0.0D).endVertex();
        bb.pos(x + 19, y + 19, 0.0D).endVertex();
        bb.pos(x + 19, y - 3, 0.0D).endVertex();
        tess.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    /** 把覆写贴图画在图标位：bind + tessellator quad。 */
    private static boolean drawIcon(LegacyVisualItemIcons.Selection selected, int x, int y) {
        if (selected == null || selected.texture == null || !(selected.scale > 0.0)
                || Double.isNaN(selected.scale) || Double.isInfinite(selected.scale)) {
            return false;
        }
        com.opendreamcore.client.LooseTextureLoader.Sheet sheet =
                com.opendreamcore.client.LooseTextureLoader.sheetOf(
                        selected.texture, selected.frameMs);
        ResourceLocation rl;
        try {
            if (sheet != null) {
                rl = new ResourceLocation(sheet.rl);
            } else {
                String loose = com.opendreamcore.client.LooseTextureLoader.lookup(selected.texture);
                if (loose != null) {
                    rl = new ResourceLocation(loose);
                } else {
                    int colon = selected.texture.indexOf(':');
                    rl = colon > 0
                            ? new ResourceLocation(selected.texture)
                            : new ResourceLocation(OdcLegacy.MODID, selected.texture);
                    try (java.io.InputStream in = Minecraft.getMinecraft().getResourceManager()
                            .getResource(rl).getInputStream()) {
                        // Opening the stream verifies that the vanilla resource exists.
                    }
                }
            }
            Minecraft.getMinecraft().getTextureManager().bindTexture(rl);
        } catch (Exception ex) {
            return false;
        }
        double size = 16.0 * selected.scale;
        double left = x + 8.0 - size / 2.0;
        double top = y + 8.0 - size / 2.0;
        double u0 = sheet != null && sheet.frames > 1
                ? (double) sheet.frame / sheet.frames : 0.0;
        double u1 = sheet != null && sheet.frames > 1
                ? (double) (sheet.frame + 1) / sheet.frames : 1.0;
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bb.pos(left, top, 0.0D).tex(u0, 0.0D).endVertex();
        bb.pos(left, top + size, 0.0D).tex(u0, 1.0D).endVertex();
        bb.pos(left + size, top + size, 0.0D).tex(u1, 1.0D).endVertex();
        bb.pos(left + size, top, 0.0D).tex(u1, 0.0D).endVertex();
        tess.draw();
        GlStateManager.disableBlend();
        return true;
    }

    /** 全局替换安装：幂等，反射换掉 Minecraft.renderItem（1.12.2 字段是 private）。 */
    private static boolean installed;

    public static void install(Minecraft mc) {
        if (installed) {
            return;
        }
        try {
            GlobalRenderItem gri = new GlobalRenderItem(mc.getTextureManager(),
                    mc.getRenderItem().getItemModelMesher().getModelManager(),
                    new net.minecraft.client.renderer.color.ItemColors());
            java.lang.reflect.Field f = Minecraft.class.getDeclaredField("renderItem");
            f.setAccessible(true);
            f.set(mc, gri);
            installed = true;
        } catch (Throwable t) {
            LOGGER.warn("[ODC] 反射替换 Minecraft.renderItem 失败，物品图标覆写不生效：{}", t.toString());
        }
    }
}
