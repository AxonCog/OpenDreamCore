package com.opendreamcore.visual;

import java.util.List;
import java.util.Map;

/**
 * IR → YAML 文本序列化（服务端下发给客户端的规则原文形态）。
 *
 * 服务端权威：规则以**文本**随 VisualRulesSync 下发，匹配与渲染在客户端。所以序列化
 * 必须无损——曾经把「表列表」（相机的 keyframes 就是这种）用 {@code String.valueOf(map)}
 * 压成 {@code {t=0.0, pos=[100, 72, 0]}} 这种行，客户端解析出来是空列表（0.1.2.2 实测坑）。
 *
 * 形态约定：
 * <ul>
 *   <li>标量列表：flow 序列 {@code [a, b]}。<b>曾经用「每行重复同一个 key」</b>（{@code key: a} +
 *       {@code key: b}）——那根本不是合法 YAML，客户端解析器报 duplicate key 整条规则丢弃，
 *       0.1.2.2 修（transition/interrupt/shake 这类两元素以上列表全中招）。</li>
 *   <li>表列表：真块序列（{@code key:} + {@code - 首键: 值}），项内后续键缩进对齐；</li>
 *   <li>表里再嵌的标量列表：同 flow 序列。</li>
 * </ul>
 *
 * JDK8 字节码兼容（common-j8 门禁）：不用 {@code String.repeat}、不用 {@code List.copyOf}。
 */
public final class VisualYaml {

    private VisualYaml() {
    }

    /** 序列化一条规则（顶层是字段表）。 */
    public static String dump(Map<String, Object> ir) {
        StringBuilder sb = new StringBuilder();
        dumpMap(ir, sb, 0, false);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void dumpMap(Map<String, Object> ir, StringBuilder sb, int depth, boolean asItem) {
        String pad = repeat("  ", depth);
        boolean first = true;
        for (Map.Entry<String, Object> e : ir.entrySet()) {
            String head = asItem ? (first ? pad + "- " : pad + "  ") : pad;
            first = false;
            Object v = e.getValue();
            if (v instanceof Map<?, ?> child) {
                sb.append(head).append(e.getKey()).append(":\n");
                dumpMap((Map<String, Object>) child, sb, depth + 1, false);
            } else if (v instanceof List<?> list) {
                dumpList(e.getKey(), list, sb, depth, asItem, head, pad);
            } else if (v instanceof String s && s.indexOf('\n') >= 0) {
                // 多行值走块标量（|- 去尾换行）：双引号里的 \n 转义本仓解析器不认，
                // 而块标量是它认的形态（龙核/老壳配置里大量 | 与 |-）。
                sb.append(head).append(e.getKey()).append(": |-\n");
                String contentPad = repeat("  ", depth + 1);
                for (String line : s.split("\n", -1)) {
                    sb.append(contentPad).append(line).append('\n');
                }
            } else {
                sb.append(head).append(e.getKey()).append(": ").append(scalar(v)).append('\n');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void dumpList(String key, List<?> list, StringBuilder sb, int depth,
                                 boolean asItem, String head, String pad) {
        if (list.isEmpty()) {
            sb.append(head).append(key).append(": []\n");
            return;
        }
        if (isMapList(list)) {
            sb.append(head).append(key).append(":\n");
            for (Object item : list) {
                dumpMap((Map<String, Object>) item, sb, depth + 1, true);
            }
            return;
        }
        if (asItem || list.size() >= 1) {
            // 一律 flow：[a, b]。块序列（每行重复 key）是重复键，解析器直接报错；
            // 单元素也不能走行形式（`key: only` 读回来是字符串，列表性就丢了）
            sb.append(head).append(key).append(": ").append(flow(list)).append('\n');
            return;
        }
        sb.append(head).append(key).append(": []\n");
    }

    private static boolean isMapList(List<?> list) {
        for (Object item : list) {
            if (!(item instanceof Map)) {
                return false;
            }
        }
        return !list.isEmpty();
    }

    private static String flow(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(flowScalar(list.get(i)));
        }
        return sb.append(']').toString();
    }

    /** flow 里的额外引号规则：带逗号的值会被当成多个项，必须引起来。 */
    /** 行内标量：行内上下文里逗号/方括号有语法含义，出现就加引号。 */
    private static String flowScalar(Object v) {
        String s = scalar(v);
        if (s.startsWith("\"")) {
            return s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('[') >= 0 || s.indexOf(']') >= 0) {
            return quoted(s);
        }
        return s;
    }

    /**
     * 标量序列化。
     *
     * <p>以前只给含 {@code :}/{@code #}/{@code {}/{@code [} 的值加引号，结果 {@code entity: *}
     * 裸奔——{@code *} 是别名起始符，客户端读不进来，整条规则作废（2026-10 名牌不显示就是它）。
     * 现在按 YAML 规则来：首尾空白、指示符开头、含 {@code ": "}、会被读成布尔/数字的，一律加双引号。
     */
    private static String scalar(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        String s = String.valueOf(v);
        return needsQuote(s) ? quoted(s) : s;
    }

    /** 需要加引号判定（宁可多引：多引不会错，少引会炸）。 */
    private static boolean needsQuote(String s) {
        if (s.isEmpty()) {
            return true;
        }
        if (!s.equals(s.trim())) {
            return true;
        }
        // YAML 指示符开头（- 单列：多引无害）
        if ("*&!|>%@`{}[],#?:'\"-".indexOf(s.charAt(0)) >= 0) {
            return true;
        }
        if (s.contains(": ") || s.endsWith(":") || s.contains(" #")) {
            return true;
        }
        return looksLikeOtherType(s);
    }

    /** 会被 YAML 读成布尔/数字/null 的字面串（类型错位要引号）。 */
    private static boolean looksLikeOtherType(String s) {
        String t = s.toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(t) || "false".equals(t) || "yes".equals(t) || "no".equals(t)
                || "on".equals(t) || "off".equals(t) || "null".equals(t) || "~".equals(t)) {
            return true;
        }
        if (s.matches("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")) {
            return true;
        }
        return s.matches("[-+]?0[xXbBoO][0-9a-fA-F_]+");
    }

    /** 双引号标量：引号/反斜杠/换行/制表按 YAML 规则转义（无损）。 */
    private static String quoted(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Java11 才有 String.repeat，这里手动拼（common 要编成 JDK8 字节码）。 */
    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
