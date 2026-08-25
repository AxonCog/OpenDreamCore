package com.opendreamcore.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.WorldHologram;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;

import java.util.Map;

/**
 * C3 第一波：世界全息 text 组件渲染与文本尺寸（自 WorldHologram 移出）。
 * 依赖的 WH 共享助手（holo/holoNum/applyBillboardRotation/animOf/defaultQuadW/H）
 * 已放宽为 public，经 WorldHologram.xxx 调用。
 */
public final class HoloTextRender {

    private HoloTextRender() {
    }

    /** 文本自动尺寸（世界单位）：wrap > 0 时宽度 = wrap，高度 = 折行行数 × 8px × scale；
     *  未显式指定 width/height 时用——命中区域/选中框/包围盒随内容自适应。 */
    public static double[] textAutoSize(RenderNode node, Map<String, Object> pageVars) {
        Map<?, ?> holo = WorldHologram.holo(node);
        Map<?, ?> spec = com.opendreamcore.client.UiRenderer.propsMap(node, "text");
        String content = com.opendreamcore.client.UiRenderer.interpolate(
                node, com.opendreamcore.client.UiRenderer.str(spec.get("content")), pageVars);
        double scale = WorldHologram.holoNum(holo, "scale", 0.025, pageVars);
        double wrap = WorldHologram.holoNum(holo, "wrap", 0, pageVars);
        double w = holo.get("width") != null ? WorldHologram.holoNum(holo, "width", 2.0, pageVars)
                : (wrap > 0 ? wrap : 2.0);
        double h = holo.get("height") != null ? WorldHologram.holoNum(holo, "height", 0.25, pageVars)
                : (wrap > 0 && content != null
                ? Math.max(1, wrappedLineCount(content, wrap, scale)) * 8.0 * Math.max(scale, 1e-6)
                : 0.25);
        return new double[]{w, h};
    }

    /** 折行后的行数（与 renderText 同一 wrapText 算法）。 */
    private static int wrappedLineCount(String content, double wrap, double scale) {
        int wrapPx = Math.max(8, (int) (wrap / Math.max(scale, 1e-6)));
        var mc = Minecraft.getInstance();
        int lines = 0;
        for (String rawLine : content.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                lines++;
                continue;
            }
            lines += wrapText(mc.font, rawLine, wrapPx).size();
        }
        return lines;
    }

    /** 文本自适应尺寸；非文本节点返回默认命中框（悬停框/选中框共用）。 */
    public static double[] textAutoSizeSafe(RenderNode node, Map<String, Object> pageVars) {
        if ("text".equals(node.type())) {
            return textAutoSize(node, pageVars);
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        return new double[]{WorldHologram.holoNum(holo, "width", WorldHologram.defaultQuadW(node), pageVars),
                WorldHologram.holoNum(holo, "height", WorldHologram.defaultQuadH(node), pageVars)};
    }

    public static void renderText(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                  double fade, String scope, Map<String, Object> pageVars, double[] drag) {
        Map<?, ?> spec = com.opendreamcore.client.UiRenderer.propsMap(node, "text");
        String content = com.opendreamcore.client.UiRenderer.interpolate(
                node, com.opendreamcore.client.UiRenderer.str(spec.get("content")), pageVars);
        if (content == null || content.isEmpty()) {
            return;
        }
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double scale = WorldHologram.holoNum(holo, "scale", 0.025, pageVars);
        int color = com.opendreamcore.client.UiStyle.color(spec.get("color"), 0xFFFFFFFF);
        double[] anim = WorldHologram.animOf(node, scope);
        // 动画缩放 + 距离淡出 + 动画 alpha：透明度乘算
        scale *= anim == null ? 1 : anim[2];
        int alpha = (int) (((color >>> 24) & 0xFF) * fade * (anim == null ? 1 : anim[3]));
        color = (alpha << 24) | (color & 0xFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        // 对齐相机（billboard）+ 固定文本尺寸
        WorldHologram.applyBillboardRotation(pose, holo, anim, pageVars);
        pose.scale((float) scale, (float) -scale, (float) scale);
        // 多行 + 自动折行：\n 强制换行；hologram.wrap（世界单位）> 0 时按宽度折行
        java.util.List<String> lines = new java.util.ArrayList<>();
        double wrap = WorldHologram.holoNum(holo, "wrap", 0, pageVars);
        for (String rawLine : content.split("\n", -1)) {
            if (wrap > 0) {
                int wrapPx = Math.max(8, (int) (wrap / Math.max(scale, 1e-6)));
                lines.addAll(wrapText(mc.font, rawLine, wrapPx));
            } else {
                lines.add(rawLine);
            }
        }
        float lineH = 8.0F;
        float blockH = lines.size() * lineH;
        float top = -blockH / 2;
        // 文本对齐（text.align: left / center / right；默认 center；对齐参考 = 折行宽度或最长行）
        String align = com.opendreamcore.client.UiRenderer.str(spec.get("align"));
        float refW = 0;
        if (wrap > 0) {
            refW = Math.max(8, (int) (wrap / Math.max(scale, 1e-6)));
        } else {
            for (String line : lines) {
                refW = Math.max(refW, mc.font.width(line));
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            float w = mc.font.width(lines.get(i));
            float lx;
            if ("left".equals(align)) {
                lx = -refW / 2;
            } else if ("right".equals(align)) {
                lx = refW / 2 - w;
            } else {
                lx = -w / 2;
            }
            mc.font.drawInBatch(lines.get(i), lx, top + i * lineH, color, false,
                    pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    0, 0xF000F0);
        }
        pose.popPose();
    }

    /** 世界文本自动折行：按字体像素宽度逐字折行（中文/英文都适用）。 */
    public static java.util.List<String> wrapText(Font font, String text, int maxPx) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            String trial = cur + ch;
            if (font.width(trial) > maxPx && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(ch);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
