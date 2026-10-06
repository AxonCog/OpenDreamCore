/**
 * 脚本互操作钩子（第四支柱的共享层半边）。
 *
 * <p>本包职责：命名空间登记（三语名 → 同一实现）、策略判定（默认全开）、调用句柄缓存。
 * 语言本身的解析与执行在 {@code com.opendreamcore.script}（core-c），不在本包。
 *
 * <p>允许依赖：无（只 {@code java.*}）。不许 import net.minecraft.*。
 */
package com.opendreamcore.core.script;
