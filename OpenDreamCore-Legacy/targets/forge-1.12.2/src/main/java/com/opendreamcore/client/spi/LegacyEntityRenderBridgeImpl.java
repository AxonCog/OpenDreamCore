package com.opendreamcore.client.spi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;

/**
 * 1212 的 GUI 实体桥：原版背包模型方法 GuiInventory.drawEntityOnScreen。
 */
public final class LegacyEntityRenderBridgeImpl implements LegacyEntityRenderBridge {

    @Override
    public Object resolveEntity(String ref) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return null;
        }
        if (ref == null || ref.isEmpty() || "owner".equalsIgnoreCase(ref.trim())) {
            return mc.player;
        }
        String r = ref.trim();
        for (EntityPlayer p : mc.world.playerEntities) {
            if (p.getName().equals(r)) {
                return p;
            }
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(r);
            for (Entity e : mc.world.loadedEntityList) {
                if (e.getUniqueID().equals(uuid)) {
                    return e;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Object dummyFor(String model) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return null;
        }
        String m = model == null ? "player" : model.trim().toLowerCase(Locale.ROOT);
        switch (m) {
            case "player":
                return mc.player;
            case "armor_stand":
                return new EntityArmorStand(mc.world, 0, 0, 0);
            case "zombie":
                return new EntityZombie(mc.world);
            default: {
                Entity e = net.minecraft.entity.EntityList.createEntityByIDFromName(
                        new ResourceLocation(m), mc.world);
                return e == null ? new EntityArmorStand(mc.world, 0, 0, 0) : e;
            }
        }
    }

    @Override
    public void drawEntity(int cx, int cy, float scale, float yaw, float pitch, Object entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        double mx = Math.tan(Math.toRadians(yaw - 180.0)) * 40.0;
        double my = Math.tan(Math.toRadians(pitch)) * 40.0;
        int s = Math.max(1, (int) (30.0f * (scale <= 0 ? 1.0f : scale)));
        GuiInventory.drawEntityOnScreen(cx, cy, s, (float) mx, (float) my, (EntityLivingBase) entity);
    }
}