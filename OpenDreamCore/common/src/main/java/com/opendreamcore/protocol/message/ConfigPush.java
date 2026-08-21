package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 配置下发（S→C）：服务端把客户端配置（key=value 行，来自插件 config.yml 的 client 段）
 * 推给客户端，客户端合并写入 config/opendreamcore/odc.properties 并即时应用。
 */
public final class ConfigPush implements Message {

    private final String properties;

    public ConfigPush(String properties) {
        this.properties = properties == null ? "" : properties;
    }

    /** key=value 行（\n 分隔）。 */
    public String properties() {
        return properties;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(properties);
    }

    public static ConfigPush decode(OdcByteBuf buf) {
        return new ConfigPush(buf.readString());
    }
}
