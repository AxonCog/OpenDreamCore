/**
 * 描述符层（第一支柱）：系统元信息、字段声明、规则差异计算。
 *
 * <p>本包职责：纯数据结构 + 纯逻辑（Side / Sync / SystemDescriptor / RuleSchema / Field /
 * SystemImpl / RegisteredSystem / Diff）。**零 MC 依赖、零 IO、零线程**，可直接单测。
 *
 * <p>允许依赖：{@code java.*} 与 {@link com.opendreamcore.core.annotations}。
 * 不许依赖 registry / capability / script（依赖方向：registry → descriptor）。
 */
package com.opendreamcore.core.descriptor;
