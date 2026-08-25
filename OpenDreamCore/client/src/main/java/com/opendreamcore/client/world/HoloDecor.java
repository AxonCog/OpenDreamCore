package com.opendreamcore.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.CompatBuffer;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.UiStyle;
import com.opendreamcore.client.WorldHologram;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * C3 第三波：世界面板装饰渲染簇（自 WorldHologram 移出）——
 * 悬停描边/辉光/阴影/角标/状态图标/边框流光/虚线边/选中框等。
 */
public final class HoloDecor {
    private HoloDecor() {}

    public static final float SELECTION_THICKNESS = 0.03F;

    /** 悬停高亮框：围绕元素命中区域画半透明边框（世界单位）。 */
    public static void renderHoverOutline(PoseStack pose, RenderNode node, double fade, boolean box,
                                           java.util.Map<String, Object> pageVars) {
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double[] sz = com.opendreamcore.client.world.HoloTextRender.textAutoSizeSafe(node, pageVars); // 文本 wrap 折行自适应
        double w = sz[0];
        double h = sz[1];
        // 锁定元素悬停框琥珀化（hologram.locked = true）：与选中框/角标/包围盒的琥珀语义一致；
        // 元素级 hoverColor（#RRGGBB/#AARRGGBB）优先于全局悬停色
        boolean locked = Boolean.parseBoolean(String.valueOf(holo.get("locked")));
        int override = UiStyle.color(holo.get("hoverColor"), 0);
        int color = box ? (locked ? 0xFFB84D : (override != 0 ? override : WorldHologram.currentHoverColor))
                : (locked ? 0x66B84D66 : 0x663A4A66); // 面板元素亮色框；文本元素浅色底
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >>> 24) & 0xFF) / 255.0F * (float) fade;
        if (a <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        float t = box ? 0.02F : hh; // 文本：整块底色；面板：细边框
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        WorldHologram.applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        if (box) {
            // 上/下/左/右四条边框
            edge(builder, matrix, -hw, -hh, hw, -hh + t, r, g, b, a);
            edge(builder, matrix, -hw, hh - t, hw, hh, r, g, b, a);
            edge(builder, matrix, -hw, -hh, -hw + t, hh, r, g, b, a);
            edge(builder, matrix, hw - t, -hh, hw, hh, r, g, b, a);
        } else {
            edge(builder, matrix, -hw, -hh, hw, hh, r, g, b, a);
        }
        WorldHologram.drawSafe(builder);
        WorldHologram.applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 元素发光（hologram.glow）：多层同心半透明方辉（内容之前绘制，drag = 父链偏移并入）。 */
    public static void renderGlow(PoseStack pose, RenderNode node, double fade,
                                   java.util.Map<String, Object> pageVars, double[] drag, Object glowProp) {
        int color = 0x66FFD700;
        double sizeMul = 1.0;
        if (glowProp instanceof Map<?, ?> m) {
            color = UiStyle.color(m.get("color"), color);
            sizeMul = UiRenderer.num(m.get("size"), 1.0);
        } else {
            color = UiStyle.color(glowProp, color);
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, pageVars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, pageVars);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float baseA = ((color >>> 24) & 0xFF) / 255.0F;
        pose.pushPose();
        pose.translate(x + (drag == null ? 0 : drag[0]),
                y + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        WorldHologram.applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 四层同心辉光：1.25x / 1.6x / 2.1x / 2.8x，透明度递减
        float[] alphas = {0.30F, 0.16F, 0.08F, 0.04F};
        float[] scales = {1.25F, 1.6F, 2.1F, 2.8F};
        for (int i = 0; i < scales.length; i++) {
            float layerA = baseA * alphas[i] * (float) fade * (float) sizeMul;
            if (layerA <= 0.003F) {
                continue;
            }
            float hw = (float) (w / 2 * scales[i]);
            float hh = (float) (h / 2 * scales[i]);
            WorldHologram.quadTris(builder, matrix, -hw, -hh, hw, hh, r, g, b, layerA);
        }
        WorldHologram.drawSafe(builder);
        WorldHologram.applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 元素倒影（hologram.shadow）：向下偏移的多层半透明暗影（内容之前绘制，drag = 父链偏移并入）。 */
    public static void renderShadow(PoseStack pose, RenderNode node, double fade,
                                     java.util.Map<String, Object> pageVars, double[] drag, Object shadowProp) {
        int color = 0x33000000;
        double offsetMul = 1.0;
        double sizeMul = 1.0;
        if (shadowProp instanceof Map<?, ?> m) {
            color = UiStyle.color(m.get("color"), color);
            offsetMul = UiRenderer.num(m.get("offset"), 1.0);
            sizeMul = UiRenderer.num(m.get("size"), 1.0);
        } else {
            color = UiStyle.color(shadowProp, color);
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, pageVars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, pageVars);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float baseA = ((color >>> 24) & 0xFF) / 255.0F;
        pose.pushPose();
        pose.translate(x + (drag == null ? 0 : drag[0]),
                y + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        WorldHologram.applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 三层向下偏移阴影：偏移 0.08/0.16/0.26，透明度递减，尺寸微扩
        float[] alphas = {0.30F, 0.14F, 0.06F};
        float[] offsets = {0.08F, 0.16F, 0.26F};
        float[] scales = {1.0F, 1.08F, 1.18F};
        for (int i = 0; i < offsets.length; i++) {
            float layerA = baseA * alphas[i] * (float) fade;
            if (layerA <= 0.003F) {
                continue;
            }
            float dy = -offsets[i] * (float) offsetMul * (float) (h > 0 ? h : 1.0);
            float hw = (float) (w / 2 * scales[i] * sizeMul);
            float hh = (float) (h / 2 * scales[i] * sizeMul);
            WorldHologram.quadTris(builder, matrix, -hw, -hh + dy, hw, hh + dy, r, g, b, layerA);
        }
        WorldHologram.drawSafe(builder);
        WorldHologram.applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 元素角标（hologram.badge）：右上角 billboard 红点/数量角标（drag = 父链偏移并入）。 */
    public static void renderBadge(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                    java.util.Map<String, Object> pageVars, double[] drag, Object badgeProp) {
        boolean show = true;
        int count = 0;
        int color = 0xFFE53935;
        if (badgeProp instanceof Boolean b) {
            show = b;
        } else if (badgeProp instanceof Number n) {
            count = n.intValue();
        } else if (badgeProp instanceof Map<?, ?> m) {
            Object c = m.get("count");
            if (c == null) {
                c = m.get("value");
            }
            if (c != null) {
                count = (int) UiRenderer.num(c, 0);
            }
            color = UiStyle.color(m.get("color"), color);
        } else {
            show = Boolean.parseBoolean(String.valueOf(badgeProp));
        }
        if (!show) {
            return;
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, pageVars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, pageVars);
        double rb = Math.max(0.06, Math.min(h * 0.4, 0.35));
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x + w / 2 - rb + (drag == null ? 0 : drag[0]),
                y + h / 2 - rb + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        float cr = ((color >> 16) & 0xFF) / 255.0F;
        float cg = ((color >> 8) & 0xFF) / 255.0F;
        float cb = (color & 0xFF) / 255.0F;
        float ca = ((color >>> 24) & 0xFF) / 255.0F * (float) fade;
        if (ca > 0) {
            // 圆点：12 段扇形（TRIANGLES）
            CompatRender.enableBlend();
            CompatRender.defaultBlendFunc();
        WorldHologram.applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
            CompatRender.setColorShader();
            var matrix = pose.last().pose();
            var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            int seg = 12;
            float prevX = (float) rb;
            float prevY = 0;
            for (int i = 1; i <= seg; i++) {
                double ang = Math.toRadians(360.0 * i / seg);
                float nx = (float) (Math.cos(ang) * rb);
                float ny = (float) (Math.sin(ang) * rb);
                builder.addVertex(matrix, 0, 0, 0).setColor(cr, cg, cb, ca);
                builder.addVertex(matrix, prevX, prevY, 0).setColor(cr, cg, cb, ca);
                builder.addVertex(matrix, nx, ny, 0).setColor(cr, cg, cb, ca);
                prevX = nx;
                prevY = ny;
            }
            WorldHologram.drawSafe(builder);
        WorldHologram.applyContentDepth();
            CompatRender.disableBlend();
        }
        // 数量文字（>0 显示；99+ 截断）
        if (count > 0) {
            String label = count > 99 ? "99+" : String.valueOf(count);
            float ts = (float) (rb / 4.0);
            pose.scale(ts, -ts, ts);
            float tw = mc.font.width(label);
            mc.font.drawInBatch(label, -tw / 2, -4.0F,
                    WorldHologram.withAlpha(0xFFFFFFFF, (float) fade), true,
                    pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    0, 0xF000F0);
        }
        pose.popPose();
    }

    /** 元素状态图标（hologram.statusIcon）：左上角 billboard 文字图标（drag = 父链偏移并入）。 */
    public static void renderStatusIcon(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                         java.util.Map<String, Object> pageVars, double[] drag, Object statusProp) {
        String icon = String.valueOf(statusProp);
        int color = 0xFFE0E0E0;
        if (statusProp instanceof Map<?, ?> m) {
            Object i = m.get("icon");
            if (i == null) {
                return;
            }
            icon = String.valueOf(i);
            color = UiStyle.color(m.get("color"), color);
        }
        icon = UiRenderer.interpolate(node, icon, pageVars);
        if (icon == null || icon.isBlank()) {
            return;
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, pageVars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, pageVars);
        double rb = Math.max(0.06, Math.min(h * 0.4, 0.35));
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x - w / 2 + rb + (drag == null ? 0 : drag[0]),
                y + h / 2 - rb + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        float ts = (float) (rb / 4.0);
        pose.scale(ts, -ts, ts);
        float tw = mc.font.width(icon);
        mc.font.drawInBatch(icon, -tw / 2, -4.0F,
                WorldHologram.withAlpha(color, (float) fade), true,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0, 0xF000F0);
        pose.popPose();
    }

    /** 元素静态边框（hologram.border）：billboard 四边描边，yaw 同步旋转（drag = 父链偏移并入）。 */
    public static void renderBorderOutline(PoseStack pose, RenderNode node, double fade,
                                            java.util.Map<String, Object> pageVars, double[] drag,
                                            int color, float width, boolean flow, int flowColor,
                                            double borderRadius, long flowSpeedMs,
                                            boolean dash, float dashLen, boolean doubleLine,
                                            int flowColor2, float flowSeg, float flowPhase,
                                            float flowPhase2, float borderAlpha,
                                            boolean flowGradient, boolean flowReverse,
                                            int flowSegments, float flowSegGap,
                                            boolean hovered) {
        Map<?, ?> holo = WorldHologram.holo(node);
        // hover 加速开关（border.hoverBoost，默认 true；false = 悬停不加速不拉长段）
        boolean hoverBoost = true;
        Object bRaw = holo.get("border");
        if (bRaw instanceof Map<?, ?> bm && bm.get("hoverBoost") != null) {
            hoverBoost = Boolean.parseBoolean(String.valueOf(bm.get("hoverBoost")));
        }
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, pageVars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, pageVars);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >>> 24) & 0xFF) / 255.0F * (float) fade * Math.max(0, Math.min(1, borderAlpha));
        if (a <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(x + (drag == null ? 0 : drag[0]),
                y + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        float t = Math.max(0.005F, width);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        WorldHologram.applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        float rr = (float) Math.max(0, Math.min(borderRadius, Math.min(w, h) / 2));        if (rr > 0.01F) {
            // 圆角描边：四条直边（端点内缩 rr）+ 四角圆弧（半径 rr..rr+t）；虚线/双线仅直线矩形支持
            edge(builder, matrix, -hw + rr, -hh, hw - rr, -hh + t, r, g, b, a);
            edge(builder, matrix, -hw + rr, hh - t, hw - rr, hh, r, g, b, a);
            edge(builder, matrix, -hw, -hh + rr, -hw + t, hh - rr, r, g, b, a);
            edge(builder, matrix, hw - t, -hh + rr, hw, hh - rr, r, g, b, a);
            arcSegment(builder, matrix, hw - rr, hh - rr, rr, rr + t, 0, 90, r, g, b, a);
            arcSegment(builder, matrix, -hw + rr, hh - rr, rr, rr + t, 90, 180, r, g, b, a);
            arcSegment(builder, matrix, -hw + rr, -hh + rr, rr, rr + t, 180, 270, r, g, b, a);
            arcSegment(builder, matrix, hw - rr, -hh + rr, rr, rr + t, 270, 360, r, g, b, a);
        } else if (doubleLine) {
            // 双线：外线（外扩 t）+ 内线（内收 t），间隙 t
            float seg = Math.max(0.01F, dashLen);
            drawOutlineEdges(builder, matrix, -hw - t, -hh - t, hw + t, hh + t, r, g, b, a, t, dash, seg);
            drawOutlineEdges(builder, matrix, -hw + t, -hh + t, hw - t, hh - t, r, g, b, a, t, dash, seg);
        } else {
            float seg = Math.max(0.01F, dashLen);
            drawOutlineEdges(builder, matrix, -hw, -hh, hw, hh, r, g, b, a, t, dash, seg);
        }
        // 流光段（flow: true，垫在描边之上）：亮色段沿周长匀速流动；hover 加速（×0.375，下限 300ms）并提亮；
        // flowColor2 非 0 = 双色流光（对侧半周长第二段）
        if (flow) {
            float fa = Math.min(1.0F, ((flowColor >>> 24) & 0xFF) / 255.0F * (float) fade * (hovered ? 1.6F : 1.0F));
            long cycleMs = flowSpeedMs > 0 ? flowSpeedMs : 1200; // 可配 flowSpeed（ms/圈，0 = 默认）
            if (hovered && hoverBoost) {
                cycleMs = Math.max(300, (long) (cycleMs * 0.375));
            }
            float segLenFrac = (hovered && hoverBoost) ? 0.22F : 0.15F;
            if (flowSeg > 0) {
                segLenFrac = flowSeg; // 显式段长（周长比例，0.1~0.3）
            }
            long clock = com.opendreamcore.client.ClientController.get().worldFlowTime(); // K 暂停时钟
            float lEdge = (float) Math.max(0, 2 * hw - 2 * rr);
            float lArc = (float) (Math.PI * rr / 2);
            float perimeter = (rr > 0.01F) ? (4 * lEdge + 4 * lArc) : (4 * hw + 4 * hh);
            if (perimeter > 0.01F) {
                float cycle = (clock % cycleMs) / (float) cycleMs * perimeter;
                if (flowReverse) {
                    cycle = perimeter - cycle; // 反向流动
                }
                cycle += perimeter * flowPhase; // 相位偏移（Shift+,/. 微调；0~1 周长比例）
                float segLen = perimeter * segLenFrac;
                boolean gradient = flowGradient && flowColor2 != 0;
                // 段数：显式 flowSegments > 0 优先；否则双色对侧 = 2 段；默认 1 段
                int n = flowSegments > 0 ? flowSegments
                        : (flowColor2 != 0 && !gradient ? 2 : 1);
                for (int i = 0; i < n; i++) {
                    int segColor = (i % 2 == 0) ? flowColor
                            : (flowColor2 != 0 ? flowColor2 : flowColor);
                    // 段间距：flowSegGap > 0 = 固定间距（周长比例×i）；否则等距（perimeter/n）；
                    // 副色段（奇数索引）额外叠加 flowPhase2 独立相位（双色对侧微调）
                    float offset = flowSegGap > 0
                            ? perimeter * flowSegGap * i : perimeter * i / n;
                    if (i % 2 == 1) {
                        offset += perimeter * flowPhase2;
                    }
                    drawFlowSegmentAt(builder, matrix, hw, hh, rr, t,
                            cycle + offset, segLen, perimeter,
                            segColor, flowColor2, gradient && n == 1, fa);
                }
            }
        }
        WorldHologram.drawSafe(builder);
        WorldHologram.applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 单色/渐变流光段绘制（圆角或直线沿周长采样；gradient 时段内从 color 渐变到 color2）。 */
    public static void drawFlowSegmentAt(CompatBuffer builder,
                                          org.joml.Matrix4f matrix,
                                          float hw, float hh, float rr, float t,
                                          float cycle, float segLen, float perimeter,
                                          int color, int color2, boolean gradient, float alpha) {
        int samples = 28;
        float prevX = 0, prevY = 0;
        boolean first = true;
        for (int i = 0; i <= samples; i++) {
            float s = cycle + segLen * i / samples;
            float[] p = rr > 0.01F
                    ? roundedPerimeterPoint(s % perimeter, hw, hh, rr,
                    (float) Math.max(0, 2 * hw - 2 * rr), (float) (Math.PI * rr / 2))
                    : straightPerimeterPoint(s % perimeter, hw, hh);
            if (first) {
                prevX = p[0];
                prevY = p[1];
                first = false;
                continue;
            }
            float dx = p[0] - prevX;
            float dy = p[1] - prevY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0.001F) {
                float nx = -dy / len * t / 2;
                float ny = dx / len * t / 2;
                int c = gradient ? WorldHologram.lerpColor(color, color2, i / (float) samples) : color;
                float fr = ((c >> 16) & 0xFF) / 255.0F;
                float fg = ((c >> 8) & 0xFF) / 255.0F;
                float fb = (c & 0xFF) / 255.0F;
                builder.addVertex(matrix, prevX + nx, prevY + ny, 0).setColor(fr, fg, fb, alpha);
                builder.addVertex(matrix, p[0] + nx, p[1] + ny, 0).setColor(fr, fg, fb, alpha);
                builder.addVertex(matrix, p[0] - nx, p[1] - ny, 0).setColor(fr, fg, fb, alpha);
                builder.addVertex(matrix, prevX - nx, prevY - ny, 0).setColor(fr, fg, fb, alpha);
            }
            prevX = p[0];
            prevY = p[1];
        }
    }

    /** 直线矩形周长参数化采样点（s 沿周长：上→右→下→左）。 */
    public static float[] straightPerimeterPoint(float s, float hw, float hh) {
        float top = 2 * hw;
        float right = top + 2 * hh;
        float bottom = right + 2 * hw;
        float p = bottom + 2 * hh;
        s = ((s % p) + p) % p;
        if (s < top) {
            return new float[]{-hw + s, -hh};
        }
        if (s < right) {
            return new float[]{hw, -hh + (s - top)};
        }
        if (s < bottom) {
            return new float[]{hw - (s - right), hh};
        }
        return new float[]{-hw, hh - (s - bottom)};
    }

    /** 圆弧带（角度制）：半径 r0..r1 的扇形环段（描边圆角用）。 */
    public static void arcSegment(CompatBuffer builder, org.joml.Matrix4f matrix,
                                   float cx, float cy, float r0, float r1, double a0, double a1,
                                   float red, float green, float blue, float alpha) {
        int seg = 6;
        for (int i = 0; i < seg; i++) {
            double ang0 = Math.toRadians(a0 + (a1 - a0) * i / seg);
            double ang1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / seg);
            float x0 = cx + (float) Math.cos(ang0) * r0;
            float y0 = cy + (float) Math.sin(ang0) * r0;
            float x1 = cx + (float) Math.cos(ang1) * r0;
            float y1 = cy + (float) Math.sin(ang1) * r0;
            float x2 = cx + (float) Math.cos(ang1) * r1;
            float y2 = cy + (float) Math.sin(ang1) * r1;
            float x3 = cx + (float) Math.cos(ang0) * r1;
            float y3 = cy + (float) Math.sin(ang0) * r1;
            builder.addVertex(matrix, x0, y0, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, x1, y1, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, x2, y2, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, x3, y3, 0).setColor(red, green, blue, alpha);
        }
    }

    /** 流光段与一条边的重叠部分 → 轴对齐细四边形。边定义：起坐标 (ex,ey) + 方向 (dx,dy)，范围 [e0, e1]。 */
    /** 圆角矩形周长参数 s(0..perimeter) → 轮廓点（billboard 局部坐标，起点 = 左边中点，顺时针）。 */
    public static float[] roundedPerimeterPoint(float s, float hw, float hh, float rr,
                                                 float lEdge, float lArc) {
        float segLeft = lEdge;
        float segArcTL = segLeft + lArc;
        float segTop = segArcTL + lEdge;
        float segArcTR = segTop + lArc;
        float segRight = segArcTR + lEdge;
        float segArcBR = segRight + lArc;
        float segBottom = segArcBR + lEdge;
        float segArcBL = segBottom + lArc;
        if (s < segLeft) {
            return new float[]{-hw, hh - rr - s};
        }
        if (s < segArcTL) {
            float a = (float) (Math.PI + (s - segLeft) / lArc * (Math.PI / 2));
            return new float[]{-hw + rr + rr * (float) Math.cos(a), -hh + rr + rr * (float) Math.sin(a)};
        }
        if (s < segTop) {
            return new float[]{-hw + rr + (s - segArcTL), -hh};
        }
        if (s < segArcTR) {
            float a = (float) (-Math.PI / 2 + (s - segTop) / lArc * (Math.PI / 2));
            return new float[]{hw - rr + rr * (float) Math.cos(a), -hh + rr + rr * (float) Math.sin(a)};
        }
        if (s < segRight) {
            return new float[]{hw, -hh + rr + (s - segArcTR)};
        }
        if (s < segArcBR) {
            float a = (float) ((s - segRight) / lArc * (Math.PI / 2));
            return new float[]{hw - rr + rr * (float) Math.cos(a), hh - rr + rr * (float) Math.sin(a)};
        }
        if (s < segBottom) {
            return new float[]{hw - rr - (s - segArcBR), hh};
        }
        // 左下圆弧（回到起点）
        float a = (float) (Math.PI / 2 + (s - segBottom) / lArc * (Math.PI / 2));
        return new float[]{-hw + rr + rr * (float) Math.cos(a), hh - rr + rr * (float) Math.sin(a)};
    }

    /** 矩形描边四边绘制（虚线：段长 seg、段隙 seg×0.5；实线：整条）。 */
    public static void drawOutlineEdges(CompatBuffer builder,
                                         org.joml.Matrix4f matrix,
                                         float x0, float y0, float x1, float y1,
                                         float r, float g, float b, float a, float t,
                                         boolean dash, float seg) {
        if (!dash) {
            edge(builder, matrix, x0, y0, x1, y0 + t, r, g, b, a);
            edge(builder, matrix, x0, y1 - t, x1, y1, r, g, b, a);
            edge(builder, matrix, x0, y0, x0 + t, y1, r, g, b, a);
            edge(builder, matrix, x1 - t, y0, x1, y1, r, g, b, a);
            return;
        }
        float gap = seg * 0.5F;
        drawDashedEdge(builder, matrix, x0, y0, x1, y0, t, true, r, g, b, a, seg, gap);
        drawDashedEdge(builder, matrix, x0, y1 - t, x1, y1 - t, t, true, r, g, b, a, seg, gap);
        drawDashedEdge(builder, matrix, x0, y0, x0, y1 - t, t, false, r, g, b, a, seg, gap);
        drawDashedEdge(builder, matrix, x1 - t, y0, x1 - t, y1 - t, t, false, r, g, b, a, seg, gap);
    }

    /** 单边虚线：沿边分段绘制（段长 seg、段隙 gap）。 */
    public static void drawDashedEdge(CompatBuffer builder,
                                       org.joml.Matrix4f matrix,
                                       float x0, float y0, float x1, float y1, float t,
                                       boolean horizontal, float r, float g, float b, float a,
                                       float seg, float gap) {
        float len = horizontal ? (x1 - x0) : (y1 - y0);
        float period = seg + gap;
        if (period <= 0 || len <= 0) {
            return;
        }
        for (float s = 0; s < len; s += period) {
            float e = Math.min(s + seg, len);
            if (horizontal) {
                edge(builder, matrix, x0 + s, y0, x0 + e, y0 + t, r, g, b, a);
            } else {
                edge(builder, matrix, x0, y0 + s, x0 + t, y0 + e, r, g, b, a);
            }
        }
    }

    /** WYSIWYG 编辑模式选中框：亮蓝边框（脉冲透明度），锁定元素琥珀色，任意元素类型通用（drag = 父链偏移并入）。 */
    public static void renderSelectionOutline(PoseStack pose, RenderNode node, double fade,
                                               java.util.Map<String, Object> pageVars, double[] drag) {
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double[] sz = com.opendreamcore.client.world.HoloTextRender.textAutoSizeSafe(node, pageVars); // 文本 wrap 折行自适应
        double w = sz[0];
        double h = sz[1];
        // 脉冲：400ms 周期在 0xCC/0x66 间切换
        int alpha = ((System.currentTimeMillis() / 400) & 1) == 0 ? 0xCC : 0x66;
        // 锁定元素选中框琥珀色（与悬停框/角标/包围盒琥珀语义一致）
        boolean locked = Boolean.parseBoolean(String.valueOf(holo.get("locked")));
        float r = locked ? 1.0F : 0x42 / 255.0F;
        float g = locked ? 0.72F : 0xA5 / 255.0F;
        float b = locked ? 0.2F : 0xF5 / 255.0F;
        float a = alpha / 255.0F * (float) fade;
        if (a <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(x + (drag == null ? 0 : drag[0]),
                y + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, null, pageVars);
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        float t = 0.03F;
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 四边框（比 hover 粗）
        edge(builder, matrix, -hw, -hh, hw, -hh + t, r, g, b, a);
        edge(builder, matrix, -hw, hh - t, hw, hh, r, g, b, a);
        edge(builder, matrix, -hw, -hh, -hw + t, hh, r, g, b, a);
        edge(builder, matrix, hw - t, -hh, hw, hh, r, g, b, a);
        WorldHologram.drawSafe(builder);
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
        pose.popPose();
    }

    public static void edge(CompatBuffer builder, org.joml.Matrix4f matrix,
                             float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        builder.addVertex(matrix, x0, y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x0, y1, 0).setColor(r, g, b, a);
    }
}
