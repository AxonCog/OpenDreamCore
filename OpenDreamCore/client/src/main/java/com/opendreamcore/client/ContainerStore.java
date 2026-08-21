package com.opendreamcore.client;

import com.opendreamcore.protocol.message.ContainerSync;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器内容缓存：服务端 container_sync 推送的真实容器槽位数据。
 * chest_slot / container 组件按当前页面会话取数（无服务端时为空槽）。
 */
public final class ContainerStore {

    /** 槽位数据。 */
    public record SlotData(String itemId, int count) {
    }

    /** 一个容器会话的数据。 */
    public record ContainerData(String sessionId, String type, String title, int size,
                                Map<Integer, SlotData> slots,
                                String cursorItemId, int cursorCount) {

        public SlotData slot(int index) {
            return slots.get(index);
        }

        /** 光标物品 id（null = 无光标；槽位拖放时鼠标拾起的物品）。 */
        public String cursorItemId() {
            return cursorItemId;
        }

        public int cursorCount() {
            return cursorCount;
        }
    }

    private final Map<String, ContainerData> containers = new ConcurrentHashMap<>();

    /** 服务端容器同步到达：按会话存（槽位全量覆盖 + 光标）。 */
    public void handleSync(ContainerSync sync) {
        Map<Integer, SlotData> slots = new LinkedHashMap<>();
        for (ContainerSync.Slot entry : sync.slots()) {
            slots.put(entry.slot(), new SlotData(entry.itemId(), entry.count()));
        }
        containers.put(sync.sessionId(),
                new ContainerData(sync.sessionId(), sync.type(), sync.title(), sync.size(), slots,
                        sync.cursorItemId(), sync.cursorCount()));
    }

    /** 取容器数据（无则 null）。 */
    public ContainerData get(String sessionId) {
        return sessionId == null ? null : containers.get(sessionId);
    }

    /** 会话关闭/页面关闭时移除。 */
    public void remove(String sessionId) {
        if (sessionId != null) {
            containers.remove(sessionId);
        }
    }

    public void clear() {
        containers.clear();
    }
}
