package com.opendreamcore.plugin.server.visual;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 龙核（DragonCore）配置文件 → 九系统规则的翻译路由。
 *
 * 服务器上装着龙核时，plugins/DragonCore/ 下的原生配置不该作废：
 * 这里按文件名认领（Blood 就是我们的 HeadTag，其余同名直译），
 * 翻译本体全在 common 的 adapter/dreamcore/DreamCoreSystems（纯函数，
 * 有单测兜着），本类只负责「读文件 + 选翻译器」，装包和并入规则库
 * 由 VisualRuleManager 做——它要管去重前缀和版本号。
 *
 * 两个都要管的家伙：ItemTip.yml 是一整个界面页（走 Gui 适配器），
 * config.yml 是龙核自己的运行配置——都不归九系统，直接不认领。
 */
public final class DreamCoreBridge {

    private DreamCoreBridge() {
    }

    /**
     * 单个配置文件翻译成规则集。
     *
     * 返回：系统名 → (规则id → 规则IR)；文件不认领或解析失败都返回空 map
     *         （失败原因打日志，不打断别的文件——一份坏配置不该拖垮整个装载）。
     */
    public static Map<String, Map<String, Map<String, Object>>> translateFile(
            Path file, java.util.function.BiConsumer<String, Throwable> onError) {
        String system = com.opendreamcore.adapter.dreamcore.DreamCoreSystems
                .systemFor(file.getFileName().toString());
        if (system.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> root;
        try {
            // 老服 JVM 得用 readAllBytes 拼，readString 是 11 号才有的
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            // lenient：龙核配置里有重复键和 UTF-8 BOM，严格模式直接炸
            root = com.opendreamcore.config.YamlParser.lenient().parse(text);
        } catch (Exception e) {
            if (onError != null) {
                onError.accept(file.getFileName().toString(), e);
            }
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> rules;
        switch (system) {
            case "KeyConfig" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.keyConfig(root);
            case "ItemIcon" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.itemIcon(root);
            case "WorldTexture" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.worldTexture(root);
            case "ArmorLayer" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.armorLayer(root);
            case "FontConfig" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.fontConfig(root);
            case "SlotConfig" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.slotConfig(root);
            case "HeadTag" -> rules = com.opendreamcore.adapter.dreamcore.DreamCoreSystems.blood(root);
            default -> {
                return Collections.emptyMap();
            }
        }
        if (rules.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<>();
        out.put(system, rules);
        return out;
    }
}
