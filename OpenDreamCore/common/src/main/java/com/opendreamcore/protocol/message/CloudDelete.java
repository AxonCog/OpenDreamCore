package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 云资源删除（S→C）：清单里消失的文件，客户端同步删本地缓存。
 */
public final class CloudDelete implements Message {

    private final List<String> paths;

    public CloudDelete(List<String> paths) {
        this.paths = paths == null ? new ArrayList<>() : List.copyOf(paths);
    }

    public List<String> paths() {
        return paths;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(paths.size());
        for (String path : paths) {
            buf.writeString(path);
        }
    }

    public static CloudDelete decode(OdcByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 100000) {
            throw new IllegalStateException("删除数量非法: " + count);
        }
        List<String> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(buf.readString());
        }
        return new CloudDelete(paths);
    }
}
