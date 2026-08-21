package com.opendreamcore.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * type 别名映射 + 后缀推断。
 *
 * 两层映射：
 * 1. 别名表（ALIAS_MAP）：type: texture → image, type: label → text
 *    用户写 type: texture 或 _texture 后缀都能映射到 image
 * 2. 后缀表（SUFFIX_MAP）：_btn → button, _img → image
 *    后缀先查后缀表，没有再查别名表（所以 _texture 也能用）
 *
 * 用户可通过 registerAlias / registerSuffix 动态注册自定义映射，
 * 也可以从 type-aliases.yml 热加载（文件监听变更时自动重载）。
 *
 * 语法迁移自23年梦想核心与26年梦想核心正式版。
 * 作者：梦幻 QQ:2496599413
 *
 * 内置后缀：
 * _btn/_button→button  _txt/_text→text  _img/_image→image  _rect→rect
 * _input→input  _dd/_dropdown→dropdown  _toggle→toggle  _slider→slider
 * _prog/_progress→progress  _tabs→tabs  _slot/_item_slot→item_slot
 * _video→video  _entity→entity  _layout→layout  _chk/_checkbox→checkbox
 *
 * 内置别名（type 值映射）：
 * texture→image  label→text  pic→image  field→input
 * combo→dropdown  switch→toggle  bar→progress  checkbox→checkbox
 */
public final class TypeInferrer {

    /** 后缀 → type 映射 */
    private static final Map<String, String> SUFFIX_MAP = new LinkedHashMap<>();

    /** type 别名 → 标准 type 映射 */
    private static final Map<String, String> ALIAS_MAP = new LinkedHashMap<>();

    static {
        // ---- 后缀 ----
        registerSuffix("btn", "button");
        registerSuffix("button", "button");
        registerSuffix("txt", "text");
        registerSuffix("text", "text");
        registerSuffix("img", "image");
        registerSuffix("image", "image");
        registerSuffix("rect", "rect");
        registerSuffix("input", "input");
        registerSuffix("dd", "dropdown");
        registerSuffix("dropdown", "dropdown");
        registerSuffix("toggle", "toggle");
        registerSuffix("slider", "slider");
        registerSuffix("prog", "progress");
        registerSuffix("progress", "progress");
        registerSuffix("tabs", "tabs");
        registerSuffix("slot", "item_slot");
        registerSuffix("item_slot", "item_slot");
        registerSuffix("video", "video");
        registerSuffix("entity", "entity");
        registerSuffix("layout", "layout");
        registerSuffix("chk", "checkbox");
        registerSuffix("checkbox", "checkbox");

        // ---- 别名（type 值映射）----
        registerAlias("texture", "image");
        registerAlias("pic", "image");
        registerAlias("label", "text");
        registerAlias("field", "input");
        registerAlias("combo", "dropdown");
        registerAlias("switch", "toggle");
        registerAlias("bar", "progress");
        registerAlias("btn", "button");
        registerAlias("txt", "text");
        registerAlias("img", "image");
        registerAlias("dd", "dropdown");
        registerAlias("prog", "progress");
        registerAlias("slot", "item_slot");
        registerAlias("chk", "checkbox");
    }

    private TypeInferrer() {
    }

    /** 注册后缀映射，覆盖同名 */
    public static void registerSuffix(String suffix, String type) {
        if (suffix != null && !suffix.isBlank() && type != null && !type.isBlank()) {
            SUFFIX_MAP.put(suffix.toLowerCase(), type);
        }
    }

    /** 注册 type 别名，覆盖同名 */
    public static void registerAlias(String alias, String type) {
        if (alias != null && !alias.isBlank() && type != null && !type.isBlank()) {
            ALIAS_MAP.put(alias.toLowerCase(), type);
        }
    }

    /** 兼容旧接口 */
    public static void register(String suffix, String type) {
        registerSuffix(suffix, type);
    }

    /**
     * 从 id 后缀推断 type。
     * 先查后缀表，没有再查别名表（texture 后缀也能映射到 image）。
     * fill_btn → button, title_txt → text, bg_texture → image
     */
    public static String infer(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        int idx = id.lastIndexOf('_');
        if (idx < 0 || idx >= id.length() - 1) {
            return null;
        }
        String suffix = id.substring(idx + 1).toLowerCase();
        String type = SUFFIX_MAP.get(suffix);
        if (type != null) {
            return type;
        }
        return ALIAS_MAP.get(suffix);
    }

    /**
     * 解析最终 type：
     * 1. 有显式 type → 走别名映射（texture → image, label → text）
     * 2. 无显式 type → 从 id 后缀推断
     */
    public static String resolve(String explicitType, String id) {
        if (explicitType != null && !explicitType.isBlank()) {
            return resolveAlias(explicitType);
        }
        return infer(id);
    }

    /** 别名映射：texture→image, label→text，非别名原样返回 */
    public static String resolveAlias(String type) {
        if (type == null || type.isBlank()) {
            return type;
        }
        return ALIAS_MAP.getOrDefault(type.toLowerCase(), type);
    }

    /** 所有后缀映射（只读，调试用） */
    public static Map<String, String> suffixMappings() {
        return java.util.Collections.unmodifiableMap(SUFFIX_MAP);
    }

    /** 所有别名映射（只读，调试用） */
    public static Map<String, String> aliasMappings() {
        return java.util.Collections.unmodifiableMap(ALIAS_MAP);
    }

    /** 兼容旧接口 */
    public static Map<String, String> mappings() {
        return suffixMappings();
    }

    /**
     * 从 Map 批量加载别名（热加载用）。
     * 格式：{texture: image, label: text, ...}
     * 不会清除内置别名，后加载的覆盖同名。
     */
    public static void loadAliases(Map<String, String> aliases) {
        if (aliases == null) {
            return;
        }
        aliases.forEach((alias, type) -> {
            if (alias != null && !alias.equalsIgnoreCase("type") && type != null) {
                registerAlias(alias, type);
            }
        });
    }

    /** 清空所有自定义映射（恢复内置） */
    public static void reset() {
        SUFFIX_MAP.clear();
        ALIAS_MAP.clear();
        // 重新注册内置
        registerSuffix("btn", "button");
        registerSuffix("button", "button");
        registerSuffix("txt", "text");
        registerSuffix("text", "text");
        registerSuffix("img", "image");
        registerSuffix("image", "image");
        registerSuffix("rect", "rect");
        registerSuffix("input", "input");
        registerSuffix("dd", "dropdown");
        registerSuffix("dropdown", "dropdown");
        registerSuffix("toggle", "toggle");
        registerSuffix("slider", "slider");
        registerSuffix("prog", "progress");
        registerSuffix("progress", "progress");
        registerSuffix("tabs", "tabs");
        registerSuffix("slot", "item_slot");
        registerSuffix("item_slot", "item_slot");
        registerSuffix("video", "video");
        registerSuffix("entity", "entity");
        registerSuffix("layout", "layout");
        registerSuffix("chk", "checkbox");
        registerSuffix("checkbox", "checkbox");

        registerAlias("texture", "image");
        registerAlias("pic", "image");
        registerAlias("label", "text");
        registerAlias("field", "input");
        registerAlias("combo", "dropdown");
        registerAlias("switch", "toggle");
        registerAlias("bar", "progress");
        registerAlias("btn", "button");
        registerAlias("txt", "text");
        registerAlias("img", "image");
        registerAlias("dd", "dropdown");
        registerAlias("prog", "progress");
        registerAlias("slot", "item_slot");
        registerAlias("chk", "checkbox");
    }
}
