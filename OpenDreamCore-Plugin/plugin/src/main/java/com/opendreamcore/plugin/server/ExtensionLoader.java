package com.opendreamcore.plugin.server;

import com.opendreamcore.config.YamlParser;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.DreamLangExecutor;
import com.opendreamcore.script.NamespaceRegistry;
import com.opendreamcore.script.Scope;
import com.opendreamcore.adapter.Adapter;
import com.opendreamcore.adapter.AdapterChain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DreamLang 扩展装载器（R3 的扩展投喂口）。
 *
 * 目录约定（没有就自动建，不生成示例也不吭声——扩展本来就是可选项）：
 *   extensions/*.java|.dream       —— 扩展脚本（/odc reload 全量重载）
 *   extensions/adapters/*.yml      —— 方言适配器规则（跟脚本里的声明配对）
 *
 * 脚本里能干的事（TOP_LEVEL，reload 清场重放）：
 *   Extension.注册("我的扩展.方法", (args) => { ... })  —— 往脚本语言里加戏
 *   Extension.适配器("名字")                            —— 认领一个方言（配适配啥都行）
 *
 * 方言适配器配对玩法（extensions/adapters/名字.yml，热拔插的插头盒）：
 *     我的方言.yml:
 *       优先级: 20              # 可选：链上插队顺序，越小越靠前（默认 100）
 *       包含匹配: true          # 可选：文件名"包含"就算认领（默认全等）
 *       ItemIcon:               # 翻译产出：目标系统名 → (规则id → 规则IR)
 *         例规则:
 *           name: "X"
 *           texture: "x.png"
 */
public final class ExtensionLoader {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore");

    /** 本轮装载登记的脚本命名空间（reload 清场用，别把核心内置的清了）。 */
    private final List<String> scriptNamespaces = new ArrayList<>();
    /** 本轮装载声明的适配器：名字 → 规则文件路径（脚本执行完统一配对注册）。 */
    private final Map<String, Path> declaredAdapters = new LinkedHashMap<>();

    /** 扩展根目录：插件数据目录下的 extensions/。 */
    private final Path root;

    public ExtensionLoader(Path dataRoot) {
        this.root = dataRoot.resolve("extensions");
    }

    /** 全量装载/重载。返回装载的脚本数。 */
    public int loadAll() {
        // 清场：上一轮登记的命名空间与适配器全部注销（核心内置的不动）
        for (String ns : scriptNamespaces) {
            NamespaceRegistry.unregister(ns);
        }
        scriptNamespaces.clear();
        declaredAdapters.clear();
        // 适配器链清脚本适配器（Java 附属注册的常驻；内置龙核适配器常驻）
        AdapterChain.clearScripted();
        ensureDefaults();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int count = 0;
        try (var walk = Files.walk(root, 3)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString().toLowerCase();
                if (!(name.endsWith(".java") || name.endsWith(".dream"))) {
                    continue; // 只吃脚本文件；yml 规则走 VisualRules 双形态
                }
                try {
                    loadScript(file);
                    count++;
                } catch (Exception e) {
                    LOGGER.warning("[OpenDreamCore][extension] 脚本装载失败 "
                            + root.relativize(file) + ": " + e);
                }
                // 单文件失败不影响其他扩展——坏一个扩展不该拖垮整个装载
            }
        } catch (Exception e) {
            LOGGER.warning("[OpenDreamCore][extension] 扩展目录不可读: " + e);
        }
        if (count > 0) {
            LOGGER.info("[OpenDreamCore][extension] 已装载 " + count + " 个扩展脚本（"
                    + scriptNamespaces.size() + " 个脚本命名空间）");
        }
        return count;
    }

    /** 单脚本装载：读源码 → 顶层执行 → 脚本通过 Extension 登记东西。 */
    private void loadScript(Path file) throws Exception {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Scope scope = new Scope();
        // Extension 全局对象：脚本侧唯一的登记入口（reload 清场跟着这轮走）
        scope.assignGlobal("Extension", new DreamLangExecutor.Callable() {
            @Override
            public Object call(Object[] args) {
                return null;
            }

            @Override
            public Object member(String name) {
                return switch (name) {
                    // Extension.注册("命名空间.方法", lambda)：注册脚本方法
                    case "注册", "register" -> (DreamLangExecutor.Callable) regArgs -> {
                        if (regArgs.length < 2 || regArgs[0] == null || regArgs[1] == null) {
                            return false;
                        }
                        String full = String.valueOf(regArgs[0]);
                        int dot = full.indexOf('.');
                        if (dot <= 0 || dot == full.length() - 1) {
                            LOGGER.warning("[OpenDreamCore][extension] 注册名要带命名空间: " + full);
                            return false;
                        }
                        String ns = full.substring(0, dot);
                        String method = full.substring(dot + 1);
                        DreamLangExecutor.Callable lambda = (DreamLangExecutor.Callable) regArgs[1];
                        NamespaceRegistry.register(ns, method, lambda::call);
                        scriptNamespaces.add(ns);
                        return true;
                    };
                    // Extension.适配器("名字")：声明方言适配器（配对 adapters/名字.yml）
                    case "适配器", "adapter" -> (DreamLangExecutor.Callable) adArgs -> {
                        if (adArgs.length < 1 || adArgs[0] == null) {
                            return false;
                        }
                        String adapterName = String.valueOf(adArgs[0]);
                        declaredAdapters.put(adapterName,
                                root.resolve("adapters").resolve(adapterName + ".yml"));
                        return true;
                    };
                    default -> null;
                };
            }
        });
        DreamLang.execute(text, scope);
    }

    /** 把脚本声明的适配器配对规则文件后注册进链子（找不到规则文件的声明告警跳过）。 */
    private void registerDeclaredAdapters() {
        for (Map.Entry<String, Path> e : declaredAdapters.entrySet()) {
            Path ruleFile = e.getValue();
            if (!Files.isRegularFile(ruleFile)) {
                LOGGER.warning("[OpenDreamCore][extension] 适配器 " + e.getKey()
                        + " 缺规则文件 " + ruleFile.getFileName() + "（声明了但没配规则，跳过）");
                continue;
            }
            try {
                Map<String, Object> rules = new YamlParser().parse(
                        new String(Files.readAllBytes(ruleFile), StandardCharsets.UTF_8));
                if (rules == null || rules.isEmpty()) {
                    continue;
                }
                AdapterChain.register(new ScriptedAdapter(e.getKey(), rules));
            } catch (Exception ex) {
                LOGGER.warning("[OpenDreamCore][extension] 适配器规则装载失败 "
                        + ruleFile.getFileName() + ": " + ex);
            }
        }
    }

    /** 首次体验：建好 extensions/ 与 adapters/ 目录（空目录即可，不放示例添乱）。 */
    private void ensureDefaults() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("adapters"));
        } catch (Exception ignored) {
        }
    }
}

