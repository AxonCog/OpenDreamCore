package com.opendreamcore.client.model;

import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;

import com.opendreamcore.config.YamlParser;
import com.opendreamcore.model.rule.ModelRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型替换规则库（Model.yml，多规则同文件，与 Camera.yml 同构）。
 *
 * <p>规则体（ DreamCore 单行 match 语法 / 块式均支持，ModelRules.normalize 归一）：
 * <pre>
 * 史莱姆:
 *   model: rpg_zombie1
 *   texture: texture/rpg_zombie1.png   # 可选，覆盖模型自带贴图
 * </pre>
 */
public final class ModelRuleStore {

    private static final ModelRuleStore INSTANCE = new ModelRuleStore();

    /** 服务端下发：整体覆盖入库（bundle = id→YAML 平铺表；EntityModel/ItemModel 双体系，缺省键跳过）。 */
    public static void handleServerBundle(Map<String, Map<String, String>> bundles) {
        Map<String, String> entity = bundles.get("EntityModel");
        Map<String, String> item = bundles.get("ItemModel");
        if (entity != null) {
            get().applyEntity(entity);
        }
        if (item != null) {
            get().applyItem(item);
        }
        if (entity != null || item != null) {
            ModelLog.info("模型配置已同步：实体规则 " + get().entitySize() + "，物品规则 " + get().itemSize());
        }
    }

    /** 本地 EntityModel.yml/ItemModel.yml + 模型资产（OpenDreamCore/assets/model）装载；失败仅告警不阻断启动。 */
    public static void handleLocal(Map<String, Map<String, String>> localBundles, Path gameDir) {
        // 本地直读：loadLocalVisualRules 只扫 visual/*，模型双文件不在那个名单里。
        // 服务端没下发对应键（单机/未开模型的服务器）时，直接读 OpenDreamCore/EntityModel.yml 与 ItemModel.yml
        // （同名文件夹下的 *.yml 一并合并；文件缺省/空白就跳过，不做任何写入）。
        if (localBundles == null || !localBundles.containsKey("EntityModel")) {
            Map<String, String> local = readLocalRules(gameDir, "EntityModel");
            if (!local.isEmpty()) {
                get().applyEntity(local);
            }
        } else {
            get().applyEntity(localBundles.get("EntityModel"));
        }
        if (localBundles == null || !localBundles.containsKey("ItemModel")) {
            Map<String, String> local = readLocalRules(gameDir, "ItemModel");
            if (!local.isEmpty()) {
                get().applyItem(local);
            }
        } else {
            get().applyItem(localBundles.get("ItemModel"));
        }
        try {
            handleAssets(gameDir);
        } catch (Throwable t) {
            ModelLog.warn("模型资产加载失败: " + t);
        }
    }

    /**
     * 模型资产解析根（服务端/本地两条路径都要走，否则连服时 roots 空、
     * 按名查找永远「模型没找到 根=[]」）。
     */
    public static void handleAssets(Path gameDir) {
        // 解析根：实例 assets/model + 资源包根（用户常把模型直接丢在 resourcepacks/OpenDreamCore）
        java.util.List<java.nio.file.Path> assetRoots = new java.util.ArrayList<java.nio.file.Path>();
        assetRoots.add(gameDir.resolve("OpenDreamCore").resolve("assets").resolve("model"));
        assetRoots.add(gameDir.resolve("resourcepacks").resolve("OpenDreamCore"));
        ModelAssetStore.get().setRoots(assetRoots);
        // addons：本地扩展热拔插（jar SPI + 脚本包；模型格式 codec 是第一个消费者）
        try {
            int addonCount = ClientAddons.reload();
            if (addonCount > 0) {
                ModelLog.info("addons 已装载 " + addonCount + " 项");
            }
        } catch (Throwable t) {
            ModelLog.warn("addons 重扫失败: " + t);
        }
    }

    public static ModelRuleStore get() {
        return INSTANCE;
    }

