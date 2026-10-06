package com.opendreamcore.core.descriptor;

import java.util.Map;

/**
 * 系统实现：**只写这两个方法**。差异计算、热重载、撤销顺序、异常兜底都由框架负责。
 *
 * <p>线程约定：{@code apply} / {@code revoke} 一律在**主线程**被调用（服务端世界操作与客户端
 * 渲染状态都只能在各自主线程改）；框架保证不会并发调用同一个系统。
 *
 * <p>异常约定：这里抛出的异常会被框架捕获并记一条 WARN（带系统 id 与规则 id），
 * 不会影响其它规则与其它系统 —— 「一条坏规则炸掉整份配置」是 0.1.x 踩过的坑。
 */
public interface SystemImpl {

    /**
     * 应用（或更新）一条规则。
     *
     * @param ruleId 规则 id（YAML 里那条的 key）
     * @param rule   规则内容（框架已按 schema 校验过；调用方视角是只读的）
     */
    void apply(String ruleId, Map<String, Object> rule);

    /**
     * 撤销一条规则（规则被删掉、或 reload 后该条消失时调用）。
     *
     * <p>默认空实现：纯「设置型」系统（如全局字体）不需要撤销动作。
     */
    default void revoke(String ruleId) {
    }
}