/**
 * 脚本登记的方言适配器（链子上 scripted()=true，reload 清场重载）。
 * 规则键 = 外来文件名（含或不含 .yml 都认；包含匹配看"包含匹配"开关），
 * 值 = {优先级?, 包含匹配?, 九系统名 → (规则id → 规则IR)}。
 */
final class ScriptedAdapter implements Adapter, AdapterChain.Prioritized {

    private final String name;
    private final Map<String, Object> rules;

    ScriptedAdapter(String name, Map<String, Object> rules) {
        this.name = name;
        this.rules = rules;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean scripted() {
        return true;
    }

    @Override
    public int priority() {
        Object p = rules.get("优先级");
        if (p instanceof Number n) {
            return n.intValue();
        }
        try {
            return p == null ? 100 : Integer.parseInt(String.valueOf(p));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    @Override
    public boolean accepts(String fileName, Map<String, Object> rootIr) {
        return entryFor(fileName) != null;
    }

    @Override
    public Map<String, Map<String, Map<String, Object>>> translate(
            String fileName, Map<String, Object> rootIr) {
        Map.Entry<String, Object> hit = entryFor(fileName);
        if (!(hit.getValue() instanceof Map<?, ?> body)) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : body.entrySet()) {
            String system = String.valueOf(e.getKey());
            if (system.equals("优先级") || system.equals("包含匹配")) {
                continue; // 元键不进规则库
            }
            if (e.getValue() instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> ruleSet = (Map<String, Map<String, Object>>) m;
                out.put(system, ruleSet);
            }
        }
        return out;
    }

    /** 文件名 → 命中的规则条目（全等优先，包含匹配其次；大小写不敏感）。 */
    private Map.Entry<String, Object> entryFor(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase().replace('\\', '/');
        int slash = lower.lastIndexOf('/');
        if (slash >= 0) {
            lower = lower.substring(slash + 1);
        }
        String stem = lower.endsWith(".yml") ? lower.substring(0, lower.length() - 4) : lower;
        boolean containsMode = Boolean.parseBoolean(String.valueOf(rules.get("包含匹配")));
        for (Map.Entry<String, Object> e : rules.entrySet()) {
            String key = e.getKey().toLowerCase();
            if (key.equals("优先级") || key.equals("包含匹配")) {
                continue;
            }
            String k = key.endsWith(".yml") ? key.substring(0, key.length() - 4) : key;
            boolean hit = containsMode ? lower.contains(k) || stem.contains(k) : stem.equals(k);
            if (hit) {
                return e;
            }
        }
        return null;
    }
}