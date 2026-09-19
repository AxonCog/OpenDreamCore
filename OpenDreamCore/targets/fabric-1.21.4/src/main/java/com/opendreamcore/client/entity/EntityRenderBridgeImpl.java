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
 * 1.21.1/1.21.4 实体渲染桥：八参 renderEntityInInventory（带中心偏移）。
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
                        .get(net.minecraft.resources.ResourceLocation.tryParse(m));
                if (type != null) {
                    // create 签名 1.21.4+ 漂移，统一反射调单参重载
                    Object e = createEntity(type, mc.level);
                    if (e != null) {
                        return e;
                    }
                }
                return new ArmorStand(mc.level, 0, 0, 0);
            }
        }
    }

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
            org.joml.Vector3f offset = new org.joml.Vector3f(
                    0.0F, e.getBbHeight() / 2.0F + s * 0.5F, 0.0F);
            InventoryScreen.renderEntityInInventory(g, (float) cx, (float) cy, s,
                    offset, pose, null, e);
        } finally {
            e.yBodyRot = b0;
            e.setYRot(y0);
            e.setXRot(x0);
            e.yHeadRot = h0;
            e.yHeadRotO = hb0;
        }
    }
}