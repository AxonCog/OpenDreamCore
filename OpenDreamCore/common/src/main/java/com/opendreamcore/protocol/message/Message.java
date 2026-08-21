package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 消息接口：所有协议消息实现 encode/decode（common 纯逻辑，可单测）。
 */
public interface Message {

    void encode(OdcByteBuf buf);

    /** 读一条消息；返回 null 表示数据不足。 */
    interface Decoder<T extends Message> {
        T decode(OdcByteBuf buf);
    }
}
