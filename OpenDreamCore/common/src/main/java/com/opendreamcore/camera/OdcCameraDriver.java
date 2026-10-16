package com.opendreamcore.camera;

/**
 * 相机直接驱动（M1.4）。
 *
 * <p>职责只有一个：**记住「这台相机现在归我们开」，并在每帧把它写到位**。它刻意不碰 MC 类型——
 * 相机通过 {@link CameraAccess} 接口写、姿态通过 {@link PoseSource} 问播放器要，
 * 于是这套逻辑能用假相机 + 假姿态源完整单测（不用起客户端）。
 *
 * <p>调用时序（三个入口，全部由上层驱动）：
 * <ol>
 *   <li>{@link #takeOver}：播放器开播、且让位判定通过时调用；</li>
 *   <li>{@link #onFrame}：per-target 混入在 {@code Camera.setup}（26.1.2 是 {@code update/tick}）
 *       的 <b>TAIL</b> 调——必须在原版算完之后，否则姿态会被原版覆盖回去；</li>
 *   <li>{@link #release}：停止/中断/自动结束/世界切换时调用，把 {@code detached} 复位并放手。</li>
 * </ol>
 *
 * <p>身份用 {@code ==} 比：原版 {@code Camera} 是 {@code Minecraft} 持有的单例
 * （{@code gameRenderer.getMainCamera()}），谁被换掉、或者混入没生效，这里的引用就对不上，
 * 我们自然不写——不猜、不反射、不覆盖其他模组的相机。
 */
public final class OdcCameraDriver {

    /** 姿态来源：渲染帧问一次。返回 5 元组 {x,y,z,yRot,xRot}；null = 本帧不该由我们驱动。 */
    public interface PoseSource {
        double[] pose(float partialTick);
    }

    private static final OdcCameraDriver INSTANCE = new OdcCameraDriver();

    public static OdcCameraDriver get() {
        return INSTANCE;
    }

    /** 当前接管的相机；null = 没接管（所有入口都会先看它）。 */
    private CameraAccess owned;
    private PoseSource source;
    private String ownerId = "";
    private long frames;

    /** 包内可见：单测造独立实例，不碰单例（避免测试互相污染）。 */
    OdcCameraDriver() {
    }

    /** 接管。返回 false = 没接管（参数不全），调用方应退回 M1.2 的临时实体路线。 */
    public boolean takeOver(CameraAccess cam, PoseSource src, String ruleId) {
        if (cam == null || src == null) {
            return false;
        }
        this.owned = cam;
        this.source = src;
        this.ownerId = ruleId == null ? "" : ruleId;
        this.frames = 0L;
        // 立刻置一次：语义上从这一 tick 起相机就归我们开（原版下帧 setup 会把它改回去，
        // 但那之后就是我们的 TAIL 注入，所以外部观察者看到的始终是「已接管」）
        cam.odcCameraSetDetached(true);
        return true;
    }

    /** 放手：detached 复位 + 清所有权。没接管时是空操作；相机对象已失效也不抛。 */
    public void release() {
        CameraAccess cam = this.owned;
        this.owned = null;
        this.source = null;
        this.ownerId = "";
        this.frames = 0L;
        if (cam != null) {
            try {
                cam.odcCameraSetDetached(false);
            } catch (Throwable ignored) {
                // 世界已卸载时相机壳子可能已经不可用：复位的失败不该再往上抛
            }
        }
    }

    /** 这台相机是不是我们正在开的（混入的每帧入口用它过滤）。 */
    public boolean owns(CameraAccess cam) {
        return cam != null && cam == owned;
    }

    public boolean active() {
        return owned != null;
    }

    public long frames() {
        return frames;
    }

    public String ownerId() {
        return ownerId;
    }

    /**
     * 每帧写姿态（混入调用）。不是我们接管的那台、或姿态源说「本帧不管」，就一个字节都不写。
     */
    public void onFrame(CameraAccess cam, float partialTick) {
        if (cam == null || cam != owned || source == null) {
            return;
        }
        double[] p = source.pose(partialTick);
        if (p == null || p.length < 5) {
            return;
        }
        cam.odcCameraSetDetached(true);
        cam.odcCameraSetPosition(p[0], p[1], p[2]);
        cam.odcCameraSetRotation((float) p[3], (float) p[4]);
        frames++;
    }

    /** 诊断单行。 */
    public String statusLine() {
        if (owned == null) {
            return "未接管";
        }
        return "接管中（" + ownerId + "，已驱动 " + frames + " 帧）";
    }
}
