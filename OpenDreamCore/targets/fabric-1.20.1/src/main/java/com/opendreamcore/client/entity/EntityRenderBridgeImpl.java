package com.opendreamcore.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * 1.20.1 的实体渲染桥：基础版七参 renderEntityInInventory
 * (GuiGraphics, int x, int y, int scale, Quaternionf, Quaternionf, LivingEntity)。
 * 朝向走实体预转向，渲染后还原。
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
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .get(new net.minecraft.resources.ResourceLocation(m));
                if (type != null) {
                    var e = type.create(mc.level);
                    if (e != null) {
                        return e;
                    }
                }
                return new ArmorStand(mc.level, 0, 0, 0);
            }
        }
    }

    @Override
    public void drawEntity(GuiGraphics g, int cx, int cy, float scale, float yaw, float pitch,
                           Object entity, int alpha, boolean hideName) {
        if (!(entity instanceof LivingEntity e)) {
            return;
        }
        float s = scale <= 0 ? 30.0f : 30.0f * scale;
        float b0 = e.yBodyRot;
        float y0 = e.getYRot();
        float x0 = e.getXRot();
        float h0 = e.yHeadRot;
        float hb0 = e.yHeadRotO;
        e.yBodyRot = yaw;
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.yHeadRot = yaw;
        e.yHeadRotO = yaw;
        try {
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            pose.mul(new Quaternionf().rotateX((float) (Math.PI / 9)));
            InventoryScreen.renderEntityInInventory(g, cx, cy, Math.max(1, (int) s), pose, null, e);
        } finally {
            e.yBodyRot = b0;
            e.setYRot(y0);
            e.setXRot(x0);
            e.yHeadRot = h0;
            e.yHeadRotO = hb0;
        }
    }
}