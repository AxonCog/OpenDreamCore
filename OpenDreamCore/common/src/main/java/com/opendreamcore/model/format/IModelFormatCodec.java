package com.opendreamcore.model.format;

/**
 * 模型格式解码器 SPI：所有格式解码到统一的 ModelIR，这是新增格式的唯一扩展点。
 *
 * 实现约束：纯 Java 8、无 MC import、线程安全（无共享可变状态）；
 * 解析失败抛 IllegalArgumentException（带行号/定位信息），上层缺省即安全（跳过该文件并日志）。
 */
public interface IModelFormatCodec {

    /** 格式 id（如 bbmodel / geo.json / obj），用于 /odc model formats 展示与规则匹配。 */
    String id();

    /** 支持的扩展名（小写含点，如 ".bbmodel"）。 */
    String[] extensions();

    /** 是否可解析（按魔数/结构嗅探；用于扩展名冲突时的裁决）。 */
    boolean sniff(String text);

    /** 解码。文本入参 = 完整文件内容。 */
    DecodeResult decode(String text);
}
