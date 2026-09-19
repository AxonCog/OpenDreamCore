package com.opendreamcore.legacy;

import com.opendreamcore.script.NamespaceRegistry;

/**
 * 命名空间注册（1.6.4）：MC API 走反射（1.6.4 混淆环境无稳定映射），
 * 纯逻辑命名空间（Time/Var/Screen 变量）直接落地。
 */
public final class LegacyNamespaces164 {
    private LegacyNamespaces164() { }

    public static void install() {
        // Time：纯 Java
        NamespaceRegistry.registerOrReplace("Time", "当前时间戳",
                a -> (double) (System.currentTimeMillis() / 1000L));
        NamespaceRegistry.registerOrReplace("Time", "当前毫秒",
                a -> (double) System.currentTimeMillis());
        // Var：client 层 PageVariables 后端
        NamespaceRegistry.registerOrReplace("Var", "设置变量", a ->
        {
            com.opendreamcore.client.PageVariables.set(null,
                    str(a, 0), a.length > 1 ? a[1] : null);
            return null;
        });
        NamespaceRegistry.registerOrReplace("Var", "获取变量",
                a -> com.opendreamcore.client.PageVariables.get(null, str(a, 0)));
        // Player 反射版：字段/方法名按 1.6.4 混淆表在运行时解析，失败静默降级
        NamespaceRegistry.registerOrReplace("Player", "getHealth", a -> reflectNum("player", new String[]{"func_70672_aI", "getHealth"}));
        NamespaceRegistry.registerOrReplace("Player", "getX", a -> reflectNum("player", new String[]{"posX"}));
        NamespaceRegistry.registerOrReplace("Player", "getY", a -> reflectNum("player", new String[]{"posY"}));
        NamespaceRegistry.registerOrReplace("Player", "getZ", a -> reflectNum("player", new String[]{"posZ"}));
    }

    /** 从 Minecraft 实例链式取字段后按候选名试方法/字段，返回 double。 */
    private static double reflectNum(String field, String[] names) {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object inst = mc.getField("theMinecraft").get(null);
            Object player = inst.getClass().getField(field).get(inst);
            if (player == null) {
                return 0;
            }
            for (String name : names) {
                try {
                    Object v = player.getClass().getMethod(name).invoke(player);
                    return v instanceof Number ? ((Number) v).doubleValue() : 0;
                } catch (NoSuchMethodException ignored) {
                    try {
                        java.lang.reflect.Field f = player.getClass().getField(name);
                        return f.getDouble(player);
                    } catch (NoSuchFieldException ignored2) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static String str(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }
}
