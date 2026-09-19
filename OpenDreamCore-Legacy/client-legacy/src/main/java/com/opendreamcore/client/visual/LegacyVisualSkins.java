package com.opendreamcore.client.visual;

import com.opendreamcore.client.ClientControllerLegacy;
import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.config.PageSchema;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.protocol.message.VisualRulesSync;
import com.opendreamcore.util.J8;
import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.KeyCombo;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视觉规则客户端仓库：服务端把九大系统（ItemIcon/ItemEffect/HeadTag/
 * FontConfig/ArmorLayer/KeyConfig/Sounds/WorldTexture/SlotConfig）的规则
 * YAML 原文整包下发，这里收货、分系统摆好，各渲染口按需来取。
 *
 * 本版消费面与现代端同款：ItemIcon 出物品皮肤、KeyConfig 出按键组合上报、
 * WorldTexture/HeadTag 转世界面板页。ItemEffect/Sounds 等剩下几个系统现代端
 * 也只是入库待用，这里同样摆着，钩子将来要接随时有货。
 */
public final class LegacyVisualSkins {

    private LegacyVisualSkins() {
    }

    /** system → (规则id → 规则 YAML 原文)。整包替换，不增量。 */
    private static volatile Map<String, Map<String, String>> bundles = Collections.emptyMap();

    /** 已解析的物品皮肤条目（priority 降序），规则入库时刷新。 */
    private static volatile List<SkinEntry> skins = Collections.emptyList();

    /** KeyConfig 主键名 → 组合串集合，规则入库时刷新。 */
    private static volatile Map<String, Set<String>> keyCombos = Collections.emptyMap();

    /** 应用整包规则；世界面板页顺手注册进调度器的页面库。 */
    public static void apply(VisualRulesSync sync, MessageDispatcher dispatcher) {
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        for (VisualRulesSync.Entry e : sync.entries()) {
            Map<String, String> sys = grouped.get(e.system());
            if (sys == null) {
                sys = new LinkedHashMap<>();
                grouped.put(e.system(), sys);
            }
            sys.put(e.id(), e.yaml());
        }
        bundles = grouped;
        refreshSkins();
        refreshKeys();
        LegacyFontReplace.refresh(); // FontConfig 字符替换规则入库即重解析
        LegacyVisualItemIcons.refresh();
        LegacyVisualItemEffects.refresh();
        LegacyVisualArmorLayer.refresh();
        LegacyVisualNameTags.refresh();
        registerWorldPages(dispatcher);
    }

    /** 某系统的全部规则原文；没有给空 map。 */
    public static Map<String, String> rulesOf(String system) {
        Map<String, String> sys = bundles.get(system);
        return sys == null ? Collections.<String, String>emptyMap() : sys;
    }

    /** KeyConfig 规则引用的主键名集合（tick 轮询边沿检测用，别的不碰）。 */
    public static java.util.Set<String> keyPrimaries() {
        return keyCombos.keySet();
    }

    /**
     * 组合键 tick 轮询：主键按下沿触发一次命中判定。
     *
     * 1.12.2/1.7.10 旧 Forge 没有游戏内按键总线事件（1.7.10 那个只在
     * 部分路径触发），轮询 + 边沿检测是跨版本最稳的路子。键盘 API 的
     * isKeyDown 由各版钩子包一层喂进来，这里不碰 LWJGL，免得 1.16.5
     * 那边的 LWJGL3 编不过。
     *
     * focus：游戏内且无界面打开（界面期按键归界面，清边沿防穿屏）
     * isKeyDown：键名 → 是否按下（查不到的键返 false）
     * activeModifiers：当前激活修饰键集合（主键判定由这里做，修饰键只查包含）
     */
    public static void keyTickPoll(boolean focus,
                                   java.util.function.Predicate<String> isKeyDown,
                                   Set<String> activeModifiers) {
        if (!focus) {
            lastKeyDown.clear();
            return;
        }
        if (keyCombos.isEmpty()) {
            return;
        }
        java.util.Set<String> downNow = new java.util.HashSet<>();
        for (String primary : keyCombos.keySet()) {
            try {
                if (isKeyDown.test(primary)) {
                    downNow.add(primary);
                    if (!lastKeyDown.contains(primary)) {
                        // 上升沿：主键刚按下，看修饰键是否满足
                        reportComboFor(primary, activeModifiers);
                    }
                }
            } catch (RuntimeException ignored) {
                // 查不到的键名（鼠标键名等）跳过
            }
        }
        lastKeyDown.clear();
        lastKeyDown.addAll(downNow);
    }

