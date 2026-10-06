package com.opendreamcore.camera;

/**
 * 相机接管的让位决策（M1.4）。
 *
 * <p>为什么要有「让位」这一层：detached 直接驱动是**抢**原版相机的最终话语权
 * （我们写在 {@code Camera.setup} 的 TAIL 之后）。别的模组（自由视角、回放、部分光影组合包）
 * 可能也在同一位置写姿态，谁后写谁赢——硬抢的结果是两边都抖。所以按配置让位：
 * 判断「相机是不是已经被别人占了」，占着就不抢，退回 M1.2 的临时实体路线
 * （那条路只换「相机跟谁走」，不碰 {@code Camera} 内部状态，天然能共存）。
 *
 * <p>本类只做**纯判断**（不触 MC），MC 侧的取证（{@code Minecraft.getCameraEntity()}）
 * 由客户端层的调用方喂进来——这样决策表能完整单测。
 */
public final class CameraTakeover {

    /** 决策结果。除 {@link #TAKE_OVER} 外都要退回 M1.2 路线，并给玩家一句中文说明。 */
    public enum Decision {
        /** 接管：走 detached 直接驱动。 */
        TAKE_OVER,
        /** 配置里关掉了直接驱动。 */
        DISABLED,
        /** 拿不到相机写入面（混入没注册 / Camera 被别的模组换掉）→ 只能走旧路线。 */
        NO_ACCESS,
        /** 相机已被别的模组接管，按 yield 让位。 */
        YIELD_OTHERS,
        /** 装了光影，按 yield_with_shader 让位。 */
        YIELD_SHADER
    }

    private CameraTakeover() {
    }

    /**
     * @param s               运镜设置（null 视为默认）
     * @param accessAvailable 相机是否实现了 {@link CameraAccess}（混入生效与否）
     * @param othersOwn       是否已有别的接管者（原版判据：{@code cameraEntity} 不是玩家）
     * @param shaderEnv       是否检测到光影环境
     */
    public static Decision decide(CameraSettings s, boolean accessAvailable, boolean othersOwn, boolean shaderEnv) {
        CameraSettings set = s == null ? CameraSettings.defaults() : s;
        if (!set.detached) {
            return Decision.DISABLED;
        }
        if (!accessAvailable) {
            return Decision.NO_ACCESS;
        }
        if (othersOwn && set.yieldToOthers) {
            return Decision.YIELD_OTHERS;
        }
        if (shaderEnv && set.yieldToShader) {
            return Decision.YIELD_SHADER;
        }
        return Decision.TAKE_OVER;
    }

    /** 决策的中文原因（聊天栏/日志用）。 */
    public static String why(Decision d) {
        switch (d) {
            case DISABLED:
                return "配置已关闭相机直接驱动";
            case NO_ACCESS:
                return "相机写入面不可用（混入未生效）";
            case YIELD_OTHERS:
                return "相机已被其它模组接管，按让位策略退让";
            case YIELD_SHADER:
                return "检测到光影环境，按让位策略退让";
            default:
                return "直接驱动";
        }
    }

    /**
     * 光影环境探测：只看类在不在，不触 MC、不加载类（initialize=false）。
     * 覆盖 Iris 两代包名与 OptiFine；纯类名探测在两端（fabric/neoforge）都成立。
     */
    public static boolean shaderEnvironment() {
        return present("net.coderbot.iris.Iris")
                || present("net.irisshaders.iris.Iris")
                || present("net.optifine.Config");
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, CameraTakeover.class.getClassLoader());
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }
}
