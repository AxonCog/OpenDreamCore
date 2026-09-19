package com.opendreamcore.plugin.server.visual;

import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KeyConfig 服务端执行器。
 *
 * 客户端上报按键组合（keyconfig:<combo>），这里按规则执行：
 *   形态一（极简）：命令列表——[op] 临时 OP 执行 / [Console] 控制台 / 无前缀玩家身份；
 *   形态二（进阶）：cooldown 冷却 + when 条件表达式 + fail_message 提示 + script 脚本。
 *
 * 全部在服务端执行：客户端只负责上报按键，零下发、零解析压力。
 */
public final class KeyConfigExecutor {

    /** 一条按键规则。 */
    public static final class ComboRule {
        public final String id;
        public final long cooldownMs;
        public final String whenExpr;      // null = 无条件
        public final String failMessage;   // null = 不提示
        public final List<String> commands;
        public final String script;        // null = 无脚本
        public final String ruleRun;       // run: 键（九系统通用脚本声明，null = 无）
        public final String eventName;     // event: 键（触发时发布的事件名，null = 无）

        ComboRule(String id, Map<String, Object> ir) {
            this.id = id;
            Object cd = ir.get("cooldown");
            double sec = cd instanceof Number n ? n.doubleValue() : 0;
            this.cooldownMs = (long) (sec * 1000);
            this.whenExpr = str(ir.get("when"));
            this.failMessage = str(ir.get("fail_message"));
            List<String> cmds = new ArrayList<>();
            Object commands = ir.get("commands");
            if (commands instanceof List<?> l) {
                for (Object o : l) {
                    cmds.add(String.valueOf(o));
                }
            } else if (commands != null) {
                cmds.add(String.valueOf(commands));
            }
            this.commands = new java.util.ArrayList<>(cmds);
            this.script = str(ir.get("script"));
            this.ruleRun = str(ir.get("run"));
            this.eventName = str(ir.get("event"));
        }
    }

    private static final class State {
        long lastFiredAt = Long.MIN_VALUE;
        boolean lastConditionPassed = true;
    }

    private final Map<String, ComboRule> rules = new ConcurrentHashMap<>();
    private final Map<String, State> states = new ConcurrentHashMap<>(); // playerId|combo → 状态

    /** 从规则 IR 装载（VisualRuleManager 转交）。两种形态都认：新形态 ID + keys 列表（每个键展开成一条规则，commands 承接命令，老配置写的 trigger 也认）；老形态键名即组合串。组合串一律 canonical 归一，与客户端上报一致。 */
    public void load(Map<String, Map<String, Object>> rulesIr) {
        rules.clear();
        for (Map.Entry<String, Map<String, Object>> e : rulesIr.entrySet()) {
            try {
                String id = e.getKey();
                Map<String, Object> ir = e.getValue();
                if (ir != null && ir.containsKey("keys")) {
                    // 新形态：一个 ID 绑多组键，每组键各自展开成一条规则
                    for (String keyStr : asStringList(ir.get("keys"))) {
                        String combo = com.opendreamcore.visual.KeyCombo.canonical(keyStr);
                        if (combo.isEmpty()) {
                            continue;
                        }
                        Map<String, Object> merged = new java.util.LinkedHashMap<>(ir);
                        merged.remove("keys");
                        if (!merged.containsKey("commands") && merged.containsKey("trigger")) {
                            merged.put("commands", merged.get("trigger"));
                            merged.remove("trigger");
                        }
                        rules.put(combo, new ComboRule(combo, merged));
                    }
                } else {
                    // 老形态：键名即组合串
                    String combo = com.opendreamcore.visual.KeyCombo.canonical(id);
                    if (!combo.isEmpty()) {
                        rules.put(combo, new ComboRule(combo, ir));
                    }
                }
            } catch (Exception ex) {
                log("规则装载失败 " + e.getKey() + ": " + ex);
            }
        }
    }

    private static java.util.List<String> asStringList(Object o) {
        java.util.List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        } else if (o != null) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    public int size() {
        return rules.size();
    }

    /**
     * 客户端上报按键组合触发。
     *
     * player：触发玩家
     * combo：组合串（客户端已按修饰键状态解析好，如 "C+LEFT_SHIFT+左键"）
     * 返回：true = 有规则命中并执行
     */
    public boolean feed(Player player, String combo) {
        if (combo == null || (combo).trim().isEmpty()) {
            return false;
        }
        ComboRule rule = rules.get(combo.trim());
        return rule != null && fire(player, rule, combo.trim());
    }

