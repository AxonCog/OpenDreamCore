package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.adapter.AdapterRegistry;
import com.opendreamcore.config.ConfigParser;
import com.opendreamcore.config.ConfigParseException;
import com.opendreamcore.config.YamlParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DreamCore 旧版语法兼容解析器。
 * 自动检测旧格式（元素为顶层 key）并转换为 OpenDreamCore 标准格式。
 * 旧页面零修改即可在 OpenDreamCore 上运行。
 */
public final class DreamCoreParser implements ConfigParser, AdapterRegistry.SelfDetecting {

    /** 单例（AdapterRegistry 注册用）。 */
    public static final DreamCoreParser INSTANCE = new DreamCoreParser();

    static {
        // 自注册：检测与路由统一走 AdapterRegistry（v2 规划 E4）
        AdapterRegistry.register(INSTANCE);
    }

    private final YamlParser yamlParser = new YamlParser();

    @Override
    public String format() {
        return "dreamcore";
    }

    /**
     * 内容检测（强特征，避免误伤标准嵌套语法——两者顶层结构相同）：
     * ① 特征串：hideVanillaList:/界面变量/用户变量./ChatDisplay/preRender:/行首 Functions:
     * ② IR 级：顶层元素的 type 为旧类型名（mapType 能映射出新类型，如 Texture→image）。
     * 这是唯一的格式判定来源（LocalPageManager 等一律经 {@link AdapterRegistry#detect} 路由）。
     */
    @Override
    public boolean detects(String text) {
        if (text == null) {
            return false;
        }
        if (text.contains("hideVanillaList:") || text.contains("界面变量")
                || text.contains("用户变量.") || text.contains("ChatDisplay")
                || text.contains("preRender:") || text.contains("\nFunctions:")
                || text.contains("\r\nFunctions:")) {
            return true;
        }
        try {
            Map<String, Object> ir = yamlParser.parse(text);
            if (ir.containsKey("elements")) {
                return false;
            }
            for (Object v : ir.values()) {
                if (v instanceof Map<?, ?> m && m.get("type") instanceof String t && !t.isBlank()) {
                    String mapped = mapType(t);
                    if (!mapped.equalsIgnoreCase(t)) {
                        return true; // 旧类型名（Texture/label/…）→ 旧格式
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /** 判断文本是否为 DreamCore 旧格式（顶层有 type 键的元素 = 旧格式特征）。 */
    public static boolean isDreamCoreFormat(String text) {
        return text.contains("hideVanillaList:") || text.contains("界面变量")
                || text.contains("ChatDisplay") || text.contains("preRender:");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> parse(String text) throws ConfigParseException {
        Map<String, Object> raw = yamlParser.parse(text);
        return transform(raw);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> transform(Map<String, Object> raw) {
        // 页面级保留键（不当作元素）；旧版用大写 Functions，新版小写 functions，两者都保留
        var pageKeys = java.util.Set.of("match", "display", "title", "background", "options",
                "variables", "functions", "Functions", "priority", "through", "hideVanilla", "hideVanillaList");

        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> page = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (pageKeys.contains(key)) {
                // Functions/functions 脚本块：零参裸调用补括号 + 作用域变量/赋值改写
                if (("Functions".equals(key) || "functions".equals(key)) && val instanceof Map<?, ?> fns) {
                    Map<String, Object> fixed = new java.util.LinkedHashMap<>();
                    for (Map.Entry<?, ?> fn : fns.entrySet()) {
                        fixed.put(String.valueOf(fn.getKey()),
                                LegacyExpressionRewriter.rewrite(
                                        LegacyMethods.ensureZeroArgParens(str(fn.getValue()))));
                    }
                    page.put(key, fixed);
                    continue;
                }
                page.put(key, val);
                continue;
            }
            // 元素定义：值是 Map 且含 type 键；无 type 时按 id 后缀推断（_label/_texture/…，龙核惯例）
            if (val instanceof Map<?, ?> m && (m.containsKey("type")
                    || com.opendreamcore.config.TypeInferrer.resolve(null, key) != null)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> elMap = (Map<String, Object>) m;
                Map<String, Object> el = new LinkedHashMap<>(elMap);
                el.put("id", key);
                String oldType = String.valueOf(el.get("type"));
                el.put("type", oldType == null || "null".equals(oldType)
                        ? com.opendreamcore.config.TypeInferrer.resolve(null, key)
                        : mapType(oldType));
                mapSpecKey(oldType, el);
                processElement(el);
                elements.add(el);
                continue;
            }
            // 其余顶层标量（如 allowEscClose）/列表：按新版约定透传（PageSchema 自行归类为选项或变量）
            page.put(key, val);
        }

        // 摊平回新版标准嵌套形态：元素 id → 元素 Map（PageSchema 按顶层含 type 键识别元素）
        for (Map<String, Object> el : elements) {
            Object id = el.remove("id");
            page.put(String.valueOf(id), el);
        }
        return page;
    }

    /** 元素级脚本/表达式改写：actions 零参补括号+变量改写；表达式白名单键作用域改写（含 children 递归）。 */
    private static void processElement(Map<String, Object> el) {
        Object actions = el.get("actions");
        if (actions instanceof Map<?, ?> am) {
            Map<String, Object> fixed = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> a : am.entrySet()) {
                fixed.put(String.valueOf(a.getKey()),
                        LegacyExpressionRewriter.rewrite(
                                LegacyMethods.ensureZeroArgParens(str(a.getValue()))));
            }
            el.put("actions", fixed);
        }
        LegacyExpressionRewriter.rewriteElementExpressions(el);
        Object children = el.get("children");
        if (children instanceof Map<?, ?> cm) {
            for (Object c : cm.values()) {
                if (c instanceof Map<?, ?> child && child.containsKey("type")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childEl = (Map<String, Object>) child;
                    processElement(childEl);
                }
            }
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** DreamCore → OpenDreamCore 类型映射。 */
    private static final Map<String, String> TYPE_MAP = new java.util.HashMap<>() {{
        // PascalCase（旧版标准写法）
        put("Texture", "image");
        put("Image", "image");
        put("TextModule", "text");
        put("Button", "button");
        put("Input", "input");
        put("ChatInput", "chat_input");
        put("ChatDisplay", "chat_display");
        put("Suggestion", "suggestion");
        put("AreaInput", "area_input");
        put("Select", "dropdown");
        put("CheckModule", "checkbox");
        put("RangeModule", "slider");
        put("Slot", "chest_slot");
        put("HotSlot", "hot_slot");
        put("ChestSlot", "chest_slot");
        put("ContainerModule", "scroll");
        put("EntityModule", "entity");
        put("ProgressModule", "progress");
        put("VideoModule", "video");
        put("Foreach", "foreach");
        put("Embed", "embed");
        // 用户菜单.yml 实际使用的小写变体
        // texture = 图片元素（texture/textureHovered 贴图）→ image（hoverSrc 承载悬停换图）
        put("texture", "image");
        put("slot", "chest_slot");
        put("label", "text");
        put("image", "image");
        put("text", "text");
        put("button", "button");
    }};

    static String mapType(String oldType) {
        // 精确匹配
        var mapped = TYPE_MAP.get(oldType);
        if (mapped != null) return mapped;
        // 忽略大小写匹配
        for (var entry : TYPE_MAP.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(oldType)) return entry.getValue();
        }
        // 兜底：小写化
        return oldType.toLowerCase(java.util.Locale.ROOT);
    }

    /** 旧 spec key → 新 spec key（如 Texture → image）。 */
    static void mapSpecKey(String oldType, Map<String, Object> el) {
        String newType = mapType(oldType);
        // 类型专属 spec 块：旧版把参数平铺在元素上（texture: "xxx.png"），新版收进类型同名子对象
        Map<String, Object> spec = new java.util.LinkedHashMap<>();
        Object specVal = el.remove(oldType);
        if (specVal instanceof Map<?, ?> sm) {
            for (Map.Entry<?, ?> e : sm.entrySet()) {
                spec.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else if (specVal != null) {
            spec.put("src", specVal); // texture: "xxx.png" 平铺写法
        }
        if (!spec.isEmpty()) {
            el.put(newType, spec);
        }
        mapCommonProps(el);
    }

    /**
     * 通用属性映射（菜单.yml 实际使用的旧键名 → 新键名）：
     * alpha→opacity、tip→tooltip、drawBackground→chest_slot.showSlot(取反)、
     * identifier(container_N)→chest_slot.slot、texture/textureHovered→image.src/hoverSrc。
     */
    @SuppressWarnings("unchecked")
    private static void mapCommonProps(Map<String, Object> el) {
        String type = String.valueOf(el.get("type"));
        // alpha → opacity
        if (el.containsKey("alpha")) {
            el.put("opacity", el.remove("alpha"));
        }
        // tip → tooltip（多行字符串列表直通）
        if (el.containsKey("tip")) {
            el.put("tooltip", el.remove("tip"));
        }
        // 图片族：texture / textureHovered → image.{src,hoverSrc}
        if ("image".equals(type)) {
            Object tex = el.remove("texture");
            if (tex != null) {
                Map<String, Object> img = specOf(el, "image");
                img.putIfAbsent("src", tex);
                el.put("image", img);
            }
            Object hover = el.remove("textureHovered");
            if (hover != null) {
                Map<String, Object> img = specOf(el, "image");
                img.put("hoverSrc", hover);
                el.put("image", img);
            }
        }
        // 槽位：identifier container_N → chest_slot.slot；drawBackground → showSlot(取反)
        if ("chest_slot".equals(type)) {
            Object idf = el.remove("identifier");
            if (idf != null) {
                Map<String, Object> slotSpec = specOf(el, "chest_slot");
                int idx = containerIndex(String.valueOf(idf));
                if (idx >= 0) {
                    slotSpec.put("slot", idx);
                    el.put("chest_slot", slotSpec);
                }
            }
            Object drawBg = el.remove("drawBackground");
            if (drawBg != null) {
                Map<String, Object> slotSpec = specOf(el, "chest_slot");
                // 同极性直映射：drawBackground:true(画槽底) ≡ showSlot:true
                slotSpec.put("showSlot", Boolean.parseBoolean(String.valueOf(drawBg)));
                el.put("chest_slot", slotSpec);
            }
        }
    }

    /** 取（必要时创建）元素上的类型 spec 子对象。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> specOf(Map<String, Object> el, String type) {
        Object cur = el.get(type);
        if (cur instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new java.util.LinkedHashMap<>();
    }

    /** "container_6" / "container6" / 纯数字 → 槽位号；无法解析返回 -1。 */
    static int containerIndex(String identifier) {
        if (identifier == null) return -1;
        String t = identifier.trim();
        if (t.matches("\\d+")) return Integer.parseInt(t);
        var m = java.util.regex.Pattern.compile("container[_\\-]?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(t);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
