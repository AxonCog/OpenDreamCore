package com.opendreamcore.ui;

import java.util.Map;

/**
 * entity / model 组件规格（纯逻辑层，零 MC 依赖，单测可跑）。
 *
 * type: entity —— GUI 里渲染实时实体（玩家模型看鼠标那套）；
 * type: model —— 纯模型展示（只画模型+姿态，不绑实体数据，资源占用小）。
 *
 * 字段值保留原始形态（数字或 DreamLang 表达式字符串），每帧求值在渲染端做：
 *   entity: owner / 玩家名 / 实体UUID / {player.*} 占位符 / 表达式
 *   model:  player / armor_stand / 原版生物类型
 *   followMouse: true 身体跟着鼠标转（原版背包玩家模型手感）
 *   head: true 头部额外跟随
 *   hideName: true 隐藏名牌
 *   scale / rotateX / rotateY / rotateZ / alpha: 数字或表达式
 *   animation: 姿态（骨骼动画后续版本的事）
 *   tip: hover 提示；cooldown: 点击冷却秒；Functions.onClick: 点击钩子
 */
public final class EntityViewSpec {

    public enum Kind { ENTITY, MODEL }

    public final Kind kind;
    public final String entity;        // ENTITY 用：实体引用
    public final String model;         // 模型类型（MODEL 必填；ENTITY 默认 player）
    public final boolean followMouse;
    public final boolean head;
    public final boolean hideName;
    public final Object scale;
    public final Object rotateX;
    public final Object rotateY;
    public final Object rotateZ;
    public final Object alpha;
    public final String animation;
    public final String tip;
    public final double cooldown;
    public final boolean hasOnClick;

    private EntityViewSpec(Kind kind, String entity, String model, boolean followMouse, boolean head,
                           boolean hideName, Object scale, Object rotateX, Object rotateY, Object rotateZ,
                           Object alpha, String animation, String tip, double cooldown, boolean hasOnClick) {
        this.kind = kind;
        this.entity = entity;
        this.model = model;
        this.followMouse = followMouse;
        this.head = head;
        this.hideName = hideName;
        this.scale = scale;
        this.rotateX = rotateX;
        this.rotateY = rotateY;
        this.rotateZ = rotateZ;
        this.alpha = alpha;
        this.animation = animation;
        this.tip = tip;
        this.cooldown = cooldown;
        this.hasOnClick = hasOnClick;
    }

    /** 从元素 props 解析（type 为 node.type()）。 */
    public static EntityViewSpec parse(String type, Map<String, Object> props) {
        Map<String, Object> p = props == null ? java.util.Collections.emptyMap() : props;
        boolean isModel = "model".equals(type);
        return new EntityViewSpec(
                isModel ? Kind.MODEL : Kind.ENTITY,
                str(p.get("entity")),
                str(p.get("model")),
                bool(p.get("followMouse"), false),
                bool(p.get("head"), true),
                bool(p.get("hideName"), true),
                p.getOrDefault("scale", 1.0),
                p.getOrDefault("rotateX", 0.0),
                p.getOrDefault("rotateY", 0.0),
                p.getOrDefault("rotateZ", 0.0),
                p.getOrDefault("alpha", 1.0),
                str(p.get("animation")),
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