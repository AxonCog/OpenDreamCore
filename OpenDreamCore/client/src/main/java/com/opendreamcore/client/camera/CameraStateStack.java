package com.opendreamcore.client.camera;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

/**
 * 运镜播放前后的客户端状态栈（M1.2）。
 *
 * <p>压栈的东西就是「播放期间会被我们改掉、结束后必须还回去」的那几项：相机实体、视角模式、
 * HUD 开关、玩家隐身标记，外加玩家本体的位置/朝向（假人与锁输入时每 tick 要拉回来）。
 *
 * <p>两条硬纪律（规范 §4.1「不能把玩家锁死」）：
 * <ul>
 *   <li>世界换了/退出了就**只清状态不写回**——旧世界的对象再写回去才是事故源；</li>
 *   <li>写回过程包在 try 里，写回失败也必须把栈清空，绝不让一个坏状态卡住后续播放。</li>
 * </ul>
 *
 * <p>单独成类的原因：备份/恢复是纯状态管理，和「算时间轴、推姿态」是两件事；分开之后
 * 播放器本体保持在 500 行以内（反臃肿规范 R1/R2）。
 */
final class CameraStateStack {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore");

    /** 恢复结果，供调用方决定记什么日志。 */
    enum Result {
        /** 没有可恢复的东西（栈空或 MC 不可用）。 */
        NOTHING,
        /** 已写回。 */
        RESTORED,
        /** 世界已切换/退出，只清了状态。 */
        SKIPPED_WORLD_CHANGED
    }

    /** 一条状态备份。字段对同包可见：假人拉回位置时播放器要读它。 */
    static final class Entry {
        Entity cameraEntity;
        CameraType cameraType;
        boolean hideGui;
        boolean playerInvisible;
        double px;
        double py;
        double pz;
        float pYaw;
        float pPitch;
        float pHeadYaw;
    }

    private final Deque<Entry> stack = new ArrayDeque<>();

    /** 快照当前客户端状态并压栈，返回该条备份（播放器要用它做假人拉回）。 */
    Entry push(Minecraft mc) {
        Entry b = new Entry();
        b.cameraEntity = mc.getCameraEntity();
        b.cameraType = mc.options != null ? mc.options.getCameraType() : null;
        b.hideGui = mc.options != null && mc.options.hideGui;
        b.playerInvisible = mc.player != null && mc.player.isInvisible();
        Entity p = mc.player;
        if (p != null) {
            b.px = p.getX();
            b.py = p.getY();
            b.pz = p.getZ();
            b.pYaw = p.getYRot();
            b.pPitch = p.getXRot();
            b.pHeadYaw = p.getYHeadRot();
        }
        stack.push(b);
        return b;
    }

    /** 栈顶（当前播放对应的那条备份），空栈返回 null。 */
    Entry peek() {
        return stack.peek();
    }

    boolean isEmpty() {
        return stack.isEmpty();
    }

    void clear() {
        stack.clear();
    }

    /**
     * 弹栈并把状态写回。
     *
     * @param playingLevel 播放时所处的世界对象；与当前世界不同则跳过写回（只清状态）
     */
    Result restore(Minecraft mc, Object playingLevel) {
        Entry b = stack.isEmpty() ? null : stack.pop();
        if (b == null || mc == null) {
            stack.clear();
            return Result.NOTHING;
        }
        if (playingLevel != null && (mc.level == null || mc.level != playingLevel)) {
            stack.clear();
            return Result.SKIPPED_WORLD_CHANGED;
        }
        try {
            if (b.cameraEntity != null) {
                mc.setCameraEntity(b.cameraEntity);
            } else if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
            if (mc.options != null) {
                if (b.cameraType != null) {
                    mc.options.setCameraType(b.cameraType);
                }
                mc.options.hideGui = b.hideGui;
            }
            if (mc.player != null && mc.player.isInvisible() != b.playerInvisible) {
                mc.player.setInvisible(b.playerInvisible);
            }
        } catch (Throwable t) {
            // 写回失败不能把栈留着：否则下一次播放会拿着过期状态继续写
            stack.clear();
            LOGGER.warning("运镜恢复写回失败: " + t);
        }
        return Result.RESTORED;
    }
}
