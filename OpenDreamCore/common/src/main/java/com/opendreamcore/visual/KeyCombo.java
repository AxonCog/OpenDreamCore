package com.opendreamcore.visual;

import com.opendreamcore.util.J8;

import java.util.HashSet;
import java.util.Set;

/**
 * 按键组合。
 *
 * 组合串用 + 连接键名，修饰键与普通键一视同仁：
 *   "C+LEFT_SHIFT+左键"  →  {C, LEFT_SHIFT, 左键}
 *
 * 命中判定：玩家当前按下集合 ⊇ 组合全集。冷却窗口由持有方（服务端执行器）
 * 自行计时，本类只做纯匹配，方便单测。
 */
public final class KeyCombo {

    private final String raw;
    private final Set<String> keys;

    public KeyCombo(String combo) {
        this.raw = combo == null ? "" : combo.trim();
        Set<String> out = new HashSet<>();
        for (String k : this.raw.split("\\+")) {
            if (!J8.isBlank(k)) {
                out.add(k.trim());
            }
        }
        this.keys = J8.setCopy(out);
    }

    /** 组合是否被当前按下集合完全覆盖（按下集合 ⊇ 组合全集）。 */
    public boolean matches(Set<String> pressed) {
        return pressed != null && pressed.containsAll(keys);
    }

    /** 是否为空组合（无意义，调用方可跳过注册）。 */
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /**
     * 组合串规范化：修饰键归一成规范组（CTRL/SHIFT/ALT）+ 固定排序 + '+' 连接，主键保持原样。
     * 配置层怎么写（Ctrl+左键 / C+LEFT_SHIFT+左键 / ctrl shift 左键）在这里收敛成同一个串，
     * 客户端上报与服务端装载共用这一个函数，两头拼不岔。
     *   返回示例："Ctrl+Shift+左键" → "CTRL+SHIFT+左键"；"R" → "R"
     */
    public static String canonical(String combo) {
        if (combo == null) {
            return "";
        }
        String[] parts = combo.trim().split("[+\\s]+");
        java.util.Set<String> mods = new HashSet<>();
        String primary = null;
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            String g = modifierGroup(p);
            if (g != null) {
                mods.add(g);
            } else if (primary == null) {
                primary = p;
            } else {
                primary = primary + "+" + p; // 多主键罕见，原样拼上保底
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String m : new String[]{"CTRL", "SHIFT", "ALT"}) {
            if (mods.contains(m)) {
                sb.append(m).append('+');
            }
        }
        if (primary != null) {
            sb.append(primary);
        } else if (sb.length() > 0) {
            sb.setLength(sb.length() - 1); // 只有修饰键没有主键：去掉尾巴的 '+'
        }
        return sb.toString();
    }

    /** 单键名 → 修饰键规范组名（CTRL/SHIFT/ALT）；非修饰键 null。别名全收：驼峰、全大写、左右之分。 */
    public static String modifierGroup(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "C":
            case "CTRL":
            case "CONTROL":
            case "LEFT_CTRL":
            case "RIGHT_CTRL":
            case "LEFT_CONTROL":
            case "RIGHT_CONTROL":
                return "CTRL";
            case "SHIFT":
            case "LEFT_SHIFT":
            case "RIGHT_SHIFT":
                return "SHIFT";
            case "ALT":
            case "LEFT_ALT":
            case "RIGHT_ALT":
                return "ALT";
            default:
                return null;
        }
    }

    /** 全部组成键（只读）。 */
    public Set<String> keys() {
        return keys;
    }

    @Override
    public String toString() {
        return raw;
    }
}
