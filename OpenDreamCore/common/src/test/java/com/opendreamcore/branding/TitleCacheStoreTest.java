package com.opendreamcore.branding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务端标题磁盘缓存内容存储（哈希去重 / LRU 淘汰 / 快照恢复）。
 */
class TitleCacheStoreTest {

    private TitleConfig cfg(String text) {
        TitleConfig c = new TitleConfig();
        c.text = text;
        return c;
    }

    @Test
    void hashChangesWithContent() {
        assertNotEquals(TitleCacheStore.hashOf(cfg("A")), TitleCacheStore.hashOf(cfg("B")));
        assertEquals(TitleCacheStore.hashOf(cfg("A")), TitleCacheStore.hashOf(cfg("A")));
        // titles 变化也影响哈希
        TitleConfig withTitles = new TitleConfig();
        withTitles.titles = List.of("甲", "乙");
        assertNotEquals(TitleCacheStore.hashOf(withTitles), TitleCacheStore.hashOf(new TitleConfig()));
    }

    @Test
    void putDedupesByHash() {
        TitleCacheStore store = new TitleCacheStore();
        assertTrue(store.put("s1", cfg("标题")));   // 新条目
        assertFalse(store.put("s1", cfg("标题")));  // 哈希相同跳过
        assertTrue(store.put("s1", cfg("新标题"))); // 内容变化更新
        assertEquals(1, store.size());
    }

    @Test
    void getRefreshesLruOrder() {
        TitleCacheStore store = new TitleCacheStore();
        for (int i = 0; i < 5; i++) {
            store.put("k" + i, cfg("t" + i));
        }
        store.get("k0"); // k0 刷新为最新
        List<String> order = store.keysByLruOrder();
        assertEquals("k0", order.get(order.size() - 1));
        assertEquals("k1", order.get(0)); // 最旧变为 k1
    }

    @Test
    void evictsEldestBeyondCapacity() {
        TitleCacheStore store = new TitleCacheStore();
        for (int i = 0; i < TitleCacheStore.MAX_ENTRIES + 5; i++) {
            store.put("k" + i, cfg("t" + i));
        }
        assertEquals(TitleCacheStore.MAX_ENTRIES, store.size());
        assertNull(store.get("k0"));                       // 最旧被淘汰
        assertNotNull(store.get("k" + (TitleCacheStore.MAX_ENTRIES + 4))); // 最新仍在
    }

    @Test
    void removeDeletesEntry() {
        TitleCacheStore store = new TitleCacheStore();
        store.put("srv", cfg("x"));
        assertTrue(store.remove("srv"));
        assertFalse(store.remove("srv")); // 二次删除无效果
        assertNull(store.get("srv"));
    }

    @Test
    void snapshotRestoreRoundTrip() {
        TitleCacheStore a = new TitleCacheStore();
        a.put("alpha", cfg("服一"));
        a.put("beta", cfg("服二"));
        Map<String, TitleCacheStore.Entry> snap = a.snapshot();

        TitleCacheStore b = new TitleCacheStore();
        b.restore(snap);
        assertEquals(2, b.size());
        assertEquals(TitleCacheStore.hashOf(cfg("服一")), b.get("alpha").hash);
        assertEquals("服二", b.get("beta").config.text);
        // restore 后哈希去重依然生效
        assertFalse(b.put("alpha", cfg("服一")));
    }

    @Test
    void restoreIgnoresBrokenEntries() {
        TitleCacheStore store = new TitleCacheStore();
        Map<String, TitleCacheStore.Entry> bad = new java.util.HashMap<>();
        TitleCacheStore.Entry e1 = new TitleCacheStore.Entry();
        e1.config = null; // 非法条目
        bad.put("broken", e1);
        TitleCacheStore.Entry e2 = new TitleCacheStore.Entry();
        e2.config = cfg("ok");
        bad.put("ok", e2);
        store.restore(bad);
        assertEquals(1, store.size());
        assertNull(store.get("broken"));
        assertNotNull(store.get("ok"));
    }
}
