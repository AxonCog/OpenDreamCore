package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.adapter.Adapter;
import com.opendreamcore.adapter.AdapterChain;
import com.opendreamcore.adapter.dreamcore.DreamCoreSystems;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DragonCore 原生系统配置适配器。
 *
 * 文件认领统一使用 DreamCoreSystems.systemFor，服务端外部目录和公共
 * AdapterChain 入口使用同一份翻译。Blood 文件中的血条与名字组件分别
 * 产出 Blood 和 HeadTag，保留两个系统的独立规则。
 */
public final class DragonCoreAdapter implements Adapter, AdapterChain.Prioritized {


    @Override
    public String name() {
        return "dragoncore";
    }

    @Override
    public int priority() {
        return 0; // 内置适配器优先级最小，永远最先匹配
    }

    @Override
    public boolean accepts(String fileName, Map<String, Object> rootIr) {
        return rootIr != null && !DreamCoreSystems.systemFor(fileName).isEmpty();
    }

    @Override
    public String rewriteScript(String script) {
        return DragonCoreScriptRewrite.rewrite(script);
    }

    @Override
    public Map<String, Map<String, Map<String, Object>>> translate(
            String fileName, Map<String, Object> rootIr) {
        String system = DreamCoreSystems.systemFor(fileName);
        if (system.isEmpty() || rootIr == null) {
            return java.util.Collections.emptyMap();
        }
        // 文件路由只在 DreamCoreSystems 中定义，这里选择对应的规则翻译器。
        Map<String, Map<String, Object>> rules = switch (system) {
            case "KeyConfig" -> DreamCoreSystems.keyConfig(rootIr);
            case "ItemIcon" -> DreamCoreSystems.itemIcon(rootIr);
            case "WorldTexture" -> DreamCoreSystems.worldTexture(rootIr);
            case "ArmorLayer" -> DreamCoreSystems.armorLayer(rootIr);
            case "FontConfig" -> DreamCoreSystems.fontConfig(rootIr);
            case "SlotConfig" -> DreamCoreSystems.slotConfig(rootIr);
            case "Blood" -> DreamCoreSystems.blood(rootIr);
            case "HeadTag" -> DreamCoreSystems.headTag(rootIr);
            default -> java.util.Collections.emptyMap();
        };
        Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<>();
        if (!rules.isEmpty()) {
            out.put(system, rules);
        }
        if ("Blood".equals(system)) {
            Map<String, Map<String, Object>> nameRules = DreamCoreSystems.headTag(rootIr);
            if (!nameRules.isEmpty()) {
                out.put("HeadTag", nameRules);
            }
        }
        return out;
    }

}