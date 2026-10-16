package com.opendreamcore.core.addons;

/**
 * addons jar 的 SPI 标记接口：实现类放在 addons/*.jar 里，由 ServiceLoader 发现。
 *
 * <p>本接口活在共享层（父加载器），实现类活在子加载器 —— reload 时子加载器整组丢弃重建，
 * 本接口与注册表不受影响（addons 底座不依赖任何业务类型，model 只是第一个消费者）。
 */
public interface AddonProvider {

    /** 扩展唯一 id（重复 id 后到者拒绝，先到者优先）。 */
    String id();

    /** 扩展类型（如 model_format / script / 未来任意模块）。 */
    String type();

    /** 版本描述。 */
    String version();
}