    /**
     * 本地双文件直读：主文件 + 同名文件夹下全部 *.yml 合并，顶层每条规则拆成 bundle 一条
     * （与服务端下发同构：id → 单条规则 YAML）。解析失败按空处理，只告警不阻断。
     */
    private static Map<String, String> readLocalRules(Path gameDir, String system) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Path base = gameDir.resolve("OpenDreamCore");
        List<Path> files = new ArrayList<Path>();
        files.add(base.resolve(system + ".yml"));
        Path folder = base.resolve(system);
        try {
            if (Files.isDirectory(folder)) {
                try (java.util.stream.Stream<Path> ds = Files.list(folder)) {
                    ds.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().forEach(files::add);
                }
            }
        } catch (Throwable t) {
            ModelLog.warn("模型规则文件夹扫描失败: " + t);
        }
        for (Path f : files) {
            try {
                if (!Files.isRegularFile(f)) {
                    continue;
                }
                String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                if (text.trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> root = YamlParser.lenient().parse(text);
                if (root == null) {
                    continue;
                }
                for (Map.Entry<String, Object> e : root.entrySet()) {
                    if (e.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rule = (Map<String, Object>) e.getValue();
                        out.put(e.getKey(), com.opendreamcore.visual.VisualYaml.dump(rule));
                    }
                }
            } catch (Throwable t) {
                ModelLog.warn("模型规则文件读取失败（跳过）: " + f.getFileName() + " -> " + t);
            }
        }
        return out;
    }

    /** 单条规则：匹配条件 + 目标模型。 */
    public static final class Rule {
        public final String id;
        public final List<ModelRules.Cond> conditions;
        public final String model;
        public final String texture;

        Rule(String id, List<ModelRules.Cond> conditions, String model, String texture) {
            this.id = id;
            this.conditions = conditions;
            this.model = model;
            this.texture = texture;
        }
    }

    /** 实体规则集（EntityModel.yml）。 */
    private final List<Rule> entityRules = new ArrayList<Rule>();
    /** 物品规则集（ItemModel.yml）。 */
    private final List<Rule> itemRules = new ArrayList<Rule>();

    private ModelRuleStore() {
    }

    /** 实体规则（EntityModelReplacer 等实体胶水消费）。 */
    public synchronized List<Rule> all() {
        return Collections.unmodifiableList(entityRules);
    }

    /** 物品规则（物品胶水/CDM 通道消费）。 */
    public synchronized List<Rule> allItems() {
        return Collections.unmodifiableList(itemRules);
    }

    public synchronized int size() {
        return entityRules.size();
    }

    public synchronized int entitySize() {
        return entityRules.size();
    }

    public synchronized int itemSize() {
        return itemRules.size();
    }

    public synchronized void clear() {
        entityRules.clear();
        itemRules.clear();
    }

    /** 兼容旧签名：等价 applyEntity（实体规则集）。 */
    public synchronized void apply(Map<String, String> texts) {
        applyEntity(texts);
    }

