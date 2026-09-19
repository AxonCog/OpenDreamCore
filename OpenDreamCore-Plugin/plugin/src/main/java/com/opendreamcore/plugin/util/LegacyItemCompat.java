package com.opendreamcore.plugin.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 老服物品兼容层：Material.isAir() 是 1.13+ 的 API，Material.getKey() 是
 * 1.11+ 的（1.8 压根没有键位）。这里全部用"名字判断 + 反射"兜底，让同一份
 * 代码从 1.8 跑到最新版，缺哪个 API 走哪个分支。
 */
public final class LegacyItemCompat {

    private LegacyItemCompat() {
    }

    /** 空物品判定：1.13+ 走 isAir，老服按 AIR/名字兜底（null 安全）。 */
    public static boolean isAir(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        Material type = stack.getType();
        if (type == null) {
            return true;
        }
        try {
            return type.isAir();
        } catch (NoSuchMethodError old) {
            String name = type.name();
            return "AIR".equals(name) || "LEGACY_AIR".equals(name);
        }
    }

    /** 物品键路径（minecraft:stone）：1.11+ 有 getKey，1.8 直接用枚举名小写。 */
    public static String key(ItemStack stack) {
        if (isAir(stack)) {
            return "";
        }
        return key(stack.getType());
    }

    /** 材质键路径：同上，方块/物品的 Material 共用这一套。 */
    public static String key(Material type) {
        if (type == null) {
            return "";
        }
        try {
            return type.getKey().toString();
        } catch (NoSuchMethodError old) {
            return type.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * 玩家视线落点方块：新服有 rayTrace 语义的 API，老服只有标废弃的
     * getTargetBlock。新版 catch 的是 NoSuchMethodError，所以调用处别再
     * 自己包一层——老版本直接走废弃分支，同一个口子进出。
     */
    /**
     * 玩家延迟：Paper 有 getPing()，Spigot 1.12.2 及更早只有 NMS 字段
     * EntityPlayer.ping。反射两连招都拿不到才认输填 0，别让占位符
     * 链路在老服上炸 NoSuchMethodError 把整个 query 拖死。
     */
    public static long ping(Player p) {
        if (p == null) {
            return 0L;
        }
        try {
            Object nms = p.getClass().getMethod("getHandle").invoke(p);
            Object f = nms.getClass().getField("ping").get(nms);
            if (f instanceof Number) {
                return ((Number) f).longValue();
            }
            return 0L;
        } catch (Throwable noField) {
            try {
                Object r = p.getClass().getMethod("getPing").invoke(p);
                return r instanceof Number ? ((Number) r).longValue() : 0L;
            } catch (Throwable t) {
                return 0L;
            }
        }
    }

    public static Block targetBlock(Player p, int dist) {
        if (p == null) {
            return null;
        }
        try {
            return p.getTargetBlockExact(dist);
        } catch (NoSuchMethodError old) {
            try {
                java.util.Set<Material> air = new java.util.HashSet<>(
                        java.util.Collections.singletonList(Material.AIR));
                return p.getTargetBlock(air, dist);
            } catch (Throwable t) {
                // 老服连废弃版都异常时认输，返回空让上层给默认值
                return null;
            }
        }
    }
}
