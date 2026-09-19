package com.opendreamcore.client.spi;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderTypeBuffers;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Locale;

/**
 * 1165 的 GUI 实体桥：EntityRendererManager.render 摆进 GUI 矩阵画。
 */
public final class LegacyEntityRenderBridgeImpl implements LegacyEntityRenderBridge {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    @Override
    public Object resolveEntity(String ref) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        if (ref == null || ref.isEmpty() || "owner".equalsIgnoreCase(ref.trim())) {
            return mc.player;
        }
        String r = ref.trim();
        for (PlayerEntity p : mc.level.players()) {
            if (p.getName().getString().equals(r)) {
                return p;
            }
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(r);
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.getUUID().equals(uuid)) {
                    return e;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Object dummyFor(String model) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        String m = model == null ? "player" : model.trim().toLowerCase(Locale.ROOT);
        switch (m) {
            case "player":
                return mc.player;
            case "armor_stand":
                return new ArmorStandEntity(mc.level, 0, 0, 0);
            case "zombie":
                return new ZombieEntity(mc.level);
            default: {
                Entity e = EntityType.byString(m).map(t -> t.create(mc.level)).orElse(null);
                return e == null ? new ArmorStandEntity(mc.level, 0, 0, 0) : e;
            }
        }
    }

    @Override
    public void drawEntity(int cx, int cy, float scale, float yaw, float pitch, Object entity) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRendererManager dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null || mc.level == null) {
            return;
        }
        RenderTypeBuffers buffers = mc.renderBuffers();
        IRenderTypeBuffer.Impl source = buffers.bufferSource();
        MatrixStack stack = new MatrixStack();
        stack.translate(cx, cy, 100.0);
        float s = scale <= 0 ? 30.0f : 30.0f * scale;
        stack.scale(s, s, 1.0f);
        LivingEntity le = (LivingEntity) entity;
        float b0 = le.yBodyRot;
        float y0 = le.yRot;
        float x0 = le.xRot;
        le.yBodyRot = yaw;
        le.yRot = yaw;
        le.xRot = pitch;
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(le, 0.0, 0.0, 0.0, yaw, 0.0F, stack, source, 0xF000F0);
        } finally {
            dispatcher.setRenderShadow(true);
            le.yBodyRot = b0;
            le.yRot = y0;
            le.xRot = x0;
        }
        source.endBatch();
    }
}