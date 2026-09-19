package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyVisualItemEffects;
import com.opendreamcore.client.visual.LegacyVisualItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 全局物品图标覆写（1.6.4）：跟 1.12.2 一个套路，换掉 Minecraft.renderItem。
 * 这代的 renderItemAndEffectIntoGUI 是五参（FontRenderer + TextureManager 显式递），
 * 物品 id 从 itemRegistry 反查。画不了就原样交给原版。
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
        if (stack != null && stack.getItem() != null) {
            // 1.6.4 没有 registryName，拿 unlocalizedName 的尾段当类型匹配
            String un = stack.getItem().getUnlocalizedName();
            if (un != null && un.startsWith("item.")) {
                un = un.substring(5);
            } else if (un != null && un.startsWith("tile.")) {
                un = un.substring(5);
            }
            id = un == null ? "" : un;
            String tex = LegacyVisualItemIcons.textureFor(id);
            if (tex != null) {
                drawIcon(tex, x, y);
                drawEffect(id, x, y);
                return;
            }
        }
        super.renderItemAndEffectIntoGUI(fr, tm, stack, x, y);
        drawEffect(id, x, y);
    }

    private static void drawIcon(String tex, int x, int y) {
        ResourceLocation rl = resolve(tex);
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(rl);
        } catch (Exception ex) {
            LOGGER.warn("[ODC] 覆写图标贴图加载失败：" + ex);
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tess.addVertexWithUV(x, y + 16, 0.0D, 0.0D, 1.0D);
        tess.addVertexWithUV(x + 16, y + 16, 0.0D, 1.0D, 1.0D);
        tess.addVertexWithUV(x + 16, y, 0.0D, 1.0D, 0.0D);
        tess.draw();
        GL11.glDisable(GL11.GL_BLEND);
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

    private static ResourceLocation resolve(String tex) {
        // 散装贴图（含中文文件名）优先
        String loose = com.opendreamcore.client.LooseTextureLoader.lookup(tex);
        if (loose != null) {
            return new ResourceLocation(loose);
        }
        int colon = tex.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(tex);
        }
        return new ResourceLocation(OdcLegacy164.MODID, tex);
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