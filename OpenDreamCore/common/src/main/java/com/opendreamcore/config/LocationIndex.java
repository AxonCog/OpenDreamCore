package com.opendreamcore.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * YAML 文本 → 键的行列位置索引。
 *
 * 存在的理由：解析出的 Map 会丢掉原文位置，布局值写错（"12 px"、1e3）要能报出
 * 第几行第几列，否则用户只能拿整份配置去猜。这里做一遍与 YamlParser 产出等价的
 * 轻量扫描：只认「行首缩进 + key:」的映射层级，值原样存字符串（够定位与回查）。
 *
 * 不是完整 YAML 实现：流程式写法（{a: 1}）与多行标量不索引进来，查不到就报
 * 「未能定位」，绝不因此改变解析结果。
 */
public final class LocationIndex {

    /** 一次键命中：层级键链 + 行列（1 起）。 */
    public static final class Hit {
        private final List<String> path;
        private final int line;
        private final int column;

        Hit(List<String> path, int line, int column) {
            this.path = path;
            this.line = line;
            this.column = column;
        }

        public List<String> path() {
            return path;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }
    }

    private static final class Entry {
        final List<String> path;
        final String key;
        final int line;
        final int column;

        Entry(List<String> path, String key, int line, int column) {
            this.path = path;
            this.key = key;
            this.line = line;
            this.column = column;
        }
    }

    private final List<Entry> entries;

    private LocationIndex(List<Entry> entries) {
        this.entries = entries;
    }

    public static LocationIndex of(String yamlText) {
        List<Entry> out = new ArrayList<>();
        if (yamlText == null) {
            return new LocationIndex(out);
        }
        String[] lines = yamlText.replace("\r\n", "\n").split("\n", -1);
        boolean inBlockScalar = false;
        int blockScalarIndent = -1;
        List<Integer> stackDepths = new ArrayList<>();
        List<String> stackKeys = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = stripComment(raw);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (inBlockScalar) {
                int indent = indentOf(line);
                if (indent > blockScalarIndent) {
                    continue; // 块标量内部，整段跳过
                }
                inBlockScalar = false;
            }
            int colon = keyColon(trimmed);
            if (colon < 0) {
                continue; // 列表项（"- "）与续行不索引
            }
            int indent = indentOf(line);
            String key = unquote(trimmed.substring(0, colon).trim());
            String value = trimmed.substring(colon + 1).trim();
            // 键链栈：弹出所有 ≥ 本行缩进的祖先，栈里剩下的就是父链
            while (!stackDepths.isEmpty() && stackDepths.get(stackDepths.size() - 1) >= indent) {
                stackDepths.remove(stackDepths.size() - 1);
                stackKeys.remove(stackKeys.size() - 1);
            }
            List<String> path = new ArrayList<>(stackKeys);
            path.add(key);
            stackDepths.add(indent);
            stackKeys.add(key);
            out.add(new Entry(new java.util.ArrayList<>(path), key, i + 1, line.indexOf(key) + 1));
            if (value.equals("|") || value.equals(">") || value.startsWith("|-") || value.startsWith(">-")
                    || value.equals("|+") || value.equals(">+")) {
                inBlockScalar = true;
                blockScalarIndent = indent;
            }
        }
        return new LocationIndex(out);
    }

    /** 块标量结束后由调用方继续扫描；此处只提供注释剥离。 */
    private static String stripComment(String line) {
        int q = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '#' && (i == 0 || line.charAt(i - 1) == ' ' || line.charAt(i - 1) == '\t')) {
                return line.substring(0, i);
            }
            if (q < 0 && (c == '\'' || c == '"')) {
                q = c;
            } else if (q > 0 && c == q) {
                q = -1;
            }
        }
        return line;
    }

    /** 引号外第一个冒号（键的分界），找不到返回 -1。 */
    private static int keyColon(String trimmed) {
        char quote = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (quote == 0 && (c == '\'' || c == '"')) {
                quote = c;
            } else if (quote != 0 && c == quote) {
                quote = 0;
            } else if (c == ':' && quote == 0) {
                if (i + 1 >= trimmed.length() || trimmed.charAt(i + 1) == ' ') {
                    return i;
                }
                return -1; // "http://..." 这类值里没有键分隔
            }
        }
        return -1;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
            n++;
        }
        return n;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.charAt(0) == '"' && s.endsWith("\""))
                || (s.charAt(0) == '\'' && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 按 Map 层级键链查位置。path 是构建期已知的键链（如 ["父id","子id","x"]）。
     * 优先全链相等；查不到再退而求其次用键链后缀（import 展开后的页面与原文有层级差）；
     * 再不行退到末键命中（同名多处取第一个）；都查不到返回 null，调用方按「未能定位」处理。
     */
    public Hit locate(List<String> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        Entry lastKeyHit = null;
        for (Entry e : entries) {
            if (!e.key.equals(path.get(path.size() - 1))) {
                continue;
            }
            if (e.path.equals(path)) {
                return new Hit(path, e.line, e.column);
            }
            if (endsWith(e.path, path) || endsWith(path, e.path)) {
                return new Hit(path, e.line, e.column);
            }
            if (lastKeyHit == null) {
                lastKeyHit = e;
            }
        }
        return lastKeyHit == null ? null : new Hit(path, lastKeyHit.line, lastKeyHit.column);
    }

    private static boolean endsWith(List<String> entryPath, List<String> path) {
        if (entryPath.size() < path.size()) {
            return false;
        }
        int off = entryPath.size() - path.size();
        for (int i = 0; i < path.size(); i++) {
            if (!entryPath.get(off + i).equals(path.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** 从已解析的 IR 递归生成键链（与 locate 同源，保证可查）。 */
    public static List<List<String>> allPaths(Map<String, Object> ir) {
        List<List<String>> out = new ArrayList<>();
        if (ir != null) {
            walk(ir, new ArrayList<>(), out);
        }
        return out;
    }

    private static void walk(Map<String, Object> map, List<String> prefix, List<List<String>> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.add(e.getKey());
            out.add(Collections.unmodifiableList(path));
            if (e.getValue() instanceof Map) {
                walk(asMap(e.getValue()), path, out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    /** 条目数（单测用）。 */
    public int size() {
        return entries.size();
    }
}
