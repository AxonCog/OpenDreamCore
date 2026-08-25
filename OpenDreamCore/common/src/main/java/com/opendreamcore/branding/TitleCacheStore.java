package com.opendreamcore.branding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端标题磁盘缓存的内容存储（纯逻辑，可单测）。
 *
 * 标题缓存按服务器分条目：
 * - 每个服务器一个条目（key = 服务器地址串），值 = 配置哈希 + 标题配置；
 * - put 时哈希去重：与已存哈希相同则跳过（返回 false），不同才更新；
 * - 容量上限 {@value #MAX_ENTRIES}，超出按 LRU（访问序）淘汰最旧条目。
 *
 * 持久化由客户端侧完成（Gson 序列化 {@link #snapshot()}/{@link #restore(Map)} 到
 * OpenDreamCore/cache/title_cache.json）；本类不做文件 IO 以保持可测性。
 */
public final class TitleCacheStore {

    /** 缓存条目上限（防多服漫游膨胀）。 */
    public static final int MAX_ENTRIES = 32;

    /** 单个服务器的缓存条目。 */
    public static final class Entry {
        public String hash = "";
        public TitleConfig config = new TitleConfig();
    }

    private final LinkedHashMap<String, Entry> entries =
            new LinkedHashMap<>(16, 0.75f, true); // accessOrder=true → get 也刷新 LRU

    /** 取条目（命中即刷新 LRU）。 */
    public synchronized Entry get(String key) {
        return key == null ? null : entries.get(key);
    }

    /**
     * 存入配置。哈希与已有相同返回 false（跳过写盘）；否则更新并返回 true。
     */
    public synchronized boolean put(String key, TitleConfig cfg) {
        if (key == null || cfg == null || cfg.sequence().isEmpty()) {
            return false;
        }
        String h = hashOf(cfg);
        Entry old = entries.get(key);
        if (old != null && h.equals(old.hash)) {
            return false;
        }
        Entry e = new Entry();
        e.hash = h;
        e.config = cfg;
        entries.put(key, e);
        while (entries.size() > MAX_ENTRIES) {
            String eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
        return true;
    }

    /** 最近使用的条目 key（accessOrder 末位）；空缓存返回 null。模组启动即应用最近标题用。 */
    public synchronized String latestKey() {
        String last = null;
        for (String k : entries.keySet()) {
            last = k;
        }
        return last;
    }

    /** 删除条目（服务端 RESET 时）。 */
    public synchronized boolean remove(String key) {
        return key != null && entries.remove(key) != null;
    }

    public synchronized int size() {
        return entries.size();
    }

    /** 快照（供持久化序列化；深拷贝键集，条目共享只读使用）。 */
    public synchronized Map<String, Entry> snapshot() {
        Map<String, Entry> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry ne = new Entry();
            ne.hash = e.getValue().hash;
            ne.config = e.getValue().config;
            copy.put(e.getKey(), ne);
        }
        return copy;
    }

    /** 从持久化数据恢复（整体替换）。 */
    public synchronized void restore(Map<String, Entry> data) {
        entries.clear();
        if (data == null) {
            return;
        }
        int n = 0;
        for (Map.Entry<String, Entry> e : data.entrySet()) {
            if (e.getKey() == null || e.getValue() == null
                    || e.getValue().config == null) {
                continue;
            }
            if (++n > MAX_ENTRIES) {
                break;
            }
            entries.put(e.getKey(), e.getValue());
        }
    }

    /**
     * 配置规范化串的 SHA-256（DreamCore 哈希比对语义）：
     * 仅取影响显示的字段，字段顺序固定，空白不归一（内容变化即哈希变化）。
     */
    public static String hashOf(TitleConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.typewriter).append('|');
        sb.append(cfg.random).append('|');
        sb.append(cfg.speed).append('|');
        sb.append(cfg.interval).append('|');
        sb.append(cfg.holdMs).append('|');
        sb.append(cfg.loop).append('|');
        sb.append(cfg.text == null ? "" : cfg.text).append('|');
        List<String> seq = cfg.sequence();
        for (String s : seq) {
            sb.append(s).append('\n');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 测试辅助：当前全部键（LRU 序，最旧在前）。 */
    synchronized List<String> keysByLruOrder() {
        return new ArrayList<>(entries.keySet());
    }
}
