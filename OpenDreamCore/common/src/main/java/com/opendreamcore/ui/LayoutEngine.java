package com.opendreamcore.ui;

import com.opendreamcore.util.J8;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;
import com.opendreamcore.page.Page;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 布局引擎：页面模型 → 渲染节点树。
 * x/y/width/height 支持数字或表达式（parent.width、window.width / 2、vars.coin 等），
 * 表达式用 DreamLang 求值；子元素坐标相对父元素。
 */
public final class LayoutEngine {

    /** 文本自动高度测量钩子（客户端注入：MC 字体折行测量；返回内容高度 px；null = 不启用）。 */
    @FunctionalInterface
    public interface TextAutoHeight {
        double measure(String content, double maxWidth, Map<String, Object> vars,
                       double lineHeight, double fallback);
    }

    private static TextAutoHeight textAutoHeight;

    public static void setTextAutoHeight(TextAutoHeight hook) {
        textAutoHeight = hook;
    }

    /**
     * 状态覆盖层想改 x/y/width/height？布局期就得知道改多少。
     * 客户端把"当前状态下该元素的布局属性数值表"注册进来，引擎照单合成 Layout。
     * 放成接口注入而不是直接引用客户端类型：common 必须保持零 MC 依赖。
     */
    public interface StateLayoutResolver {
        java.util.Map<String, Object> resolve(String elementId);
    }

    private static volatile StateLayoutResolver stateLayoutResolver;

    public static void setStateLayoutResolver(StateLayoutResolver resolver) {
        stateLayoutResolver = resolver;
    }

    private LayoutEngine() {
    }

    /** 计算整页布局。windowWidth/windowHeight 是渲染窗口尺寸（像素）。 */
    public static List<RenderNode> layout(Page page, double windowWidth, double windowHeight) {
        return layout(page, windowWidth, windowHeight, null);
    }

    /**
     * 页面声明了 design:{width,height,fit?} 就按 fit 策略建视口（默认 letterbox 居中），
     * 没声明/写坏一律 IDENTITY——老包一帧都不挪。
     * fit 可热拔插：ViewportStrategies 注册的自定义坐标系名直接可用。
     */
    public static Viewport viewportFor(Page page, double screenW, double screenH) {
        Object design = page == null || page.options() == null ? null : page.options().get("design");
        if (!(design instanceof Map<?, ?> m)) {
            return Viewport.IDENTITY;
        }
        try {
            double dw = Double.parseDouble(String.valueOf(m.get("width")).trim());
            double dh = Double.parseDouble(String.valueOf(m.get("height")).trim());
            Object fit = m.get("fit");
            String fitName = fit == null ? "letterbox" : String.valueOf(fit).trim();
            return ViewportStrategies.get(fitName).build(dw, dh, screenW, screenH);
        } catch (RuntimeException bad) {
            return Viewport.IDENTITY; // design 写错当没写，不拦渲染
        }
    }

    /** 本轮布局使用的视口（eval 里 px 折算、锚点入向判定都要）：布局全在渲染线程串行跑。 */
    private static volatile Viewport CURRENT = Viewport.IDENTITY;

    /**
     * 长度字面量→设计值（尺寸口径）：px 尺寸 = v/s 个设计单位，投影后恰好还原成 v 个像素。
     * IDENTITY 视口（老包）下 s=1，一切照旧。
     */
    private static double evalLayout(Layout l, int which, Map<String, Object> env, double fallback) {
        if (l == null) {
            return fallback;
        }
        Length len = l.length(which);
        if (len != null) {
            return CURRENT.layoutLength(len);
        }
        String expr;
        switch (which) {
            case Layout.X: expr = l.x(); break;
            case Layout.Y: expr = l.y(); break;
            case Layout.WIDTH: expr = l.width(); break;
            default: expr = l.height(); break;
        }
        return eval(expr, env, fallback);
    }

    /**
     * 位置口径（x/y 专用，与尺寸的差别在顶层）：
     * 顶层元素写 px = 贴真屏幕边（(v-ox)/s 反解，居中度不把元素顶离边）；
     * 子元素里 px = 相对父原点的画布像素偏移（v/s 个设计单位）；
     * 无单位与表达式一律照设计单位走。IDENTITY 下三个分支全部退化为 v，逐帧照旧。
     */
    private static double evalPosition(Layout l, int which, Map<String, Object> env,
                                       double fallback, boolean rootLevel) {
        if (l == null) {
            return fallback;
        }
        Length len = l.length(which);
        if (len != null && len.abs() && rootLevel) {
            return which == Layout.X ? CURRENT.pxToDesignX(len.v()) : CURRENT.pxToDesignY(len.v());
        }
        return evalLayout(l, which, env, fallback);
    }

    /**
     * 设计坐标节点树 → 屏幕坐标（ClientController.layoutPage 里接好一步到位，
     * 渲染/命中/动画层零改动）；IDENTITY 原树返回，一帧都不挪。
     * 宽高由两条边相减得出（相邻元素共用边取整后无缝）。
     */
    public static List<RenderNode> projectTree(List<RenderNode> nodes, Viewport vp) {
        if (nodes == null || nodes.isEmpty() || vp == null || vp.isIdentity()) {
            return nodes;
        }
        List<RenderNode> out = new ArrayList<>(nodes.size());
        for (RenderNode n : nodes) {
            out.add(projectOne(n, vp));
        }
        return out;
    }

    private static RenderNode projectOne(RenderNode n, Viewport vp) {
        List<RenderNode> kids = n.children().isEmpty()
                ? n.children() : projectTree(n.children(), vp);
        double w = Double.isNaN(n.width()) ? 0 : n.width();
        double h = Double.isNaN(n.height()) ? 0 : n.height();
        return new RenderNode(n.id(), n.type(), n.source(),
                vp.edgeX(n.x()), vp.edgeY(n.y()),
                vp.spanW(n.x(), w), vp.spanH(n.y(), h),
                n.visible(), n.enabled(), kids, n.props());
    }

