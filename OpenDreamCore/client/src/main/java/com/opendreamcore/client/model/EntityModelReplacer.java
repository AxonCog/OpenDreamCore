package com.opendreamcore.client.model;

import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;

import com.opendreamcore.model.rule.ModelRules;

import java.util.List;

/**
 * 实体替换解析入口（版本无关：代际 glue 只传字符串）。
 *
 * <p>流程：规则库按 (实体类型 id, 自定义名/显示名) 找第一条命中 → 资产库取烘焙产物；
 * 规则 texture 覆盖时把该条产物的贴图标签替换掉。
 */
public final class EntityModelReplacer {

    private EntityModelReplacer() {
    }

    /** 命中产物；null=不替换。 */
    public static final class Hit {
        public final List<BakedModel> models;
        public final String ruleId;

        Hit(String ruleId, List<BakedModel> models) {
            this.ruleId = ruleId;
            this.models = models;
        }
    }

    public static Hit resolve(String entityType, String customName) {
        if (ModelRuleStore.get().size() == 0) {
            return null;
        }
        final String type = entityType == null ? "" : entityType;
        final String name = customName == null ? "" : customName;
        ModelRules.Lookup lookup = new ModelRules.Lookup() {
            public String get(String key) {
                if ("type".equals(key)) {
                    return type;
                }
                if ("name".equals(key)) {
                    return name;
                }
                return null;
            }
        };
        for (ModelRuleStore.Rule rule : ModelRuleStore.get().all()) {
            if (ModelRules.matches(rule.conditions, lookup)) {
                List<BakedModel> baked = ModelAssetStore.get().byModelId(rule.model);
                if (baked == null) {
                    // 缺模型不能直接收工：后面的规则可能还能命中（以前这里 return null，
                    // 一条坏规则就把整张表后面全废掉）
                    if (MISS_LOG.add(rule.id)) {
                        ModelLog.warn("规则 " + rule.id + " 的模型 " + rule.model
                                + " 没找到（继续试后面的规则）");
                    }
                    continue;
                }
                if (HIT_LOG.add(rule.id + "|" + type + "|" + name)) {
                    ModelLog.info("模型替换命中: " + name + "(" + type + ") -> 规则 " + rule.id
                            + " 模型 " + rule.model);
                }
                return new Hit(rule.id, baked);
            }
        }
        return null;
    }

    private static final java.util.Set<String> MISS_LOG = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> HIT_LOG = java.util.concurrent.ConcurrentHashMap.newKeySet();
}
