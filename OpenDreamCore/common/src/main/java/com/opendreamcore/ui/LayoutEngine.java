package com.opendreamcore.ui;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;
import com.opendreamcore.page.Page;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private LayoutEngine() {
    }

    /** 计算整页布局。windowWidth/windowHeight 是渲染窗口尺寸（像素）。 */
    public static List<RenderNode> layout(Page page, double windowWidth, double windowHeight) {
        return layout(page, windowWidth, windowHeight, null);
    }

    /** 已计算元素的框架缓存（元素 id → {x,y,width,height}），供交叉引用。 */
    private static final Map<String, Map<String, Object>> COMPUTED_FRAMES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 计算整页布局；positionOverrides 非空时按元素 id 覆盖最终坐标（编辑模式用，
     * 键 → {x, y}，值是覆盖后的绝对坐标）。
     */
    public static List<RenderNode> layout(Page page, double windowWidth, double windowHeight,
                                          Map<String, double[]> positionOverrides) {
        COMPUTED_FRAMES.clear(); // 每次布局前清空，防止旧数据残留
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
        double x = eval(layout == null ? null : layout.x(), env, 0);
        double y = eval(layout == null ? null : layout.y(), env, 0);
        double width = eval(layout == null ? null : layout.width(), env, Double.NaN);
        self.put("width", width);
        double height = eval(layout == null ? null : layout.height(), env, Double.NaN);
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

        // 元素锚点定位（anchor: center/bottom_left/top_right 等，相对屏幕或父容器）
        String anchorVal = element.props().get("anchor") != null
                ? String.valueOf(element.props().get("anchor")).trim().toLowerCase(java.util.Locale.ROOT)
                : null;
        if (anchorVal != null && !anchorVal.isEmpty()) {
            double anchorW = Double.isNaN(width) ? 0 : width;
            double anchorH = Double.isNaN(height) ? 0 : height;
            double baseX = parentFrame == null ? 0 : ((Number) parentFrame.get("x")).doubleValue();
            double baseY = parentFrame == null ? 0 : ((Number) parentFrame.get("y")).doubleValue();
            switch (anchorVal) {
                case "center" -> { absX = baseX + (windowWidth - anchorW) / 2 + x; absY = baseY + (windowHeight - anchorH) / 2 + y; }
                case "top_center" -> { absX = baseX + (windowWidth - anchorW) / 2 + x; absY = baseY + y; }
                case "bottom_center" -> { absX = baseX + (windowWidth - anchorW) / 2 + x; absY = baseY + windowHeight - anchorH + y; }
                case "bottom_left" -> { absX = baseX + x; absY = baseY + windowHeight - anchorH + y; }
                case "bottom_right" -> { absX = baseX + windowWidth - anchorW + x; absY = baseY + windowHeight - anchorH + y; }
                case "top_right" -> { absX = baseX + windowWidth - anchorW + x; absY = baseY + y; }
                case "center_left" -> { absX = baseX + x; absY = baseY + (windowHeight - anchorH) / 2 + y; }
                case "center_right" -> { absX = baseX + windowWidth - anchorW + x; absY = baseY + (windowHeight - anchorH) / 2 + y; }
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
        Map<String, Object> props = element.props() == null ? Map.of() : element.props();
        Object specRaw = props.get("adaptive");
        Map<String, Object> spec = specRaw instanceof Map<?, ?> m ? asMap(m) : props;
        double spacing = doubleProp(spec, "spacing", 4);
        double cursor = 0;
        for (Element child : element.children()) {
            Layout cl = child.layout();
            double h = cl == null || cl.height() == null
                    ? 20 : eval(cl.height(), envOf(element, frame, windowWidth, windowHeight, variables), 20);
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
                    ? 40 : eval(cl.height(), envOf(element, frame, windowWidth, windowHeight, variables), 40);
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
        Map<String, Object> props = element.props() == null ? Map.of() : element.props();
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
                        List.of(), element.id());
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
                            List.of(), element.id());
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
                        List.of(), element.id());
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

    /** 堆叠布局：h_stack 横排 / v_stack 竖排，子元素依次排列。 */
    private static List<RenderNode> layoutStack(Element element, Map<String, Object> frame,
                                                boolean horizontal, double windowWidth, double windowHeight,
                                                Map<String, Object> variables,
                                                Map<String, double[]> positionOverrides) {
        List<RenderNode> nodes = new ArrayList<>();
        if (element.children() == null) {
            return nodes;
        }
        double spacing = doubleProp(element.props(), "spacing", 0);
        double cursor = 0;
        for (Element child : element.children()) {
            Layout cl = child.layout();
            double size = horizontal
                    ? (cl == null || cl.width() == null ? 50 : eval(cl.width(), envOf(element, frame, windowWidth, windowHeight, variables), 50))
                    : (cl == null || cl.height() == null ? 20 : eval(cl.height(), envOf(element, frame, windowWidth, windowHeight, variables), 20));
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
        if (expr == null || expr.isBlank()) {
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
        if (v instanceof String s && !s.isBlank()) {
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
        if (expr == null || expr.isBlank()) {
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
