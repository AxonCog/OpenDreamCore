/**
 * 注解声明（第一、二支柱的「声明侧」）。
 *
 * <p>本包职责：只放注解类型与它们的语义说明，**不含任何实现**。
 *
 * <p>保留策略统一为 {@code SOURCE}：运行期不需要这些注解 —— 构建期由 {@code tools/odcindex}
 * 扫出来写成 {@code META-INF/odc-services.idx}，运行期只读索引（见
 * {@link com.opendreamcore.core.registry.ServiceIndex}）。
 *
 * <p>允许依赖：{@link com.opendreamcore.core.descriptor}（注解默认值要用到 Side/Sync）。
 * 不许依赖 mc、各 target、业务包。
 */
package com.opendreamcore.core.annotations;
