package com.opendreamcore.client.world;

import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.WorldHologram;
import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C3 第一波：世界全息 entity 组件渲染（自 WorldHologram 移出）。
 * 依赖的 WH 共享助手（holoNum 等）已放宽为 public，经 WorldHologram.xxx 调用。
 */
public final class HoloEntityRender {

    /** 实体缓存：cacheKey(元素id@nbtHash) → 临时实体（不进世界）。 */
    public static final Map<String, Entity> ENTITY_CACHE = new ConcurrentHashMap<>();

    private HoloEntityRender() {
    }

    /** 世界内实体渲染（entity 组件）：按实体类型创建临时实体（不进世界），全息位置展示。 */
    public static void renderEntity(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                    double[] drag, Map<String, Object> pageVars) {
        Map<?, ?> spec = com.opendreamcore.client.UiRenderer.propsMap(node, "entity");
        String typeId = com.opendreamcore.client.UiRenderer.str(spec.get("type"));
        if (typeId == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String nbt = com.opendreamcore.client.UiRenderer.str(spec.get("nbt"));
        String cacheKey = node.id() + "@" + (nbt == null ? "" : nbt.hashCode());
        Entity entity = ENTITY_CACHE.get(cacheKey);
        if (entity == null || !typeId.equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) {
            var type = com.opendreamcore.client.UiRenderer.entityType(
                    ResourceLocation.tryParse(typeId));
            if (type == null) {
                return;
            }
            entity = (Entity) CompatRender.invokeByShape(type, "create", new Object[]{mc.level});
            if (entity == null) {
                return;
            }
            // NBT 快照（创建时应用一次：村民职业/盔甲架姿势/狼项圈等任意实体数据）
            if (nbt != null && !nbt.isBlank()) {
                try {
                    Object parsed = CompatRender.parseNbtCompound(nbt);
                    if (parsed instanceof CompoundTag tag) {
                        CompatRender.entityLoad(entity, tag);
                    }
                } catch (Exception ignored) {
                    // NBT 解析失败按原样渲染
                }
            }
            ENTITY_CACHE.put(cacheKey, entity);
        }
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double scale = WorldHologram.holoNum(holo, "scale", 1.0, pageVars);
        double yaw = WorldHologram.holoNum(holo, "yaw", 0, pageVars);
        // 悬浮呼吸：entity.bob: true → 正弦上下浮动（幅度/速度可调）
        boolean bob = com.opendreamcore.client.UiRenderer.bool(spec.get("bob"), false);
        double bobAmp = com.opendreamcore.client.UiRenderer.num(spec.get("bobAmplitude"), 0.05);
        double bobSpeed = com.opendreamcore.client.UiRenderer.num(spec.get("bobSpeed"), 1.0);
        double bobOff = bob ? Math.sin(System.currentTimeMillis() / 1000.0 * bobSpeed) * bobAmp : 0;

        entity.setPos(x + (drag == null ? 0 : drag[0]), y + bobOff + (drag == null ? 0 : drag[1]), z + (drag == null ? 0 : drag[2]));
        // 正交朝向（entity.orthographic: true → 实体完全正对相机,像贴片一样无侧脸,UI 化展示）
        if (com.opendreamcore.client.UiRenderer.bool(spec.get("orthographic"), false)) {
            var euler = mc.gameRenderer.getMainCamera().rotation().getEulerAnglesYXZ(new org.joml.Vector3f());
            entity.setYRot((float) Math.toDegrees(euler.y) + 180.0F);
            entity.setXRot((float) -Math.toDegrees(euler.x));
        }
        // 头部/视线追踪（entity.lookAtPlayer: true → 实体始终看向玩家眼睛；否则静态 yaw）
        if (!com.opendreamcore.client.UiRenderer.bool(spec.get("orthographic"), false)
                && com.opendreamcore.client.UiRenderer.bool(spec.get("lookAtPlayer"), false) && mc.player != null) {
            entity.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    mc.player.getEyePosition());
        } else if (!com.opendreamcore.client.UiRenderer.bool(spec.get("orthographic"), false)) {
            entity.setYRot((float) yaw);
            entity.setXRot(0);
        }
        // 自定义名牌（entity.name + nameVisible）与发光轮廓（entity.glowing）
        String name = com.opendreamcore.client.UiRenderer.interpolate(
                node, com.opendreamcore.client.UiRenderer.str(spec.get("name")), pageVars);
        if (name != null && !name.isEmpty()) {
            entity.setCustomName(net.minecraft.network.chat.Component.literal(name));
            entity.setCustomNameVisible(com.opendreamcore.client.UiRenderer.bool(spec.get("nameVisible"), false));
        } else {
            entity.setCustomName(null);
        }
        entity.setGlowingTag(com.opendreamcore.client.UiRenderer.bool(spec.get("glowing"), false));

        pose.pushPose();
        pose.scale((float) scale, (float) scale, (float) scale);
        CompatRender.invokeByShape(mc.getEntityRenderDispatcher(), "render",
                new Object[]{entity, x + (drag == null ? 0 : drag[0]), y + bobOff + (drag == null ? 0 : drag[1]),
                        z + (drag == null ? 0 : drag[2]), entity.getYRot(), 1.0F, pose, buffers, 0xF000F0});
        pose.popPose();
    }
}
