package com.opendreamcore.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 播放期姿态 IO 小助手（M1.2~M1.5 共用）：替身实体写回、相机视角捕获、玩家本体复位。
 * 全是纯静态、无状态的搬运工；播放状态机留在 {@link CameraPlayback}。
 */
final class CameraPoseIo {

    private CameraPoseIo() {
    }

/**
     * 把姿态写到相机实体上。y 要减掉实体自己的眼高——相机取的是
     * {@code getEyePosition()}，不减的话镜头会整体抬高一个盔甲架的身高。
     */
    static void applyPose(Entity body, double[] pos, double[] rot) {
        body.setPos(pos[0], pos[1] - body.getEyeHeight(), pos[2]);
        body.setYRot((float) rot[0]);
        body.setXRot((float) rot[1]);
        body.setYHeadRot((float) rot[0]);
    }

    /** 播放瞬间的相机视角（进/出过渡的端点）。 */
    static void captureCurrentView(Minecraft mc, double[] pos, double[] rot) {
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        pos[0] = cam.getX();
        pos[1] = cam.getY() + cam.getEyeHeight();
        pos[2] = cam.getZ();
        rot[0] = cam.getYRot();
        rot[1] = cam.getXRot();
    }

    /** 假人/锁输入：把玩家本体拉回播放瞬间的位置与朝向，并清掉速度累积。 */
    static void pinPlayer(Minecraft mc, CameraActive a) {
        CameraStateStack.Entry b = a.backup;
        if (b == null || mc.player == null) {
            return;
        }
        mc.player.setPos(b.px, b.py, b.pz);
        mc.player.setYRot(b.pYaw);
        mc.player.setXRot(b.pPitch);
        mc.player.setYHeadRot(b.pHeadYaw);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
    }
}
