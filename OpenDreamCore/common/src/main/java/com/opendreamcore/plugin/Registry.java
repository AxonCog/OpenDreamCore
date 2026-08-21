package com.opendreamcore.plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 通用注册表：万物皆插件的基础。
 * 支持运行时注册/注销（热插拔），id 唯一。
 */
public final class Registry<T> {

    private final Map<String, T> entries = new LinkedHashMap<>();

    public void register(String id, T value) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("注册 id 不能为空");
        }
        if (entries.containsKey(id)) {
            throw new IllegalStateException("重复注册: " + id);
        }
        entries.put(id, value);
    }

    /** 覆盖式注册（插件重载时用）。 */
    public void registerOrReplace(String id, T value) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("注册 id 不能为空");
        }
        entries.put(id, value);
    }

    public Optional<T> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public T require(String id) {
        T value = entries.get(id);
        if (value == null) {
            throw new IllegalStateException("未注册: " + id);
        }
        return value;
    }

    public boolean contains(String id) {
        return entries.containsKey(id);
    }

    public void unregister(String id) {
        entries.remove(id);
    }

    public void clear() {
        entries.clear();
    }

    public Map<String, T> snapshot() {
        return Map.copyOf(entries);
    }
}
