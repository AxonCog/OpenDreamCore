package com.opendreamcore.client;

import com.opendreamcore.protocol.message.BossBarSync;
import com.opendreamcore.protocol.message.ItemTipSync;
import com.opendreamcore.protocol.message.NameTagSync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界 UI 存储（P3-14）：服务端下发的 Boss 血条 / 头顶名牌 / 物品提示。
 * 渲染：BossBar 顶部叠放（HUD）；NameTag 世界内 billboard（实体上方）；ItemTip 屏幕中央浮窗。
 */
public final class WorldUiStore {

    /** Boss 条数据。 */
    public record BossBar(String id, String text, double progress, int color) {
    }

    /** 名牌数据。 */
    public record NameTag(int entityId, String text, int color) {
    }

    private final Map<String, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<Integer, NameTag> nameTags = new ConcurrentHashMap<>();

    // 物品提示（单条，新提示覆盖旧的）
    private String tipItemId;
    private int tipCount;
    private long tipUntil;

    public void handleBossBar(BossBarSync sync) {
        switch (sync.action()) {
            case REMOVE -> bossBars.remove(sync.id());
            default -> bossBars.put(sync.id(), new BossBar(sync.id(), sync.text(),
                    Math.max(0, Math.min(100, sync.progress())),
                    UiStyle.color(sync.color(), 0xFFE53935)));
        }
    }

    public void handleNameTag(NameTagSync sync) {
        if (sync.text().isEmpty()) {
            nameTags.remove(sync.entityId());
        } else {
            nameTags.put(sync.entityId(), new NameTag(sync.entityId(), sync.text(),
                    UiStyle.color(sync.color(), 0xFFFFFFFF)));
        }
    }

    public void handleItemTip(ItemTipSync sync) {
        tipItemId = sync.itemId();
        tipCount = sync.count();
        tipUntil = System.currentTimeMillis() + sync.durationMs();
    }

    /** 全部 Boss 条（按 id 排序，稳定叠放顺序）。 */
    public List<BossBar> bossBars() {
        List<BossBar> out = new ArrayList<>(bossBars.values());
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    /** 全部名牌（按实体 id）。 */
    public List<NameTag> nameTags() {
        return new ArrayList<>(nameTags.values());
    }

    public NameTag nameTag(int entityId) {
        return nameTags.get(entityId);
    }

    /** 物品提示剩余透明度 0..1（无则 -1）；最后 30% 时间淡出。 */
    public double tipAlpha() {
        if (tipItemId == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        if (now >= tipUntil) {
            return -1;
        }
        double remain = (tipUntil - now) / (double) 2000;
        return Math.min(1.0, remain / 0.3);
    }

    public String tipItemId() {
        return tipItemId;
    }

    public int tipCount() {
        return tipCount;
    }

    public void clear() {
        bossBars.clear();
        nameTags.clear();
        tipItemId = null;
    }
}
