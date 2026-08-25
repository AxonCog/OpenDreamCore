package com.opendreamcore.client.screen;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 屏幕编辑器纯函数助手（C4 自 OdcScreen 抽出，零状态零 MC GUI 依赖）：
 * 默认尺寸/默认样式工厂、键值对便捷构造、深拷贝、复制偏移等。
 * 全部 public static，供 OdcScreen / EditorPanels / ClientController 复用。
 */
public final class EditSpecs {

    private EditSpecs() {
    }

    /** 键值对便捷构造（保序）。 */
    public static LinkedHashMap<String, Object> spec(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** 新放置元素的默认尺寸（像素）：按类型给可见的初始大小。 */
    public static int[] defaultSizeFor(String type) {
        return switch (type == null ? "" : type) {
            case "text" -> new int[]{120, 14};
            case "button" -> new int[]{100, 20};
            case "image", "gif", "video", "flip_card" -> new int[]{128, 128};
            case "input", "chat_input", "dropdown", "suggestion", "table" -> new int[]{140, 18};
            case "area_input" -> new int[]{160, 60};
            case "slider", "arc_slider", "progress", "gauge" -> new int[]{120, 10};
            case "toggle", "checkbox" -> new int[]{44, 14};
            case "item_slot", "chest_slot", "hot_slot", "entity" -> new int[]{18, 18};
            case "scroll", "container", "embed", "foreach" -> new int[]{140, 90};
            default -> new int[]{100, 40}; // rect 等
        };
    }

    /**
     * 新放置元素的默认样式（修复"放的组件不显示组件样式"）：
     * 按类型给一套开箱可见的默认值，放置后立即可见，再由属性面板微调。
     */
    public static Map<String, Object> defaultPropsFor(String type) {
        Map<String, Object> props = new LinkedHashMap<>();
        switch (type == null ? "" : type) {
            case "text" -> props.put("text", spec(
                    "content", "文本内容", "scale", 1, "color", "#FFFFFF"));
            case "button" -> props.put("button", spec(
                    "label", "按钮", "background", "#2A2F3A", "hoverColor", "#3A3F4A",
                    "textColor", "#FFFFFF", "radius", 3));
            case "rect" -> props.put("rect", spec(
                    "color", "#7A8BFF", "radius", 4));
            case "input" -> props.put("input", spec(
                    "placeholder", "输入…", "background", "#20242C", "textColor", "#FFFFFF",
                    "border", "#3A4254"));
            case "chat_input" -> props.put("chat_input", spec(
                    "placeholder", "聊天输入…", "background", "#20242C", "textColor", "#FFFFFF"));
            case "area_input" -> props.put("area_input", spec(
                    "placeholder", "多行输入…", "background", "#20242C", "textColor", "#FFFFFF"));
            case "suggestion" -> props.put("suggestion", spec(
                    "background", "#20242C", "textColor", "#FFFFFF"));
            case "slider" -> props.put("slider", spec(
                    "min", 0, "max", 100, "value", 50));
            case "arc_slider" -> props.put("arc_slider", spec(
                    "min", 0, "max", 100, "value", 50));
            case "toggle" -> props.put("toggle", spec(
                    "value", false, "color", "#7A8BFF"));
            case "checkbox" -> props.put("checkbox", spec(
                    "label", "选项", "value", false, "color", "#7A8BFF"));
            case "dropdown" -> props.put("dropdown", spec(
                    "options", List.of("选项一", "选项二", "选项三"),
                    "background", "#20242C", "textColor", "#FFFFFF"));
            case "image" -> props.put("image", spec(
                    "src", "minecraft:textures/block/stone.png"));
            case "progress" -> props.put("progress", spec(
                    "min", 0, "max", 100, "value", 60, "color", "#4CAF50", "background", "#303540"));
            case "gauge" -> props.put("gauge", spec(
                    "min", 0, "max", 100, "value", 60, "color", "#4CAF50"));
            case "flip_card" -> {
                props.put("flip_card", spec("duration", 300));
                props.put("front", spec("rect", spec("color", "#3A4254", "radius", 6)));
                props.put("back", spec("rect", spec("color", "#7A8BFF", "radius", 6)));
            }
            case "table" -> props.put("table", spec(
                    "columns", List.of("列一", "列二"), "textColor", "#FFFFFF"));
            case "scroll", "container", "embed", "foreach" -> props.put("rect", spec(
                    "color", "#33FFFFFF", "radius", 2));
            default -> { /* 其余类型保持空 props（纯容器/逻辑元素） */ }
        }
        return props;
    }

    /** 属性表深拷贝（Map/List 递归，其余原样）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> props) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> m) {
                out.put(entry.getKey(), deepCopy((Map<String, Object>) m));
            } else if (value instanceof List<?> list) {
                List<Object> copy = new java.util.ArrayList<>();
                for (Object item : list) {
                    copy.add(item instanceof Map<?, ?> m ? deepCopy((Map<String, Object>) m) : item);
                }
                out.put(entry.getKey(), copy);
            } else {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    /** 复制子元素（id 加 _copy 后缀避免重复）。 */
    public static List<Element> copyChildren(Element element) {
        List<Element> out = new java.util.ArrayList<>();
        for (Element child : element.children()) {
            out.add(new Element(child.id() + "_copy", child.type(), child.layout(),
                    deepCopy(child.props()), child.visibleWhen(), child.enabledWhen(),
                    new LinkedHashMap<>(child.actions()), copyChildren(child), child.parent()));
        }
        return out;
    }

    /** y 值字符串 +20（复制元素下移一行）。解析失败原样返回。 */
    public static String offsetY(String y) {
        try {
            return String.valueOf(Double.parseDouble(y.trim()) + 20);
        } catch (NumberFormatException e) {
            return y;
        }
    }

    /** 数值显示格式：整数不带小数点。 */
    public static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
