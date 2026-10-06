package com.opendreamcore.client.model;

import com.opendreamcore.config.YamlParser;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;
import com.opendreamcore.model.rule.ModelRules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型规则（Legacy 客户端，纯 J8）：承接服务端下发的 Model bundle（id→YAML），
 * 可选叠加本地 Model.yml；match 命中 → 给出烘焙模型（ModelAssetStore）。
 *
 * <p>规则 YAML：{ match: "Type:minecraft:sheep;Name:xx", model: 模型id,
 * texture: 贴图标签(可省), scale: 1.0(可省) }；match 语义与共享层 ModelRules 一致。
 */
public final class LegacyModelRuleStore {

    /** 一条命中结果：烘焙模型（按材质拆条）+ 缩放 + 贴图覆盖。 */
    public static final class Hit {
        public final String id;
        public final List<BakedModel> baked;
        public final float scale;
        /** 规则里 texture= 的显式覆盖（null=用烘焙产物自带标签）。 */
        public final String textureOverride;

        Hit(String id, List<BakedModel> baked, float scale, String textureOverride) {
            this.id = id;
            this.baked = baked;
            this.scale = scale;
            this.textureOverride = textureOverride;
        }
    }

    private static final LegacyModelRuleStore INSTANCE = new LegacyModelRuleStore();

    public static LegacyModelRuleStore get() {
        return INSTANCE;
    }

    private final Map<String, Map<String, Object>> rules =
            new LinkedHashMap<String, Map<String, Object>>();

    private LegacyModelRuleStore() {
    }

    /** 服务端下发（整体覆盖）。bundle = id → 规则 YAML 文本。 */
    public synchronized void applyBundle(Map<String, String> bundle) {
        if (bundle == null) {
            return;
        }
        rules.clear();
        for (Map.Entry<String, String> e : bundle.entrySet()) {
            add(e.getKey(), e.getValue());
        }
        ModelLog.info("模型规则(服务端)装载 " + rules.size() + " 条");
    }

    /** 本地 Model.yml（单文件多规则；连服后服务端下发会整体覆盖）。 */
    public synchronized void applyLocalFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            Object parsed = new YamlParser().parse(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (parsed instanceof Map) {
                rules.clear();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) parsed).entrySet()) {
                    if (e.getValue() instanceof String) {
                        add(String.valueOf(e.getKey()), (String) e.getValue());
                    } else if (e.getValue() instanceof Map) {
                        rules.put(String.valueOf(e.getKey()), castMap(e.getValue()));
                    }
                }
                ModelLog.info("模型规则(本地)装载 " + rules.size() + " 条");
            }
        } catch (Exception e) {
            ModelLog.warn("本地 Model.yml 解析失败: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    public synchronized void add(String id, String yaml) {
        if (yaml == null || yaml.trim().isEmpty()) {
            return;
        }
        try {
            Object parsed = new YamlParser().parse(yaml);
            if (parsed instanceof Map) {
                rules.put(id, castMap(parsed));
            } else if (parsed instanceof String) {
                // 单文件简写：规则值就是模型 id
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("model", parsed);
                rules.put(id, m);
            }
        } catch (Exception e) {
            ModelLog.warn("模型规则 " + id + " 解析失败: " + e);
        }
    }

    public synchronized int count() {
        return rules.size();
    }

    /** 命中第一条 match 全 AND 的规则；未命中或模型未装载返回 null（缺省即安全）。 */
    public synchronized Hit hit(ModelRules.Lookup lookup) {
        for (Map.Entry<String, Map<String, Object>> e : rules.entrySet()) {
            Map<String, Object> m = e.getValue();
            List<ModelRules.Cond> conds = ModelRules.normalize(
                    m.containsKey("match") ? m.get("match") : m.get("匹配"));
            if (conds.isEmpty() || !ModelRules.matches(conds, lookup)) {
                continue;
            }
            String model = str(m.get("model"));
            List<BakedModel> baked =
                    ModelAssetStore.get().byModelId(model != null ? model : e.getKey());
            if (baked == null || baked.isEmpty()) {
                continue;
            }
            float scale = 1f;
            Object s = m.get("scale");
            if (s != null) {
                try {
                    scale = Float.parseFloat(String.valueOf(s));
                } catch (Exception ignore) {
                    // 非数字缩放按 1.0
                }
            }
            return new Hit(e.getKey(), Collections.unmodifiableList(
                    new ArrayList<BakedModel>(baked)), scale, str(m.get("texture")));
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
