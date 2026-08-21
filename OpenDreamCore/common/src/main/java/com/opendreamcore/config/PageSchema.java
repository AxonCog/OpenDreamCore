package com.opendreamcore.config;

import com.opendreamcore.page.DisplayMode;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;
import com.opendreamcore.page.Match;
import com.opendreamcore.page.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ConfigIR（Map 树）→ 页面模型 映射层。
 * 语法迁移自23年梦想核心与26年梦想核心正式版。
 * 作者：梦幻 QQ:2496599413
 *
 * 顶层键：match/title/display/Functions 及页面选项
 * 其余顶层键：值是 map 且含 type → 元素；否则 → 变量
 * 元素内：x/y/width/height/visibleWhen/enabledWhen/actions/children/parent 为通用字段，
 * 其余进 props
 * type 为空时从 id 后缀推断（TypeInferrer）
 */
public final class PageSchema {

    private static final String KEY_MATCH = "match";
    private static final String KEY_TITLE = "title";
    private static final String KEY_DISPLAY = "display";
    private static final String KEY_FUNCTIONS = "Functions";
    private static final String KEY_TYPE = "type";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_VISIBLE_WHEN = "visibleWhen";
    private static final String KEY_ENABLED_WHEN = "enabledWhen";
    private static final String KEY_ACTIONS = "actions";
    private static final String KEY_CHILDREN = "children";
    private static final String KEY_PARENT = "parent";

    /** 页面级选项键，不进变量表 */
    private static final java.util.Set<String> PAGE_OPTION_KEYS =
            java.util.Set.of("allowEscClose", "background", "through", "hideVanilla", "hideVanillaList",
                    "animations", "world", "draggable");

    private PageSchema() {
    }

    /**
     * ConfigIR → Page。
     *
     * @param id 页面 id（不写时由调用方给文件名）
     */
    public static Page build(String id, Map<String, Object> ir) {
        if (ir == null) {
            throw new ConfigParseException("页面配置为空", 1, 1);
        }

        String title = str(ir.get(KEY_TITLE));
        Match match = parseMatch(ir.get(KEY_MATCH));
        DisplayMode mode = parseDisplay(ir.get(KEY_DISPLAY));

        Map<String, Object> variables = new LinkedHashMap<>();
        List<Element> elements = new ArrayList<>();
        Map<String, String> functions = new LinkedHashMap<>();
        Map<String, Object> options = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : ir.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (KEY_MATCH.equals(key) || KEY_TITLE.equals(key) || KEY_DISPLAY.equals(key)) {
                continue;
            }
            if (KEY_FUNCTIONS.equals(key)) {
                if (value instanceof Map<?, ?> fns) {
                    for (Map.Entry<?, ?> fn : fns.entrySet()) {
                        functions.put(String.valueOf(fn.getKey()), str(fn.getValue()));
                    }
                }
                continue;
            }
            if (PAGE_OPTION_KEYS.contains(key)) {
                options.put(key, value);
                continue;
            }
            if (isElement(value)) {
                elements.add(buildElement(key, asMap(value), null));
            } else {
                variables.put(key, value);
            }
        }

        return new Page(id, title, match, mode, variables, elements, functions, options);
    }

    private static boolean isElement(Object value) {
        return value instanceof Map<?, ?> map && map.containsKey(KEY_TYPE);
    }

    private static Element buildElement(String id, Map<String, Object> map, String inheritedParent) {
        String type = TypeInferrer.resolve(str(map.get(KEY_TYPE)), id);
        if (type == null || type.isBlank()) {
            throw new ConfigParseException("元素缺少 type 且无法从 id 推断: " + id
                    + "（显式写 type: text 或用 _txt/_btn 等后缀）", 0, 0);
        }

        Layout layout = new Layout(
                layoutValue(map.get(KEY_X)),
                layoutValue(map.get(KEY_Y)),
                layoutValue(map.get(KEY_WIDTH)),
                layoutValue(map.get(KEY_HEIGHT)));

        Map<String, String> actions = new LinkedHashMap<>();
        Object actionsRaw = map.get(KEY_ACTIONS);
        if (actionsRaw instanceof Map<?, ?> actionMap) {
            for (Map.Entry<?, ?> a : actionMap.entrySet()) {
                actions.put(String.valueOf(a.getKey()), str(a.getValue()));
            }
        }

        List<Element> children = new ArrayList<>();
        Object childrenRaw = map.get(KEY_CHILDREN);
        if (childrenRaw instanceof Map<?, ?> childMap) {
            for (Map.Entry<?, ?> child : childMap.entrySet()) {
                String childId = String.valueOf(child.getKey());
                Object childValue = child.getValue();
                if (!isElement(childValue)) {
                    throw new ConfigParseException("子元素缺少 type: " + childId, 0, 0);
                }
                children.add(buildElement(childId, asMap(childValue), id));
            }
        }

        String parent = str(map.get(KEY_PARENT));
        if (parent == null && inheritedParent != null) {
            parent = inheritedParent;
        }

        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String k = entry.getKey();
            if (isCommonKey(k)) {
                continue;
            }
            props.put(k, entry.getValue());
        }

        return new Element(id, type, layout, props,
                str(map.get(KEY_VISIBLE_WHEN)),
                str(map.get(KEY_ENABLED_WHEN)),
                actions, children, parent);
    }

    private static boolean isCommonKey(String key) {
        return KEY_TYPE.equals(key) || KEY_X.equals(key) || KEY_Y.equals(key)
                || KEY_WIDTH.equals(key) || KEY_HEIGHT.equals(key)
                || KEY_VISIBLE_WHEN.equals(key) || KEY_ENABLED_WHEN.equals(key)
                || KEY_ACTIONS.equals(key) || KEY_CHILDREN.equals(key) || KEY_PARENT.equals(key);
    }

    private static Match parseMatch(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> m) {
            return new Match(str(m.get("target")),
                    m.get("priority") instanceof Number n ? n.intValue() : 0,
                    str(m.get("when")));
        }
        return new Match(str(raw));
    }

    private static DisplayMode parseDisplay(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return DisplayMode.byId(str(raw));
        } catch (IllegalArgumentException e) {
            throw new ConfigParseException("未知显示模式: " + raw, 0, 0);
        }
    }

    private static String layoutValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue() == n.longValue() ? String.valueOf(n.longValue()) : String.valueOf(n.doubleValue());
        }
        return str(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
