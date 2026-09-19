package com.opendreamcore.legacy;

import com.opendreamcore.client.render.LegacyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 1.6.4 渲染实现：GL11 立即模式（与 1710 同代 API，Tessellator 单例同构）。
 */
public final class Renderer164 implements LegacyRenderer {

    private int scaledWidth;
    private int scaledHeight;

    public void setScaledResolution(net.minecraft.client.gui.ScaledResolution sr) {
        this.scaledWidth = sr.getScaledWidth();
        this.scaledHeight = sr.getScaledHeight();
    }

    @Override
    public int screenWidth() {
        return scaledWidth;
    }

    @Override
    public int screenHeight() {
        return scaledHeight;
    }

    @Override
    public void pushPose() {
        GL11.glPushMatrix();
    }

    @Override
    public void popPose() {
        GL11.glPopMatrix();
    }

    @Override
    public void translate(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
    }

    @Override
    public void rotateZ(double degrees) {
        GL11.glRotated(degrees, 0, 0, 1);
    }

    @Override
    public void scale(double factor) {
        GL11.glScaled(factor, factor, 1);
    }

    /**
     * 世界相位画布：挂墙牌匾那套朝向公式（先偏航后俯仰），
     * 和原版名牌的 billboard 同序（字节码验过：rotate(-viewY, Y轴) 在前），
     * 锚在世界坐标上正对镜头，与 1710 同配方。
     * 相机扣位用 RenderManager.renderPos（静态）。
     */
    @Override
    public boolean beginWorld(double camX, double camY, double camZ,
                              double anchorX, double anchorY, double anchorZ) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glTranslated(-camX, -camY, -camZ);
        GL11.glTranslated(anchorX, anchorY, anchorZ);
        net.minecraft.client.renderer.entity.RenderManager rm =
                net.minecraft.client.renderer.entity.RenderManager.instance;
        GL11.glRotated(-rm.playerViewY, 0, 1, 0);
        GL11.glRotated(rm.playerViewX, 1, 0, 0);
        double s = com.opendreamcore.client.render.PageDirector.BLOCKS_PER_PX;
        GL11.glScaled(-s, -s, s);
        return true;
    }

    @Override
    public void endWorld() {
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    public void fillRect(double x, double y, double w, double h, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        net.minecraft.client.renderer.Tessellator tess =
                net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x, y, 0);
        tess.addVertex(x, y + h, 0);
        tess.addVertex(x + w, y + h, 0);
        tess.addVertex(x + w, y, 0);
        tess.draw();

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void outlineRect(double x, double y, double w, double h, int argb) {
        fillRect(x, y, w, 1, argb);
        fillRect(x, y + h - 1, w, 1, argb);
        fillRect(x, y, 1, h, argb);
        fillRect(x + w - 1, y, 1, h, argb);
    }

    @Override
    public void fillRounded(double x, double y, double w, double h, double radius, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.001F) {
            return;
        }
        double rad = Math.min(radius, Math.min(w, h) / 2.0);
        if (rad < 0.5) {
            fillRect(x, y, w, h, argb);
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(
                ((argb >>> 16) & 0xFF) / 255.0F,
                ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);

        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLES);
        // 中央矩形（两个三角）
        tess.addVertex(x + rad, y, 0);
        tess.addVertex(x + rad, y + h, 0);
        tess.addVertex(x + w - rad, y + h, 0);
        tess.addVertex(x + rad, y, 0);
        tess.addVertex(x + w - rad, y + h, 0);
        tess.addVertex(x + w - rad, y, 0);
        // 左右竖条
        tess.addVertex(x, y + rad, 0);
        tess.addVertex(x, y + h - rad, 0);
        tess.addVertex(x + rad, y + h - rad, 0);
        tess.addVertex(x, y + rad, 0);
        tess.addVertex(x + rad, y + h - rad, 0);
        tess.addVertex(x + rad, y + rad, 0);
        tess.addVertex(x + w - rad, y + rad, 0);
        tess.addVertex(x + w - rad, y + h - rad, 0);
        tess.addVertex(x + w, y + h - rad, 0);
        tess.addVertex(x + w - rad, y + rad, 0);
        tess.addVertex(x + w, y + h - rad, 0);
        tess.addVertex(x + w, y + rad, 0);
        // 上下横条
        tess.addVertex(x + rad, y, 0);
        tess.addVertex(x + rad, y + rad, 0);
        tess.addVertex(x + w - rad, y + rad, 0);
        tess.addVertex(x + rad, y, 0);
        tess.addVertex(x + w - rad, y + rad, 0);
        tess.addVertex(x + w - rad, y, 0);
        // 四角扇形
        cornerFan(tess, x, y, rad, 180.0);
        cornerFan(tess, x + w, y, rad, 270.0);
        cornerFan(tess, x + w, y + h, rad, 0.0);
        cornerFan(tess, x, y + h, rad, 90.0);
        tess.draw();

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** 单个角：四分之一圆拆 6 个三角，baseDeg 是起始角（沿圆周逆时针 90 度扫完）。 */
    private static void cornerFan(net.minecraft.client.renderer.Tessellator tess, double cx, double cy, double rad, double baseDeg) {
        // 圆心 = 角落点向矩形内部收 rad：
        //   左上(180°)→(cx+rad,cy+rad) 右上(270°)→(cx-rad,cy+rad)
        //   右下(0°)→(cx-rad,cy-rad)  左下(90°)→(cx+rad,cy-rad)
        double ox, oy;
        if (baseDeg == 180.0)      { ox = cx + rad; oy = cy + rad; }
        else if (baseDeg == 270.0) { ox = cx - rad; oy = cy + rad; }
        else if (baseDeg == 0.0)   { ox = cx - rad; oy = cy - rad; }
        else                       { ox = cx + rad; oy = cy - rad; }
        for (int i = 0; i < 6; i++) {
            double a0 = Math.toRadians(baseDeg + 90.0 * i / 6);
            double a1 = Math.toRadians(baseDeg + 90.0 * (i + 1) / 6);
            tess.addVertex(ox, oy, 0);
            tess.addVertex(ox + Math.cos(a0) * rad, oy + Math.sin(a0) * rad, 0);
            tess.addVertex(ox + Math.cos(a1) * rad, oy + Math.sin(a1) * rad, 0);
        }
    }

    @Override
    public void fillRoundedGradient(double x, double y, double w, double h, double radius,
                                    int topArgb, int bottomArgb) {
        if (((topArgb >>> 24) & 0xFF) == 0 && ((bottomArgb >>> 24) & 0xFF) == 0) {
            return;
        }
        double rad = Math.min(radius, Math.min(w, h) / 2.0);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 1);
        net.minecraft.client.renderer.Tessellator tess =
                net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLES);
        // 中央 + 四边 + 四角扇形一次批次画完，顶点颜色沿整面板高度插值。
        // 逐行条纹那版每行翻一次 GL 状态，透色叠亮还会随相机微动闪贴——废掉
        if (rad < 0.5) {
            gradQuad(tess, x, y, x + w, y + h, y, y + h, topArgb, bottomArgb);
        } else {
            gradQuad(tess, x + rad, y + rad, x + w - rad, y + h - rad, y, y + h, topArgb, bottomArgb);
            gradQuad(tess, x + rad, y, x + w - rad, y + rad, y, y + h, topArgb, bottomArgb);
            gradQuad(tess, x + rad, y + h - rad, x + w - rad, y + h, y, y + h, topArgb, bottomArgb);
            gradQuad(tess, x, y + rad, x + rad, y + h - rad, y, y + h, topArgb, bottomArgb);
            gradQuad(tess, x + w - rad, y + rad, x + w, y + h - rad, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(tess, x + w - rad, y + h - rad, rad, 0.0, 90.0, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(tess, x + rad, y + h - rad, rad, 90.0, 180.0, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(tess, x + rad, y + rad, rad, 180.0, 270.0, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(tess, x + w - rad, y + rad, rad, 270.0, 360.0, y, y + h, topArgb, bottomArgb);
        }
        tess.draw();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** 渐变顶点：老式 Tessellator 的颜色是「先设后落笔」，每个顶点各设一次。 */
    private static void gradVertex(net.minecraft.client.renderer.Tessellator tess, double x, double y,
                                   double yTop, double yBot, int topArgb, int bottomArgb) {
        double t = yBot > yTop ? (y - yTop) / (yBot - yTop) : 0.0;
        int c = lerpColor(topArgb, bottomArgb, Math.max(0.0, Math.min(1.0, t)));
        tess.setColorRGBA((c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
        tess.addVertex(x, y, 0);
    }

    /** 渐变四边形：两个三角形六顶点，颜色带的是全面板插值跨度。 */
    private static void gradQuad(net.minecraft.client.renderer.Tessellator tess,
                                 double x0, double y0, double x1, double y1,
                                 double yTop, double yBot, int topArgb, int bottomArgb) {
        gradVertex(tess, x0, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(tess, x1, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(tess, x1, y1, yTop, yBot, topArgb, bottomArgb);
        gradVertex(tess, x0, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(tess, x1, y1, yTop, yBot, topArgb, bottomArgb);
        gradVertex(tess, x0, y1, yTop, yBot, topArgb, bottomArgb);
    }

    /** 渐变角扇形：圆心向内收 rad 的四分之一圆拆 6 段三角，顶点同样带插值色。 */
    private static void cornerFanGrad(net.minecraft.client.renderer.Tessellator tess,
                                      double cx, double cy, double rad,
                                      double a0, double a1, double yTop, double yBot,
                                      int topArgb, int bottomArgb) {
        double px = cx + Math.cos(Math.toRadians(a0)) * rad;
        double py = cy + Math.sin(Math.toRadians(a0)) * rad;
        for (int i = 1; i <= 6; i++) {
            double ang = Math.toRadians(a0 + (a1 - a0) * i / 6.0);
            double nx = cx + Math.cos(ang) * rad;
            double ny = cy + Math.sin(ang) * rad;
            gradVertex(tess, cx, cy, yTop, yBot, topArgb, bottomArgb);
            gradVertex(tess, px, py, yTop, yBot, topArgb, bottomArgb);
            gradVertex(tess, nx, ny, yTop, yBot, topArgb, bottomArgb);
            px = nx;
            py = ny;
        }
    }

    /** 颜色按 t 线性插值（t=0 顶色，t=1 底色）。 */
    @Override
    public void drawLine(double x1, double y1, double x2, double y2, double width, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.001F) {
            return;
        }
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001) {
            return;
        }
        // 法向撑宽：两端各垂直偏移半宽，一个四边形沿线的方向铺下去
        double nx = -dy / len * width / 2.0;
        double ny = dx / len * width / 2.0;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x1 + nx, y1 + ny, 0);
        tess.addVertex(x2 + nx, y2 + ny, 0);
        tess.addVertex(x2 - nx, y2 - ny, 0);
        tess.addVertex(x1 - nx, y1 - ny, 0);
        tess.draw();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void fillCircle(double cx, double cy, double radius, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.001F) {
            return;
        }
        if (radius < 0.5) {
            fillRect(cx - radius, cy - radius, radius * 2, radius * 2, argb);
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLES);
        // 整圆扇形 24 段，和四角扇形一个数学
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.toRadians(360.0 * i / segs);
            double a1 = Math.toRadians(360.0 * (i + 1) / segs);
            tess.addVertex(cx, cy, 0);
            tess.addVertex(cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius, 0);
            tess.addVertex(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, 0);
        }
        tess.draw();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void fillTriangle(double x1, double y1, double x2, double y2,
                             double x3, double y3, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.001F) {
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLES);
        tess.addVertex(x1, y1, 0);
        tess.addVertex(x2, y2, 0);
        tess.addVertex(x3, y3, 0);
        tess.draw();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static int lerpColor(int top, int bottom, double t) {
        int a = (int) Math.round(((top >>> 24) & 0xFF) + (((bottom >>> 24) & 0xFF) - ((top >>> 24) & 0xFF)) * t);
        int r = (int) Math.round(((top >>> 16) & 0xFF) + (((bottom >>> 16) & 0xFF) - ((top >>> 16) & 0xFF)) * t);
        int g = (int) Math.round(((top >>> 8) & 0xFF) + (((bottom >>> 8) & 0xFF) - ((top >>> 8) & 0xFF)) * t);
        int b = (int) Math.round((top & 0xFF) + ((bottom & 0xFF) - (top & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }


    @Override
    public double drawText(String text, double x, double y, int argb, boolean shadow) {
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawString(text, (int) x, (int) y, argb, shadow);
        return font.getStringWidth(text);
    }

    @Override
    public double textWidth(String text) {
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
    }

    @Override
    public void drawImage(String texture, double x, double y, double w, double h,
                          double u0, double v0, double u1, double v1, int tintArgb) {
        ResourceLocation rl = resolve(texture);
        // 缺图不绑「缺失纹理」（品红棋盘格），不画、只喊一次
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(rl);
        } catch (java.io.IOException gone) {
            com.opendreamcore.client.spi.ChatNotifier.Host.warnOnce(
                    "tex:" + texture, "§e[OpenDreamCore] §f贴图不存在: " + texture);
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(rl);
        GL11.glEnable(GL11.GL_BLEND);

        net.minecraft.client.renderer.Tessellator tess =
                net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y, 0, u0, v0);
        tess.addVertexWithUV(x, y + h, 0, u0, v1);
        tess.addVertexWithUV(x + w, y + h, 0, u1, v1);
        tess.addVertexWithUV(x + w, y, 0, u1, v0);
        tess.draw();

        GL11.glDisable(GL11.GL_BLEND);
    }

    private static ResourceLocation resolve(String texture) {
        // 散装贴图（含中文文件名）优先
        String loose = com.opendreamcore.client.LooseTextureLoader.lookup(texture);
        if (loose != null) {
            return new ResourceLocation(loose);
        }
        int colon = texture.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(texture);
        }
        return new ResourceLocation(OdcLegacy164.MODID, texture.toLowerCase(java.util.Locale.ROOT));
    }
}
