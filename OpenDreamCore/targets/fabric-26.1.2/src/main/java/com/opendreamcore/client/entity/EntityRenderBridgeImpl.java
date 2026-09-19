package com.opendreamcore.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * 26.1.2 实体渲染桥：渲染提到 extract 管线，鼠标跟随走
 * extractEntityInInventoryFollowsMouse（前四 int 是渲染矩形，p5 scale，后两 float 鼠标偏移）。
 */
public final class EntityRenderBridgeImpl implements EntityRenderBridge {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("OpenDreamCore");

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
                return new Zombie(mc.level);
            default: {
                // 任意注册表生物类型：Identifier + BuiltInRegistries 反射解析，识别不了回退盔甲架
                Object type = resolveEntityType26(m);
                if (type instanceof net.minecraft.world.entity.EntityType<?> et) {
                    Object e = createEntity26(et, mc.level);
                    if (e != null) {
                        return e;
                    }
                }
                return new ArmorStand(mc.level, 0, 0, 0);
            }
        }
    }

    @Override
    public void drawEntity(GuiGraphicsExtractor g, int cx, int cy, float scale, float yaw, float pitch,
                           Object entity, int alpha, boolean hideName) {
        if (!(entity instanceof LivingEntity e)) {
            return;
        }
        int s = Math.max(8, (int) (30.0f * (scale <= 0 ? 1.0f : scale)));
        // 渲染矩形：实体高约两倍宽（背包模型比例），中心 = 元素中心
        int x1 = cx - s / 2;
        int y1 = cy - s;
        int x2 = cx + s / 2;
        int y2 = cy + s;
        // 鼠标偏移：yaw/pitch 反算回 extract 的鼠标输入（原版 yaw = 180 + atan(m/40)*40 逆推）
        double mx = Math.tan(Math.toRadians(yaw - 180.0)) * 40.0;
        double my = Math.tan(Math.toRadians(pitch)) * 40.0;
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, s,
                0.0F, (float) mx, (float) my, e);
    }

    /** 26.1.2 反射取 EntityType：Identifier 键 + BuiltInRegistries，get 可能返回 Optional<Reference>。 */
    private static Object resolveEntityType26(String id) {
        try {
            Class<?> regClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object reg = regClass.getField("ENTITY_TYPE").get(null);
            Class<?> idClass = Class.forName("net.minecraft.resources.Identifier");
            Object key = idClass.getMethod("of", String.class).invoke(null, id);
            Object got = reg.getClass().getMethod("get", Object.class).invoke(reg, key);
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
            LOGGER.warn("[ODC] 实体类型反射解析失败（{}）: {}", id, t.toString());
            return null;
        }
    }

    /** 26.1.2 反射创建实体：单参 create 且参数能接收该 level。 */
    private static Object createEntity26(Object type, Object level) {
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
}