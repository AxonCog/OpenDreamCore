package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.Ready;
import com.opendreamcore.protocol.message.ReadyAck;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界编辑器操作快照。
 */
    final class WorldEditEntry {
        final String id;
        final boolean exists;                 // 该时间点元素是否存在
        final Map<String, Object> props;      // 该时间点元素完整 props 快照（含 hologram 位置）
        final Map<String, String> actions;    // 该时间点元素动作脚本快照（click/hover/input）
        final Map<String, String> pending;    // 该时间点 worldEditProps[id] 副本
        final boolean dirty;                  // 该时间点是否在 worldEditDirty
        final String parentId;                // 该时间点父元素 id（null = 顶层；还原插入用）
        final int index;                      // 该时间点在父 children 列表中的下标（-1 = 未知）

        WorldEditEntry(String id, boolean exists, Map<String, Object> props, Map<String, String> actions,
                       Map<String, String> pending, boolean dirty, String parentId, int index) {
            this.id = id;
            this.exists = exists;
            this.props = props;
            this.actions = actions;
            this.pending = pending;
            this.dirty = dirty;
            this.parentId = parentId;
            this.index = index;
        }
}
