package com.opendreamcore.client;

import java.util.List;
import java.util.Map;

/**
 * 世界编辑器操作快照（撤销/重做单元）。
 * C7：清理机械拆分残留——移除无关 imports、修正类体缩进。
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
