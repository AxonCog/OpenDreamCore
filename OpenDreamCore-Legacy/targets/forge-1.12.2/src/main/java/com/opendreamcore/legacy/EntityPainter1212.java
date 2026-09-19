package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import com.opendreamcore.client.spi.EntityPainter;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体真身画笔：把 entity 元素变成屏幕上的村民/盔甲架。
 *
 * 思路和高版本 HoloEntityRender 一致——临时实体不进世界，
 * 建一次缓存住，每帧按面板锚点摆到 GUI 矩阵里画。GUI 里画实体的
 * 配方是玩家头渲染那套的变体：亮度关掉、视角 yaw 压到 180 度正对、
 * 模型自身的 RenderManager 管剩下的。
 */
final class EntityPainter1212 implements EntityPainter {

    /** 缓存池：key = 类型ID@nbt哈希。退出世界整个清掉（实体绑 world）。 */
    private static final Map<String, Entity> CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean render(String typeId, String nbt, double px, double py, double scale, double yaw,
                          boolean orthographic, boolean lookAtPlayer,
                          String name, boolean nameVisible, boolean glowing) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getRenderManager() == null || mc.world == null) {
            return false;
        }
        Entity entity = obtain(typeId, nbt, mc);
        if (entity == null) {
            return false;
        }

        // 名牌：自定义文本优先，其次看 nameVisible 要不要用实体自带名
        String shownName = name;
        if (shownName == null && nameVisible) {
            shownName = entity.getName();
        }
        entity.setCustomNameTag(shownName == null || shownName.isEmpty() ? "" : shownName);
        entity.setAlwaysRenderNameTag(shownName != null && !shownName.isEmpty() && nameVisible);
        entity.setGlowing(glowing);

        // 视线：正对 = 看玩家反方向压平；追踪 = 眼睛对眼睛。
        // getLookHelper 挂在 EntityLiving 上，普通 Entity 没有这口子
        if (orthographic) {
            entity.setLocationAndAngles(0, 0, 0, mc.player != null ? mc.player.rotationYaw + 180 : 0, 0);
        } else if (lookAtPlayer && mc.player != null
                && entity instanceof net.minecraft.entity.EntityLiving) {
            ((net.minecraft.entity.EntityLiving) entity).getLookHelper().setLookPosition(
                    mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ,
                    30.0F, 30.0F);
        }

        RenderManager rm = mc.getRenderManager();
        // GUI 实体渲染的固定视角：yaw 180 让模型正对镜头，pitch 轻微俯视
        float viewYaw = orthographic ? 180.0F : rm.playerViewY;
        float viewPitch = orthographic ? 0.0F : rm.playerViewX;

        GlStateManager.enableAlpha();
        // 平移到锚点（脚底中点）再缩放；z 抬一点避免和底衬/文字打架
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 50.0);
        GlStateManager.scale(scale, scale, -scale);
        GL11.glEnable(GL11.GL_LIGHTING);
        RenderHelper.enableStandardItemLighting();
        try {
            rm.renderEntity(entity, 0.0, 0.0, 0.0, (float) yaw, 0.0F, false);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GlStateManager.popMatrix();
        }
        return true;
    }

    /**
     * 世界画布相位：字节码验过 renderEntity 不扣相机位，直接在当前矩阵原点落笔，
     * 画布局部系和 GUI 一样 x 右/y 下/z 朝镜头，配方照搬。
     * 只有 z 抬升不同：画布 1px = 0.025 格，屏幕的 50px 到这儿是 1.25 格，
     * 换 2px（0.05 格）刚好贴在面板前面一点点。
     */
    @Override
    public boolean renderWorld(String typeId, String nbt, double px, double py, double scale, double yaw,
                               boolean orthographic, boolean lookAtPlayer,
                               String name, boolean nameVisible, boolean glowing) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getRenderManager() == null || mc.world == null) {
            return false;
        }
        Entity entity = obtain(typeId, nbt, mc);
        if (entity == null) {
            return false;
        }

        String shownName = name;
        if (shownName == null && nameVisible) {
            shownName = entity.getName();
        }
        entity.setCustomNameTag(shownName == null || shownName.isEmpty() ? "" : shownName);
        entity.setAlwaysRenderNameTag(shownName != null && !shownName.isEmpty() && nameVisible);
        entity.setGlowing(glowing);

        if (orthographic) {
            entity.setLocationAndAngles(0, 0, 0, mc.player != null ? mc.player.rotationYaw + 180 : 0, 0);
        } else if (lookAtPlayer && mc.player != null
                && entity instanceof net.minecraft.entity.EntityLiving) {
            ((net.minecraft.entity.EntityLiving) entity).getLookHelper().setLookPosition(
                    mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ,
                    30.0F, 30.0F);
        }

        GlStateManager.enableAlpha();
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 2.0);
        GlStateManager.scale(scale, scale, -scale);
        GL11.glEnable(GL11.GL_LIGHTING);
        RenderHelper.enableStandardItemLighting();
        try {
            mc.getRenderManager().renderEntity(entity, 0.0, 0.0, 0.0, (float) yaw, 0.0F, false);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GlStateManager.popMatrix();
        }
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
                NBTTagCompound tag = JsonToNBT.getTagFromJson(nbt);
                created = EntityList.createEntityFromNBT(tag, mc.world);
            } catch (NBTException e) {
                OdcLegacy.LOGGER.warn("[ODC] 实体 NBT 解析失败: {}", e.getMessage());
            }
        }
        if (created == null) {
            created = EntityList.createEntityByIDFromName(new ResourceLocation(normalized), mc.world);
        }
        if (created == null) {
            return null;
        }
        created.setPosition(0, 0, 0);
        CACHE.put(key, created);
        return created;
    }

    /** 退出世界清缓存：实体绑着旧 world 引用，留着就是泄漏。 */
    static void dropCache() {
        CACHE.clear();
    }
}
