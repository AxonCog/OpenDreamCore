/**
 * 能力总线（第三支柱）。
 *
 * <p>本包职责：把「跨 12 个版本 + 双加载器 + 附属可能不存在」这三种不确定性收口成**一次查询**
 * （{@link com.opendreamcore.core.capability.Capabilities#of}），以及代际特性开关
 * （{@link com.opendreamcore.core.capability.Features}）。
 *
 * <p>允许依赖：无（只 {@code java.*}）。不许 import net.minecraft.*：能力**接口**只允许出现
 * 全版本共有的类型或我们自己的 IR。
 */
package com.opendreamcore.core.capability;
