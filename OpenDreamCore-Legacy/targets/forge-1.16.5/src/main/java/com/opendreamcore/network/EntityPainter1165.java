package com.opendreamcore.network;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.opendreamcore.client.spi.EntityPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderTypeBuffers;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.registry.Registry;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体真身画笔（1165）：临时实体不进世界，建一次缓存住，
 * 每帧按面板锚点摆进 GUI 矩阵里画。
 *
 * 这代 render() 直接吃 MatrixStack，共享层推好的平移/缩放都在栈上，
 * 画完 endBatch 把缓冲吐到屏幕就行。
 */
public final class EntityPainter1165 implements EntityPainter {

    /** 缓存池：key = 类型ID@nbt哈希。退出世界整个清掉（实体绑 level）。 */
    private static final Map<String, Entity> CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean render(String typeId, String nbt, double px, double py, double scale, double yaw,
                          boolean orthographic, boolean lookAtPlayer,
                          String name, boolean nameVisible, boolean glowing) {
        Minecraft mc = Minecraft.getInstance();
        EntityRendererManager dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null || mc.level == null) {
            return false;
        }
        Entity entity = obtain(typeId, nbt, mc);
        if (entity == null) {
            return false;
        }

        // 名牌：自定义文本优先，其次看 nameVisible 要不要用实体自带名
        String shownName = name;
        if (shownName == null && nameVisible) {
            shownName = entity.getName().getString();
        }
        boolean showTag = shownName != null && !shownName.isEmpty() && nameVisible;
        entity.setCustomName(showTag ? new StringTextComponent(shownName) : null);
        entity.setCustomNameVisible(showTag);
        entity.setGlowing(glowing);

        // 视线：正对 = 朝向压平（面向镜头）；追踪 = 眼睛对眼睛
        if (orthographic) {
            entity.yRot = mc.player != null ? mc.player.yRot + 180.0F : 0.0F;
            entity.yRotO = entity.yRot;
        } else if (lookAtPlayer && mc.player != null && entity instanceof MobEntity) {
            ((MobEntity) entity).getLookControl().setLookAt(mc.player, 30.0F, 30.0F);
        }

        RenderTypeBuffers buffers = mc.renderBuffers();
        IRenderTypeBuffer.Impl source = buffers.bufferSource();
        // 发光轮廓要吃 OutlineLayerBuffer 的颜色口子，不然只画模型不发光
        if (glowing) {
            mc.renderBuffers().outlineBufferSource().setColor(0x6F, 0xE3, 0xC4, 0xFF);
        }

        MatrixStack stack = new MatrixStack();
        stack.translate((float) px, (float) py, 50.0F);
        stack.scale((float) scale, (float) scale, -(float) scale);
        try {
            dispatcher.setRenderShadow(false);
            dispatcher.render(entity, 0.0, 0.0, 0.0, (float) yaw, 0.0F, stack, source, 0xF000F0);
        } finally {
            dispatcher.setRenderShadow(true);
        }
        source.endBatch();
        return true;
    }

    /**
     * 世界画布相位：这代 dispatcher.render 也不扣相机位，直接在传入栈的
     * 原点落笔，画布局部系和 GUI 一样 x 右/y 下/z 朝镜头，配方照搬。
     * 只有 z 抬升不同：画布 1px = 0.025 格，屏幕的 50px 到这儿是 1.25 格，
     * 换 2px（0.05 格）刚好贴在面板前面一点点。
     */
    @Override
    public boolean renderWorld(String typeId, String nbt, double px, double py, double scale, double yaw,
                               boolean orthographic, boolean lookAtPlayer,
                               String name, boolean nameVisible, boolean glowing) {
        Minecraft mc = Minecraft.getInstance();
        EntityRendererManager dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null || mc.level == null) {
            return false;
        }
        Entity entity = obtain(typeId, nbt, mc);
        if (entity == null) {
            return false;
        }

        String shownName = name;
        if (shownName == null && nameVisible) {
            shownName = entity.getName().getString();
        }
        boolean showTag = shownName != null && !shownName.isEmpty() && nameVisible;
        entity.setCustomName(showTag ? new StringTextComponent(shownName) : null);
        entity.setCustomNameVisible(showTag);
        entity.setGlowing(glowing);

        if (orthographic) {
            entity.yRot = mc.player != null ? mc.player.yRot + 180.0F : 0.0F;
            entity.yRotO = entity.yRot;
        } else if (lookAtPlayer && mc.player != null && entity instanceof MobEntity) {
            ((MobEntity) entity).getLookControl().setLookAt(mc.player, 30.0F, 30.0F);
        }

        IRenderTypeBuffer.Impl source = mc.renderBuffers().bufferSource();
        if (glowing) {
            mc.renderBuffers().outlineBufferSource().setColor(0x6F, 0xE3, 0xC4, 0xFF);
        }

        MatrixStack stack = new MatrixStack();
        stack.translate((float) px, (float) py, 2.0F);
        stack.scale((float) scale, (float) scale, -(float) scale);
        try {
            dispatcher.setRenderShadow(false);
            dispatcher.render(entity, 0.0, 0.0, 0.0, (float) yaw, 0.0F, stack, source, 0xF000F0);
        } finally {
            dispatcher.setRenderShadow(true);
        }
        source.endBatch();
        return true;
    }

    /** 拿实体：缓存命中直接用；未命中创建并应用 NBT 快照。 */
    private static Entity obtain(String typeId, String nbt, Minecraft mc) {
        String key = typeId + "@" + (nbt == null ? 0 : nbt.hashCode());
        Entity cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String normalized = typeId.contains(":") ? typeId
                : "minecraft:" + typeId.toLowerCase(Locale.ROOT);
        Entity created = null;
        if (nbt != null && !nbt.isEmpty()) {
            try {
                // NBT 快照里带了 id 字段时 by() 直接还原整只实体
                CompoundNBT tag = JsonToNBT.parseTag(nbt);
                created = EntityType.by(tag)
                        .map(t -> t.create(mc.level))
                        .orElse(null);
            } catch (CommandSyntaxException e) {
                com.opendreamcore.OdcLegacy.LOGGER.warn("[ODC] 实体 NBT 解析失败: {}", e.getMessage());
            }
        }
        if (created == null) {
            created = EntityType.byString(normalized)
                    .map(t -> t.create(mc.level))
                    .orElse(null);
        }
        if (created == null) {
            return null;
        }
        created.moveTo(0.0, 0.0, 0.0, 0.0F, 0.0F);
        CACHE.put(key, created);
        return created;
    }

    /** 退出世界清缓存：实体绑着旧 level 引用，留着就是泄漏。 */
    public static void dropCache() {
        CACHE.clear();
    }
}