    /** 上一帧按住的主键（边沿检测用）。 */
    private static final Set<String> lastKeyDown =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, java.lang.Boolean>());

    /** 按主键找组合，逐个查修饰键；命中即上报并返回。 */
    private static void reportComboFor(String primary, Set<String> active) {
        Set<String> combos = keyCombos.get(primary);
        if (combos == null) {
            return;
        }
        for (String combo : combos) {
            KeyCombo kc;
            try {
                kc = new KeyCombo(combo);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (modsSatisfied(kc.keys(), active)) {
                reportCombo(combo);
                return;
            }
        }
    }

    /** 断线/换服清仓：规则跟着会话走，不跨服残留。 */
    public static void clear() {
        bundles = Collections.emptyMap();
        skins = Collections.emptyList();
        keyCombos = Collections.emptyMap();
        headTagMatches.clear();
    }

    //
    // HeadTag 实体匹配：页id → (entity/name/distance/y) 匹配键
    //

    /**
     * 名牌页的匹配键存档。转换成页面时把 entity/name/distance 这几个
     * 非页面键剥掉，但导演要靠它们逐只匹配生物——所以剥之前先存档。
     * y 是页面级的头顶偏移（龙核 offsetY/10 折算出来的），一并存档。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.LinkedHashMap<String, Object>> headTagMatches =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 名牌页的匹配键（没存档返回 null）。 */
    public static java.util.Map<String, Object> headTagMatchOf(String pageId) {
        return headTagMatches.get(pageId);
    }

    /** 当前生效的全部名牌页 id（导演据此从普通世界页里分流出去）。 */
    public static java.util.Set<String> headTagPageIds() {
        return headTagMatches.keySet();
    }

    //
    // ItemIcon：物品皮肤
    //

    /** 一条皮肤：贴图路径 + 缩放。命中即把贴图盖在原版图标上。 */
    public static final class SkinEntry {
        public final MatchSpec spec;
        public final int priority;
        public final String texture;
        public final double scale;

        SkinEntry(MatchSpec spec, int priority, String texture, double scale) {
            this.spec = spec;
            this.priority = priority;
            this.texture = texture;
            this.scale = scale;
        }
    }

    /** 物品视图找皮肤：全字段匹配（名称/lore/类型/正则）。无命中 null。 */
    public static SkinEntry skinFor(ItemView view) {
        for (SkinEntry e : skins) {
            try {
                if (e.spec.matches(view)) {
                    return e;
                }
            } catch (RuntimeException ignored) {
                // 单条规则坏了不拖累整队，跳过看下一条
            }
        }
        return null;
    }

    /** 只有物品 id 时的退化匹配（名称/lore 传空）。 */
    public static SkinEntry skinForId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        String t = stripNs(itemId);
        return skinFor(new ItemView(t, "", J8.list(), J8.map()));
    }

    /** ItemIcon 规则全部解析成皮肤条目，priority 降序排好。 */
    private static void refreshSkins() {
        List<SkinEntry> out = new ArrayList<>();
        int fallbackPrio = 0;
        for (String yaml : rulesOf("ItemIcon").values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                for (Map.Entry<String, Object> en : ir.entrySet()) {
                    if (!(en.getValue() instanceof Map)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rule = (Map<String, Object>) en.getValue();
                    Object texRaw = firstOf(rule, "texture");
                    if (texRaw == null) {
                        continue;
                    }
                    String texture = String.valueOf(texRaw).trim();
                    if (texture.isEmpty()) {
                        continue;
                    }
                    // 注意：MatchSpec.parse 自己会读 match 键，规则整包丢进去
                    MatchSpec spec = MatchSpec.parse(rule);
                    int priority = numOr(rule.get("priority"), fallbackPrio);
                    double scale = numOr(rule.get("scale"), 1.0);
                    out.add(new SkinEntry(spec, priority, texture, scale));
                    if (priority > fallbackPrio) {
                        fallbackPrio = priority + 1;
                    }
                }
            } catch (Exception ignored) {
                // 单个规则文件坏了静默跳过——皮肤缺失只是难看，不该炸渲染
            }
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.priority, a.priority));
        skins = out;
    }

    private static Object firstOf(Map<String, Object> m, String key) {
        Object direct = m.get(key);
        if (direct != null) {
            return direct;
        }
        Object nested = m.get("match");
        if (nested instanceof Map) {
            return ((Map<?, ?>) nested).get(key);
        }
        return null;
    }

    private static int numOr(Object o, int def) {
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    private static double numOr(Object o, double def) {
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    private static String stripNs(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    //
    // KeyConfig：按键组合上报
    //

    /** 主键名 → 组合串集合（规则键即组合串，末段为主键）。 */
    private static void refreshKeys() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (String yaml : rulesOf("KeyConfig").values()) {
            for (String line : yaml.split("\n")) {
                String t = line.trim();
                int colon = t.indexOf(':');
                if (colon <= 0 || t.startsWith("#")) {
                    continue;
                }
                String combo = t.substring(0, colon).trim();
                if (combo.isEmpty() || !isValidCombo(combo)) {
                    continue;
                }
                KeyCombo kc;
                try {
                    kc = new KeyCombo(combo);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (kc.isEmpty()) {
                    continue;
                }
                // 主键 = 最后一段
                String primary = null;
                for (String k : kc.keys()) {
                    primary = k;
                }
                if (primary != null) {
                    Set<String> set = out.get(primary);
                    if (set == null) {
                        set = new java.util.HashSet<>();
                        out.put(primary, set);
                    }
                    set.add(combo);
                }
            }
        }
        keyCombos = out;
    }

    /** 组合段全小写单词多半是普通配置键，不当按键名。 */
    private static boolean isValidCombo(String combo) {
        if (!combo.contains("+")) {
            return !combo.matches("[a-z_0-9]+");
        }
        return true;
    }

    /**
     * 每个按键事件进来过一遍：主键对上且规则声明的修饰键全激活，就上报服务端。
     * 只报被规则引用的组合，无关按键零流量。事件式入口（有按键事件流的版本用）。
     */
    public static void keyComboHit(String keyName, Set<String> activeModifiers) {
        if (keyName == null) {
            return;
        }
        Set<String> combos = keyCombos.get(keyName);
        if (combos == null) {
            return;
        }
        for (String combo : combos) {
            KeyCombo kc;
            try {
                kc = new KeyCombo(combo);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (modsSatisfied(kc.keys(), activeModifiers)) {
                reportCombo(combo);
                return;
            }
        }
    }

    /** 规则声明的修饰键 ⊆ 当前激活修饰键才算命中。 */
    private static boolean modsSatisfied(Set<String> ruleKeys, Set<String> active) {
        for (String k : ruleKeys) {
            String n = k.trim().toUpperCase();
            if (isModifierName(n) && !active.contains(n)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isModifierName(String n) {
        switch (n) {
            case "C":
            case "CTRL":
            case "CONTROL":
            case "LEFT_CTRL":
            case "RIGHT_CTRL":
            case "SHIFT":
            case "LEFT_SHIFT":
            case "RIGHT_SHIFT":
            case "ALT":
            case "LEFT_ALT":
            case "RIGHT_ALT":
                return true;
            default:
                return false;
        }
    }

    /** 命中上报：走 ui_event 通道，服务端按 KeyConfig 规则执行命令/脚本。 */
    private static void reportCombo(String combo) {
        String sessionId = MessageDispatcher.anySessionId();
        if (sessionId == null) {
            return;
        }
        ClientControllerLegacy.sendUiEvent(sessionId, "keyconfig",
                UiEvent.Trigger.KEY, "keyconfig:" + combo);
    }

    //
    // WorldTexture / HeadTag：转世界面板页
    //

    /** 两类规则转成 display=world 的标准页面，注册进调度器页面库复用渲染管线。 */
    private static void registerWorldPages(MessageDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        for (Map.Entry<String, String> rule : rulesOf("WorldTexture").entrySet()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(rule.getValue());
                if (ir == null) {
                    continue;
                }
                dispatcher.registerVisualPage(worldTextureToPage(rule.getKey(), ir));
            } catch (Exception ignored) {
                // 单条规则坏了跳过，别的还活着
            }
        }
        for (Map.Entry<String, String> rule : rulesOf("HeadTag").entrySet()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(rule.getValue());
                if (ir == null) {
                    continue;
                }
                dispatcher.registerVisualPage(headTagToPage(rule.getKey(), ir));
            } catch (Exception ignored) {
                // 同上
            }
        }
    }

    /** WorldTexture 规则 → 世界模式页面：texture 键转标准 image 元素。 */
    static Page worldTextureToPage(String id, Map<String, Object> ir) {
        Map<String, Object> page = new LinkedHashMap<>(ir);
        Object tex = page.remove("texture");
        if (tex != null) {
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("type", "image");
            // 规则的 width/height 是贴图尺寸，拷进元素 hologram 让世界路照尺寸画；
            // x/y/z 是方块坐标，投屏空间里没意义，不跟（元素默认居中）
            Map<String, Object> holo = new LinkedHashMap<>();
            for (String k : new String[]{"width", "height"}) {
                if (page.containsKey(k)) {
                    holo.put(k, page.get(k));
                }
            }
            img.put("hologram", holo);
            if (tex instanceof String) {
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("src", tex);
                img.put("image", src);
            } else if (tex instanceof Map) {
                img.put("image", new LinkedHashMap<Object, Object>((Map<?, ?>) tex));
            }
            page.put("背景", img);
        }
        page.put("display", "world");
        return PageSchema.build("vt_" + id, page);
    }

    /** HeadTag 规则 → 世界模式页面：匹配键存档后剥掉，剩的就是普通页面。 */
    static Page headTagToPage(String id, Map<String, Object> ir) {
        Map<String, Object> page = new LinkedHashMap<>(ir);
        java.util.LinkedHashMap<String, Object> match = new java.util.LinkedHashMap<>();
        for (String k : new String[]{"entity", "name", "distance", "y"}) {
            if (page.containsKey(k)) {
                match.put(k, page.remove(k));
            }
        }
        page.put("display", "world");
        String pageId = "ht_" + id;
        headTagMatches.put(pageId, match);
        return PageSchema.build(pageId, page);
    }
}
