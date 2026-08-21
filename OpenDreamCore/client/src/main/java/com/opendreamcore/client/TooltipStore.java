package com.opendreamcore.client;

import com.opendreamcore.protocol.message.TooltipRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 tooltip 注册表（客户端缓存）：元素 id → 条目（文本 + 可选样式 + 权限已由服务端过滤）。
 * 渲染时服务端 tooltip 优先于 YAML 静态 tooltip。
 */
public final class TooltipStore {

    private final Map<String, TooltipRegistry.Entry> tooltips = new ConcurrentHashMap<>();

    public void handleRegistry(TooltipRegistry registry) {
        tooltips.clear();
        for (TooltipRegistry.Entry entry : registry.entries()) {
            tooltips.put(entry.elementId(), entry);
        }
        ClientController.LOGGER.info("服务端 tooltip 已加载 {} 条", tooltips.size());
    }

    /** 取服务端 tooltip 条目（无返回 null）。 */
    public TooltipRegistry.Entry get(String elementId) {
        return tooltips.get(elementId);
    }
}
