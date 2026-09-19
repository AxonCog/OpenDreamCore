package com.opendreamcore.client.entity;

import com.opendreamcore.ui.ItemModelSpec;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.GuiGraphics;

/**
 * item_model 组件渲染核心（共享层）。跟实体组件同一套路：
 * 解析规格 → 每帧求值字段（scale/rotate/alpha 支持 DreamLang 表达式）→
 * followMouse 按鼠标偏移算 yaw/pitch → 交给物品桥画。
 */
public final class ItemModelComponent {

    private ItemModelComponent() {
    }

    public static void draw(GuiGraphics g, RenderNode node, double mouseX, double mouseY,
                            java.util.Map<String, Object> pageVars) {
        ItemModelSpec spec = ItemModelSpec.parse(node.props());
        ItemModelRenderBridge br = ItemModelViews.bridge();
        if (br == null) {
            return;
        }
        String itemId = evalRef(spec.item, node, pageVars);
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        double cx = node.x() + Math.max(node.width(), 0) / 2.0;
        double cy = node.y() + Math.max(node.height(), 0) / 2.0;
        double scale = evalNum(spec.scale, 1.0);
        if (scale <= 0) {
            scale = 1.0;
        }
        double rx = evalNum(spec.rotateX, 0.0);
        double ry = evalNum(spec.rotateY, 0.0);
        double alpha = clamp(evalNum(spec.alpha, 1.0), 0.0, 1.0);
        float yaw;
        float pitch;
        if (spec.followMouse) {
            float dx = (float) (mouseX - cx);
            float dy = (float) (mouseY - cy);
            yaw = (float) ry + 180.0f + (float) (Math.atan(dx / 40.0) * 40.0);
            pitch = (float) rx + (float) (Math.atan(dy / 40.0) * 20.0);
        } else {
            yaw = (float) ry;
            pitch = (float) rx;
        }
        try {
            br.drawItemModel(g, (int) Math.round(cx), (int) Math.round(cy), (float) scale,
                    yaw, pitch, itemId, (int) (alpha * 255.0));
        } catch (Throwable t) {
            // 渲染异常不炸页面
        }
    }

    private static String evalRef(String ref, RenderNode node, java.util.Map<String, Object> pageVars) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        return com.opendreamcore.client.UiRenderer.interpolate(node, ref, pageVars);
    }

    private static double evalNum(Object raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignore) {
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p != null) {
                scope.assignPlayer("name", p.getName().getString());
                scope.assignVar("player_name", p.getName().getString());
            }
            Object r = com.opendreamcore.script.DreamLang.evaluate(s, scope);
            return r instanceof Number n ? n.doubleValue() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}