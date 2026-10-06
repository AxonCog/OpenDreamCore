/**
 * 注册表与编译期索引（第一、二支柱的「运行侧」）。
 *
 * <p>本包职责：读 {@code META-INF/odc-services.idx}（一次 IO、零扫描、零注解反射）、把系统实例
 * 装进 {@link com.opendreamcore.core.registry.SystemRegistry}、按 id 应用整份规则集。
 *
 * <p>允许依赖：{@link com.opendreamcore.core.descriptor}。不许依赖 capability / script / 业务包，
 * 也不许 import net.minecraft.*。
 */
package com.opendreamcore.core.registry;
