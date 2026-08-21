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
    final class WorldEditOp {
        final String label;
        final String key;
        final List<WorldEditEntry> entries;
        final Map<String, Object> worldOptions;   // 页面 options 快照（背景/淡出等 world 段；null = 纯元素操作）

        WorldEditOp(String label, String key, List<WorldEditEntry> entries) {
            this(label, key, entries, null);
        }

        WorldEditOp(String label, String key, List<WorldEditEntry> entries, Map<String, Object> worldOptions) {
            this.label = label;
            this.key = key;
            this.entries = entries;
            this.worldOptions = worldOptions;
        }
}
