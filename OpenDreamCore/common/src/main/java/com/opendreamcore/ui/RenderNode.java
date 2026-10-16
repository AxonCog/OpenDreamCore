package com.opendreamcore.ui;

import com.opendreamcore.util.J8;

import com.opendreamcore.page.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 布局计算后的渲染节点。
 * 坐标一律是绝对坐标（相对窗口左上角），宽度/高度为 NaN 表示"自动"（渲染器按内容撑开）。
 * 布局引擎只负责几何，不碰像素。
 */
public final class RenderNode {

    private final String id;
    private final String type;
    private final Element source;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final boolean visible;
    private final boolean enabled;
    private final List<RenderNode> children;
    private final Map<String, Object> props;
    // 元素级绝对裁剪矩形；四个 limit 字段齐全时才启用。
    private final boolean hasLimit;
    private final double limitX;
    private final double limitY;
    private final double limitWidth;
    private final double limitHeight;

    // 元素属性（来自 props，布局时解析）
    private final int z;
    private final double opacity;
    private final double scale;
    private final double rotation;
    private final String pointerEvents;

    public RenderNode(String id, String type, Element source,
                      double x, double y, double width, double height,
                      boolean visible, boolean enabled,
                      List<RenderNode> children, Map<String, Object> props) {
        this.id = id;
        this.type = type;
        this.source = source;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.visible = visible;
        this.enabled = enabled;
        this.children = sortByZ(children == null ? new ArrayList<>() : J8.listCopy(children));
        this.props = props == null ? new LinkedHashMap<>() : props;
        this.limitX = numberProp(props, "limitX");
        this.limitY = numberProp(props, "limitY");
        this.limitWidth = numberProp(props, "limitWidth");
        this.limitHeight = numberProp(props, "limitHeight");
        this.hasLimit = Double.isFinite(limitX) && Double.isFinite(limitY)
                && Double.isFinite(limitWidth) && Double.isFinite(limitHeight);
        this.z = intProp(props, "z", 0);
        this.opacity = doubleProp(props, "opacity", 1);
        this.scale = doubleProp(props, "scale", 1);
        this.rotation = doubleProp(props, "rotation", 0);
        this.pointerEvents = strProp(props, "pointerEvents", "auto");
    }

    /** 同层子节点按 z 升序（z 大画在上面、命中优先）。 */
    private static List<RenderNode> sortByZ(List<RenderNode> nodes) {
        List<RenderNode> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(RenderNode::z));
        return J8.listCopy(sorted);
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    /** 原始元素（取 actions/visibleWhen 等用）。 */
    public Element source() {
        return source;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    /** 层级（z 大在上，命中优先）。 */
    public int z() {
        return z;
    }

    /** 静态透明度（0-1，动画 opacity 在此基础上叠加）。 */
    public double opacity() {
        return opacity;
    }

    /** 静态缩放（1 = 原尺寸）。 */
    public double scale() {
        return scale;
    }

    /** 静态旋转角（度，0 = 不旋转；正值顺时针，绕元素中心）。 */
    public double rotation() {
        return rotation;
    }

    /** 指针事件模式：auto（默认）/ none（自己和子都不响应）/ children（只响应子）。 */
    public String pointerEvents() {
        return pointerEvents;
    }
    public boolean hasLimit() { return hasLimit; }
    public double limitX() { return limitX; }
    public double limitY() { return limitY; }
    public double limitWidth() { return limitWidth; }
    public double limitHeight() { return limitHeight; }
    /** 点位是否通过本节点的显式 limit；未声明完整 limit 时不裁剪。 */
    public boolean containsLimit(double px, double py) {
        return !hasLimit || inClip(px, py, limitX, limitY,
                limitX + Math.max(limitWidth, 0), limitY + Math.max(limitHeight, 0));
    }


    /** 命中测试：点是否在节点矩形内（含自动尺寸节点，尺寸未知按 0 处理）。 */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + Math.max(width, 0)
                && py >= y && py <= y + Math.max(height, 0);
    }

