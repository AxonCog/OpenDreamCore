package com.opendreamcore.visual;

import com.opendreamcore.util.J8;

import java.util.List;
import java.util.Map;

/**
 * 物品抽象视图。
 *
 * 匹配引擎只认这四个字段，不碰 ItemStack——common 层保持零 MC 依赖，
 * 客户端把原版物品适配成本接口，服务端把 NMS 物品适配成同一张脸。
 * 这样一套匹配逻辑，背包/HUD/手持三个渲染口和附属 API 全部共用。
 */
public record ItemView(String type, String name, List<String> lore, Map<String, Object> nbt) {

    public ItemView {
        type = type == null ? "" : type;
        name = name == null ? "" : name;
        lore = lore == null ? J8.list() : J8.listCopy(lore);
        nbt = nbt == null ? J8.map() : J8.mapCopy(nbt);
    }

    /** 空物品视图（无匹配意义，但保证引擎判空路径可测）。 */
    public static ItemView empty() {
        return new ItemView("", "", J8.list(), J8.map());
    }
}
