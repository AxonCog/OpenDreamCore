package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.adapter.Adapter;
import com.opendreamcore.adapter.AdapterChain;
import com.opendreamcore.adapter.dreamcore.DreamCoreSystems;
import com.opendreamcore.config.YamlParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 龙核方言适配器（住在 adapter/dragoncore/ 这个龙核专用文件夹里的那位）。
 *
 * 龙核（DragonCore）老服主的配置文件不能白扔：plugins/DragonCore/ 顶层的
 * 原生配置，由他认领并翻译成 ODC 九系统规则。链上第一位（priority=0），
 * 只要装了龙核就是他先说话。
 *
 * 认领表就在这（Blood.yml → HeadTag 这种"名字不一样但说的是一回事"的
 * 映射也在这）——龙核自己家的东西怎么进门，全写在这个文件夹里，
 * 跟别的方言互不打扰，这就是热拔插文件夹该有的自觉。
 *
 * 翻译本体全在 adapter/dreamcore/ 的 DreamCoreSystems 纯函数（有单测兜底），
 * 他老人家只干两件事：认领 + 路由，翻译的脏活累活不碰。
 *
 * 老路径 DreamCoreBridge（读文件+选翻译器）继续活着；他负责链式认领的新
 * 路径（extensions/systems 里丢的龙核方言文件一样能翻）——两条路殊途同归，
 * 最终都找 DreamCoreSystems 要答案。
 */
public final class DragonCoreAdapter implements Adapter, AdapterChain.Prioritized {

    /** 龙核方言文件名 → 九系统名（认领表；小写比对，别跟服主的大小写较劲）。 */
    private static final Map<String, String> FILE_ROUTES = buildRoutes();

    private static Map<String, String> buildRoutes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("keyconfig.yml", "KeyConfig");
        m.put("itemicon.yml", "ItemIcon");
        m.put("worldtexture.yml", "WorldTexture");
        m.put("armorlayer.yml", "ArmorLayer");
        m.put("fontconfig.yml", "FontConfig");
        m.put("slotconfig.yml", "SlotConfig");
        m.put("blood.yml", "HeadTag"); // 龙核的"血条"就是我们的"头顶标签"，名字对不上但心是通的
        return m;
    }

    @Override
    public String name() {
        return "dragoncore";
    }

    @Override
    public int priority() {
        return 0; // 内置方言抢沙发：装了龙核就该他先开口
    }

    @Override
    public boolean accepts(String fileName, Map<String, Object> rootIr) {
        return routeOf(fileName) != null;
    }

    @Override
    public String rewriteScript(String script) {
        return DragonCoreScriptRewrite.rewrite(script);
    }

    @Override
    public Map<String, Map<String, Map<String, Object>>> translate(
            String fileName, Map<String, Object> rootIr) {
        String system = routeOf(fileName);
        if (system == null) {
            return java.util.Collections.emptyMap();
        }
        // 翻译脏活全在 DreamCoreSystems（借了隔壁家的翻译机），这里只当路由
        Map<String, Map<String, Object>> rules = switch (system) {
            case "KeyConfig" -> DreamCoreSystems.keyConfig(rootIr);
            case "ItemIcon" -> DreamCoreSystems.itemIcon(rootIr);
            case "WorldTexture" -> DreamCoreSystems.worldTexture(rootIr);
            case "ArmorLayer" -> DreamCoreSystems.armorLayer(rootIr);
            case "FontConfig" -> DreamCoreSystems.fontConfig(rootIr);
            case "SlotConfig" -> DreamCoreSystems.slotConfig(rootIr);
            case "HeadTag" -> DreamCoreSystems.blood(rootIr);
            default -> java.util.Collections.emptyMap();
        };
        if (rules.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<>();
        out.put(system, rules);
        return out;
    }

    /** 文件名 → 系统名（小写比对；带目录的路径也只认尾巴，别拿路径吓唬人）。 */
    static String routeOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase().replace('\\', '/');
        int slash = lower.lastIndexOf('/');
        if (slash >= 0) {
            lower = lower.substring(slash + 1);
        }
        return FILE_ROUTES.get(lower);
    }

    /**
     * 便捷入口：龙核 yml 文本直接翻译（VisualRuleManager 并外部目录用，
     * 省得那条路还得自己先 parse 一遍）。
     */
    public static Map<String, Map<String, Map<String, Object>>> translateText(String fileName, String yamlText) {
        Map<String, Object> root;
        try {
            // lenient：龙核配置里重复键和 BOM 都是家常便饭，严格模式直接当场暴毙
            root = YamlParser.lenient().parse(yamlText);
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
        if (root == null) {
            return java.util.Collections.emptyMap();
        }
        return new DragonCoreAdapter().translate(fileName, root);
    }

    /** 龙核方言的认领名单（列表/文档展示用）。 */
    public static List<String> routedFiles() {
        return new ArrayList<>(FILE_ROUTES.keySet());
    }
}