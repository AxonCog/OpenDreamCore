package com.opendreamcore.model.cdm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CDM 资源包 json 生成器（按代语义，服务端用；纯 J8 无 MC import）。
 *
 *  - gen1（1.20.1/1.21.1）：物品模型 overrides + custom_model_data 谓词（数值）；
 *    文件 assets/minecraft/models/item/<itemId>.json（保留 layer0 原贴图，未命中 CDM 时显示原版）。
 *  - gen3（1.21.4+，含 1.21.4/1.21.8/1.21.11/26.1.2）：item 定义 + select(custom_model_data, index 0)；
 *    文件 assets/minecraft/items/<itemId>.json；CDM 组件 strings 匹配，未命中回落原版模型。
 *  - 模型本体（odc:model/<key>）与贴图由模型模块资源通道下发，这里只生成「绑定」json。
 *  - 生成永不抛错：缺字段条目跳过（缺省即安全）。
 */
public final class CdmGenerator {

    /** 目标代（决定 json 形态）。 */
    public enum Gen {
        /** 1.20.1 / 1.21.1：overrides + custom_model_data 数值谓词。 */
        V1201,
        /** 1.21.4 及以后：items 定义 + select(custom_model_data)。 */
        V1214UP
    }

    /** 一条绑定：给某物品挂一个 CDM 值指向我们的模型。 */
    public static final class Entry {
        /** 物品注册名（不带命名空间，如 paper；或带命名空间 paper）。 */
        public final String itemId;
        /** 模型 key（odc 命名空间下，如 rpg_skeleton）。 */
        public final String modelKey;
        /** CDM 值（gen1 数值；gen3 转字符串）。 */
        public final int cdmValue;

        public Entry(String itemId, String modelKey, int cdmValue) {
            this.itemId = itemId;
            this.modelKey = modelKey;
            this.cdmValue = cdmValue;
        }
    }

    private CdmGenerator() {
    }

    /**
     * 生成资源包文件表：路径 → json 文本。
     * 同一物品多条目合并为一个文件（overrides/cases 按条目顺序）。
     */
    public static Map<String, String> generate(List<Entry> entries, Gen gen) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (entries == null) {
            return out;
        }
        // 按物品聚合（保持顺序）
        Map<String, List<Entry>> byItem = new LinkedHashMap<String, List<Entry>>();
        for (Entry e : entries) {
            if (e == null) {
                continue;
            }
            String id = e.itemId == null || e.itemId.trim().isEmpty() ? null : trimNs(e.itemId.trim());
            String key = e.modelKey == null || e.modelKey.trim().isEmpty() ? null : safeKey(e.modelKey.trim());
            if (id == null || key == null) {
                continue;
            }
            List<Entry> list = byItem.get(id);
            if (list == null) {
                list = new ArrayList<Entry>();
                byItem.put(id, list);
            }
            list.add(new Entry(id, key, e.cdmValue));
        }
        for (Map.Entry<String, List<Entry>> e : byItem.entrySet()) {
            String id = e.getKey();
            StringBuilder sb = new StringBuilder();
            if (gen == Gen.V1201) {
                sb.append("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/")
                  .append(id).append("\"},\"overrides\":[");
                boolean first = true;
                for (Entry en : e.getValue()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append("{\"predicate\":{\"custom_model_data\":").append(en.cdmValue)
                      .append("},\"model\":\"odc:model/").append(en.modelKey).append("\"}");
                }
                sb.append("]}");
                out.put("assets/minecraft/models/item/" + id + ".json", sb.toString());
            } else {
                sb.append("{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",")
                  .append("\"index\":0,\"cases\":[");
                boolean first = true;
                for (Entry en : e.getValue()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append("{\"when\":[\"").append(en.cdmValue).append("\"],")
                      .append("\"model\":{\"type\":\"minecraft:model\",\"model\":\"odc:model/")
                      .append(en.modelKey).append("\"}}");
                }
                sb.append("],\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/")
                  .append(id).append("\"}}}");
                out.put("assets/minecraft/items/" + id + ".json", sb.toString());
            }
        }
        return out;
    }

    /** 去命名空间（minecraft:paper → paper）。 */
    static String trimNs(String id) {
        int c = id.indexOf(':');
        return c >= 0 ? id.substring(c + 1) : id;
    }

    /** 模型 key 白名单清洗（只留小写字母数字/_/-，其余 _）。 */
    static String safeKey(String key) {
        return key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/_-]", "_");
    }
}
