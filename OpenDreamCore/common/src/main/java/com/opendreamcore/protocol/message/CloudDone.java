package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 云资源同步完成（S→C）：服务端发完所有差异文件后的结束标记。
 */
public final class CloudDone implements Message {

    @Override
    public void encode(OdcByteBuf buf) {
        // 无字段
    }

    public static CloudDone decode(OdcByteBuf buf) {
        return new CloudDone();
    }
}
