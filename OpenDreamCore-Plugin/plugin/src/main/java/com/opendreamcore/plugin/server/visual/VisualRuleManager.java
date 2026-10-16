package com.opendreamcore.plugin.server.visual;

import com.opendreamcore.adapter.AdapterChain;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.visual.VisualTemplates;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 服务端视觉规则管理器。
 *
 * 启动/reload 时对各系统做双形态装载（根文件 + 同名文件夹，缺失自动生成
 * 默认示例），把全部规则原文打包成 VisualRulesSync 在玩家 ready 时下发——
 * 匹配与渲染在客户端，服务端只负责"谁生效"的权威分发。
 */
public final class VisualRuleManager {

    /** 视觉系统的固定清单：system 名 → 数据目录相对名（同名）。 */
    public static final List<String> SYSTEMS = java.util.Arrays.asList(
            "ItemIcon", "ItemEffect", "HeadTag", "Blood", "FontConfig", "ArmorLayer",
            "KeyConfig", "Sounds", "WorldTexture", "SlotConfig", "Camera", "EntityModel", "ItemModel");

    private final Path dataRoot;
    // system → (规则id → 规则 YAML 原文)
    private final Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
    private String versionHash = "";
    private final KeyConfigExecutor keyExecutor = new KeyConfigExecutor();
    /** SlotConfig 服务端裁决器（槽位点击权威校验，ProtocolHandler 调用）。 */
    private final SlotConfigGuard slotGuard = new SlotConfigGuard();
    // 服务器上装了龙核时的原生配置目录（plugins/DragonCore），可选
    private Path externalRoot;

    public VisualRuleManager(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    /** 龙核原生配置目录；设了就会在每次 reload 时翻译并入（见 mergeExternal）。 */
    public void setExternalRoot(Path dir) {
        this.externalRoot = dir;
    }

    /** 当前龙核配置目录（文件监听用；没装龙核返回 null）。 */
    public Path getExternalRoot() {
        return externalRoot;
    }

    /** 装载/重载全部系统。返回装载的规则总数。 */
    public int reload() {
        // 适配器链先清场：脚本登记的方言适配器（extensions/adapters）全部注销，
        // 扩展装载器在本方法之后重载并重新登记——Java 附属注册的常驻，不受影响
        AdapterChain.clearScripted();
        bundles.clear();
        int total = 0;
        Map<String, Map<String, Object>> keyRules = new LinkedHashMap<>();
        Map<String, Map<String, Object>> slotRules = new LinkedHashMap<>();
        for (String system : SYSTEMS) {
            com.opendreamcore.visual.VisualRules.ensureDefault(
                    dataRoot, system, templateFor(system));
            Map<String, Map<String, Object>> rules =
                    com.opendreamcore.visual.VisualRules.load(dataRoot, system);
            Map<String, String> bundle = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : rules.entrySet()) {
                bundle.put(e.getKey(), com.opendreamcore.visual.VisualYaml.dump(e.getValue()));
                total++;
            }
            if (!bundle.isEmpty()) {
                bundles.put(system, bundle);
            }
            // KeyConfig 攒着不急着喂：等龙核那份也并进来后一次装载
            // （执行器 load 是清空式的，分两次会把先到的冲掉）
            if (system.equals("KeyConfig")) {
                keyRules.putAll(rules);
            }
            // SlotConfig 同理攒住（服务端裁决器一次装载）
            if (system.equals("SlotConfig")) {
                slotRules.putAll(rules);
            }
        }
        total += mergeExternal(keyRules, slotRules);
        keyExecutor.load(keyRules);
        // SlotConfig 装进裁决器（KeyConfig 执行器同一套 reload 习惯）
        slotGuard.load(slotRules);
        if (slotGuard.size() > 0) {
            java.util.logging.Logger.getLogger("OpenDreamCore")
                    .info("SlotConfig 裁决规则已装载 " + slotGuard.size() + " 条");
        }
        versionHash = sha32(String.valueOf(total) + bundles.hashCode());
        return total;
    }

