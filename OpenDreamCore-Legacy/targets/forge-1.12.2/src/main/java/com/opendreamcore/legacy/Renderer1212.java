package com.opendreamcore.legacy;

import com.opendreamcore.client.render.LegacyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 1.12.2 渲染实现：GL11 立即模式 + GlStateManager。
 * 矩阵栈走 glPush/glPop；坐标按 ScaledResolution 的逻辑像素。
 */
public final class Renderer1212 implements LegacyRenderer {

    private int scaledWidth;
    private int scaledHeight;

    /** 每帧由事件层注入当前 ScaledResolution。 */
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
        GlStateManager.pushMatrix();
    }

    @Override
    public void popPose() {
        GlStateManager.popMatrix();
    }

    @Override
    public void translate(double x, double y, double z) {
        GlStateManager.translate(x, y, z);
    }

    @Override
    public void rotateZ(double degrees) {
        GlStateManager.rotate((float) degrees, 0F, 0F, 1F);
    }

    @Override
    public void scale(double factor) {
        GlStateManager.scale(factor, factor, 1F);
    }

    /**
     * 世界相位画布：挂墙牌匾那套朝向公式（先偏航后俯仰，再负向缩放），
     * 和原版名牌的 billboard 同序（字节码验过：rotate(-viewY, Y轴) 在前），
     * 俯仰变了自己不会歪成斜条。面板平面像素 1px = 0.025 格，锚在世界坐标上正对镜头。
     * 相机扣位用 viewerPos（RenderGlobal 每帧注入的渲染视点），
     * 和 renderEntity 内部扣 renderPos 同一基准，第三人称也不穿帮。
     * 深度测试留着（面板会被墙挡），深度写入关掉（面板同 z 元素靠 LEQUAL 按序叠）。
     */
    @Override
    public boolean beginWorld(double camX, double camY, double camZ,
                              double anchorX, double anchorY, double anchorZ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getRenderManager() == null || mc.getRenderManager().renderViewEntity == null) {
            return false;
        }
        net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.translate(anchorX, anchorY, anchorZ);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        double s = com.opendreamcore.client.render.PageDirector.BLOCKS_PER_PX;
        GlStateManager.scale(-s, -s, s);
        return true;
    }

    @Override
    public void endWorld() {
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public java.util.List<com.opendreamcore.client.spi.EntitySource.Snapshot> nearbyLiving(
            double camX, double camY, double camZ, double maxDist) {
        Minecraft mc = Minecraft.getMinecraft();
        java.util.List<com.opendreamcore.client.spi.EntitySource.Snapshot> out =
                new java.util.ArrayList<>();
        if (mc.world == null) {
            return out;
        }
        double maxSq = maxDist * maxDist;
        for (Object raw : mc.world.loadedEntityList) {
            if (!(raw instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase e = (EntityLivingBase) raw;
            // 死透的和空血的别费劲（死亡动画还没播完时血条先撤）
            if (e.isDead || e.getHealth() <= 0.0F) {
                continue;
            }
            if (e.getDistanceSq(camX, camY, camZ) > maxSq) {
                continue;
            }
            com.opendreamcore.client.spi.EntitySource.Snapshot s =
                    new com.opendreamcore.client.spi.EntitySource.Snapshot();
            s.x = e.posX;
            s.y = e.posY;
            s.z = e.posZ;
            s.height = e.height;
            s.health = e.getHealth();
            s.maxHealth = e.getMaxHealth();
            s.name = e.getName() == null ? "" : e.getName();
            String tid = EntityList.getEntityString(e);
            s.typeId = tid == null ? "" : tid;
            out.add(s);
        }
        return out;
    }

    @Override
    public void fillRect(double x, double y, double w, double h, int argb) {
        // Gui.drawRect 是 z=0 的纯色四边形；直接用 Tessellator 保浮点精度
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(r, g, b, a);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        bb.pos(x, y, 0).endVertex();
        bb.pos(x, y + h, 0).endVertex();
        bb.pos(x + w, y + h, 0).endVertex();
        bb.pos(x + w, y, 0).endVertex();
        tess.draw();

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
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
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        double rad = Math.min(radius, Math.min(w, h) / 2.0);
        if (rad < 0.5) {
            fillRect(x, y, w, h, argb);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        // 中央矩形 + 四边条
        bb.pos(x + rad, y, 0).endVertex();
        bb.pos(x + rad, y + h, 0).endVertex();
        bb.pos(x + w - rad, y + h, 0).endVertex();
        bb.pos(x + rad, y, 0).endVertex();
        bb.pos(x + w - rad, y + h, 0).endVertex();
        bb.pos(x + w - rad, y, 0).endVertex();
        bb.pos(x, y + rad, 0).endVertex();
        bb.pos(x, y + h - rad, 0).endVertex();
        bb.pos(x + rad, y + h - rad, 0).endVertex();
        bb.pos(x, y + rad, 0).endVertex();
        bb.pos(x + rad, y + h - rad, 0).endVertex();
        bb.pos(x + rad, y + rad, 0).endVertex();
        bb.pos(x + w - rad, y + rad, 0).endVertex();
        bb.pos(x + w - rad, y + h - rad, 0).endVertex();
        bb.pos(x + w, y + h - rad, 0).endVertex();
        bb.pos(x + w - rad, y + rad, 0).endVertex();
        bb.pos(x + w, y + h - rad, 0).endVertex();
        bb.pos(x + w, y + rad, 0).endVertex();
        bb.pos(x + rad, y, 0).endVertex();
        bb.pos(x + rad, y + rad, 0).endVertex();
        bb.pos(x + w - rad, y + rad, 0).endVertex();
        bb.pos(x + rad, y, 0).endVertex();
        bb.pos(x + w - rad, y + rad, 0).endVertex();
        bb.pos(x + w - rad, y, 0).endVertex();
        // 四角扇形：圆心在角落内收 rad 处，6 段扇形拼四分之一圆
        corners(bb, x, y, rad, 0.0, 0.0, r, g, b, a);
        corners(bb, x + w, y, rad, 1.0, 0.0, r, g, b, a);
        corners(bb, x + w, y + h, rad, 1.0, 1.0, r, g, b, a);
        corners(bb, x, y + h, rad, 0.0, 1.0, r, g, b, a);
        tess.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** 单个角：四分之一圆扇形拆 6 个三角，sx/sy 是圆心方向（0 或 1）。 */
    private static void corners(BufferBuilder bb, double cx, double cy, double rad,
                                double sx, double sy, float r, float g, float b, float a) {
        double ox = cx + (sx == 0 ? rad : -rad);
        double oy = cy + (sy == 0 ? rad : -rad);
        double base = sx == 0 ? (sy == 0 ? 180.0 : 90.0) : (sy == 0 ? 270.0 : 0.0);
        int segs = 6;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.toRadians(base + 90.0 * i / segs);
            double a1 = Math.toRadians(base + 90.0 * (i + 1) / segs);
            bb.pos(ox, oy, 0).endVertex();
            bb.pos(ox + Math.cos(a0) * rad, oy + Math.sin(a0) * rad, 0).endVertex();
            bb.pos(ox + Math.cos(a1) * rad, oy + Math.sin(a1) * rad, 0).endVertex();
        }
    }

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
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        bb.pos(x1 + nx, y1 + ny, 0).endVertex();
        bb.pos(x2 + nx, y2 + ny, 0).endVertex();
        bb.pos(x2 - nx, y2 - ny, 0).endVertex();
        bb.pos(x1 - nx, y1 - ny, 0).endVertex();
        tess.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
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
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        // 整圆扇形 24 段，和四角扇形一个数学
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.toRadians(360.0 * i / segs);
            double a1 = Math.toRadians(360.0 * (i + 1) / segs);
            bb.pos(cx, cy, 0).endVertex();
            bb.pos(cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius, 0).endVertex();
            bb.pos(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, 0).endVertex();
        }
        tess.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Override
    public void fillTriangle(double x1, double y1, double x2, double y2,
                             double x3, double y3, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.001F) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, a);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        bb.pos(x1, y1, 0).endVertex();
        bb.pos(x2, y2, 0).endVertex();
        bb.pos(x3, y3, 0).endVertex();
        tess.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Override
    public void fillRoundedGradient(double x, double y, double w, double h, double radius,
                                    int topArgb, int bottomArgb) {
        if (((topArgb >>> 24) & 0xFF) == 0 && ((bottomArgb >>> 24) & 0xFF) == 0) {
            return;
        }
        double rad = Math.min(radius, Math.min(w, h) / 2.0);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // 中央 + 四边 + 四角扇形一次批次画完，顶点颜色沿整面板高度插值。
        // 逐行条纹那版每行翻一次 GL 状态，透色叠亮还会随相机微动闪贴——废掉
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
        tess.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** 渐变顶点：颜色沿 y 在 [yTop,yBot] 区间内插值（顶色→底色）。 */
    private static void gradVertex(BufferBuilder bb, double x, double y, double yTop, double yBot,
                                   int topArgb, int bottomArgb) {
        double t = yBot > yTop ? (y - yTop) / (yBot - yTop) : 0.0;
        int c = lerpColor(topArgb, bottomArgb, Math.max(0.0, Math.min(1.0, t)));
        bb.pos(x, y, 0)
                .color(((c >>> 16) & 0xFF) / 255.0F, ((c >>> 8) & 0xFF) / 255.0F,
                        (c & 0xFF) / 255.0F, ((c >>> 24) & 0xFF) / 255.0F)
                .endVertex();
    }

    /** 渐变四边形：两个三角形六顶点，颜色带的是全面板插值跨度。 */
    private static void gradQuad(BufferBuilder bb, double x0, double y0, double x1, double y1,
                                 double yTop, double yBot, int topArgb, int bottomArgb) {
        gradVertex(bb, x0, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x1, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x1, y1, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x0, y0, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x1, y1, yTop, yBot, topArgb, bottomArgb);
        gradVertex(bb, x0, y1, yTop, yBot, topArgb, bottomArgb);
    }

    /** 渐变角扇形：圆心向内收 rad 的四分之一圆拆 6 段三角，顶点同样带插值色。 */
    private static void cornerFanGrad(BufferBuilder bb, double cx, double cy, double rad,
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
        // 字体绘制以左上角为基线起点，与主仓语义一致
        if (shadow) {
            font.drawStringWithShadow(text, (float) x, (float) y, argb);
        } else {
            font.drawString(text, (float) x, (float) y, argb, false);
        }
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
        if (!bindCloudOrPack(rl, texture)) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        float a = ((tintArgb >>> 24) & 0xFF) / 255.0F;
        float r = ((tintArgb >>> 16) & 0xFF) / 255.0F;
        float g = ((tintArgb >>> 8) & 0xFF) / 255.0F;
        float b = (tintArgb & 0xFF) / 255.0F;
        GlStateManager.color(r, g, b, a);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        tex(bb, x, y, u0, v0);
        tex(bb, x, y + h, u0, v1);
        tex(bb, x + w, y + h, u1, v1);
        tex(bb, x + w, y, u1, v0);
        tess.draw();

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.disableBlend();
    }

    private static void tex(BufferBuilder bb, double x, double y, double u, double v) {
        bb.pos(x, y, 0).tex(u, v).endVertex();
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

    /**
     * 纹理绑定：云缓存里的图优先（服务器 resources/ 下发的），落成动态纹理再绑定；
     * 云里没有走资源包原链。返回 false 表示两边都没有，调用方直接跳过绘制。
     */
    private static boolean bindCloudOrPack(ResourceLocation rl, String texture) {
        net.minecraft.client.renderer.texture.TextureManager tm =
                Minecraft.getMinecraft().getTextureManager();
        if (com.opendreamcore.client.CloudCache.isSynced()) {
            // 云缓存命中就遮住资源包同路径：服务器下发的总比本地散图新
            ResourceLocation cloudRl = cloudTexture(texture);
            if (cloudRl != null) {
                tm.bindTexture(cloudRl);
                return true;
            }
        }
        // bindTexture 对缺资源不报错：异步加载失败后绑「缺失纹理」（品红棋盘格），
        // 实机就是那个大粉模子。先探资源是否存在，不存在不画、只喊一次。
        if (!resourceExists(rl)) {
            com.opendreamcore.client.spi.ChatNotifier.Host.warnOnce(
                    "tex:" + texture, "§e[OpenDreamCore] §f贴图不存在: " + texture);
            return false;
        }
        tm.bindTexture(rl);
        return true;
    }

    /** 资源管理器里能不能拿到这份资源（各类资源包统一口径）。 */
    private static boolean resourceExists(ResourceLocation rl) {
        try {
            return Minecraft.getMinecraft().getResourceManager()
                    .getResource(rl) != null;
        } catch (java.io.FileNotFoundException gone) {
            return false;
        } catch (Throwable t) {
            return true; // 探测失败别把好图拦死，交回原链路兜底
        }
    }

    /** 云缓存字节 → 动态纹理（带已注册表，同一贴图只建一次）。 */
    private static ResourceLocation cloudTexture(String texture) {
        byte[] png = com.opendreamcore.client.CloudCache.textureBytes(texture);
        if (png == null) {
            return null;
        }
        java.util.Map<String, ResourceLocation> cache = CLOUD_TEXTURES;
        ResourceLocation cached = cache.get(texture);
        if (cached != null) {
            return cached;
        }
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(png));
            if (img == null) {
                return null;
            }
            ResourceLocation rl = new ResourceLocation(OdcLegacy.MODID,
                    "cloud/" + Integer.toHexString(texture.hashCode()));
            Minecraft.getMinecraft().getTextureManager().loadTexture(rl,
                    new net.minecraft.client.renderer.texture.DynamicTexture(img));
            cache.put(texture, rl);
            return rl;
        } catch (Exception e) {
            OdcLegacy.LOGGER.warn("[ODC] 云贴图解码失败 {}: {}", texture, e.toString());
            return null;
        }
    }

    private static final java.util.Map<String, ResourceLocation> CLOUD_TEXTURES =
            new java.util.concurrent.ConcurrentHashMap<>();
}
