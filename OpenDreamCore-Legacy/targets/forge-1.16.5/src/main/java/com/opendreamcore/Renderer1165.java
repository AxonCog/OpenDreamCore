package com.opendreamcore;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.BufferBuilder;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.opendreamcore.client.render.LegacyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * 1.16.5 渲染实现：这代开始原版把立即模式换成了矩阵栈 + BufferBuilder，
 * 坐标全靠 MatrixStack 摆，画顶点时还得手动把 pose 矩阵喂给每个点。
 * 逻辑像素走 MainWindow 的 GUI 缩放尺寸，和其他版本一个口径。
 */
public final class Renderer1165 implements LegacyRenderer {

    private final MatrixStack stack = new MatrixStack();
    private int scaledWidth;
    private int scaledHeight;
    /** 世界画布是否展开中：字体/纹理画法看这口子决定要不要额外关深度写。 */
    private boolean worldActive;

    /** 每帧由事件层注入当前 GUI 缩放尺寸。 */
    public void updateViewport(Minecraft mc) {
        this.scaledWidth = mc.getWindow().getGuiScaledWidth();
        this.scaledHeight = mc.getWindow().getGuiScaledHeight();
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
        stack.pushPose();
    }

    @Override
    public void popPose() {
        stack.popPose();
    }

    @Override
    public void translate(double x, double y, double z) {
        stack.translate(x, y, z);
    }

    @Override
    public void rotateZ(double degrees) {
        stack.mulPose(Vector3f.ZP.rotationDegrees((float) degrees));
    }

    @Override
    public void scale(double factor) {
        stack.scale((float) factor, (float) factor, 1.0F);
    }

