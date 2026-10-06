package com.opendreamcore.core.descriptor;

/**
 * 规则字段声明（第一支柱的「一份信息只写一次」）。
 *
 * <p>实现类只用 public 字段 + {@link Field} 注解描述字段，一份声明同时产出四样东西：
 * 校验器、默认 YAML 模板里的注释、文档字段表、编辑器补全。
 *
 * <p>约束（写代码时遵守，机检暂不覆盖）：
 * <ul>
 *   <li>实现类里**不写逻辑**，只声明字段；</li>
 *   <li>字段类型只用 String / boolean / int / long / double 及其数组（与 YAML 标量、序列一一对应）。</li>
 * </ul>
 */
public interface RuleSchema {
}