    /**
     * 并入龙核原生配置（plugins/DragonCore/ 顶层的 .yml/.yaml 系统文件，
     * 如 KeyConfig.yml、Blood.yaml）。id 统一挂 dc/ 前缀：跟服主自己写的规则天然不撞车，日志里
     * 也一眼认出出处。同名按键组合龙核后到、后到嬴，跟龙核自己的装载
     * 顺序一致。翻译失败只记一条日志，不拖垮其它文件。
     */
    private int mergeExternal(Map<String, Map<String, Object>> keyRules,
                              Map<String, Map<String, Object>> slotRules) {
        if (externalRoot == null || !java.nio.file.Files.isDirectory(externalRoot)) {
            return 0;
        }
        int merged = 0;
        List<Path> files = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(externalRoot)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    files.add(p);
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("OpenDreamCore")
                    .warning("龙核配置目录读不了（跳过并入）：" + e);
            return 0;
        }
        // 排序后装载，同名键结果可复现
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        for (Path file : files) {
            try {
                // 宽松解析保留龙核配置中的重复键和 BOM 处理；各文件独立失败。
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Map<String, Object> root = YamlParser.lenient().parse(text);
                for (Map.Entry<String, Map<String, Map<String, Object>>> entry :
                        AdapterChain.translate(file.getFileName().toString(), root).entrySet()) {
                    String system = entry.getKey();
                    Map<String, String> bundle = bundles.computeIfAbsent(system,
                            k -> new LinkedHashMap<>());
                    for (Map.Entry<String, Map<String, Object>> rule : entry.getValue().entrySet()) {
                        String id = "dc/" + rule.getKey();
                        Map<String, Object> body = rule.getValue();
                        if (system.equals("KeyConfig")) {
                            body = new LinkedHashMap<>(body);
                            // Namespace the rule identity, never the execution combo. Explicit
                            // bindings survive unchanged; legacy map-key combos become keys.
                            if (!body.containsKey("keys")) {
                                body.put("keys", java.util.Collections.singletonList(rule.getKey()));
                            }
                            keyRules.put(id, body);
                        } else if (system.equals("SlotConfig")) {
                            slotRules.put(id, body);
                        }
                        bundle.put(id, com.opendreamcore.visual.VisualYaml.dump(body));
                        merged++;
                    }
                }
            } catch (Exception e) {
                logExternalError(file.getFileName().toString(), e);
            }
        }
        if (merged > 0) {
            java.util.logging.Logger.getLogger("OpenDreamCore")
                    .info("已从龙核配置并入 " + merged + " 条十系统规则（id 带 dc/ 前缀）");
        }
        return merged;
    }

    private static void logExternalError(String name, Throwable t) {
        java.util.logging.Logger.getLogger("OpenDreamCore")
                .warning("龙核配置解析失败 " + name + "：" + t);
    }

    /** 打包成同步消息。 */
    public com.opendreamcore.protocol.message.VisualRulesSync buildSync() {
        return com.opendreamcore.protocol.message.VisualRulesSync.fromBundles(bundles, versionHash);
    }

    public String versionHash() {
        return versionHash;
    }

    /** 按键执行器访问器（ProtocolHandler KEY 路由用）。 */
    public KeyConfigExecutor keyExecutor() {
        return keyExecutor;
    }

    /** SlotConfig 服务端裁决器访问器（ProtocolHandler 槽位点击裁决用）。 */
    public SlotConfigGuard slotGuard() {
        return slotGuard;
    }

    /** 单系统规则表（附属查询用）：id → YAML 原文。 */
    public Map<String, String> rulesOf(String system) {
        return bundles.getOrDefault(system, java.util.Collections.emptyMap());
    }

    // 内部

    private static String templateFor(String system) {
        var all = VisualTemplates.all();
        return all.getOrDefault(system, "# " + system + "\n");
    }

    /**
     * 极简 YAML 序列化：规则 IR 都是两层以内的标量/map，
     * 不引第三方依赖，手写足够（值里的特殊字符走引号包裹）。
     */
    private static String sha32(String text) {
        try {
            var d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "00000000";
        }
    }

    /** 供协议层取“要下发的系统列表”。 */
    public List<String> systems() {
        return new ArrayList<>(SYSTEMS);
    }
}
