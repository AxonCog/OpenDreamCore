package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * tooltip 重传请求（C→S）：客户端进服后主动拉一次服务端 tooltip 注册表。
 */
public final class TooltipResync implements Message {

    @Override
    public void encode(OdcByteBuf buf) {
        // 无字段
    }

    public static TooltipResync decode(OdcByteBuf buf) {
        return new TooltipResync();
    }
}
