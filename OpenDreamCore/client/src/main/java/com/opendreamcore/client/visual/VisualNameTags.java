package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 头顶名牌（HeadTag）与纯血条（Blood）两系统的规则解析与逐实体命中判定。
 * 两系统独立存储（rulesOf("HeadTag") / rulesOf("Blood")），共享 Entry 结构与
 * HeadTagRenderer 渲染管线；两系统独立认领——同一实体 HeadTag 与 Blood 各自
 * 判定命中，命中的页面按系统顺序叠加渲染（HeadTag 在前 Blood 在后），
 * 名字+血条可同屏（龙核原生行为对齐）。
 *
 * 一条规则可以只配样式（背景/文字），也可以带完整页面元素：
 * 前者由各平台的 MixinNameTag 就地画文字条，后者登记给 HeadTagRenderer
 * 在实体渲染收尾后整页渲染——health / health_max / name 变量对页面表达式可用。
 *
 * 规则形态：
 *   entity: minecraft:zombie,minecraft:villager   # 实体类型，逗号分隔，命名空间可省
 *   name: 老王                 # 可选：名字包含匹配
 *   contains: 老王             # 可选：名字包含匹配（name 的别名，龙核词汇同义）
 *   match: xxx                 # 可选：match 键走物品式匹配（对实体类型字符串生效）
 *   distance: 16               # 可选：最大显示距离（格）
 *   offsetX: 0.3               # 可选：横向偏移（格，billboard 左右）
 *   offsetY: 0.2               # 可选：纵向偏移（格，旧键 y 等效）
 *   mode: always|aim|health|aimorhealth|distance_16   # 显示时机，默认 always
 *   背景: { color: "#00000080", 圆角: 4, 边框色: "#FFFFFF", 边框宽: 1 }
 *   文字: { color: "#FFFFFF", 字号: 8 }
 */
public final class VisualNameTags {

    /** 名牌样式；没配的颜色字号就用这套默认。 */
    public record TagStyle(int bgColor, int bgRadius, int borderColor, int borderWidth,
                           int textColor, int fontSize, String texture) {

        public static TagStyle fallback() {
            return new TagStyle(0x80000000, 3, 0xFFFFFFFF, 1, 0xFFFFFFFF, 9, null);
        }
    }

    /** 命中结果：fullHud=true 时整页交给 HeadTagRenderer，false 时调用方就地画文字条。 */
    public record Hit(TagStyle style, boolean fullHud) {
    }

    /** 一条已解析的 HeadTag 规则；offsetX/offsetY 为头顶锚点偏移（格）。 */
    public record Entry(String ruleId, List<String> entityTypes, MatchSpec spec, String name,
                        double distance, String mode, TagStyle style,
                        double offsetX, double offsetY, Map<String, Object> pageIr,
                        boolean hasContent) {
    }

    private static volatile List<Entry> entries = List.of();

    /** Blood（纯血条）规则集：独立存储，与 HeadTag 并行独立认领（可同屏叠加）。 */
    private static volatile List<Entry> bloodEntries = List.of();

    /** 当前正在渲染的实体（1.21.4+ state 体系下由 extractRenderState 钩子写入，名牌 hook 读取）。 */
    private static final ThreadLocal<Entity> CURRENT_ENTITY = new ThreadLocal<>();

    public static void setCurrentEntity(Entity entity) {
        CURRENT_ENTITY.set(entity);
    }

    public static Entity currentEntity() {
        return CURRENT_ENTITY.get();
    }

    private VisualNameTags() {
    }

    /** 规则集变化后重解析（handleVisualRules / reloadAll 入库时调用）。 */
    public static void refresh() {
        entries = parseAll("HeadTag");
        bloodEntries = parseAll("Blood");
        com.opendreamcore.client.HeadTagRenderer.invalidate();
    }