    /** 已计算元素的框架缓存（元素 id → {x,y,width,height}），供交叉引用。 */
    private static final Map<String, Map<String, Object>> COMPUTED_FRAMES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 计算整页布局；positionOverrides 非空时按元素 id 覆盖最终坐标（编辑模式用，
     * 键 → {x, y}，值是覆盖后的绝对坐标）。
     */
    public static List<RenderNode> layout(Page page, double windowWidth, double windowHeight,
                                          Map<String, double[]> positionOverrides) {
        return layout(page, windowWidth, windowHeight, positionOverrides,
                viewportFor(page, windowWidth, windowHeight));
    }

    /**
     * 带真视口的布局入口（页面画布用）：windowWidth/Height 允许传设计尺寸，
     * 但 CURRENT 必须拿真屏幕算出的 vp——否则 px 尺寸会先除 s=1 再由投影乘一遍，多吃一次缩放。
     */
    public static List<RenderNode> layout(Page page, double windowWidth, double windowHeight,
                                          Map<String, double[]> positionOverrides, Viewport vp) {
        COMPUTED_FRAMES.clear(); // 每次布局前清空，防止旧数据残留
        CURRENT = vp == null ? Viewport.IDENTITY : vp;
        return layout(page.elements(), null, null, windowWidth, windowHeight, page.variables(), positionOverrides);
    }

    private static List<RenderNode> layout(List<Element> elements, RenderNode parent,
                                           Map<String, Object> parentFrame,
                                           double windowWidth, double windowHeight,
                                           Map<String, Object> variables) {
        return layout(elements, parent, parentFrame, windowWidth, windowHeight, variables, null);
    }

    private static List<RenderNode> layout(List<Element> elements, RenderNode parent,
                                           Map<String, Object> parentFrame,
                                           double windowWidth, double windowHeight,
                                           Map<String, Object> variables,
                                           Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        if (elements == null) {
            return nodes;
        }
        for (Element element : elements) {
            nodes.add(layoutOne(element, parent, parentFrame, windowWidth, windowHeight, variables, positionOverrides));
        }
        return nodes;
    }

    private static RenderNode layoutOne(Element element, RenderNode parent,
                                        Map<String, Object> parentFrame,
                                        double windowWidth, double windowHeight,
                                        Map<String, Object> variables) {
        return layoutOne(element, parent, parentFrame, windowWidth, windowHeight, variables, null);
    }