    public boolean visible() {
        return visible;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<RenderNode> children() {
        return children;
    }

    public Map<String, Object> props() {
        return props;
    }

    public RenderNode hitTest(double px, double py) {
        return hitTest(px, py, 0, 0, 0, 0, false, false);
    }

    /** 交互命中：禁用元素视为穿透。 */
    public RenderNode hitTestInteractive(double px, double py) {
        return hitTest(px, py, 0, 0, 0, 0, false, true);
    }

    /** 显式裁剪矩形内命中；供宿主已有视口约束时复用，调用期间不分配临时矩形。 */
    public RenderNode hitTest(double px, double py, double clipLeft, double clipTop,
                              double clipRight, double clipBottom, boolean interactive) {
        return hitTest(px, py, clipLeft, clipTop, clipRight, clipBottom, true, interactive);
    }

    private RenderNode hitTest(double px, double py, double clipLeft, double clipTop,
                               double clipRight, double clipBottom, boolean hasClip,
                               boolean interactive) {
        if (!visible || "none".equals(pointerEvents)) return null;
        if (hasLimit) {
            double left = limitX;
            double top = limitY;
            double right = left + Math.max(limitWidth, 0);
            double bottom = top + Math.max(limitHeight, 0);
            if (right <= left || bottom <= top) return null;
            if (!hasClip) {
                clipLeft = left;
                clipTop = top;
                clipRight = right;
                clipBottom = bottom;
                hasClip = true;
            } else {
                clipLeft = Math.max(clipLeft, left);
                clipTop = Math.max(clipTop, top);
                clipRight = Math.min(clipRight, right);
                clipBottom = Math.min(clipBottom, bottom);
                if (clipRight <= clipLeft || clipBottom <= clipTop) return null;
            }
        }
        // Ordinary parents do not clip overflowing children; scroll viewports do.
        double childLeft = clipLeft;
        double childTop = clipTop;
        double childRight = clipRight;
        double childBottom = clipBottom;
        boolean childHasClip = hasClip;
        if ("scroll".equals(type)) {
            double left = x;
            double top = y;
            double right = left + Math.max(width, 0);
            double bottom = top + Math.max(height, 0);
            if (right <= left || bottom <= top) return null;
            if (!childHasClip) {
                childLeft = left;
                childTop = top;
                childRight = right;
                childBottom = bottom;
                childHasClip = true;
            } else {
                childLeft = Math.max(childLeft, left);
                childTop = Math.max(childTop, top);
                childRight = Math.min(childRight, right);
                childBottom = Math.min(childBottom, bottom);
                if (childRight <= childLeft || childBottom <= childTop) return null;
            }
        }
        if (childHasClip && !inClip(px, py, childLeft, childTop, childRight, childBottom)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            RenderNode hit = children.get(i).hitTest(px, py, childLeft, childTop,
                    childRight, childBottom, childHasClip, interactive);
            if (hit != null) return hit;
        }
        if (!contains(px, py)) return null;
        if ("children".equals(pointerEvents) || (interactive && !enabled)) return null;
        return this;
    }

    private static boolean inClip(double px, double py, double left, double top,
                                  double right, double bottom) {
        // Scissors are half-open rectangles; a zero-width/height intersection is empty.
        return px >= left && px < right && py >= top && py < bottom;
    }


    private static double numberProp(Map<String, Object> props, String key) {
        Object v = props == null ? null : props.get(key);
        return v instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private static int intProp(Map<String, Object> props, String key, int fallback) {
        Object v = props == null ? null : props.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static double doubleProp(Map<String, Object> props, String key, double fallback) {
        Object v = props == null ? null : props.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }

    private static String strProp(Map<String, Object> props, String key, String fallback) {
        Object v = props == null ? null : props.get(key);
        return v == null ? fallback : String.valueOf(v);
    }
}
