package com.opendreamcore.client;

import com.opendreamcore.protocol.message.TooltipRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 tooltip 注册表（远古版）：元素 id → 条目。跟现代端 TooltipStore
 * 一个意思，Java8 语法。渲染时服务端注册的优先于 YAML 静态 tooltip。
 */
public final class LegacyTooltipStore {

    /** tooltip 条目（Java8 不用 record）。 */
    public static final class Entry {
        public final String elementId;
        public final String text;
        public final String color;
        public final String background;
        public final String border;
        public final double width;

        public Entry(String elementId, String text, String color, String background,
                     String border, double width) {
            this.elementId = elementId;
            this.text = text;
            this.color = color;
            this.background = background;
            this.border = border;
            this.width = width;
        }
    }

    private static final Map<String, Entry> TOOLTIPS = new ConcurrentHashMap<String, Entry>();

    private LegacyTooltipStore() {
    }

    /** 服务端注册表下发（MessageDispatcher 收到 tooltip_registry 时调用）。 */
    public static void handle(TooltipRegistry registry) {
        TOOLTIPS.clear();
        if (registry == null) {
            return;
        }
        for (TooltipRegistry.Entry e : registry.entries()) {
            TOOLTIPS.put(e.elementId(), new Entry(e.elementId(), e.text(), e.color(),
                    e.background(), e.border(), e.width()));
        }
    }

    /** 按元素 id 查；无返回 null。 */
    public static Entry get(String elementId) {
        return elementId == null ? null : TOOLTIPS.get(elementId);
    }
}