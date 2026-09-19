package com.opendreamcore.ui;

import java.util.Map;

/**
 * item_model 组件规格（纯逻辑层，零 MC 依赖）。
 *
 * GUI 里渲染物品的 3D 模型（像原版掉落物/手持那种可旋转展示），
 * 跟实体组件同一套自定义路子：字段值保留原始形态（数字或 DreamLang 表达式），
 * 每帧求值在渲染端做。
 */
public final class ItemModelSpec {

    public final String item;        // 物品 id：minecraft:diamond_sword 等（支持 {player.*} 占位符/表达式）
    public final boolean followMouse;
    public final Object scale;
    public final Object rotateX;
    public final Object rotateY;
    public final Object rotateZ;
    public final Object alpha;
    public final String tip;
    public final double cooldown;
    public final boolean hasOnClick;

    private ItemModelSpec(String item, boolean followMouse, Object scale, Object rotateX,
                          Object rotateY, Object rotateZ, Object alpha, String tip,
                          double cooldown, boolean hasOnClick) {
        this.item = item;
        this.followMouse = followMouse;
        this.scale = scale;
        this.rotateX = rotateX;
        this.rotateY = rotateY;
        this.rotateZ = rotateZ;
        this.alpha = alpha;
        this.tip = tip;
        this.cooldown = cooldown;
        this.hasOnClick = hasOnClick;
    }

    public static ItemModelSpec parse(Map<String, Object> props) {
        Map<String, Object> p = props == null ? java.util.Collections.emptyMap() : props;
        return new ItemModelSpec(
                str(p.get("item")),
                bool(p.get("followMouse"), false),
                p.getOrDefault("scale", 1.0),
                p.getOrDefault("rotateX", 0.0),
                p.getOrDefault("rotateY", 0.0),
                p.getOrDefault("rotateZ", 0.0),
                p.getOrDefault("alpha", 1.0),
                str(p.get("tip")),
                num(p.get("cooldown"), 0),
                hasKey(p, "onClick")
                        || p.get("Functions") instanceof Map<?, ?> f && f.containsKey("onClick"));
    }

    private static boolean hasKey(Map<String, Object> p, String k) {
        Object v = p.get(k);
        if (v == null) {
            return false;
        }
        if (v instanceof Map<?, ?> m && m.isEmpty()) {
            return false;
        }
        return true;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim().isEmpty() ? null : String.valueOf(o).trim();
    }

    private static boolean bool(Object o, boolean fallback) {
        if (o == null) {
            return fallback;
        }
        return o instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(o));
    }

    private static double num(Object o, double fallback) {
        if (o == null) {
            return fallback;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}