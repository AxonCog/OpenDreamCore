package com.opendreamcore.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 世界侧卡片（手持/掉落物贴图）的朝向修正：手持坐标系里卡片法线沿手臂方向，
 * 第一人称会边缘对相机而不可见。改用世界空间广告牌——位置取原版变换的平移分量
 * （物品在哪就画哪），基向量取主相机右/上方向（面片永远正对玩家）。
 * 任何一环失败返回 null，调用方回退原矩阵路径（GUI/展示框场景本来就不需要修正）。
 */
public final class Billboard {

    private Billboard() {
    }

    public static Matrix4f matrix(Matrix4f original) {
        try {
            Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (cam == null || cam.rotation() == null) {
                return null;
            }
            Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cam.rotation());
            Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cam.rotation());
            Vector3f normal = new Vector3f(right).cross(up);
            Vector3f t = original.getTranslation(new Vector3f());
            return new Matrix4f(
                    right.x(), right.y(), right.z(), 0.0F,
                    up.x(), up.y(), up.z(), 0.0F,
                    normal.x(), normal.y(), normal.z(), 0.0F,
                    t.x(), t.y(), t.z(), 1.0F);
        } catch (Throwable t) {
            return null;
        }
    }
}
