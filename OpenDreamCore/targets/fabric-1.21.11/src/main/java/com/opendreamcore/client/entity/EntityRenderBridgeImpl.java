package com.opendreamcore.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie; // 1.21.11 起 monster 子包小写化
import net.minecraft.world.entity.player.Player;

/**
 * 1.20.1 的实体渲染桥：老端 InventoryScreen.renderEntityInInventory 还在原样位置，
 * 直接类型化调用，不玩反射。
 */
public final class EntityRenderBridgeImpl implements EntityRenderBridge {

    @Override
    public Object resolveEntity(String ref) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        if (ref == null || ref.isBlank() || "owner".equalsIgnoreCase(ref.trim())) {
            return mc.player;
        }
        String r = ref.trim();
        for (Player p : mc.level.players()) {
            if (p.getName().getString().equals(r)) {
                return p;
            }
        }
        // UUID 查询：老端 Level.getEntity 收 int 实体 id，UUID 得遍历
        try {
            java.util.UUID uuid = java.util.UUID.fromString(r);
            for (var e : mc.level.entitiesForRendering()) {
                if (e.getUUID().equals(uuid)) {
                    return e;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    @Override
    public Object dummyFor(String model) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        String m = model == null ? "player" : model.trim().toLowerCase(java.util.Locale.ROOT);
        switch (m) {
            case "player":
                return mc.player;
            case "armor_stand":
                return new ArmorStand(mc.level, 0, 0, 0);
            case "zombie":
                return new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, mc.level);
            default: {
                // 实体类型解析走反射：1.21.2+ ENTITY_TYPE.get 返回 Optional，老端直接返回类型
                Object type = resolveEntityType(m);
                if (type instanceof net.minecraft.world.entity.EntityType<?> et) {
                    Object e = createEntity(et, mc.level);
                    if (e != null) {
                        return e;
                    }
                }
                return new ArmorStand(mc.level, 0, 0, 0);
            }
        }
    }

    /**
     * 按 id 反射取 EntityType：兼容老端直接返回实体类型 / 新版返回 Optional<Reference>；
     * create 签名各版本漂移（1.21.8+ 加 SpawnReason），统一反射创建。
     */
    private static Object resolveEntityType(String id) {
        try {
            Class<?> regClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object reg = regClass.getField("ENTITY_TYPE").get(null);
            Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Object rl = rlClass.getMethod("tryParse", String.class).invoke(null, id);
            Object got = reg.getClass().getMethod("get", Object.class).invoke(reg, rl);
            if (got instanceof java.util.Optional<?> opt) {
                return opt.map(o -> {
                    try {
                        return o.getClass().getMethod("value").invoke(o);
                    } catch (Exception e) {
                        return null;
                    }
                }).orElse(null);
            }
            return got;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 反射创建实体：找单参 create 且参数能接收该 level 的重载。 */
    private static Object createEntity(Object type, Object level) {
        try {
            for (var m : type.getClass().getMethods()) {
                if (m.getName().equals("create") && m.getParameterCount() == 1) {
                    if (m.getParameterTypes()[0].isAssignableFrom(level.getClass())) {
                        return m.invoke(type, level);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void drawEntity(GuiGraphics g, int cx, int cy, float scale, float yaw, float pitch,
                           Object entity, int alpha, boolean hideName) {
        if (!(entity instanceof LivingEntity e)) {
            return;
        }
        int s = Math.max(1, (int) (30.0f * (scale <= 0 ? 1.0f : scale)));
        // 鼠标跟随由原版 FollowsMouse 内部完成：把 yaw/pitch 反算成它的鼠标输入
        // （原版 yaw = 180 + atan(mx/40)*40、pitch = atan(my/40)*20，这里逆推 mx/my）
        double mx = Math.tan(Math.toRadians(yaw - 180.0)) * 40.0;
        double my = Math.tan(Math.toRadians(pitch)) * 40.0;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, s,
                (int) Math.round(mx), (int) Math.round(my), 0.0F, 0.0F, 0.0F, e);
    }
}