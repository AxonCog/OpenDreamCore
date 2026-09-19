package com.opendreamcore.client.visual;

import com.opendreamcore.protocol.message.VisualRulesSync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端视觉规则仓库。
 *
 * 收到服务端 visual_rules_sync 后，把各系统的规则 YAML 原文存到这里；
 * 渲染钩子（物品图标覆盖/世界贴图全息/头顶标签等）按需读取并解析。
 * 进出服务器时清空——规则跟随会话，不跨服残留。
 */
public final class ClientVisualStore {

    private static final ClientVisualStore INSTANCE = new ClientVisualStore();

    public static ClientVisualStore get() {
        return INSTANCE;
    }

    private ClientVisualStore() {
    }

    /** system → (规则id → 规则 YAML)。 */
    private volatile Map<String, Map<String, String>> bundles = Map.of();

    /** 整体替换规则集。 */
    public void apply(VisualRulesSync sync) {
        this.bundles = sync.toBundles();
    }

    /** 某系统的全部规则 YAML（无则空 map）。 */
    public Map<String, String> rulesOf(String system) {
        return bundles.getOrDefault(system, Map.of());
    }

    /** 是否有任何规则存在（快速短路用）。 */
    public boolean hasAny(String system) {
        return !rulesOf(system).isEmpty();
    }

    /** 清空（断线/退服）。 */
    public void clear() {
        bundles = Map.of();
    }
}
