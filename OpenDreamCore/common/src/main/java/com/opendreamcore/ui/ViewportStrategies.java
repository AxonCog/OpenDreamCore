package com.opendreamcore.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 视口策略注册表（坐标系热拔插的排插）。
 *
 * 内置 9 个：letterbox（居中留边） + anchor 八向（等比缩放 + 贴边，裁非锚定侧）。
 * 附属想整自定义坐标系：register 一个 {@link ViewportStrategy}，页面 design.fit 写你的名字。
 * 没有匹配名字一律退回 letterbox——写错 fit 不拦渲染，这是规矩。
 */
public final class ViewportStrategies {

    private static final Logger LOGGER = Logger.getLogger(ViewportStrategies.class.getName());

    private static final Map<String, ViewportStrategy> STRATEGIES = new ConcurrentHashMap<>();

    private ViewportStrategies() {
    }

    static {
        register(new Letterbox());
        register(new Anchor("anchor_top_left", 0, 0));
        register(new Anchor("anchor_top_center", 0.5, 0));
        register(new Anchor("anchor_top_right", 1, 0));
        register(new Anchor("anchor_center_left", 0, 0.5));
        register(new Anchor("anchor_center", 0.5, 0.5));
        register(new Anchor("anchor_center_right", 1, 0.5));
        register(new Anchor("anchor_bottom_left", 0, 1));
        register(new Anchor("anchor_bottom_center", 0.5, 1));
        register(new Anchor("anchor_bottom_right", 1, 1));
    }

    /** 注册策略（同名覆盖；null 忽略）。 */
    public static void register(ViewportStrategy strategy) {
        if (strategy == null || strategy.name() == null || strategy.name().trim().isEmpty()) {
            return;
        }
        STRATEGIES.put(strategy.name(), strategy);
        LOGGER.info(() -> "[OpenDreamCore][viewport] 视口策略已注册 " + strategy.name());
    }

    /** 按名取策略；没有返回默认 letterbox（空实现，绝不返回 null 让调用方炸）。 */
    public static ViewportStrategy get(String fit) {
        if (fit == null) {
            return new Letterbox();
        }
        ViewportStrategy s = STRATEGIES.get(fit.trim().toLowerCase(java.util.Locale.ROOT));
        return s == null ? new Letterbox() : s;
    }

    /** 全名单（补全/文档用）。 */
    public static List<String> names() {
        List<String> out = new ArrayList<>(STRATEGIES.keySet());
        Collections.sort(out);
        return out;
    }

    /** 内置：等比缩放 + 居中留边（默认行为，等价旧版 letterbox 语义）。 */
    public static final class Letterbox implements ViewportStrategy {
        @Override
        public String name() {
            return "letterbox";
        }

        @Override
        public Viewport build(double dw, double dh, double sw, double sh) {
            return Viewport.of(dw, dh, sw, sh);
        }
    }

    /** 内置：等比缩放 + 锚定到某个角/边（偏移比例 ox/oy 决定），溢出裁在非锚定侧。 */
    public static final class Anchor implements ViewportStrategy {
        private final String name;
        private final double rx; // 锚点水平比例 0..1
        private final double ry; // 锚点垂直比例 0..1

        Anchor(String name, double rx, double ry) {
            this.name = name;
            this.rx = rx;
            this.ry = ry;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Viewport build(double dw, double dh, double sw, double sh) {
            if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) {
                return Viewport.IDENTITY;
            }
            double s = Math.min(sw / dw, sh / dh);
            double ox = (sw - dw * s) * rx;
            double oy = (sh - dh * s) * ry;
            return Viewport.ofTransform(dw, dh, s, ox, oy, name);
        }
    }
}