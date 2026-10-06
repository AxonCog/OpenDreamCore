package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraRule;
import com.opendreamcore.client.CompatRender;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * 运镜与 MC 之间的胶水层（M1.2）。
 *
 * <p>播放器只管时间轴与状态机，凡是直接碰 MC 类的东西都收在这里：造相机实体、切视角模式、
 * 藏 HUD、往聊天栏发一行。好处有二：跨版本核对时只看这一个文件；M1.4 换成 detached 直接驱动
 * 相机时，改的也是这里的实现，不动播放逻辑。
 *
 * <p>唯一一处不能直调的地方是 {@code EntityType.create}：它的签名跨版本漂移
 * （1.20.1/1.21.1 单参 {@code create(Level)}，1.21.4+ 双参 {@code create(Level, EntitySpawnReason)}），
 * 所以走 {@link CompatRender#invokeByShape} 按形状挑重载，并且在双参失败时退回单参。
 */
final class CameraMcBridge {

    private CameraMcBridge() {
    }

    /**
     * 造一个「不加入世界」的相机实体（盔甲架）。
     * 不加进世界是刻意的：它只是个姿态载体，MC 的相机逻辑只读它的位置/朝向。
     */
    static Entity createBody(Object level) {
        Object reason = spawnReasonCommand();
        Object made = reason != null
                ? CompatRender.invokeByShape(EntityType.ARMOR_STAND, "create", new Object[]{level, reason})
                : CompatRender.invokeByShape(EntityType.ARMOR_STAND, "create", new Object[]{level});
        if (made == null) {
            made = CompatRender.invokeByShape(EntityType.ARMOR_STAND, "create", new Object[]{level});
        }
        return made instanceof Entity e ? e : null;
    }

    /** {@code EntitySpawnReason.COMMAND}（1.21.4+）；老版本没有这个类 → null，改走单参 create。 */
    private static Object spawnReasonCommand() {
        try {
            Class<?> cls = CompatRender.resolveClass("net.minecraft.world.entity.EntitySpawnReason");
            if (cls != null && cls.isEnum()) {
                for (Object c : cls.getEnumConstants()) {
                    if ("COMMAND".equals(String.valueOf(c))) {
                        return c;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 类不存在（1.21.1 及更早）属正常分支，不记日志避免噪音
        }
        return null;
    }

    /** 按规则里的视角模式切原版视角；认不出来就不动（不改玩家当前设置）。 */
    static void applyPerspective(Minecraft mc, CameraRule rule) {
        CameraType t = cameraType(rule.perspective);
        if (t != null && mc.options != null) {
            mc.options.setCameraType(t);
        }
    }

    /** 按规则藏 HUD（原版 F1 的那个开关）。 */
    static void applyHideHud(Minecraft mc, CameraRule rule) {
        if (rule.hideHud && mc.options != null) {
            mc.options.hideGui = true;
        }
    }

    /**
     * 同步「上一 tick 的头部朝向」（{@code yHeadRotO = yHeadRot}）。
     *
     * <p>为什么得我们手动做：这个相机实体**没有加入世界**，世界的 tick 碰不到它，而原版只在
     * {@code LivingEntity.tick()} 里跑这一步；{@code Entity.setOldPosAndRot()} 也盖不到它
     * （那个方法只同步 xo/yo/zo 与 xRotO/yRotO）。不同步的话旧值永远是 0，渲染侧的插值就成了
     * 「从 0 度插到当前朝向」——画面上表现为每次开始播放/转向时视角先猛扭一下。
     *
     * <p>字段在 1.20.1 与 26.1.2 两侧都是 {@code public}（逐版本核对过），所以直调、不引反射。
     */
    static void syncOldHeadRot(Entity body) {
        if (body instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) body;
            le.yHeadRotO = le.yHeadRot;
        }
    }

    private static CameraType cameraType(String s) {
        if (s == null) {
            return null;
        }
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "first_person":
                return CameraType.FIRST_PERSON;
            case "third_person_back":
                return CameraType.THIRD_PERSON_BACK;
            case "third_person_front":
                return CameraType.THIRD_PERSON_FRONT;
            default:
                return null;
        }
    }

    /** 往聊天栏发一行；聊天栏不可用时静默（状态已在 latest.log 落过）。 */
    static void chat(String msg) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                mc.player.displayClientMessage(Component.literal(msg), false);
            }
        } catch (Throwable ignored) {
            // 见上
        }
    }
}
