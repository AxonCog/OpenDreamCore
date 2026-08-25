package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class ItemLegacy {
    private ItemLegacy() { }

    private static String s(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }

    public static void install() {
        LegacyMethods.register("取物品ID", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("取物品名", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("取物品名称", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("取物品Lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("取物品所有Lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("获取lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("取lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("取所有物品", a -> LegacyMethods.delegate("Screen", "获取元素", "_container_items"));
        LegacyMethods.register("取活跃物品", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("取槽位物品", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("取槽位物品ID", a -> LegacyMethods.slotItem(a, 0));
    }
}
