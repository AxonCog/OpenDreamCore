package com.opendreamcore.model.anim;

/**
 * 模型动画意图（全局当前值）：脚本/命令（Model.播放动画 / 相机时间轴事件点）写入，
 * 各代客户端胶水在渲染帧读取并驱动动画运行时。纯 Java 8、无 MC 依赖、线程安全。
 *
 * <p>「意图」语义：写入即生效（版本号单调递增），不做排队/混合——动画混合是各代
 * 动画运行时（P3 的 AnimationIR 求值器）自己的事，这里只传"当前想放哪个"。
 */
public final class ModelAnimState {

    private static final ModelAnimState INSTANCE = new ModelAnimState();

    private volatile String animId;
    private volatile long version;

    private ModelAnimState() {
    }

    public static ModelAnimState get() {
        return INSTANCE;
    }

    /**
     * 设置当前动画意图。
     *
     * @return 本次的版本号（≥1）
     */
    public long set(String id) {
        if (id == null || id.trim().isEmpty()) {
            return clear();
        }
        animId = id.trim();
        return ++version;
    }

    /** 清除意图（回到各代默认动画），返回清除后的版本号。 */
    public long clear() {
        animId = null;
        return ++version;
    }

    /** 当前意图（null = 无覆盖，用默认动画）。 */
    public String current() {
        return animId;
    }

    /** 版本号（单调递增；胶水可据此判断"有没有新意图"）。 */
    public long version() {
        return version;
    }
}
