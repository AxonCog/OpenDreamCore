package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import org.lwjgl.opengl.GL11;

import com.opendreamcore.client.spi.EntityPainter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体真身画笔（164）：临时实体不进世界，建一次缓存住，
 * 每帧按面板锚点摆进 GUI 矩阵里画。
 *
 * 这代还没有 JsonToNBT（1.7 才引入），entity 段的 nbt 快照
 * 解析不了，只按 type 建；村民职业这类自定义等升级再说。
 */
final class EntityPainter164 implements EntityPainter {

    /** 缓存池：key = 类型ID。退出世界整个清掉（实体绑 world）。 */
    private static final Map<String, Entity> CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean render(String typeId, String nbt, double px, double py, double scale, double yaw,
                          boolean orthographic, boolean lookAtPlayer,
                          String name, boolean nameVisible, boolean glowing) {
        Minecraft mc = Minecraft.getMinecraft();
        if (RenderManager.instance == null || mc.theWorld == null) {
            return false;
        }
        Entity entity = obtain(typeId, mc);
        if (entity == null) {
            return false;
        }

        // 名牌走 DataWatcher（索引 2=名字文本，3=常显开关）
        String shownName = name;
        if (shownName == null && nameVisible) {
            shownName = entity.getEntityName();
        }
        boolean showTag = shownName != null && !shownName.isEmpty() && nameVisible;
        entity.getDataWatcher().updateObject(2,
                shownName == null || shownName.isEmpty() ? "" : shownName);
        entity.getDataWatcher().updateObject(3, (byte) (showTag ? 1 : 0));

        // 视线：正对 = 看玩家反方向压平；追踪 = 眼睛对眼睛
        if (orthographic) {
            entity.setLocationAndAngles(0, 0, 0,
                    mc.thePlayer != null ? mc.thePlayer.rotationYaw + 180.0F : 0.0F, 0.0F);
        } else if (lookAtPlayer && mc.thePlayer != null && entity instanceof EntityLiving) {
            ((EntityLiving) entity).getLookHelper().setLookPosition(
                    mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ,
                    30.0F, 30.0F);
        }

        RenderManager rm = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(px, py, 50.0);
        GL11.glScaled(scale, scale, -scale);
        GL11.glEnable(GL11.GL_LIGHTING);
        try {
            rm.renderEntityWithPosYaw(entity, 0.0, 0.0, 0.0, (float) yaw, 0.0F);
        } finally {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
        }
        return true;
    }

    /**
     * 世界画布相位：字节码验过 renderEntityWithPosYaw 不扣相机位，直接在当前矩阵
     * 原点落笔，画布局部系和 GUI 一样 x 右/y 下/z 朝镜头，配方照搬。
     * 只有 z 抬升不同：画布 1px = 0.025 格，屏幕的 50px 到这儿是 1.25 格，
     * 换 2px（0.05 格）刚好贴在面板前面一点点。
     */
    @Override
    public boolean renderWorld(String typeId, String nbt, double px, double py, double scale, double yaw,
                               boolean orthographic, boolean lookAtPlayer,
                               String name, boolean nameVisible, boolean glowing) {
        Minecraft mc = Minecraft.getMinecraft();
        if (RenderManager.instance == null || mc.theWorld == null) {
            return false;
        }
        Entity entity = obtain(typeId, mc);
        if (entity == null) {
            return false;
        }

        String shownName = name;
        if (shownName == null && nameVisible) {
            shownName = entity.getEntityName();
        }
        boolean showTag = shownName != null && !shownName.isEmpty() && nameVisible;
        entity.getDataWatcher().updateObject(2,
                shownName == null || shownName.isEmpty() ? "" : shownName);
        entity.getDataWatcher().updateObject(3, (byte) (showTag ? 1 : 0));

        if (orthographic) {
            entity.setLocationAndAngles(0, 0, 0,
                    mc.thePlayer != null ? mc.thePlayer.rotationYaw + 180.0F : 0.0F, 0.0F);
        } else if (lookAtPlayer && mc.thePlayer != null && entity instanceof EntityLiving) {
            ((EntityLiving) entity).getLookHelper().setLookPosition(
                    mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ,
                    30.0F, 30.0F);
        }

        RenderManager rm = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(px, py, 2.0);
        GL11.glScaled(scale, scale, -scale);
        GL11.glEnable(GL11.GL_LIGHTING);
        try {
            rm.renderEntityWithPosYaw(entity, 0.0, 0.0, 0.0, (float) yaw, 0.0F);
        } finally {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
        }
        return true;
    }

    /** 拿实体：缓存命中直接用；未命中按 type 名建。 */
    private static Entity obtain(String typeId, Minecraft mc) {
        Entity cached = CACHE.get(typeId);
        if (cached != null) {
            return cached;
        }
        // 164 的 EntityList 只认不带前缀的名字
        String shortName = typeId.contains(":")
                ? typeId.substring(typeId.indexOf(':') + 1) : typeId;
        Entity created = EntityList.createEntityByName(shortName, mc.theWorld);
        if (created == null) {
            return null;
        }
        created.setPosition(0, 0, 0);
        CACHE.put(typeId, created);
        return created;
    }

    /** 退出世界清缓存：实体绑着旧 world 引用，留着就是泄漏。 */
    static void dropCache() {
        CACHE.clear();
    }
}