    /**
     * 世界相位画布：现代端 WorldHologram 同配方——相机位置扣位、
     * camera.rotation() 做 billboard、y 负缩放（把 2D 画布的 y 翻成世界 y-up）。
     * 1px = 0.025 格，锚在世界坐标上。深度测试留着（会被墙挡），写入关掉。
     */
    @Override
    public boolean beginWorld(double camX, double camY, double camZ,
                              double anchorX, double anchorY, double anchorZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return false;
        }
        net.minecraft.client.renderer.ActiveRenderInfo camera = mc.gameRenderer.getMainCamera();
        stack.pushPose();
        stack.translate(-camX, -camY, -camZ);
        stack.translate(anchorX, anchorY, anchorZ);
        stack.mulPose(camera.rotation());
        float s = (float) com.opendreamcore.client.render.PageDirector.BLOCKS_PER_PX;
        stack.scale(s, -s, s);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        worldActive = true;
        return true;
    }

    @Override
    public void endWorld() {
        worldActive = false;
        RenderSystem.depthMask(true);
        stack.popPose();
    }

    /** 把 pose 矩阵套到顶点上再入队，这代没有现成的矩阵顶点口子。
     * z 走变换后的真值：屏幕相位恒为 0，世界相位要拿它做深度遮挡。 */
    private void putVertex(IVertexBuilder bb, double x, double y, int argb) {
        Vector4f v = new Vector4f((float) x, (float) y, 0.0F, 1.0F);
        v.transform(stack.last().pose());
        bb.vertex(v.x(), v.y(), v.z())
                .color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF,
                        (argb >>> 24) & 0xFF)
                .endVertex();
    }

    @Override
    public void fillRect(double x, double y, double w, double h, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        putVertex(bb, x, y, argb);
        putVertex(bb, x, y + h, argb);
        putVertex(bb, x + w, y + h, argb);
        putVertex(bb, x + w, y, argb);
        tess.end();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
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
        if ((argb >>> 24) == 0) {
            return;
        }
        double rad = Math.min(radius, Math.min(w, h) / 2.0);
        if (rad < 0.5) {
            fillRect(x, y, w, h, argb);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // 中央矩形 + 左右竖条 + 上下横条（每个两三角，逐顶点过 pose 矩阵）
        tri(bb, x + rad, y, x + rad, y + h, x + w - rad, y + h, argb);
        tri(bb, x + rad, y, x + w - rad, y + h, x + w - rad, y, argb);
        tri(bb, x, y + rad, x, y + h - rad, x + rad, y + h - rad, argb);
        tri(bb, x, y + rad, x + rad, y + h - rad, x + rad, y + rad, argb);
        tri(bb, x + w - rad, y + rad, x + w - rad, y + h - rad, x + w, y + h - rad, argb);
        tri(bb, x + w - rad, y + rad, x + w, y + h - rad, x + w, y + rad, argb);
        tri(bb, x + rad, y, x + rad, y + rad, x + w - rad, y + rad, argb);
        tri(bb, x + rad, y, x + w - rad, y + rad, x + w - rad, y, argb);
        cornerFan(bb, x, y, rad, 180.0, argb);
        cornerFan(bb, x + w, y, rad, 270.0, argb);
        cornerFan(bb, x + w, y + h, rad, 0.0, argb);
        cornerFan(bb, x, y + h, rad, 90.0, argb);
        tess.end();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    /** 单三角三顶点，同色。 */
    private void tri(BufferBuilder bb, double ax, double ay, double bx, double by,
                     double cx, double cy, int argb) {
        putVertex(bb, ax, ay, argb);
        putVertex(bb, bx, by, argb);
        putVertex(bb, cx, cy, argb);
    }

    /** 单个角：四分之一圆拆 6 个三角。圆心 = 角落点向矩形内部收 rad。 */
    private void cornerFan(BufferBuilder bb, double cx, double cy, double rad,
                           double baseDeg, int argb) {
        double ox, oy;
        if (baseDeg == 180.0)      { ox = cx + rad; oy = cy + rad; }
        else if (baseDeg == 270.0) { ox = cx - rad; oy = cy + rad; }
        else if (baseDeg == 0.0)   { ox = cx - rad; oy = cy - rad; }
        else                       { ox = cx + rad; oy = cy - rad; }
        for (int i = 0; i < 6; i++) {
            double a0 = Math.toRadians(baseDeg + 90.0 * i / 6);
            double a1 = Math.toRadians(baseDeg + 90.0 * (i + 1) / 6);
            putVertex(bb, ox, oy, argb);
            putVertex(bb, ox + Math.cos(a0) * rad, oy + Math.sin(a0) * rad, argb);
            putVertex(bb, ox + Math.cos(a1) * rad, oy + Math.sin(a1) * rad, argb);
        }
    }

    @Override
    public void fillRoundedGradient(double x, double y, double w, double h, double radius,
                                    int topArgb, int bottomArgb) {
        if (((topArgb >>> 24) & 0xFF) == 0 && ((bottomArgb >>> 24) & 0xFF) == 0) {
            return;
        }
        double rad = Math.min(radius, Math.min(w, h) / 2.0);
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // 中央 + 四边 + 四角扇形一次批次画完，顶点颜色沿整面板高度插值。
        // 逐行条纹那版每行翻一次状态、透色叠亮还会随相机微动闪贴——废掉
        if (rad < 0.5) {
            gradQuad(bb, x, y, x + w, y + h, y, y + h, topArgb, bottomArgb);
        } else {
            gradQuad(bb, x + rad, y + rad, x + w - rad, y + h - rad, y, y + h, topArgb, bottomArgb);
            gradQuad(bb, x + rad, y, x + w - rad, y + rad, y, y + h, topArgb, bottomArgb);
            gradQuad(bb, x + rad, y + h - rad, x + w - rad, y + h, y, y + h, topArgb, bottomArgb);
            gradQuad(bb, x, y + rad, x + rad, y + h - rad, y, y + h, topArgb, bottomArgb);
            gradQuad(bb, x + w - rad, y + rad, x + w, y + h - rad, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(bb, x + w - rad, y + h - rad, rad, 0.0, 90.0, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(bb, x + rad, y + h - rad, rad, 90.0, 180.0, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(bb, x + rad, y + rad, rad, 180.0, 270.0, y, y + h, topArgb, bottomArgb);
            cornerFanGrad(bb, x + w - rad, y + rad, rad, 270.0, 360.0, y, y + h, topArgb, bottomArgb);
        }
        tess.end();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    /** 渐变顶点：颜色沿 y 在 [yTop,yBot] 区间内插值（顶色→底色）。 */
    private void gradVertex(BufferBuilder bb, double x, double y, double yTop, double yBot,
                                   int topArgb, int bottomArgb) {
        double t = yBot > yTop ? (y - yTop) / (yBot - yTop) : 0.0;
        putVertex(bb, x, y, lerpColor(topArgb, bottomArgb, Math.max(0.0, Math.min(1.0, t))));
    }

    /** 渐变四边形：两个三角形六顶点，颜色带的是全面板插值跨度。 */
    private void gradQuad(BufferBuilder bb, double x0, double y0, double x1, double y1,
                                 double yTop, double yBot, int topArgb, int bottomArgb) {
        gradVertex(bb, x0, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x1, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x1, y1, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x0, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x1, y1, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x0, y1, yTop, yBot, topArgb, bottomArgb);
    }

    /** 渐变角扇形：圆心向内收 rad 的四分之一圆拆 6 段三角，顶点同样带插值色。 */
    private void cornerFanGrad(BufferBuilder bb, double cx, double cy, double rad,
                                      double a0, double a1, double yTop, double yBot,
                                      int topArgb, int bottomArgb) {
        double px = cx + Math.cos(Math.toRadians(a0)) * rad;
        double py = cy + Math.sin(Math.toRadians(a0)) * rad;
        for (int i = 1; i <= 6; i++) {
            double ang = Math.toRadians(a0 + (a1 - a0) * i / 6.0);
            double nx = cx + Math.cos(ang) * rad;
            double ny = cy + Math.sin(ang) * rad;
            gradVertex(bb, cx, cy, yTop, yBot, topArgb, bottomArgb);
            gradVertex(bb, px, py, yTop, yBot, topArgb, bottomArgb);
            gradVertex(bb, nx, ny, yTop, yBot, topArgb, bottomArgb);
            px = nx;
            py = ny;
        }
    }

    /** 颜色按 t 线性插值（t=0 顶色，t=1 底色）。 */
    @Override
    public void drawLine(double x1, double y1, double x2, double y2, double width, int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (((argb >>> 24) & 0xFF) == 0 || len < 0.0001) {
            return;
        }
        // 法向撑宽：两端各垂直偏移半宽，一个四边形拆两三角铺下去
        double nx = -dy / len * width / 2.0;
        double ny = dx / len * width / 2.0;
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        tri(bb, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, argb);
        tri(bb, x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, argb);
        tess.end();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    @Override
    public void fillCircle(double cx, double cy, double radius, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        if (radius < 0.5) {
            fillRect(cx - radius, cy - radius, radius * 2, radius * 2, argb);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // 整圆扇形 24 段，和四角扇形一个数学
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.toRadians(360.0 * i / segs);
            double a1 = Math.toRadians(360.0 * (i + 1) / segs);
            tri(bb, cx, cy,
                    cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius,
                    cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, argb);
        }
        tess.end();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    @Override
    public void fillTriangle(double x1, double y1, double x2, double y2,
                             double x3, double y3, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        tri(bb, x1, y1, x2, y2, x3, y3, argb);
        tess.end();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
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
        net.minecraft.client.gui.FontRenderer font = Minecraft.getInstance().font;
        // 字体以左上角为起点，和其他版本一个口径
        if (shadow) {
            font.drawShadow(stack, text, (float) x, (float) y, argb);
        } else {
            font.draw(stack, text, (float) x, (float) y, argb);
        }
        return font.width(text);
    }

    @Override
    public double textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public void drawImage(String texture, double x, double y, double w, double h,
                          double u0, double v0, double u1, double v1, int tintArgb) {
        ResourceLocation rl = resolve(texture);
        // 缺图不绑「缺失纹理」（品红棋盘格），不画、只喊一次
        try {
            Minecraft.getInstance().getResourceManager().getResource(rl);
        } catch (java.io.IOException gone) {
            com.opendreamcore.client.spi.ChatNotifier.Host.warnOnce(
                    "tex:" + texture, "§e[OpenDreamCore] §f贴图不存在: " + texture);
            return;
        }
        Minecraft.getInstance().getTextureManager().bind(rl);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
        putTex(bb, x, y, u0, v0, tintArgb);
        putTex(bb, x, y + h, u0, v1, tintArgb);
        putTex(bb, x + w, y + h, u1, v1, tintArgb);
        putTex(bb, x + w, y, u1, v0, tintArgb);
        tess.end();

        RenderSystem.disableBlend();
    }

    /** POSITION_COLOR_TEX 的元素顺序是 position → color → uv，入队顺序别乱。
     * z 同 putVertex：世界相位要真值。 */
    private void putTex(IVertexBuilder bb, double x, double y, double u, double v, int tintArgb) {
        Vector4f p = new Vector4f((float) x, (float) y, 0.0F, 1.0F);
        p.transform(stack.last().pose());
        bb.vertex(p.x(), p.y(), p.z())
                .color((tintArgb >>> 16) & 0xFF, (tintArgb >>> 8) & 0xFF, tintArgb & 0xFF,
                        (tintArgb >>> 24) & 0xFF)
                .uv((float) u, (float) v)
                .endVertex();
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
        return new ResourceLocation(OdcLegacy.MODID, texture);
    }
}
