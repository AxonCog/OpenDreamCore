package com.opendreamcore.config;

import com.opendreamcore.util.J8;

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
    // 主题系统的三个新键：theme 选哪套主题，class 挂样式类（空格分隔），
    // style 是内联样式块。都是可选项，老页面不写就当不存在
    private static final String KEY_THEME = "theme";
    private static final String KEY_CLASS = "class";
    private static final String KEY_STYLE = "style";

    /** 页面级选项键，不进变量表。design 是历史遗留键：客户端不认这个键，
     *  画布一律按 guiScaled 1:1（老壳现代端一个口径）。留名单里只为不把它
     *  漏进变量表。 */
    private static final java.util.Set<String> PAGE_OPTION_KEYS =
            J8.set("allowEscClose", "background", "through", "hideVanilla", "hideVanillaList",
                    "animations", "world", "draggable", "theme", "design");

    private PageSchema() {
    }

    /**
     * ConfigIR → Page。
     *
     * id：页面 id（不写时由调用方给文件名）
     */
    public static Page build(String id, Map<String, Object> ir) {
        return build(id, ir, null);
    }

    /**
     * 带原文位置索引的构建：布局值写错（"12 px"、1e3）时报错能指到 YAML 行列。
     * loc 可为 null（引擎合成页等无原文场景），此时行列号一律传 -1。
     */
    public static Page build(String id, Map<String, Object> ir, LocationIndex loc) {
        if (ir == null) {
            throw new ConfigParseException("页面配置为空", 1, 1);
        }
        // 4.5 ConfigIR 声明式变换：transforms 列表在建模前跑完并摘掉自身
        ir = IrTransforms.apply(ir);

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
                elements.add(buildElement(key, asMap(value), null, loc, java.util.Arrays.asList(key)));
            } else {
                variables.put(key, value);
            }
        }

        Page page = new Page(id, title, match, mode, variables, elements, functions, options);

        // 到这里嵌套 children 和扁平 parent 已经归一成同一棵 Element 树了，
        // 主题匹配只认这棵树，所以两种写法天生享受同一套皮肤。
        // 这里套一层 try/catch：主题挂了顶多没皮，页面本身必须照常打开。
        try {
            com.opendreamcore.ui.theme.ThemeApplier.apply(page,
                    com.opendreamcore.ui.theme.ThemeLibrary.get()
                            .resolveFor(str(ir.get(KEY_THEME))));
        } catch (Exception e) {
            // 主题层任何异常都不阻断页面加载（样式缺失可容忍）
            System.out.println("[OpenDreamCore][theme] 主题应用失败 页面=" + id + ": " + e);
        }
        return page;
    }

    private static boolean isElement(Object value) {
        return value instanceof Map<?, ?> map && map.containsKey(KEY_TYPE);
    }

    /** 键链查 YAML 位置；查不到（无原文/流程式写法）返 {-1,-1}，报错信息里就不带行列。 */
    private static int[] locate(LocationIndex loc, List<String> path) {
        if (loc == null) {
            return new int[]{-1, -1};
        }
        LocationIndex.Hit hit = loc.locate(path);
        return hit == null ? new int[]{-1, -1} : new int[]{hit.line(), hit.column()};
    }

    private static Layout.Pos locOf(LocationIndex loc, List<String> path, String key) {
        int[] lc = locate(loc, appendKey(path, key));
        return new Layout.Pos(lc[0], lc[1]);
    }

    private static List<String> appendKey(List<String> path, String key) {
        List<String> out = new ArrayList<>(path.size() + 1);
        out.addAll(path);
        out.add(key);
        return out;
    }

    private static Element buildElement(String id, Map<String, Object> map, String inheritedParent,
                                        LocationIndex loc, List<String> path) {
        String type = TypeInferrer.resolve(str(map.get(KEY_TYPE)), id);
        if (type == null || J8.isBlank(type)) {
            int[] el = locate(loc, path);
            throw new ConfigParseException("元素缺少 type 且无法从 id 推断: " + id
                    + "（显式写 type: text 或用 _txt/_btn 等后缀）", el[0], el[1]);
        }

        Layout layout = new Layout(
                layoutValue(map.get(KEY_X)),
                layoutValue(map.get(KEY_Y)),
                layoutValue(map.get(KEY_WIDTH)),
                layoutValue(map.get(KEY_HEIGHT)),
                locOf(loc, path, KEY_X), locOf(loc, path, KEY_Y),
                locOf(loc, path, KEY_WIDTH), locOf(loc, path, KEY_HEIGHT));

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
                    int[] el = locate(loc, path);
                    throw new ConfigParseException("子元素缺少 type: " + childId, el[0], el[1]);
                }
                List<String> childPath = new ArrayList<>(path);
                childPath.add("children");
                childPath.add(childId);
                children.add(buildElement(childId, asMap(childValue), id, loc, childPath));
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

        Element element = new Element(id, type, layout, props,
                str(map.get(KEY_VISIBLE_WHEN)),
                str(map.get(KEY_ENABLED_WHEN)),
                actions, children, parent);

        // 样式类：class: "a b" 空格分隔，多个类同时参与主题匹配
        Object classRaw = map.get(KEY_CLASS);
        if (classRaw != null) {
            String[] names = String.valueOf(classRaw).trim().split("\\s+");
            java.util.List<String> classes = new ArrayList<>(names.length);
            for (String n : names) {
                if (!J8.isBlank(n)) {
                    classes.add(n);
                }
            }
            element.setClasses(classes);
        }

        // 内联样式块：style: {属性: 值} 展开进 props（仅填缺失键），
        // style 键本身保留在 props 里以维持导出回写保真；内联优先级高于一切规则。
        Object styleRaw = map.get(KEY_STYLE);
        if (styleRaw instanceof Map<?, ?> styleMap) {
            for (Map.Entry<?, ?> se : styleMap.entrySet()) {
                String prop = String.valueOf(se.getKey()).trim();
                if (!prop.isEmpty() && se.getValue() != null && !props.containsKey(prop)) {
                    props.put(prop, se.getValue());
                }
            }
        }

        return element;
    }

    private static boolean isCommonKey(String key) {
        return KEY_TYPE.equals(key) || KEY_X.equals(key) || KEY_Y.equals(key)
                || KEY_WIDTH.equals(key) || KEY_HEIGHT.equals(key)
                || KEY_VISIBLE_WHEN.equals(key) || KEY_ENABLED_WHEN.equals(key)
                || KEY_ACTIONS.equals(key) || KEY_CHILDREN.equals(key) || KEY_PARENT.equals(key)
                || KEY_CLASS.equals(key);
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
