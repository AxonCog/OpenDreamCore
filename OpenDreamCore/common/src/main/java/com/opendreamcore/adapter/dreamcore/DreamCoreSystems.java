package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.util.J8;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DragonCore 原生系统配置 → OpenDreamCore 九系统规则 IR。
 *
 * 服务器上真实跑着的龙核配置就是金标准（样本快照见
 * common/src/test/resources/dragoncore/systems/），翻译约定逐条写在
 * 对应方法注释里：能等价的等价，只能近似的注明近似点，没有对应的一律
 * 丢弃不猜——宁可少一个特效，不要一个画错位置的。
 *
 * 全部方法纯 Map→Map，零 IO；文件读取和并入规则库由桥接层负责。
 */
public final class DreamCoreSystems {

    private DreamCoreSystems() {
    }

    /** 龙核配置文件名 → 我们的系统名；空串表示不归九系统管（如 ItemTip 是界面页）。 */
    public static String systemFor(String fileName) {
        if (fileName == null) {
            return "";
        }
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".yml")) {
            n = n.substring(0, n.length() - 4);
        }
        switch (n) {
            case "keyconfig": return "KeyConfig";
            case "itemicon": return "ItemIcon";
            case "worldtexture": return "WorldTexture";
            case "armorlayer": return "ArmorLayer";
            case "fontconfig": return "FontConfig";
            case "blood": return "HeadTag";
            case "slotconfig": return "SlotConfig";
            default: return "";
        }
    }

    //
    // KeyConfig：键名 → {cooldown, commands}
    //

    /**
     * 龙核 KeyConfig 和我们同构（键名=组合，值含 cooldown 秒数和命令列表），
     * 差别只在命令前缀写法（[console] 大小写）和 %player% 占位——那两处
     * 由执行器端统一兜住，这里只把 commands 规整成列表。
     */
    public static Map<String, Map<String, Object>> keyConfig(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> ir = new LinkedHashMap<>();
            for (Map.Entry<?, ?> f : m.entrySet()) {
                String k = String.valueOf(f.getKey());
                if ("commands".equals(k)) {
                    ir.put(k, asStringList(f.getValue()));
                } else {
                    ir.put(k, f.getValue());
                }
            }
            out.put(e.getKey(), ir);
        }
        return out;
    }

    //
    // ItemIcon：{match, texture, scale, type, mode, width, height}
    //

    /**
     * match/texture/scale 直通；type 是旧版数字物品 id，转成字符串进 id
     * （老端物品本来就按数字 id 找）；mode/width/height 在我们这边没有
     * 对应物（图标尺寸由贴图和 scale 决定），丢弃。
     */
    public static Map<String, Map<String, Object>> itemIcon(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> ir = new LinkedHashMap<>();
            for (Map.Entry<?, ?> f : m.entrySet()) {
                String k = String.valueOf(f.getKey());
                if ("match".equals(k) || "texture".equals(k) || "scale".equals(k)) {
                    ir.put(k, f.getValue());
                } else if ("type".equals(k) && f.getValue() != null) {
                    ir.put("id", String.valueOf(f.getValue()));
                }
                // mode / width / height：无对应，不翻
            }
            if (!ir.isEmpty()) {
                out.put(e.getKey(), ir);
            }
        }
        return out;
    }

    //
    // WorldTexture：{world,x,y,z,rotateX,rotateY,rotateZ,path,width,height,alpha,follow,glow}
    //

    /**
     * path→texture、rotateY→rotate_y（rotateX/Z 我们的世界贴图没有第三轴
     * 旋转，丢弃）、follow:true 转成 options.world.follow（世界面板锚点
     * 语义）。龙核还有 "名字/png" 的两级嵌套写法，摊平成 名字_png。
     */
    public static Map<String, Map<String, Object>> worldTexture(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String baseId = e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m && isWorldTextureRule(m)) {
                out.put(sanitizeId(baseId), translateWorldTexture((Map<String, Object>) m));
            } else if (v instanceof Map<?, ?> m) {
                // 嵌套写法：子键（如 png）底下才是规则
                for (Map.Entry<?, ?> sub : m.entrySet()) {
                    if (sub.getValue() instanceof Map<?, ?> sm
                            && isWorldTextureRule((Map<?, ?>) sm)) {
                        out.put(sanitizeId(baseId + "_" + sub.getKey()),
                                translateWorldTexture((Map<String, Object>) sm));
                    }
                }
            }
        }
        return out;
    }

    private static boolean isWorldTextureRule(Map<?, ?> m) {
        return m.containsKey("path") || m.containsKey("world");
    }

    private static Map<String, Object> translateWorldTexture(Map<String, Object> m) {
        Map<String, Object> ir = new LinkedHashMap<>();
        for (String k : new String[]{"world", "x", "y", "z", "width", "height", "alpha", "glow"}) {
            if (m.containsKey(k)) {
                ir.put(k, m.get(k));
            }
        }
        if (m.containsKey("path")) {
            ir.put("texture", m.get("path"));
        }
        if (m.containsKey("rotateY")) {
            ir.put("rotate_y", m.get("rotateY"));
        }
        Object follow = m.get("follow");
        if (Boolean.TRUE.equals(follow) || "true".equals(String.valueOf(follow))) {
            Map<String, Object> world = new LinkedHashMap<>();
            world.put("follow", true);
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("world", world);
            ir.put("options", options);
        }
        return ir;
    }

    //
    // ArmorLayer：{match, texture}
    //

    /**
     * 龙核的 match 是"lore 或 name 或 nbt 包含"，我们的 match 正好是
     * "名称或 lore 包含即命中"，直通。texture 是盔甲贴图名，龙核约定按
     * armor/<名>_layer_1.png / _layer_2.png 取层，拆成 layer1/layer2。
     */
    public static Map<String, Map<String, Object>> armorLayer(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> ir = new LinkedHashMap<>();
            Object match = m.get("match");
            if (match != null) {
                ir.put("match", match);
            }
            Object tex = m.get("texture");
            if (tex != null && !String.valueOf(tex).trim().isEmpty()) {
                String base = String.valueOf(tex).trim();
                ir.put("layer1", "armor/" + base + "_layer_1.png");
                ir.put("layer2", "armor/" + base + "_layer_2.png");
            }
            if (!ir.isEmpty()) {
                out.put(sanitizeId(e.getKey()), ir);
            }
        }
        return out;
    }

    //
    // FontConfig：{字符: {path, width, height}}
    //

    /**
     * 字符替换表：键名就是字符（龙核一个文件几十上百个键）。path→texture，
     * range/match/ttf 语义直通，排版字段（width/height/ascent/fontWidth/u/v/…）
     * 原样带过去——客户端消费端按统一内部格式解析，这层只管把龙核叫法换成我们的。
     * 说白了就是翻译官：龙核管贴图叫 path，我们叫 texture，字段名对不上，
     * 翻译少一步配置就废——适配器就是干这个补漏的。
     */
    public static Map<String, Map<String, Object>> fontConfig(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> ir = new LinkedHashMap<>();
            // path → texture：龙核叫 path，我们叫 texture
            if (m.containsKey("path")) {
                ir.put("texture", m.get("path"));
            }
            // range（区间批量）/ match（正则选字）/ ttf（全局字体）语义直通
            for (String k : new String[]{"range", "match", "ttf"}) {
                if (m.containsKey(k)) {
                    ir.put(k, m.get(k));
                }
            }
            // 排版字段直通：width/height/ascent/fontWidth/u/v/xOffset/yOffset
            for (String k : new String[]{"width", "height", "ascent", "fontWidth",
                    "u", "v", "xOffset", "yOffset"}) {
                if (m.containsKey(k)) {
                    ir.put(k, m.get(k));
                }
            }
            if (!ir.isEmpty()) {
                // 键名就是字符（单字符/range 的 id），原样保留，不过 sanitizeId
                out.put(e.getKey(), ir);
            }
        }
        return out;
    }

    //
    // SlotConfig：{槽名: {attribute, skin, limit: [条件...]}}
    //

    /**
     * 龙核把放行条件堆在 limit 列表里（lorecontains|x、lore|x、permission|x…），
     * 我们是平铺键，逐条拆开。skin|x（时装类型限制）和 emptyslot|x（空槽
     * 才能放）语义没有对应，丢弃；attribute/skin 兼容开关直通。
     */
    public static Map<String, Map<String, Object>> slotConfig(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if ("Script".equals(e.getKey())) {
                // 龙核的服务端函数库（Nashorn JS），我们有限值语义不执行它的脚本
                continue;
            }
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> ir = new LinkedHashMap<>();
            for (Map.Entry<?, ?> f : m.entrySet()) {
                String k = String.valueOf(f.getKey());
                if ("limit".equals(k)) {
                    parseSlotLimits(f.getValue(), ir);
                } else if ("attribute".equals(k) || "skin".equals(k)) {
                    ir.put(k, f.getValue());
                }
            }
            if (!ir.isEmpty()) {
                out.put(sanitizeId(e.getKey()), ir);
            }
        }
        return out;
    }

    private static void parseSlotLimits(Object limit, Map<String, Object> ir) {
        for (String item : asStringList(limit)) {
            int sep = item.indexOf('|');
            if (sep <= 0 || sep == item.length() - 1) {
                // 裸值（如 - "项链"）：龙核按 lore 包含处理
                String bare = item.trim();
                if (!bare.isEmpty()) {
                    ir.put("lore_contains", bare);
                }
                continue;
            }
            String kind = item.substring(0, sep).toLowerCase(Locale.ROOT).trim();
            String value = item.substring(sep + 1).trim();
            switch (kind) {
                case "lorecontains" -> ir.put("lore_contains", value);
                case "lore" -> ir.put("lore", value);
                case "permission" -> ir.put("permission", value);
                // skin|x 与 emptyslot|x：无对应语义，丢弃
                default -> { }
            }
        }
    }

    //
    // Blood → HeadTag：血条页翻译
    //

    /**
     * 龙核 Blood 是"全体生物头顶血条+文字"，我们用 HeadTag 页面承载。
     * 近似点都记在这：
     *   entity 缺省 "*"（我们 HeadTag 实体绑定按通配实现）；
     *   龙核的 offsetY 以脚底为基准，我们 y 是头顶偏移，数值直出（服主
     *     大概率要微调，注释里说清楚）；
     *   贴图像素尺寸按 50px=1格 折算成世界单位；
     *   %health% → entity.health、%name% → entity.name、per →
     *     entity.health_ratio，都是实体上下文变量。
     */
    public static Map<String, Map<String, Object>> blood(Map<String, Object> raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> ir = new LinkedHashMap<>();
            ir.put("entity", "*");
            Object offY = m.get("offsetY");
            ir.put("y", offY instanceof Number n ? n.doubleValue() / 10.0 : 0.3);
            ir.put("Functions", J8.map("tick",
                    "Screen.设置变量(\"odc_blood_alive\", entity.health > 0)"));
            double px2world = 1.0 / 50.0;
            Object bg = m.get("background");
            if (bg instanceof Map<?, ?> bm) {
                ir.put("血条底", bloodImage(bm, px2world, 0.0));
            }
            Object hp = m.get("health");
            if (hp instanceof Map<?, ?> hm) {
                Map<String, Object> el = bloodImage(hm, px2world, 0.02);
                Object w = hm.get("width");
                if (w instanceof Number) {
                    // 纯数字宽度已在 bloodImage 里折算过，不动
                } else if (w != null) {
                    // 表达式宽度（如 "5+%health%*190"）是像素语义，套上同款折算
                    el.put("width", "(" + rewriteBloodVars(String.valueOf(w)) + ") / 50");
                }
                ir.put("血条", el);
            }
            if (m.get("string") instanceof Map<?, ?> sm) {
                int i = 0;
                for (Map.Entry<?, ?> se : sm.entrySet()) {
                    if (!(se.getValue() instanceof Map<?, ?> txt)) {
                        continue;
                    }
                    Map<String, Object> el = new LinkedHashMap<>();
                    el.put("type", "text");
                    Object content = rewriteBloodVars(str2(txt.get("content")));
                    Map<String, Object> textSpec = new LinkedHashMap<>();
                    textSpec.put("content", content);
                    el.put("text", textSpec);
                    el.put("x", numOr(txt.get("x"), 0.0));
                    el.put("y", numOr(txt.get("y"), 0.0));
                    Object scale = txt.get("scale");
                    if (scale != null) {
                        el.put("scale", scale);
                    }
                    ir.put("血字" + (i++ > 0 ? i : ""), el);
                }
            }
            out.put(sanitizeId(e.getKey()), ir);
        }
        return out;
    }

    /** 血条贴图 → image 元素；x/y 缺省居中贴着锚点。 */
    private static Map<String, Object> bloodImage(Map<?, ?> m, double px2world, double dy) {
        Map<String, Object> el = new LinkedHashMap<>();
        el.put("type", "image");
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("src", str2(m.get("path")));
        el.put("image", img);
        el.put("x", 0);
        el.put("y", dy);
        Map<String, Object> holo = new LinkedHashMap<>();
        holo.put("width", numOr(m.get("width"), 100.0) * px2world);
        holo.put("height", numOr(m.get("height"), 10.0) * px2world);
        el.put("hologram", holo);
        return el;
    }

    /** 龙核血量变量 → 我们实体上下文：健康值/名字/比例。 */
    static String rewriteBloodVars(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }
        return expr.replace("%health%", "entity.health")
                .replace("%name%", "entity.name")
                .replace("%per%", "entity.health_ratio");
    }

    //
    // 工具
    //

    /** 值转字符串列表（单值/列表/带引号都接住）。 */
    private static List<String> asStringList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else if (v != null) {
            out.add(String.valueOf(v));
        }
        return out;
    }

    /** 规则 id 只留文件名安全字符（龙核 id 里带 / 和空格是常事）。 */
    static String sanitizeId(String id) {
        return id == null ? "" : id.replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5.-]", "_");
    }

    private static String str2(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double numOr(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }
}