    private static List<Entry> parseAll(String system) {
        List<Entry> out = new ArrayList<>();
        for (var e : ClientVisualStore.get().rulesOf(system).entrySet()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(e.getValue());
                if (ir == null) {
                    continue;
                }
                out.add(parseEntry(e.getKey(), ir));
            } catch (Exception ignored) {
                // 单条规则坏了静默跳过——名牌丑总比炸渲染强
            }
        }
        return List.copyOf(out);
    }

    /** 匹配/偏移键：不进页面定义，也不算整页内容。 */
    private static final java.util.Set<String> MATCH_KEYS = java.util.Set.of(
            "entity", "实体", "name", "contains", "包含",
            "distance", "mode", "显示模式",
            "offsetX", "x偏移", "offsetY", "y偏移", "y");

    /** 样式键：只影响就地文字条外观（parseStyle 读的就是这几个），不构成整页内容。 */
    private static final java.util.Set<String> STYLE_KEYS = java.util.Set.of(
            "背景", "文字", "texture", "贴图");

    /**
     * 规则是否带「整页内容」——除匹配/偏移/样式键之外还有别的键（文本/图片/输入框等页面元素）。
     *
     * 这条判定决定「能不能把原版名牌交出去」：只说样式（背景/文字）的规则必须由各平台
     * MixinNameTag 就地画文字条。一旦误判成整页，Mixin 会取消原版名牌，而整页渲染器对
     * 「只有样式、没有元素」的页面布局不出任何节点（drawHeadTag 直接 return），
     * 结果就是名牌整体消失——实机回归过的坑，别再把 pageIr 非空当成有内容。
     */
    private static boolean hasPageContent(Map<String, Object> ir) {
        for (String k : ir.keySet()) {
            if (MATCH_KEYS.contains(k) || STYLE_KEYS.contains(k)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static Entry parseEntry(String ruleId, Map<String, Object> ir) {
        List<String> types = new ArrayList<>();
        Object rawTypes = firstOf(ir, "entity", "实体");
        if (rawTypes != null) {
            for (String part : String.valueOf(rawTypes).split(",")) {
                String t = stripNs(part);
                if (t.isEmpty() || t.equals("*")) {
                    continue; // * / 空段 = 通配：空类型列表即全体实体（龙核 Blood 语义）
                }
                types.add(t);
            }
        }
        String name = str(firstOf(ir, "name", "contains", "包含"));
        double distance = num(ir.get("distance"), 0);
        double offsetX = num(firstOf(ir, "offsetX", "x偏移"), 0);
        Object rawOffsetY = firstOf(ir, "offsetY", "y偏移");
        if (rawOffsetY == null) {
            rawOffsetY = ir.get("y"); // 旧键 y 兼容：头顶锚点纵向偏移
        }
        double offsetY = num(rawOffsetY, 0);
        String mode = str(firstOf(ir, "mode", "显示模式"));
        if (mode == null || mode.isBlank()) {
            mode = "always";
        }
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (mode.startsWith("distance_")) {
            // distance_N 写法：等价 always + 距离上限 N
            try {
                double n = Double.parseDouble(mode.substring(9));
                if (distance <= 0) {
                    distance = n;
                }
            } catch (NumberFormatException ignored) {
            }
            mode = "always";
        }
        TagStyle style = parseStyle(ir);
        Map<String, Object> pageIr = new LinkedHashMap<>();
        for (Map.Entry<String, Object> kv : ir.entrySet()) {
            String k = kv.getKey();
            if (k.equals("entity") || k.equals("实体") || k.equals("name")
                    || k.equals("contains") || k.equals("包含")
                    || k.equals("distance") || k.equals("mode") || k.equals("显示模式")
                    || k.equals("offsetX") || k.equals("x偏移")
                    || k.equals("offsetY") || k.equals("y偏移") || k.equals("y")) {
                continue; // 匹配/偏移键不进页面定义
            }
            pageIr.put(k, kv.getValue());
        }
        return new Entry(ruleId, List.copyOf(types), MatchSpec.parse(ir), name,
                distance, mode, style, offsetX, offsetY, pageIr, hasPageContent(ir));
    }

    /**
     * 实体名牌渲染前的命中判定（各平台 MixinNameTag 逐实体逐帧调用）。
     * 返回 null 表示不接管（原版名牌照旧）；fullHud=true 表示规则带整页内容，
     * 调用方只取消原版名牌，页面由 HeadTagRenderer 在实体渲染收尾统一画。
     */
    public static Hit hitFor(Entity entity) {
        if (entity == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        String fullType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString();
        String type = stripNs(fullType);
        String entityName = entity.getName().getString();
        float dist = mc.player != null ? entity.distanceTo(mc.player) : 0.0F;
        boolean crosshair = mc.crosshairPickEntity == entity;
        // 两系统独立认领：HeadTag 与 Blood 各自判定，命中的整页都登记给
        // HeadTagRenderer 叠加渲染；返回值只决定原版名牌接管与就地样式，
        // HeadTag 优先（样式语义），Blood 纯血条紧随其后
        Hit hit = matchList(entity, entries, fullType, type, entityName, dist, crosshair);
        matchList(entity, bloodEntries, fullType, type, entityName, dist, crosshair);
        return hit;
    }

    private static Hit matchList(Entity entity, List<Entry> list, String fullType, String type,
                                 String entityName, float dist, boolean crosshair) {
        for (Entry e : list) {
            if (!e.entityTypes().isEmpty()
                    && !e.entityTypes().contains(type) && !e.entityTypes().contains(fullType)) {
                continue;
            }
            if (!e.spec().matches(new ItemView(type, "", List.of(), Map.of()))) {
                continue;
            }
            if (e.name() != null && !e.name().isEmpty()
                    && (entityName == null || !entityName.contains(e.name()))) {
                continue;
            }
            if (e.distance() > 0 && dist > e.distance()) {
                continue;
            }
            boolean living = entity instanceof LivingEntity;
            switch (e.mode()) {
                case "aim" -> {
                    if (!crosshair) {
                        continue;
                    }
                }
                case "health" -> {
                    if (!living || isFullHealth((LivingEntity) entity)) {
                        continue;
                    }
                }
                case "aimorhealth" -> {
                    if (!crosshair && (!living || isFullHealth((LivingEntity) entity))) {
                        continue;
                    }
                }
                default -> {
                }
            }
            // 只有带整页内容的规则才把原版名牌交出去；只说样式（背景/文字）的规则
            // 必须就地画文字条——判错就会整条名牌消失（详见 hasPageContent 注释）
            boolean fullHud = e.hasContent();
            if (fullHud) {
                com.opendreamcore.client.HeadTagRenderer.noteEntity(entity.getId(), e);
            }
            return new Hit(e.style(), fullHud);
        }
        return null;
    }

    private static boolean isFullHealth(LivingEntity living) {
        return living.getHealth() >= living.getMaxHealth();
    }

    private static TagStyle parseStyle(Map<String, Object> rule) {
        Map<String, Object> bg = mapOf(rule.get("背景"));
        Map<String, Object> tx = mapOf(rule.get("文字"));
        return new TagStyle(
                argb(bg.get("color"), 0x80000000),
                (int) num(bg.get("圆角"), 3),
                argb(bg.get("边框色"), 0xFFFFFFFF),
                (int) num(bg.get("边框宽"), 1),
                argb(tx.get("color"), 0xFFFFFFFF),
                (int) num(tx.get("字号"), 9),
                str(firstOf(rule, "texture", "贴图")));
    }

    private static Map<String, Object> mapOf(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return Map.of();
    }

    private static Object firstOf(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static int argb(Object o, int fallback) {
        if (o == null) {
            return fallback;
        }
        String s = String.valueOf(o).trim();
        try {
            String hex = s.startsWith("#") ? s.substring(1) : s.replace("0x", "");
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String stripNs(String type) {
        String s = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }
}
