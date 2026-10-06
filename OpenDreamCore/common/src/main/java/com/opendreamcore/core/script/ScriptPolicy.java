package com.opendreamcore.core.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 脚本策略：**默认全开**，只有你显式配黑名单时才拒（规范 §6.2「安全靠策略，不靠阉割」）。
 *
 * <p>配置来源：{@code config/script-policy.yml}，键：{@code java_interop} / {@code file_system} /
 * {@code network} / {@code deny_class_patterns}（正则列表）。文件不存在 = 全开。
 */
public final class ScriptPolicy {

    /** 全开（默认）。 */
    public static final ScriptPolicy OPEN = new ScriptPolicy(true, true, true,
            Collections.<Pattern>emptyList());

    private final boolean javaInterop;
    private final boolean fileSystem;
    private final boolean network;
    private final List<Pattern> denyClassPatterns;

    public ScriptPolicy(boolean javaInterop, boolean fileSystem, boolean network,
                        List<Pattern> denyClassPatterns) {
        this.javaInterop = javaInterop;
        this.fileSystem = fileSystem;
        this.network = network;
        this.denyClassPatterns = denyClassPatterns == null
                ? Collections.<Pattern>emptyList() : denyClassPatterns;
    }

    /** 从已解析的配置 map 构造；null / 缺键一律取默认（全开）。 */
    public static ScriptPolicy of(Map<String, Object> cfg) {
        if (cfg == null || cfg.isEmpty()) {
            return OPEN;
        }
        List<Pattern> deny = new ArrayList<Pattern>();
        Object raw = cfg.get("deny_class_patterns");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o == null) {
                    continue;
                }
                try {
                    deny.add(Pattern.compile(String.valueOf(o)));
                } catch (Throwable ignored) {
                    // 正则写坏就当没写（不要让一个坏正则把脚本系统整个拒掉）
                }
            }
        }
        return new ScriptPolicy(
                bool(cfg.get("java_interop"), true),
                bool(cfg.get("file_system"), true),
                bool(cfg.get("network"), true),
                Collections.unmodifiableList(deny));
    }

    public boolean allowJavaInterop() {
        return javaInterop;
    }

    public boolean allowFileSystem() {
        return fileSystem;
    }

    public boolean allowNetwork() {
        return network;
    }

    public List<Pattern> denyClassPatterns() {
        return denyClassPatterns;
    }

    /** 类名命中黑名单就抛；黑名单默认空 → 永不拒。 */
    public void denyCheck(String className) {
        if (className == null) {
            return;
        }
        for (Pattern p : denyClassPatterns) {
            if (p.matcher(className).matches()) {
                throw new ScriptDeniedException("类 " + className + " 被策略禁用（deny_class_patterns）");
            }
        }
    }

    private static boolean bool(Object v, boolean def) {
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return def;
        }
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)
                || "开".equals(s) || "是".equals(s);
    }
}
