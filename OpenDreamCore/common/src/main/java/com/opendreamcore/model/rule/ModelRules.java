package com.opendreamcore.model.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * match 归一化：DreamCore 单行「类型:值」与块式两种写法，统一成条件列表。
 *
 * 单行（主推，DreamCore 实样同款）：
 *   match: "Name:史莱姆"
 *   match: "Type:minecraft:blaze;Name:炎魔"        // 多条件用 ; 分隔
 *   match: "ID:diamond_sword;Name:幽冥剑"
 * 块式（兼容）：
 *   match: { name: xx, type: yy } / { item_id: xx } / { custom_name: xx } / { entity_type: xx }
 * 归一化后的条件键（小写）：name / type / id / lore / nbt。
 * 切分规则：每个条件按「首个冒号」切 type 与 value（值内允许再出现冒号，如 NBT JSON）。
 * 块式键别名 → 归一键：custom_name/entity_type→(name/type 实体侧)，item_id→id，item_name→name。
 */
public final class ModelRules {

    /** 归一键常量。 */
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String ID = "id";
    public static final String LORE = "lore";
    public static final String NBT = "nbt";

    private ModelRules() {
    }

    /** 单条匹配条件。 */
    public static final class Cond {
        public final String key;
        public final String value;

        public Cond(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + ":" + value;
        }
    }

    /**
     * 归一化入口：接受 String（单行）或 Map（块式）或 List（多行/多块混合）。
     * 无法识别的结构 → 空列表（永不匹配，缺省即安全）。
     */
    public static List<Cond> normalize(Object match) {
        List<Cond> out = new ArrayList<Cond>();
        if (match instanceof String) {
            parseLine((String) match, out);
        } else if (match instanceof Map) {
            parseMap((Map<?, ?>) match, out);
        } else if (match instanceof List) {
            for (Object o : (List<?>) match) {
                out.addAll(normalize(o));
            }
        }
        return out;
    }

    /** 单行解析：分号切多条件；每条按首个冒号切键值；键按前缀归一。 */
    static void parseLine(String line, List<Cond> out) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        for (String part : line.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int colon = p.indexOf(':');
            if (colon <= 0) {
                // 无类型前缀 → 当作 Name 匹配（DreamCore 兼容："史莱姆" ≙ Name:史莱姆）
                out.add(new Cond(NAME, p));
                continue;
            }
            String key = canon(p.substring(0, colon).trim());
            String value = p.substring(colon + 1).trim();
            if (key != null && !value.isEmpty()) {
                out.add(new Cond(key, value));
            }
        }
    }

    /** 块式解析：键归一后逐条加入。 */
    static void parseMap(Map<?, ?> m, List<Cond> out) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String rawKey = String.valueOf(e.getKey());
            String key = canon(rawKey);
            Object v = e.getValue();
            if (key == null || v == null) {
                continue;
            }
            if (v instanceof List) {
                for (Object o : (List<?>) v) {
                    if (o != null) {
                        out.add(new Cond(key, String.valueOf(o)));
                    }
                }
            } else {
                out.add(new Cond(key, String.valueOf(v)));
            }
        }
    }

    /** 键归一：大小写不敏感 + 别名；未知键返回 null（丢弃并留给调用方日志）。 */
    static String canon(String raw) {
        String k = raw.toLowerCase(Locale.ROOT);
        if (k.equals("name") || k.equals("custom_name") || k.equals("item_name") || k.equals("customname")) {
            return NAME;
        }
        if (k.equals("type") || k.equals("entity_type") || k.equals("entitytype") || k.equals("mob")) {
            return TYPE;
        }
        if (k.equals("id") || k.equals("item_id") || k.equals("itemid")) {
            return ID;
        }
        if (k.equals("lore")) {
            return LORE;
        }
        if (k.equals("nbt")) {
            return NBT;
        }
        return null;
    }

    /** 是否匹配全部条件（全 AND；大小写不敏感；实体名/MC id 忽略命名空间差异见 isIdMatch）。 */
    public static boolean matches(List<Cond> conds, Lookup lookup) {
        if (conds == null || conds.isEmpty()) {
            return false;
        }
        for (Cond c : conds) {
            String actual = lookup.get(c.key);
            if (actual == null || !isMatch(c.value, actual)) {
                return false;
            }
        }
        return true;
    }

    /** 值比较：普通 contains（大小写不敏感）；minecraft:id 前缀可省略。 */
    static boolean isMatch(String expect, String actual) {
        String e = expect.toLowerCase(Locale.ROOT);
        String a = actual.toLowerCase(Locale.ROOT);
        if (e.contains(":") && !a.contains(":")) {
            int colon = e.indexOf(':');
            return a.equals(e.substring(colon + 1)) || a.contains(e.substring(colon + 1));
        }
        return a.contains(e);
    }

    /** 条件查询接口（客户端实现：name=显示名/type=实体类型/id=物品 id/lore/nbt）。 */
    public interface Lookup {
        String get(String key);
    }
}