    private boolean fire(Player player, ComboRule rule, String comboText) {
        String stateKey = player.getUniqueId() + "|" + rule.id;
        State st = states.computeIfAbsent(stateKey, k -> new State());
        long now = System.currentTimeMillis();

        // 冷却窗口
        if (st.lastFiredAt != Long.MIN_VALUE && now - st.lastFiredAt < rule.cooldownMs) {
            return false;
        }
        // when 条件表达式：交给 DreamLang 求值（语言本身就是条件引擎）
        if (rule.whenExpr != null && !evalWhen(player, rule.whenExpr)) {
            if (st.lastConditionPassed && rule.failMessage != null) {
                player.sendMessage(rule.failMessage.replace("&", "§"));
            }
            st.lastConditionPassed = false;
            return false;
        }
        st.lastConditionPassed = true;
        st.lastFiredAt = now;

        // 执行命令（三种身份前缀）
        for (String cmd : rule.commands) {
            dispatch(player, cmd);
        }
        // 执行脚本：龙核方言先进适配器链过一遍（方法.xxx → xxx，老脚本直接能跑）
        String script = com.opendreamcore.adapter.AdapterChain.rewriteScript(rule.script);
        if (script != null && !script.trim().isEmpty()) {
            Scope scope = new Scope();
            scope.assignPlayer("name", player.getName());
            scope.assignVar("player_name", player.getName());
            try {
                DreamLang.execute(script, scope);
            } catch (Exception ex) {
                log("脚本执行失败 " + rule.id + ": " + ex);
            }
        }
        // run:（九系统通用脚本键，与 script 同语义——KeyConfig 形态二叫 script，
        // 规则里写 run 也认）；event:（规则触发时发布到 EventBus 的事件名）
        String ruleRun = com.opendreamcore.adapter.AdapterChain.rewriteScript(rule.ruleRun);
        if (ruleRun != null && !ruleRun.trim().isEmpty()) {
            Scope scope = new Scope();
            scope.assignPlayer("name", player.getName());
            scope.assignVar("player_name", player.getName());
            try {
                DreamLang.execute(ruleRun, scope);
            } catch (Exception ex) {
                log("run 脚本执行失败 " + rule.id + ": " + ex);
            }
        }
        if (rule.eventName != null && !rule.eventName.trim().isEmpty()) {
            try {
                com.opendreamcore.script.EventBus.publish(rule.eventName,
                        player.getName(), rule.id);
            } catch (Exception ex) {
                log("事件发布失败 " + rule.id + ": " + ex);
            }
        }
        return true;
    }

    private void dispatch(Player player, String raw) {
        // 龙核配置用 %player% 这类占位符，先填再执行
        String cmd = fillPlaceholders(player, raw.trim());
        // 前缀身份判断统一走小写比对——龙核配置里 [console]/[Console]/[CONSOLE] 都见过
        String lower = cmd.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("[op]")) {
            // 临时 OP：授权 → 玩家本人执行 → 撤销；try/finally 保证异常也恢复
            boolean was = player.isOp();
            player.setOp(true);
            try {
                Bukkit.dispatchCommand(player, cmd.substring(4).trim());
            } finally {
                player.setOp(was);
            }
        } else if (lower.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(9).trim());
        } else if (lower.startsWith("[player]")) {
            player.performCommand(cmd.substring(8).trim());
        } else {
            player.performCommand(cmd);
        }
    }

    /** 填充命令里的占位符（龙核惯用的 %player%/%world%，未识别的原样保留）。 */
    private static String fillPlaceholders(Player player, String cmd) {
        return cmd.replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName());
    }

    private static boolean evalWhen(Player player, String expr) {
        try {
            Scope scope = new Scope();
            scope.assignPlayer("name", player.getName());
            scope.assignVar("player_name", player.getName());
            Object r = DreamLang.evaluate(expr, scope);
            return Boolean.TRUE.equals(r) || "true".equals(String.valueOf(r));
        } catch (Exception e) {
            log("when 表达式求值失败: " + e);
            return true; // 表达式坏了放行——别因为配置错误把功能锁死
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static void log(String msg) {
        org.bukkit.Bukkit.getLogger().warning("[OpenDreamCore][keyconfig] " + msg);
    }
}
