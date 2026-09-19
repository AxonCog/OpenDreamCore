package com.opendreamcore.client.entity;

import com.opendreamcore.ui.EntityViewSpec;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.GuiGraphics;

/**
 * entity / model 组件渲染核心（共享层）。
 *
 * 流程：解析规格 → 取实体（ENTITY 走 resolveEntity，MODEL 走 dummyFor）→
 * 每帧求值字段（scale/rotate/alpha 支持 DreamLang 表达式与占位符）→
 * followMouse 按鼠标偏移算 yaw/pitch（原版背包玩家模型手感）→ 交给 bridge 画。
 *
 * 任何一步失败都静默跳过，不炸页面渲染。
 */
public final class EntityViewComponent {

    private EntityViewComponent() {
    }

    public static void draw(GuiGraphics g, RenderNode node, double mouseX, double mouseY,
                            java.util.Map<String, Object> pageVars) {
        EntityViewSpec spec = EntityViewSpec.parse(node.type(), node.props());
        EntityRenderBridge br = EntityViews.bridge();
        if (br == null) {
            return;
        }
        Object entity;
        if (spec.kind == EntityViewSpec.Kind.ENTITY) {
            entity = br.resolveEntity(evalRef(spec.entity, node, pageVars));
        } else {
            String model = spec.model == null || spec.model.isBlank() ? "player" : spec.model.trim();
            entity = br.dummyFor(model);
        }
        if (entity == null) {
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
            // 背包玩家模型手感：yaw 180 基准 + atan(dx/40)*40，pitch atan(dy/40)*20
            yaw = (float) ry + 180.0f + (float) (Math.atan(dx / 40.0) * 40.0);
            pitch = (float) rx + (float) (Math.atan(dy / 40.0) * 20.0);
        } else {
            yaw = (float) ry;
            pitch = (float) rx;
        }
        try {
            br.drawEntity(g, (int) Math.round(cx), (int) Math.round(cy), (float) scale,
                    yaw, pitch, entity, (int) (alpha * 255.0), spec.hideName);
        } catch (Throwable t) {
            // 实体渲染异常不炸页面
        }
    }

    /** 实体引用求值：{player.*} / {vars.*} 等占位符经统一管线替换。 */
    private static String evalRef(String ref, RenderNode node, java.util.Map<String, Object> pageVars) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        return com.opendreamcore.client.UiRenderer.interpolate(node, ref, pageVars);
    }

    /** 字段求值：数字直读，字符串先按数字试，再按 DreamLang 表达式算。 */
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
            // 不是纯数字，往下走表达式
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