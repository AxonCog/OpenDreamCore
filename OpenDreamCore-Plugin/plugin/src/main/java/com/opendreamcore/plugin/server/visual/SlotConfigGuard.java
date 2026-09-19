package com.opendreamcore.plugin.server.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.SlotRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SlotConfig 服务端裁决器（槽位看门狗）。
 *
 * SlotConfig 规则 = 给命名槽位立规矩（limit 旧写法兼容，自动并入顶层）。
 * chest_slot 元素用 slot_name 点名引用，玩家点槽位时服务端当场验货：
 *   钻石宝箱:
 *     slot_name: 钻石槽
 *     match:
 *       id: diamond
 *     fail_message: "&c只有钻石能放进这个槽位"
 *
 * 匹配规矩跟客户端一毛一样（同一套 MatchSpec）：平铺条件或 match 块都认，
 * 规则一句条件没写 = 敞开大门随便进出。规则 id 就是 chest_slot 里引用的名字，
 * 龙核翻译过来的 dc/ 前缀规则一样能点名。
 */
public final class SlotConfigGuard {

    /** 规则 id → 已解析规则（整表替换，读侧无锁）。 */
    private final Map<String, SlotRule> rules = new ConcurrentHashMap<>();
    /** 规则 id → 否决提示（null/缺失 = 静默否决）。 */
    private final Map<String, String> failMessages = new ConcurrentHashMap<>();

    /** 从规则 IR 装载（VisualRuleManager reload 转交）。 */
    public void load(Map<String, Map<String, Object>> rulesIr) {
        rules.clear();
        failMessages.clear();
        for (Map.Entry<String, Map<String, Object>> e : rulesIr.entrySet()) {
            try {
                rules.put(e.getKey(), new SlotRule(e.getKey(), e.getValue()));
                Object fm = e.getValue().get("fail_message");
                if (fm != null) {
                    failMessages.put(e.getKey(), String.valueOf(fm).replace('&', '\u00a7'));
                }
            } catch (Exception ex) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[OpenDreamCore][slotconfig] 规则装载失败 " + e.getKey() + ": " + ex);
            }
        }
    }

    /** 已装载规则数（对齐日志用）。 */
    public int size() {
        return rules.size();
    }

    /**
     * 槽位点击裁决。
     *
     * slotName：chest_slot 元素引用的槽位名（slot_name），null = 无约束放行
     * item：目标槽位物品视图（空槽 null）
     * playerView：玩家上下文（name/level/permission:xxx），null = 跳过玩家侧校验
     * 返回：null = 放行；非 null = 否决提示文案（静默否决传 ""）
     */
    public String check(String slotName, ItemView item, Map<String, Object> playerView) {
        if (slotName == null || slotName.trim().isEmpty()) {
            return null; // 没名字的槽位没有规则约束
        }
        SlotRule rule = rules.get(slotName.trim());
        if (rule == null) {
            return null; // 没配规则的名字 = 无约束
        }
        if (rule.allows(item, playerView)) {
            return null;
        }
        String msg = failMessages.get(slotName.trim());
        return msg == null ? "" : msg;
    }

    /** 某名字是否配了规则（预检用）。 */
    public boolean hasRule(String slotName) {
        return slotName != null && rules.containsKey(slotName.trim());
    }

    /**
     * 从槽位元素属性里提取裁决上下文并裁决。
     *
     * props：chest_slot 元素属性（slot_name 引用规则名）
     * item：目标槽位物品视图（空槽 null）
     * player：点击玩家
     * 返回：null = 放行；非 null = 否决提示（"" 静默）
     */
    public String checkProps(Map<?, ?> props, ItemView item, org.bukkit.entity.Player player) {
        Object name = props == null ? null : props.get("slot_name");
        if (!(name instanceof String) || ((String) name).trim().isEmpty()) {
            return null;
        }
        return check((String) name, item, playerView(player));
    }

    /** 玩家上下文：name/level/permission 组装（SlotRule.allows 的 playerView 契约）。 */
    Map<String, Object> playerView(org.bukkit.entity.Player player) {
        if (player == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", player.getName());
        view.put("level", player.getLevel());
        // 权限键按需登记：SlotRule 只查 "permission:节点" 这一个键，
        // 这里把规则里声明过的权限节点逐一查询（避免全量遍历权限附件）
        for (String perm : declaredPermissions()) {
            view.put("permission:" + perm, player.hasPermission(perm));
        }
        return view;
    }

    /** 规则里声明过的权限节点集合（load 时收集）。 */
    private List<String> declaredPermissions() {
        List<String> out = new ArrayList<>();
        for (SlotRule r : rules.values()) {
            String p = r.permission();
            if (p != null && !p.trim().isEmpty()) {
                out.add(p.trim());
            }
        }
        return out;
    }
}