    /** 实体规则整体覆盖入库。 */
    public synchronized void applyEntity(Map<String, String> texts) {
        entityRules.clear();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            parse(e.getKey(), e.getValue(), entityRules);
        }
        ModelLog.info("实体模型规则入库 " + entityRules.size() + " 条");
    }

    /** 物品规则整体覆盖入库。 */
    public synchronized void applyItem(Map<String, String> texts) {
        itemRules.clear();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            parse(e.getKey(), e.getValue(), itemRules);
        }
        ModelLog.info("物品模型规则入库 " + itemRules.size() + " 条");
    }

    /**
     * match 值的写法解析：龙核式前缀 {@code Type:xxx} / {@code Name:xxx} / {@code ID:xxx} / {@code Lore:xxx}，
     * 没前缀就当显示名匹配。
     *
     * <p>只当名字比对是不行的——存量配置里大量 {@code match: "Type:minecraft:creeper"} 这种写法，
     * 会被当成「名字叫 Type:minecraft:creeper 的实体」，永远不命中。
     */
    private static void applyMatchValue(Map<String, Object> condMap, String raw) {
        String v = raw.trim();
        int colon = v.indexOf(':');
        if (colon > 0) {
            String head = v.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String rest = v.substring(colon + 1).trim();
            if (rest.isEmpty()) {
                condMap.put("name", v);
                return;
            }
            switch (head) {
                case "type":
                case "entity":
                case "entitytype":
                    condMap.put("type", rest);
                    return;
                case "name":
                case "customname":
                case "itemname":
                    condMap.put("name", rest);
                    return;
                case "id":
                case "itemid":
                    condMap.put("id", rest);
                    return;
                case "lore":
                    condMap.put("lore", rest);
                    return;
                default:
                    break;
            }
        }
        condMap.put("name", v);
    }


    /** 块式条件表归一：摘掉非条件键，把 entity / match / 单行后缀这些写法折成条件求值认的键。 */
    private List<ModelRules.Cond> buildConds(Map<String, Object> m, Map<String, Object> root, String id) {
        Map<String, Object> condMap = new LinkedHashMap<String, Object>(m);
        condMap.keySet().removeAll(java.util.Arrays.asList("path", "model", "texture",
                "scale", "height", "width", "contains", "sendPathOnly", "send_path_only",
                "y_offset", "glow", "hide_name", "render_distance"));
        Object entV = condMap.remove("entity");
        if (entV != null && str(entV) != null && !str(entV).isEmpty()) {
            boolean byName = str(entV).startsWith("§") || isTruthy(m.get("contains"));
            condMap.put(byName ? "name" : "type", entV);
        }
        Object matchV = condMap.remove("match");
        if (matchV == null) {
            matchV = condMap.remove("matchName");
        }
        if (matchV == null) {
            matchV = condMap.remove("match_name");
        }
        if (matchV != null && str(matchV) != null && !str(matchV).isEmpty()) {
            applyMatchValue(condMap, str(matchV));
        }
        // <id>.entity = xxx 单行后缀写法
        if (condMap.isEmpty()) {
            String entSuffix = str(root.get(id + ".entity"));
            if (entSuffix != null && !entSuffix.isEmpty()) {
                condMap.put(entSuffix.startsWith("§") ? "name" : "type", entSuffix);
            }
        }
        return ModelRules.normalize((Object) condMap);
    }

    /** 规则体本身形态（{match/path/scale…}）：直接取 path/model 建 Rule。 */
    private void parseSingleRule(Map<String, Object> m, String id, List<Rule> out) {
        String model = str(m.get("path"));
        if (model == null) {
            model = str(m.get("model")); // path 键优先，model 作兼容别名
        }
        String texture = str(m.get("texture"));
        List<ModelRules.Cond> conds = buildConds(m, m, id);
        if (model != null && !conds.isEmpty()) {
            out.add(new Rule(id, conds, model, texture));
        } else {
            ModelLog.warn("规则 " + id + " 缺模型路径或条件，跳过（model=" + model + "）");
        }
    }

    private void parse(String id, String yaml, List<Rule> out) {
        Map<String, Object> root;
        try {
            root = YamlParser.lenient().parse(yaml);
        } catch (Exception e) {
            ModelLog.warn("规则 " + id + " 解析失败: " + e);
            return;
        }
        if (root == null) {
            return;
        }
        // 两种合法形态：① {id: 规则体}（编辑器/离线文件）；② 规则体本身
        // （{match/path/scale…}，服务端 bundle 与 readLocalRules 传的都是单条规则体的 dump）。
        // 之前只认①，而 bundle 里全是②——遍历到的是 match: "Type:…" 这种标量值，
        // 走 else 分支取 <id>.path 永远 null → 全部 continue → 「入库 0 条」且无任何告警。
        // 判定：存在标量值就是规则体本身；全 Map 才当多规则表。
        boolean singleRule = false;
        for (Object v : root.values()) {
            if (!(v instanceof Map)) {
                singleRule = true;
                break;
            }
        }
        if (singleRule) {
            parseSingleRule(root, id, out);
            return;
        }
        for (Map.Entry<String, Object> en : root.entrySet()) {
            String model = null;
            String texture = null;
            List<ModelRules.Cond> conds;
            Object v = en.getValue();
            if (v instanceof Map) {
                Map<String, Object> m = castMap(v);
                model = str(m.get("path"));
                if (model == null) {
                    model = str(m.get("model")); // path 键优先，model 作兼容别名
                }
                texture = str(m.get("texture"));
                // 块式条件：去掉模型/贴图键后的其余键
                Map<String, Object> condMap = new LinkedHashMap<String, Object>(m);
                conds = buildConds(m, root, en.getKey());
            } else {
                // 单行/列表条件：值即条件，模型取同 id 的 <id>.path / <id>.model
                conds = ModelRules.normalize(v);
                model = str(root.get(en.getKey() + ".path"));
                if (model == null) {
                    model = str(root.get(en.getKey() + ".model"));
                }
            }
            if (model == null || conds.isEmpty()) {
                continue;
            }
            out.add(new Rule(en.getKey(), conds, model, texture));
        }
    }

    /** 解析磁盘规则文件（EntityModel.yml/ItemModel.yml；编辑器/离线用，运行时主通道是插件下发）。 */
    public synchronized int loadFile(Path file, boolean item) {
        if (file == null || !Files.exists(file)) {
            return 0;
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Map<String, String> single = new LinkedHashMap<String, String>();
            single.put("file", text);
            if (item) {
                applyItem(single);
                return itemRules.size();
            }
            applyEntity(single);
            return entityRules.size();
        } catch (Exception e) {
            ModelLog.warn("读取 " + file + " 失败: " + e);
            return 0;
        }
    }

    private static Map<String, Object> castMap(Object o) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** true/1/yes/on/是/开 都算开（配置里的布尔随便写）。 */
    static boolean isTruthy(Object v) {
        if (v == null) {
            return false;
        }
        String t = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
        return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on")
                || t.equals("是") || t.equals("开");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
