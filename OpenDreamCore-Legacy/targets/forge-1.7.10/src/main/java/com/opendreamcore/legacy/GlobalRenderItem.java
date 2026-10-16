package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyVisualItemEffects;
import com.opendreamcore.client.visual.LegacyVisualItemIcons;
import com.opendreamcore.model1710.LegacyItemModel1710;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 全局物品图标覆写（1.7.10）：跟 1.12.2 一个套路，换掉 Minecraft.renderItem。
 * 这代的 renderItemAndEffectIntoGUI 是五参（FontRenderer + TextureManager 显式递），
 * 物品 id 从 itemRegistry 反查。画不了就原样交给原版。
 * 图标使用规则的 GIF 帧与缩放，固定围绕 GUI 槽中心；held/center 仅供手持渲染。
 */
public class GlobalRenderItem extends RenderItem {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    public GlobalRenderItem() {
        super();
    }

    @Override
    public void renderItemAndEffectIntoGUI(FontRenderer fr, TextureManager tm,
                                           ItemStack stack, int x, int y) {
        String id = "";
        if (LegacyItemModel1710.tryGui(this, stack, x, y)) {
            return;
        }
        if (stack != null && stack.getItem() != null) {
            Object name = Item.itemRegistry.getNameForObject(stack.getItem());
            id = name == null ? "" : name.toString();
            LegacyVisualItemIcons.Selection selected =
                    LegacyVisualItemIcons.selectionForStack(id, stack);
            if (selected != null && drawIcon(selected, x, y)) {
                drawEffect(id, x, y);
                return;
            }
        }
        super.renderItemAndEffectIntoGUI(fr, tm, stack, x, y);
        drawEffect(id, x, y);
    }

    private static boolean drawIcon(LegacyVisualItemIcons.Selection selected, int x, int y) {
        if (selected == null || selected.texture == null || !(selected.scale > 0.0)
                || Double.isNaN(selected.scale) || Double.isInfinite(selected.scale)) {
            return false;
        }
        com.opendreamcore.client.LooseTextureLoader.Sheet sheet =
                com.opendreamcore.client.LooseTextureLoader.sheetOf(selected.texture, selected.frameMs);
        ResourceLocation rl;
        if (sheet != null) {
            rl = new ResourceLocation(sheet.rl);
        } else {
            String loose = com.opendreamcore.client.LooseTextureLoader.lookup(selected.texture);
            if (loose != null) {
                rl = new ResourceLocation(loose);
            } else {
                try {
                    rl = selected.texture.indexOf(':') > 0
                            ? new ResourceLocation(selected.texture)
                            : new ResourceLocation(OdcLegacy1710.MODID, selected.texture);
                    Minecraft.getMinecraft().getResourceManager().getResource(rl).getInputStream().close();
                } catch (Exception missing) {
                    return false;
                }
            }
        }
        double size = 16.0 * selected.scale;
        double left = x + 8.0 - size / 2.0;
        double top = y + 8.0 - size / 2.0;
        double u0 = sheet == null ? 0.0 : (double) sheet.frame / sheet.frames;
        double u1 = sheet == null ? 1.0 : (double) (sheet.frame + 1) / sheet.frames;
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(rl);
        } catch (Exception ex) {
            LOGGER.warn("[ODC] 覆写图标贴图加载失败：" + ex);
            return false;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(left, top, 0.0D, u0, 0.0D);
        tess.addVertexWithUV(left, top + size, 0.0D, u0, 1.0D);
        tess.addVertexWithUV(left + size, top + size, 0.0D, u1, 1.0D);
        tess.addVertexWithUV(left + size, top, 0.0D, u1, 0.0D);
        tess.draw();
        GL11.glDisable(GL11.GL_BLEND);
        return true;
    }

    /** 物品特效：画完图标叠半透明发光层（老 Tessellator 无色块 + GL 着色）。 */
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
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(r, g, b, alpha);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x - 3, y - 3, 0.0D);
        tess.addVertex(x - 3, y + 19, 0.0D);
        tess.addVertex(x + 19, y + 19, 0.0D);
        tess.addVertex(x + 19, y - 3, 0.0D);
        tess.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
    }


    /** 全局替换安装：幂等，换掉 Minecraft.renderItem。 */
    private static boolean installed;

    public static void install(Minecraft mc) {
        if (installed) {
            return;
        }
        try {
            // 老版本 renderItem 字段可见性/名字各版不一，反射设最稳
            java.lang.reflect.Field f = Minecraft.class.getDeclaredField("renderItem");
            f.setAccessible(true);
            f.set(mc, new GlobalRenderItem());
        } catch (Throwable ex) {
            LOGGER.warn("[ODC] 反射替换 Minecraft.renderItem 失败，物品图标覆写不生效：" + ex);
        }
        installed = true;
    }
}