package com.opendreamcore.config;

import java.util.Map;

/**
 * 配置格式插件 SPI：任何格式（YAML/JSON/TOML/自定义 DSL）实现本接口，
 * 统一输出 ConfigIR（与格式无关的 Map 树），核心不感知格式。
 */
public interface ConfigParser {

    /** 格式名（注册表 id）：yaml / json / toml / 自定义。 */
    String format();

    /**
     * 解析文本 → ConfigIR。
     *
     * @throws ConfigParseException 解析失败（应带行列信息）
     */
    Map<String, Object> parse(String text) throws ConfigParseException;
}
