package com.opendreamcore.core.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 索引里的一条记录。
 *
 * <p>行格式（TAB 分隔）：{@code KIND <TAB> key <TAB> 实现类名 <TAB> k=v <TAB> k=v ...}
 */
public final class ServiceEntry {

    private final ServiceKind kind;
    private final String key;
    private final String implClass;
    private final Map<String, String> attrs;

    public ServiceEntry(ServiceKind kind, String key, String implClass, Map<String, String> attrs) {
        this.kind = kind;
        this.key = key == null ? "" : key;
        this.implClass = implClass == null ? "" : implClass;
        this.attrs = attrs == null ? Collections.<String, String>emptyMap() : attrs;
    }

    public ServiceKind kind() {
        return kind;
    }

    /** 条目的键：SYSTEM=系统 id；METHOD=命名空间；CAPABILITY=能力接口全名。 */
    public String key() {
        return key;
    }

    /** 实现类名（METHOD 条目写成 {@code 全类名#方法名}）。 */
    public String implClass() {
        return implClass;
    }

    public String attr(String name) {
        return attrs.get(name);
    }

    public String attr(String name, String def) {
        String v = attrs.get(name);
        return v == null || v.isEmpty() ? def : v;
    }

    /** 布尔属性：只认 "true"（其余按 false），缺省用 def。 */
    public boolean boolAttr(String name, boolean def) {
        String v = attrs.get(name);
        if (v == null || v.isEmpty()) {
            return def;
        }
        return "true".equalsIgnoreCase(v.trim());
    }

    public Map<String, String> attrs() {
        return Collections.unmodifiableMap(attrs);
    }

    /** 解析一行；格式不合法返回 null（调用方记 WARN 并跳过，**启动绝不因此失败**）。 */
    public static ServiceEntry parseLine(String line) {
        if (line == null) {
            return null;
        }
        String[] cols = line.split("\t");
        if (cols.length < 3) {
            return null;
        }
        ServiceKind kind = ServiceKind.parse(cols[0]);
        if (kind == null) {
            return null;
        }
        String key = cols[1].trim();
        String impl = cols[2].trim();
        if (key.isEmpty() || impl.isEmpty()) {
            return null;
        }
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        for (int i = 3; i < cols.length; i++) {
            String c = cols[i];
            if (c.isEmpty()) {
                continue;
            }
            int eq = c.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            attrs.put(c.substring(0, eq).trim(), c.substring(eq + 1).trim());
        }
        return new ServiceEntry(kind, key, impl, attrs);
    }

    @Override
    public String toString() {
        return kind + "{" + key + " → " + implClass + " " + attrs + "}";
    }
}
