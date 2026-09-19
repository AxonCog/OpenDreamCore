package com.opendreamcore.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 布局锚点注册表（锚点热拔插的排插）。
 *
 * 内置九宫格 9 个；附属 register 自定义锚点，页面 anchor:名字 直接用。
 * 查不到的名字 = 无锚点（元素按普通相对坐标摆），不拦布局——写错就变普通摆，安全。
 */
public final class LayoutAnchors {

    private static final Map<String, LayoutAnchor> ANCHORS = new ConcurrentHashMap<>();

    private LayoutAnchors() {
    }

    static {
        register(new Fixed("center", 0.5, 0.5));
        register(new Fixed("top_left", 0, 0));
        register(new Fixed("top_center", 0.5, 0));
        register(new Fixed("top_right", 1, 0));
        register(new Fixed("center_left", 0, 0.5));
        register(new Fixed("center_right", 1, 0.5));
        register(new Fixed("bottom_left", 0, 1));
        register(new Fixed("bottom_center", 0.5, 1));
        register(new Fixed("bottom_right", 1, 1));
    }

    /** 注册锚点（同名覆盖；null 忽略）。 */
    public static void register(LayoutAnchor anchor) {
        if (anchor == null || anchor.name() == null || anchor.name().trim().isEmpty()) {
            return;
        }
        ANCHORS.put(anchor.name(), anchor);
    }

    /** 按名取锚点；没有返回 null（调用方按普通相对坐标处理）。 */
    public static LayoutAnchor get(String name) {
        return name == null ? null : ANCHORS.get(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** 全部锚点名（补全/文档用）。 */
    public static List<String> names() {
        List<String> out = new ArrayList<>(ANCHORS.keySet());
        Collections.sort(out);
        return out;
    }

    /** 内置：按比例定基准点（0=左/上，1=右/下）。 */
    static final class Fixed implements LayoutAnchor {
        private final String name;
        private final double rx;
        private final double ry;

        Fixed(String name, double rx, double ry) {
            this.name = name;
            this.rx = rx;
            this.ry = ry;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double[] point(double cw, double ch, double ew, double eh) {
            return new double[]{cw * rx, ch * ry};
        }

        @Override
        public double offsetX(double v, boolean inward) {
            return inward && rx == 1.0 ? -v : v;
        }

        @Override
        public double offsetY(double v, boolean inward) {
            return inward && ry == 1.0 ? -v : v;
        }
    }
}