package com.opendreamcore.visual;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义槽位。
 *
 * chest_slot 组件按名字引用槽位；服务端裁决放入/取出时用 {@link #allows}
 * 校验物品是否满足全部条件（条件直接平铺在规则上，没有容器包裹）。
 *
 * 条件键：lore / lore_contains / name_contains / id / nbt / permission /
 * level / custom(附属注册)；全部可选，写了才校验，全部满足才放行。
 */
public final class SlotRule {

    /** 附属可注册的自定义条件：输入玩家名与物品视图，返回是否通过。 */
    public interface CustomCondition {
        boolean test(String playerName, ItemView item);
    }

    private static final Map<String, CustomCondition> CUSTOM = new java.util.concurrent.ConcurrentHashMap<>();

    /** 附属注册自定义条件类型（同名覆盖）。 */
    public static void registerCustom(String key, CustomCondition condition) {
        CUSTOM.put(key, condition);
    }

    private final String id;
    private final MatchSpec spec;          // 物品侧条件：lore/lore_contains/name_contains/id/nbt/regex
    private final String permission;       // 玩家权限节点
    private final int level;               // 玩家等级要求（-1 = 不校验）
    private final String customKey;        // 附属自定义条件键
    private final boolean attribute;       // 属性插件兼容钩子透传
    private final boolean skin;            // 时装兼容钩子透传

    public SlotRule(String id, Map<String, Object> ir) {
        this.id = id;
        Object limit = ir.get("limit");     // 兼容旧写法：limit 内嵌块的条件并入顶层
        Map<String, Object> merged = new LinkedHashMap<>(ir);
        if (limit instanceof Map<?, ?> lm) {
            for (Map.Entry<?, ?> e : lm.entrySet()) {
                merged.putIfAbsent(String.valueOf(e.getKey()), e.getValue());
            }
        }
        this.spec = MatchSpec.parse(stripNonItemKeys(merged));
        this.permission = str(merged.get("permission"));
        this.level = merged.get("level") instanceof Number n ? n.intValue() : -1;
        this.customKey = str(merged.get("custom"));
        this.attribute = Boolean.TRUE.equals(merged.get("attribute"));
        this.skin = Boolean.TRUE.equals(merged.get("skin"));
    }

    /** 玩家权限节点要求（null = 不校验；服务端裁决收集用）。 */
    public String permission() {
        return permission;
    }

    /** 物品侧条件（供无玩家上下文的快速过滤）。 */
    public MatchSpec spec() {
        return spec;
    }

    public String id() {
        return id;
    }

    public boolean attributeHook() {
        return attribute;
    }

    public boolean skinHook() {
        return skin;
    }

    /**
     * 完整校验：物品条件 + 玩家权限/等级/自定义条件。
     *
     * playerView：玩家上下文：name/permission:xxx/level 键；无玩家场景传 null 跳过玩家侧校验
     */
    public boolean allows(ItemView item, Map<String, Object> playerView) {
        if (!spec.matches(item)) {
            return false;
        }
        if (playerView == null) {
            return true;
        }
        if (permission != null && !Boolean.TRUE.equals(playerView.get("permission:" + permission))) {
            return false;
        }
        if (level >= 0) {
            Object lv = playerView.get("level");
            if (!(lv instanceof Number n) || n.intValue() < level) {
                return false;
            }
        }
        if (customKey != null) {
            CustomCondition cond = CUSTOM.get(customKey);
            if (cond != null && !cond.test(playerName(playerView), item)) {
                return false;
            }
        }
        return true;
    }

    private static String playerName(Map<String, Object> playerView) {
        Object name = playerView.get("name");
        return name == null ? "" : String.valueOf(name);
    }

    /** 从 IR 中剥离玩家侧键，剩余的交给 MatchSpec（避免 permission/level 被当物品属性）。 */
    private static Map<String, Object> stripNonItemKeys(Map<String, Object> merged) {
        Map<String, Object> out = new LinkedHashMap<>(merged);
        out.remove("permission");
        out.remove("level");
        out.remove("custom");
        out.remove("attribute");
        out.remove("skin");
        out.remove("limit");
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
