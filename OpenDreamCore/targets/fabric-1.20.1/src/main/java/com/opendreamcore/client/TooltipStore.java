package com.opendreamcore.client;

import com.opendreamcore.protocol.message.TooltipRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 tooltip 注册表（客户端缓存）：元素 id → 提示文本。
 * 渲染时服务端 tooltip 优先于 YAML 静态 tooltip。
 */
public final class TooltipStore {

    private final Map<String, String> tooltips = new ConcurrentHashMap<>();

    public void handleRegistry(TooltipRegistry registry) {
        tooltips.clear();
        for (TooltipRegistry.Entry entry : registry.entries()) {
            tooltips.put(entry.elementId(), entry.text());
        }
        ClientController.LOGGER.info("服务端 tooltip 已加载 {} 条", tooltips.size());
    }

    /** 取服务端 tooltip（无返回 null）。 */
    public String get(String elementId) {
        return tooltips.get(elementId);
    }
}