    private static RenderNode layoutOne(Element element, RenderNode parent,
                                        Map<String, Object> parentFrame,
                                        double windowWidth, double windowHeight,
                                        Map<String, Object> variables,
                                        Map<String, double[]> positionOverrides) {
        Map<String, Object> env = new LinkedHashMap<>();
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("width", windowWidth);
        window.put("height", windowHeight);
        env.put("window", window);
        // 旧版（DreamCore/龙核）表达式简写：w = 窗口宽，h = 窗口高（菜单.yml 全篇使用）
        env.put("w", windowWidth);
        env.put("h", windowHeight);
        if (parentFrame != null) {
            env.put("parent", parentFrame);
        }
        env.putAll(variables);

        // 注入已计算元素框架，供交叉引用（如 `背景.width`）
        for (var entry : COMPUTED_FRAMES.entrySet()) {
            Map<String, Object> frameCopy = new LinkedHashMap<>();
            for (var fe : entry.getValue().entrySet()) {
                frameCopy.put(fe.getKey(), fe.getValue());
            }
            env.put(entry.getKey(), frameCopy);
        }
        // 自身框架先求尺寸（this 引用：先宽后高，先算的有值）
        Map<String, Object> self = new LinkedHashMap<>();
        env.put("this", self);

        Layout layout = element.layout();
        // 状态覆盖层的布局属性实时化：hover/focus 等改 x/y/宽高时本轮立即生效
        // （解析器由客户端注册；无注册或无命中时原 Layout 原样使用）
        StateLayoutResolver slr = stateLayoutResolver;
        if (slr != null && layout != null) {
            try {
                java.util.Map<String, Object> ov = slr.resolve(element.id());
                if (ov != null && !ov.isEmpty()) {
                    layout = mergeLayout(layout, ov);
                }
            } catch (Exception ignored) {
                // 样式层异常不阻断布局
            }
        }
        double x = evalPosition(layout, Layout.X, env, 0, parentFrame == null);
        double y = evalPosition(layout, Layout.Y, env, 0, parentFrame == null);
        double width = evalLayout(layout, Layout.WIDTH, env, Double.NaN);
        self.put("width", width);
        double height = evalLayout(layout, Layout.HEIGHT, env, Double.NaN);
        self.put("height", height);
        self.put("x", x);
        self.put("y", y);

        // 文本自动高度：text.autoHeight: true 或 高度未写 + text.wrap 设置 →
        // 客户端字体钩子按内容折行测量高度（命中区域/布局随内容自适应）
        if (Double.isNaN(height) && "text".equals(element.type()) && textAutoHeight != null) {
            Object spec = element.props().get("text");
            if (spec instanceof Map<?, ?> tm) {
                Object content = tm.get("content");
                Object wrap = tm.get("wrap");
                boolean auto = Boolean.parseBoolean(String.valueOf(tm.get("autoHeight")))
                        || (wrap != null && !Double.isNaN(width));
                if (auto && content != null) {
                    double lh = tm.get("lineHeight") instanceof Number n ? n.doubleValue() : 9;
                    double maxW = wrap instanceof Number n ? n.doubleValue()
                            : (Double.isNaN(width) ? 1e9 : width);
                    try {
                        height = textAutoHeight.measure(String.valueOf(content), maxW, variables, lh, 9);
                        self.put("height", height);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 相对父坐标 → 绝对坐标
        double absX = parentFrame == null ? x : ((Number) parentFrame.get("x")).doubleValue() + x;
        double absY = parentFrame == null ? y : ((Number) parentFrame.get("y")).doubleValue() + y;

        // 元素锚点定位（anchor: center/bottom_left/top_right 等，相对画布或父容器）。
        // 距离方按「入向为正」：非 IDENTITY（声明了 design 的页）下，贴边锚点的偏移
        // 以指向画布内部为正（bottom_* 的 y 向上、*_right 的 x 向左）；
        // 老包（IDENTITY）保持旧符号（从角点往右下为正），一帧都不挪。
        // 锚点本体走注册表（LayoutAnchors 内置九宫格 + 附属自定义）——热拔插。
        String anchorVal = element.props().get("anchor") != null
                ? String.valueOf(element.props().get("anchor")).trim().toLowerCase(java.util.Locale.ROOT)
                : null;
        if (anchorVal != null && !anchorVal.isEmpty()) {
            double anchorW = Double.isNaN(width) ? 0 : width;
            double anchorH = Double.isNaN(height) ? 0 : height;
            double baseX = parentFrame == null ? 0 : ((Number) parentFrame.get("x")).doubleValue();
            double baseY = parentFrame == null ? 0 : ((Number) parentFrame.get("y")).doubleValue();
            boolean inward = !CURRENT.isIdentity();
            var anchor = LayoutAnchors.get(anchorVal);
            if (anchor != null) {
                double[] p = anchor.point(windowWidth, windowHeight, anchorW, anchorH);
                absX = baseX + p[0] + anchor.offsetX(x, inward);
                absY = baseY + p[1] + anchor.offsetY(y, inward);
            }
        }

        // 编辑模式位置覆盖（元素 id → 绝对坐标）
        if (positionOverrides != null) {
            double[] override = positionOverrides.get(element.id());
            if (override != null) {
                absX = override[0];
                absY = override[1];
            }
        }

        boolean visible = evalBool(element.visibleWhen(), env, true);
        boolean enabled = evalBool(element.enabledWhen(), env, true);

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("x", absX);
        frame.put("y", absY);
        frame.put("width", width);
        frame.put("height", height);

        // 缓存元素框架，供后续元素交叉引用（如 `背景.width`）
        COMPUTED_FRAMES.put(element.id(), frame);

        // 数据绑定：bind 映射（路径 → 表达式）求值后覆盖元素属性（{{vars}} 之外的显式绑定语法）
        Map<String, Object> nodeProps = applyBindings(element, env);

        // @媒体块在这里兑现：每轮重算都拿当前窗口尺寸重新判定，
        // 所以拖窗口大小是即时响应的。z/opacity/scale 这些 RenderNode 构造时
        // 才从 props 读，天然吃到合并结果，不用额外处理。
        // 注意必须先拷贝再合——applyBindings 无绑定时返回的是元素活引用，
        // 直接往里写会把响应值永久烧进页面模型（探针抓过的现行）。
        com.opendreamcore.ui.theme.ElementStyle es = element.style();
        if (es != null && !es.mediaOverrides().isEmpty()) {
            nodeProps = new LinkedHashMap<>(nodeProps);
            com.opendreamcore.ui.theme.MediaResolver.mergeActive(
                    es.mediaOverrides(), windowWidth, windowHeight, nodeProps);
        }

        // 数值属性归一化：opacity/scale/rotation 支持数字或表达式（vars.x、Math.正弦(45) 等）
        normalizeNumericProps(nodeProps, env, "opacity", 1);
        normalizeNumericProps(nodeProps, env, "scale", 1);
        normalizeNumericProps(nodeProps, env, "rotation", 0);

        // 布局模式：grid/h_stack/v_stack 自动排子元素；scroll 内容可超界；foreach 列表展开
        List<RenderNode> children;
        String type = element.type();
        if ("grid".equals(type)) {
            children = layoutGrid(element, frame, windowWidth, windowHeight, variables, positionOverrides);
        } else if ("h_stack".equals(type)) {
            children = layoutStack(element, frame, true, windowWidth, windowHeight, variables, positionOverrides);
        } else if ("v_stack".equals(type)) {
            children = layoutStack(element, frame, false, windowWidth, windowHeight, variables, positionOverrides);
        } else if ("adaptive".equals(type)) {
            children = layoutAdaptive(element, frame, windowWidth, windowHeight, variables, positionOverrides);
            // 自适应：容器尺寸未写时按子元素内容回填（宽 = 最宽子元素右缘，高 = 子元素总高）
            if (Double.isNaN(width) && children != null) {
                width = adaptiveWidth(children, frame);
                frame.put("width", width);
            }
            if (Double.isNaN(height) && children != null) {
                height = adaptiveHeight(children, frame);
                frame.put("height", height);
            }
        } else if ("foreach".equals(type)) {
            children = layoutForeach(element, frame, windowWidth, windowHeight, variables, positionOverrides);
        } else if ("container".equals(type)) {
            children = layoutContainer(element, frame, windowWidth, windowHeight, variables, positionOverrides);
        } else {
            children = layout(element.children(), null, frame,
                    windowWidth, windowHeight, variables, positionOverrides);
        }

        return new RenderNode(element.id(), element.type(), element,
                absX, absY, width, height, visible, enabled, children, nodeProps);
    }

    /**
     * 自适应布局：子元素依次纵向排布（同 v_stack），容器尺寸由内容决定。
     * 子元素相对容器定位（x/y 不写 = 0），spacing 控制间距。
     */
    private static List<RenderNode> layoutAdaptive(Element element, Map<String, Object> frame,
                                                   double windowWidth, double windowHeight,
                                                   Map<String, Object> variables,
                                                   Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        if (element.children() == null || element.children().isEmpty()) {
            return nodes;
        }
        Map<String, Object> props = element.props() == null ? J8.map() : element.props();
        Object specRaw = props.get("adaptive");
        Map<String, Object> spec = specRaw instanceof Map<?, ?> m ? asMap(m) : props;
        double spacing = doubleProp(spec, "spacing", 4);
        double cursor = 0;
        for (Element child : element.children()) {
            Layout cl = child.layout();
            double h = cl == null || cl.height() == null
                    ? 20 : evalLayout(cl, Layout.HEIGHT, envOf(element, frame, windowWidth, windowHeight, variables), 20);
            Map<String, Object> childFrame = new LinkedHashMap<>(frame);
            childFrame.put("y", (double) frame.get("y") + cursor);
            childFrame.put("x", (double) frame.get("x"));
            nodes.add(layoutOne(child, null, childFrame, windowWidth, windowHeight, variables, positionOverrides));
            cursor += h + spacing;
        }
        return nodes;
    }

    /** 自适应宽：最宽子元素的右缘（相对容器原点）。 */
    private static double adaptiveWidth(List<RenderNode> children, Map<String, Object> frame) {
        double baseX = ((Number) frame.get("x")).doubleValue();
        double max = 0;
        for (RenderNode child : children) {
            max = Math.max(max, child.x() - baseX + Math.max(child.width(), 0));
        }
        return max;
    }

    /** 自适应高：最后一个子元素的底部（相对容器原点）。 */
    private static double adaptiveHeight(List<RenderNode> children, Map<String, Object> frame) {
        double baseY = ((Number) frame.get("y")).doubleValue();
        double max = 0;
        for (RenderNode child : children) {
            max = Math.max(max, child.y() - baseY + Math.max(child.height(), 0));
        }
        return max;
    }

    /**
     * 数据绑定：元素上的 bind 映射（"路径" → 表达式）在布局时求值并覆盖属性。
     * 例：bind: {text.content: "vars.name", visible: "vars.show"}。
     * 支持点路径（"button.background" → button 下的 background）；页面变量一变，
     * state_patch 刷新布局时自动重新求值（元素属性 ← 变量自动更新）。
     */
    private static Map<String, Object> applyBindings(Element element, Map<String, Object> env) {
        Map<String, Object> props = element.props();
        Object bindRaw = props.get("bind");
        if (!(bindRaw instanceof Map<?, ?> bind) || bind.isEmpty()) {
            return props;
        }
        Map<String, Object> out = new LinkedHashMap<>(props);
        out.remove("bind");
        for (Map.Entry<?, ?> entry : bind.entrySet()) {
            String path = String.valueOf(entry.getKey());
            Object expr = entry.getValue();
            Object value = null;
            if (expr != null) {
                try {
                    value = DreamLang.evaluate(String.valueOf(expr), scopeOf(env));
                } catch (Exception ignored) {
                    // 绑定表达式出错保持原属性（不拖垮整页）
                }
            }
            putPath(out, path, value);
        }
        return out;
    }

    /** 点路径写入（中间 map 自动创建）。 */
    private static void putPath(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> fresh = new LinkedHashMap<>();
                cur.put(parts[i], fresh);
                next = fresh;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) next;
            cur = m;
        }
        cur.put(parts[parts.length - 1], value);
    }

    /** 网格布局：cols 列 + spacing 间距，子元素依次填格。 */
    private static List<RenderNode> layoutGrid(Element element, Map<String, Object> frame,
                                               double windowWidth, double windowHeight,
                                               Map<String, Object> variables,
                                               Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        if (element.children() == null || element.children().isEmpty()) {
            return nodes;
        }
        int cols = Math.max(1, intProp(element.props(), "cols", 1));
        double spacing = doubleProp(element.props(), "spacing", 0);
        double parentW = doubleOf(frame.get("width"), 0);
        double cellW = cols > 0 ? Math.max(0, (parentW - spacing * (cols - 1)) / cols) : 0;
        for (int i = 0; i < element.children().size(); i++) {
            Element child = element.children().get(i);
            int row = i / cols;
            int col = i % cols;
            Layout cl = child.layout();
            // 子元素 x/y 未写时按格子排
            double cellH = cl == null || cl.height() == null
                    ? 40 : evalLayout(cl, Layout.HEIGHT, envOf(element, frame, windowWidth, windowHeight, variables), 40);
            double x = cl == null || cl.x() == null ? col * (cellW + spacing) : 0;
            double y = cl == null || cl.y() == null ? row * (cellH + spacing) : 0;
            Map<String, Object> childFrame = new LinkedHashMap<>(frame);
            childFrame.put("x", (double) frame.get("x") + x);
            childFrame.put("y", (double) frame.get("y") + y);
            nodes.add(layoutOne(child, null, childFrame, windowWidth, windowHeight, variables, positionOverrides));
        }
        return nodes;
    }

    /** foreach 列表展开：按 vars 列表复制 children 模板，{{item}} 预替换，逐项下移。 */
    private static List<RenderNode> layoutForeach(Element element, Map<String, Object> frame,
                                                  double windowWidth, double windowHeight,
                                                  Map<String, Object> variables,
                                                  Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        Object foreachRaw = element.props() == null ? null : element.props().get("foreach");
        if (!(foreachRaw instanceof Map<?, ?> spec)) {
            return nodes;
        }
        String listRef = strOf(spec.get("list"));
        String as = strOf(spec.get("as"));
        if (as == null) {
            as = "item";
        }
        Object listValue = null;
        if (listRef != null) {
            String varName = listRef.startsWith("vars.") ? listRef.substring(5) : listRef;
            listValue = variables == null ? null : variables.get(varName);
        }
        if (!(listValue instanceof List<?> items)) {
            return nodes;
        }
        double spacing = doubleProp(element.props(), "spacing", 0);
        // 模板布局一次，量出单行高度
        Map<String, Object> zeroFrame = new LinkedHashMap<>(frame);
        List<RenderNode> template = layout(element.children(), null, zeroFrame,
                windowWidth, windowHeight, variables, positionOverrides);
        double rowH = 0;
        for (RenderNode t : template) {
            rowH = Math.max(rowH, t.y() + Math.max(t.height(), 0));
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            double dy = i * (rowH + spacing);
            for (RenderNode t : template) {
                nodes.add(copyWith(t, as, item, dy));
            }
        }
        return nodes;
    }

    /** 复制模板节点：替换 {{item}} 引用 + y 偏移。 */
    private static RenderNode copyWith(RenderNode template, String as, Object item, double dy) {
        Map<String, Object> newProps = new LinkedHashMap<>();
        template.props().forEach((k, v) -> newProps.put(k, replaceVars(v, as, item)));
        List<RenderNode> newChildren = new ArrayList<>();
        for (RenderNode child : template.children()) {
            newChildren.add(copyWith(child, as, item, dy));
        }
        return new RenderNode(template.id(), template.type(), template.source(),
                template.x(), template.y() + dy, template.width(), template.height(),
                template.visible(), template.enabled(), newChildren, newProps);
    }

    /** 递归替换字符串中的 {{item}} / {{item.属性}}。 */
    private static Object replaceVars(Object v, String as, Object item) {
        if (v instanceof String s) {
            String out = s.replace("{{" + as + "}}", item == null ? "" : String.valueOf(item));
            if (item instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    out = out.replace("{{" + as + "." + e.getKey() + "}}",
                            e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), replaceVars(val, as, item)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) {
                out.add(replaceVars(o, as, item));
            }
            return out;
        }
        return v;
    }

    /**
     * 容器网格展开：按 rows/cols/slotStart 生成 chest_slot 子元素（真实容器槽位）。
     * 生成的槽位继承容器的 actions（点击脚本里用 vars.slot / vars.container 区分槽位）。
     */
    private static List<RenderNode> layoutContainer(Element element, Map<String, Object> frame,
                                                    double windowWidth, double windowHeight,
                                                    Map<String, Object> variables,
                                                    Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        Map<String, Object> props = element.props() == null ? J8.map() : element.props();
        Object specRaw = props.get("container");
        Map<String, Object> spec = specRaw instanceof Map<?, ?> m ? asMap(m) : props;
        int rows = Math.max(1, intProp(spec, "rows", 3));
        int cols = Math.max(1, intProp(spec, "cols", 9));
        int slotStart = Math.max(0, intProp(spec, "slotStart", 0));
        double spacing = doubleProp(spec, "spacing", 2);
        double cell = Math.max(8, doubleProp(spec, "cellSize", 18));
        boolean playerInv = boolProp(spec, "playerInventory", false);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int slot = slotStart + r * cols + c;
                Map<String, Object> childProps = new LinkedHashMap<>();
                childProps.put("slot", slot);
                childProps.put("cellSize", cell);
                childProps.put("showSlot", spec.get("showSlot"));
                String cellStr = String.valueOf(cell);
                Layout layout = new Layout(String.valueOf(c * (cell + spacing)),
                        String.valueOf(r * (cell + spacing)), cellStr, cellStr);
                Element child = new Element(element.id() + "_" + slot, "chest_slot", layout, childProps,
                        element.visibleWhen(), element.enabledWhen(), element.actions(),
                        J8.list(), element.id());
                nodes.add(layoutOne(child, null, frame, windowWidth, windowHeight, variables, positionOverrides));
            }
        }
        // 玩家背包区（playerInventory: true）：主背包 27 格（槽位 9..35）+ 快捷栏 9 格（槽位 0..8），
        // 类型 hot_slot → 服务端路由到玩家背包；原版箱子 UI 的完整体验
        if (playerInv) {
            String cellStr = String.valueOf(cell);
            int gridBottom = (int) (rows * (cell + spacing));
            double invGap = cell * 0.75;
            // 主背包 3×9（槽位 9..35）
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    int slot = 9 + r * 9 + c;
                    Map<String, Object> childProps = new LinkedHashMap<>();
                    childProps.put("slot", slot);
                    childProps.put("cellSize", cell);
                    childProps.put("showSlot", spec.get("showSlot"));
                    Layout layout = new Layout(String.valueOf(c * (cell + spacing)),
                            String.valueOf((int) (gridBottom + invGap + r * (cell + spacing))), cellStr, cellStr);
                    Element child = new Element(element.id() + "_inv" + slot, "hot_slot", layout, childProps,
                            element.visibleWhen(), element.enabledWhen(), element.actions(),
                            J8.list(), element.id());
                    nodes.add(layoutOne(child, null, frame, windowWidth, windowHeight, variables, positionOverrides));
                }
            }
            // 快捷栏 1×9（槽位 0..8）
            int hotY = (int) (gridBottom + invGap + 3 * (cell + spacing) + invGap * 0.5);
            for (int c = 0; c < 9; c++) {
                Map<String, Object> childProps = new LinkedHashMap<>();
                childProps.put("slot", c);
                childProps.put("cellSize", cell);
                childProps.put("showSlot", spec.get("showSlot"));
                Layout layout = new Layout(String.valueOf(c * (cell + spacing)),
                        String.valueOf(hotY), cellStr, cellStr);
                Element child = new Element(element.id() + "_hot" + c, "hot_slot", layout, childProps,
                        element.visibleWhen(), element.enabledWhen(), element.actions(),
                        J8.list(), element.id());
                nodes.add(layoutOne(child, null, frame, windowWidth, windowHeight, variables, positionOverrides));
            }
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    private static boolean boolProp(Map<String, Object> spec, String key, boolean def) {
        Object v = spec.get(key);
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 堆叠布局：h_stack 横排 / v_stack 竖排。
     *
     * 兼容红线：一个弹性属性都没出现就走老的游标累加，行为逐字节不变；
     * 出现任意一个才进两遍式解算（先锁主轴测交叉尺寸，再回写对齐）。
     */
    private static List<RenderNode> layoutStack(Element element, Map<String, Object> frame,
                                                boolean horizontal, double windowWidth, double windowHeight,
                                                Map<String, Object> variables,
                                                Map<String, double[]> positionOverrides) {
        List<Element> kids = element.children();
        if (kids == null) {
            return new ArrayList<>();
        }
        if (!stackUsesFlex(element, kids)) {
            return layoutStackLegacy(element, frame, horizontal, windowWidth, windowHeight,
                    variables, positionOverrides);
        }
        return layoutStackFlex(element, frame, horizontal, windowWidth, windowHeight,
                variables, positionOverrides);
    }

    /** 容器或任一子元素声明了弹性属性即启用流式解算。 */
    private static boolean stackUsesFlex(Element element, List<Element> kids) {
        Map<String, Object> p = element.props() == null ? J8.map() : element.props();
        if (p.containsKey("align") || p.containsKey("justify")
                || p.containsKey("wrap") || p.containsKey("reverse")) {
            return true;
        }
        for (Element k : kids) {
            Map<String, Object> cp = k.props() == null ? J8.map() : k.props();
            if (cp.containsKey("grow") || cp.containsKey("shrink")
                    || cp.containsKey("basis") || cp.containsKey("alignSelf")
                    || cp.containsKey("absolute")) {
                return true;
            }
        }
        return false;
    }

    /** 原始游标累加排布（无弹性属性时的兼容路径，逻辑保持不变）。 */
    private static List<RenderNode> layoutStackLegacy(Element element, Map<String, Object> frame,
                                                      boolean horizontal, double windowWidth, double windowHeight,
                                                      Map<String, Object> variables,
                                                      Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        double spacing = doubleProp(element.props(), "spacing", 0);
        double cursor = 0;
        for (Element child : element.children()) {
            Layout cl = child.layout();
            double size = horizontal
                    ? (cl == null || cl.width() == null ? 50 : evalLayout(cl, Layout.WIDTH, envOf(element, frame, windowWidth, windowHeight, variables), 50))
                    : (cl == null || cl.height() == null ? 20 : evalLayout(cl, Layout.HEIGHT, envOf(element, frame, windowWidth, windowHeight, variables), 20));
            Map<String, Object> childFrame = new LinkedHashMap<>(frame);
            if (horizontal) {
                childFrame.put("x", (double) frame.get("x") + cursor);
            } else {
                childFrame.put("y", (double) frame.get("y") + cursor);
            }
            nodes.add(layoutOne(child, null, childFrame, windowWidth, windowHeight, variables, positionOverrides));
            cursor += size + spacing;
        }
        return nodes;
    }

    /** 流式解算上下文：单个流内子元素的弹性参数与解析结果。 */
    private static final class FlexChild {
        final Element source;
        final double grow;
        final double shrink;
        final double basis;      // NaN = 未声明
        final String alignSelf;  // null = 跟随容器
        final boolean absolute;
        double mainSize = Double.NaN;   // 解算后的主轴尺寸
        double crossOffset;             // 对齐偏移（第二遍写入帧）
        double mainOffset;              // 主轴起始（justify 后确定）
        Element locked;                 // 锁定尺寸后的替身元素

        FlexChild(Element source) {
            this.source = source;
            Map<String, Object> p = source.props() == null ? J8.map() : source.props();
            this.grow = Math.max(0, doubleProp(p, "grow", 0));
            this.shrink = Math.max(0, doubleProp(p, "shrink", 0));
            Object basisRaw = p.get("basis");
            this.basis = basisRaw instanceof Number n ? n.doubleValue() : Double.NaN;
            this.alignSelf = strOrNull(p.get("alignSelf"));
            this.absolute = boolProp(p, "absolute", false);
        }
    }

    /** 两遍式流式解算：分配 → 测量 → 对齐回写。 */
    private static List<RenderNode> layoutStackFlex(Element element, Map<String, Object> frame,
                                                    boolean horizontal, double windowWidth, double windowHeight,
                                                    Map<String, Object> variables,
                                                    Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        Map<String, Object> props = element.props() == null ? J8.map() : element.props();
        double spacing = doubleProp(props, "spacing", 0);
        boolean reverse = boolProp(props, "reverse", false);
        boolean wrap = boolProp(props, "wrap", false);
        String justify = normalizeAlign(strOrNull(props.get("justify")), "start");
        String align = normalizeAlign(strOrNull(props.get("align")), "start");

        double availMain = horizontal ? doubleOf(frame.get("width"), Double.NaN)
                : doubleOf(frame.get("height"), Double.NaN);
        double crossSize = horizontal ? doubleOf(frame.get("height"), Double.NaN)
                : doubleOf(frame.get("width"), Double.NaN);

        // 分组：绝对定位子元素脱离流；其余按声明序（reverse 反转）
        List<FlexChild> flow = new ArrayList<>();
        List<FlexChild> absolutes = new ArrayList<>();
        for (Element child : kids(element)) {
            FlexChild fc = new FlexChild(child);
            resolveMainSize(fc, element, frame, horizontal, windowWidth, windowHeight, variables);
            if (fc.absolute) {
                absolutes.add(fc);
            } else {
                flow.add(fc);
            }
        }
        if (reverse) {
            java.util.Collections.reverse(flow);
        }

        // 换行分组（不可换行或容器尺寸未知时单行）
        List<List<FlexChild>> lines = new ArrayList<>();
        if (wrap && !Double.isNaN(availMain) && !flow.isEmpty()) {
            List<FlexChild> cur = new ArrayList<>();
            double used = 0;
            for (FlexChild fc : flow) {
                double need = fc.mainSize;
                if (!cur.isEmpty() && used + spacing + need > availMain) {
                    lines.add(cur);
                    cur = new ArrayList<>();
                    used = 0;
                }
                if (!cur.isEmpty()) {
                    used += spacing;
                }
                used += need;
                cur.add(fc);
            }
            if (!cur.isEmpty()) {
                lines.add(cur);
            }
        } else if (!flow.isEmpty()) {
            lines.add(new ArrayList<>(flow));
        }

        double baseX = doubleOf(frame.get("x"), 0);
        double baseY = doubleOf(frame.get("y"), 0);
        double lineCursor = 0; // 行在交叉轴上的起点

        for (List<FlexChild> line : lines) {
            int n = line.size();
            double natural = 0;
            double sumGrow = 0;
            double totalShrinkWeighted = 0;
            for (FlexChild fc : line) {
                natural += fc.mainSize;
                sumGrow += fc.grow;
                totalShrinkWeighted += fc.shrink * fc.mainSize;
            }
            double gaps = spacing * Math.max(0, n - 1);
            double free = (Double.isNaN(availMain) ? natural + gaps : availMain) - natural - gaps;

            // 收缩优先于扩张（空间不足时按 shrink 加权压缩）
            if (free < 0 && totalShrinkWeighted > 0) {
                for (FlexChild fc : line) {
                    if (fc.shrink > 0) {
                        fc.mainSize = Math.max(0, fc.mainSize + free * (fc.shrink * fc.mainSize) / totalShrinkWeighted);
                    }
                }
                free = 0;
            } else if (free > 0 && sumGrow > 0) {
                for (FlexChild fc : line) {
                    if (fc.grow > 0) {
                        fc.mainSize += free * fc.grow / sumGrow;
                    }
                }
                free = 0;
            }

            // justify 分配行内剩余空间 → 每项主轴起点
            double lead = switch (justify) {
                case "center" -> free / 2;
                case "end" -> free;
                case "around" -> n > 0 ? free / (n * 2) : 0;
                default -> 0; // start / between：首项贴行首
            };
            double step = switch (justify) {
                case "between" -> n > 1 ? spacing + free / (n - 1) : spacing;
                case "around" -> spacing + free / (double) Math.max(1, n);
                default -> spacing;
            };
            double pos = lead;
            for (FlexChild fc : line) {
                fc.mainOffset = pos;
                pos += fc.mainSize + step;
            }

            // 第一遍：锁定主轴尺寸，测真实交叉尺寸（对齐需要）；NaN 视为 0 防污染
            double lineCross = 0;
            for (FlexChild fc : line) {
                fc.locked = lockMain(fc.source, horizontal, fc.mainSize);
                RenderNode probe = layoutOne(fc.locked, null, offsetFrame(frame, horizontal, fc.mainOffset),
                        windowWidth, windowHeight, variables, positionOverrides);
                double measured = horizontal ? probe.height() : probe.width();
                // 未声明交叉尺寸时按缺省高度参与行高推进（与兼容路径缺省一致）
                if (!Double.isNaN(measured)) {
                    lineCross = Math.max(lineCross, measured);
                }
                double declaredCross = declaredCrossOf(fc.source, horizontal);
                if (!Double.isNaN(declaredCross)) {
                    lineCross = Math.max(lineCross, declaredCross);
                } else {
                    lineCross = Math.max(lineCross, 20);
                }
            }

            // 第二遍：交叉轴对齐偏移 + 最终渲染
            double lineLead = 0;
            if (!Double.isNaN(crossSize)) {
                lineLead = switch (align) {
                    case "center" -> Math.max(0, (crossSize - lineCross) / 2);
                    case "end" -> Math.max(0, crossSize - lineCross);
                    default -> 0;
                };
            }
            for (FlexChild fc : line) {
                RenderNode probe = layoutOne(fc.locked, null, offsetFrame(frame, horizontal, fc.mainOffset),
                        windowWidth, windowHeight, variables, positionOverrides);
                double measuredCross = horizontal ? probe.height() : probe.width();
                if (Double.isNaN(measuredCross)) {
                    measuredCross = 0;
                }
                String effAlign = fc.alignSelf != null ? normalizeAlign(fc.alignSelf, align) : align;
                double off = 0;
                if (!Double.isNaN(crossSize)) {
                    off = switch (effAlign) {
                        case "center" -> Math.max(0, (crossSize - measuredCross) / 2);
                        case "end" -> Math.max(0, crossSize - measuredCross);
                        default -> 0; // start / stretch：stretch 由锁定交叉尺寸承担（下方）
                    };
                }
                Map<String, Object> finalFrame = offsetFrame(frame, horizontal, fc.mainOffset);
                if (horizontal) {
                    finalFrame.put("y", baseY + lineCursor + off);
                } else {
                    finalFrame.put("x", baseX + lineCursor + off);
                }
                // stretch：子元素未声明交叉尺寸时拉伸至行交叉尺寸
                Element renderTarget = fc.locked;
                if ("stretch".equals(effAlign) && !Double.isNaN(crossSize) && !declaresCross(fc.source, horizontal)) {
                    renderTarget = lockCross(renderTarget, horizontal, crossSize);
                }
                nodes.add(layoutOne(renderTarget, null, finalFrame, windowWidth, windowHeight, variables, positionOverrides));
            }
            // 行推进（第二行起点 = 当前行交叉尺寸 + 行间距）
            lineCursor += lineCross + spacing;
        }

        // 绝对定位子元素：以容器帧为父帧，恢复自身 x/y 表达式定位
        for (FlexChild fc : absolutes) {
            nodes.add(layoutOne(fc.source, null, new LinkedHashMap<>(frame),
                    windowWidth, windowHeight, variables, positionOverrides));
        }
        return nodes;
    }

    private static List<Element> kids(Element e) {
        return e.children() == null ? J8.list() : e.children();
    }

    private static void resolveMainSize(FlexChild fc, Element parent, Map<String, Object> frame,
                                        boolean horizontal, double windowWidth, double windowHeight,
                                        Map<String, Object> variables) {
        if (!Double.isNaN(fc.basis)) {
            fc.mainSize = fc.basis;
            return;
        }
        Layout cl = fc.source.layout();
        String expr = cl == null ? null : (horizontal ? cl.width() : cl.height());
        if (expr != null) {
            fc.mainSize = evalLayout(cl, horizontal ? Layout.WIDTH : Layout.HEIGHT,
                    envOf(parent, frame, windowWidth, windowHeight, variables),
                    horizontal ? 50 : 20);
            return;
        }
        // 未声明尺寸：有扩张权重的子项从 0 起参与分配（CSS flex-basis:auto 语义的近似）；
        // 无权重时回退兼容缺省尺寸，保证纯 justify 布局仍有可见盒子
        fc.mainSize = fc.grow > 0 ? 0 : (horizontal ? 50 : 20);
    }

    /** 合成替身元素：锁定主轴尺寸（数值直写布局表达式），其余原样。 */
    private static Element lockMain(Element src, boolean horizontal, double size) {
        Layout l = src.layout();
        String s = trimNum(size);
        Layout locked = horizontal
                ? new Layout(l == null ? null : l.x(), l == null ? null : l.y(), s, l == null ? null : l.height())
                : new Layout(l == null ? null : l.x(), l == null ? null : l.y(), l == null ? null : l.width(), s);
        return new Element(src.id(), src.type(), locked, src.props(),
                src.visibleWhen(), src.enabledWhen(), src.actions(), src.children(), src.parent());
    }

    /** 在替身基础上再锁交叉尺寸（stretch 用）。 */
    private static Element lockCross(Element src, boolean horizontal, double size) {
        Layout l = src.layout();
        String s = trimNum(size);
        Layout locked = horizontal
                ? new Layout(l == null ? null : l.x(), l == null ? null : l.y(), l == null ? null : l.width(), s)
                : new Layout(l == null ? null : l.x(), l == null ? null : l.y(), s, l == null ? null : l.height());
        return new Element(src.id(), src.type(), locked, src.props(),
                src.visibleWhen(), src.enabledWhen(), src.actions(), src.children(), src.parent());
    }

    private static boolean declaresCross(Element src, boolean horizontal) {
        Layout l = src.layout();
        return l != null && (horizontal ? l.height() != null : l.width() != null);
    }

    /** 子元素声明的交叉尺寸（未声明返回 NaN）。 */
    private static double declaredCrossOf(Element src, boolean horizontal) {
        Layout l = src.layout();
        String expr = l == null ? null : (horizontal ? l.height() : l.width());
        return expr == null ? Double.NaN : Double.parseDouble(expr.matches("\\\s*-?\\d+(\\.\\d+)?\\s*$")
                ? expr.trim() : "NaN");
    }

    private static Map<String, Object> offsetFrame(Map<String, Object> frame, boolean horizontal, double mainOffset) {
        Map<String, Object> out = new LinkedHashMap<>(frame);
        if (horizontal) {
            out.put("x", doubleOf(frame.get("x"), 0) + mainOffset);
        } else {
            out.put("y", doubleOf(frame.get("y"), 0) + mainOffset);
        }
        return out;
    }

    private static String normalizeAlign(String raw, String fallback) {
        if (raw == null || J8.isBlank(raw)) {
            return fallback;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "start", "begin" -> "start";
            case "center" -> "center";
            case "end" -> "end";
            case "stretch" -> "stretch";
            case "between", "space-between" -> "between";
            case "around", "space-around" -> "around";
            default -> fallback;
        };
    }

    private static String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 用状态覆盖的布局属性合成替代 Layout：只替换命中的 x/y/width/height，
     * 其余维度沿用原声明。数值直接转字符串（表达式求值已由调用方完成）。
     */
    private static Layout mergeLayout(Layout base, java.util.Map<String, Object> ov) {
        Object x = ov.getOrDefault("x", base.x());
        Object y = ov.getOrDefault("y", base.y());
        Object w = ov.getOrDefault("width", base.width());
        Object h = ov.getOrDefault("height", base.height());
        return new Layout(numStr(x), numStr(y), numStr(w), numStr(h));
    }

    private static String numStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimNum(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(Math.round(d * 100.0) / 100.0);
    }

    private static Map<String, Object> envOf(Element element, Map<String, Object> frame,
                                             double windowWidth, double windowHeight,
                                             Map<String, Object> variables) {
        Map<String, Object> env = new LinkedHashMap<>();
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("width", windowWidth);
        window.put("height", windowHeight);
        env.put("window", window);
        // 旧版简写别名：w = 窗口宽，h = 窗口高
        env.put("w", windowWidth);
        env.put("h", windowHeight);
        if (frame != null) {
            env.put("parent", frame);
        }
        if (variables != null) {
            env.putAll(variables);
        }
        return env;
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

    private static double doubleOf(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }

    /** 表达式求值：裸数字直接解析，其他走 DreamLang。失败按默认值（布局表达式不该抛错拖垮整页）。 */
    private static double eval(String expr, Map<String, Object> env, double fallback) {
        if (expr == null || J8.isBlank(expr)) {
            return fallback;
        }
        String trimmed = expr.trim();
        try {
            if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
                return Double.parseDouble(trimmed);
            }
            Object value = DreamLang.evaluate(trimmed, scopeOf(env));
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            return fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 数值属性归一化：属性值若是字符串表达式（opacity: "vars.alpha"），求值后写回数字。
     * 已是数字（或 null/缺失）则不动。失败时写默认值（不拖垮整页）。
     */
    private static void normalizeNumericProps(Map<String, Object> props, Map<String, Object> env,
                                              String key, double fallback) {
        Object v = props == null ? null : props.get(key);
        if (v instanceof Number) {
            return;
        }
        double value = fallback;
        if (v instanceof String s && !J8.isBlank(s)) {
            String trimmed = s.trim();
            try {
                if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
                    value = Double.parseDouble(trimmed);
                } else {
                    Object result = DreamLang.evaluate(trimmed, scopeOf(env));
                    if (result instanceof Number n) {
                        value = n.doubleValue();
                    }
                }
            } catch (Exception ignored) {
                // 保持默认值
            }
        }
        props.put(key, value);
    }

    private static boolean evalBool(String expr, Map<String, Object> env, boolean fallback) {
        if (expr == null || J8.isBlank(expr)) {
            return fallback;
        }
        try {
            Object value = DreamLang.evaluate(expr, scopeOf(env));
            if (value instanceof Boolean b) {
                return b;
            }
            return value != null;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /** window/parent/this 走局部变量，其余走 vars 命名空间（对应文档的 vars.xxx / 裸变量引用）。 */
    private static Scope scopeOf(Map<String, Object> env) {
        Scope scope = new Scope();
        for (Map.Entry<String, Object> e : env.entrySet()) {
            String key = e.getKey();
            if ("window".equals(key) || "parent".equals(key) || "this".equals(key)) {
                scope.assign(key, e.getValue());
            } else {
                scope.assignVar(key, e.getValue());
            }
        }
        return scope;
    }
}
