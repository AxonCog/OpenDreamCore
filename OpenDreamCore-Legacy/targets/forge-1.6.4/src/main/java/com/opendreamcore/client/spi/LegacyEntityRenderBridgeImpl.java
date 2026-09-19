package com.opendreamcore.client.spi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * 164 的 GUI 实体桥：RenderManager.renderEntityWithPosYaw 摆进 GUI 矩阵画。
 */
public final class LegacyEntityRenderBridgeImpl implements LegacyEntityRenderBridge {

    @Override
    public Object resolveEntity(String ref) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return null;
        }
        if (ref == null || ref.isEmpty() || "owner".equalsIgnoreCase(ref.trim())) {
            return mc.thePlayer;
        }
        String r = ref.trim();
        for (EntityPlayer p : (java.util.List<EntityPlayer>) mc.theWorld.playerEntities) {
            if (p.getCommandSenderName().equals(r)) {
                return p;
            }
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(r);
            for (Entity e : (java.util.List<Entity>) mc.theWorld.loadedEntityList) {
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
        if (mc == null || mc.theWorld == null) {
            return null;
        }
        String m = model == null ? "player" : model.trim().toLowerCase(Locale.ROOT);
        switch (m) {
            case "player":
                return mc.thePlayer;
            case "armor_stand":
                return new EntityZombie(mc.theWorld);
            case "zombie":
                return new EntityZombie(mc.theWorld);
            default: {
                Entity e = net.minecraft.entity.EntityList.createEntityByName(m, mc.theWorld);
                return e == null ? new EntityZombie(mc.theWorld) : e;
            }
        }
    }

    @Override
    public void drawEntity(int cx, int cy, float scale, float yaw, float pitch, Object entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase le = (EntityLivingBase) entity;
        float y0 = le.rotationYaw;
        float p0 = le.rotationPitch;
        le.rotationYaw = yaw;
        le.rotationPitch = pitch;
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 100.0F);
        float s = scale <= 0 ? 30.0f : 30.0f * scale;
        GL11.glScalef(s, s, -s);
        GL11.glRotatef((float) Math.toDegrees(pitch), 1.0F, 0.0F, 0.0F);
        GL11.glRotatef((float) Math.toDegrees(yaw), 0.0F, 1.0F, 0.0F);
        GL11.glEnable(GL11.GL_LIGHTING);
        try {
            RenderManager.instance.renderEntityWithPosYaw(le, 0.0, 0.0, 0.0, yaw, 0.0F);
        } finally {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
            le.rotationYaw = y0;
            le.rotationPitch = p0;
        }
    }
}