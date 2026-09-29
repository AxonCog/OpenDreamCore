package com.opendreamcore.client.render;

/**
 * 世界语义渲染类型的注册表：目标入口启动时注册自家实现，共享层只跟这里说话。
 *
 * <p>沿用项目里既有的桥注册模式（实体渲染桥、字形替换提供者都是这个形状）：共享层保持零版本
 * 依赖，版本相关的构造留在各自目标。没有实现时 {@link #provider()} 返回 null，
 * 调用方走原有路径。
 *
 * <p>另有一个用户可见开关 {@link #enabled()}：按目标决策，自建类型先作为可选项上线，
 * 实机对比确认更优之后再决定是否默认启用。开关默认关，因此本类上线本身不改变任何现有行为。
 */
public final class WorldRenderTypes {

    private static volatile WorldRenderTypeProvider provider;

    /** 自建世界语义类型开关（先作为实验路径；关闭时一律用原版世界语义类型）。 */
    private static volatile boolean enabled;

    private WorldRenderTypes() {
    }

    /** 注册实现（重复注册覆盖；传 null 表示撤销）。 */
    public static void register(WorldRenderTypeProvider p) {
        provider = p;
    }

    /** 当前实现；未注册返回 null。 */
    public static WorldRenderTypeProvider provider() {
        return provider;
    }

    /** 是否启用自建世界语义类型。 */
    public static boolean enabled() {
        return enabled;
    }

    /** 设置开关。 */
    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
