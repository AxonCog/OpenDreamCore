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

    /** 按 id 取实体类型：直调注册表（1.21.9+ 起 ResourceLocation 已改名 Identifier）。 */
    private static Object resolveEntityType(String id) {
        net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.tryParse(id);
        if (rl == null) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(rl);
    }

    /** 造展示实体：1.21.5+ 的 create 需要 EntitySpawnReason。 */
    private static Object createEntity(net.minecraft.world.entity.EntityType<?> type,
                                       net.minecraft.world.level.Level level) {
        return type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
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