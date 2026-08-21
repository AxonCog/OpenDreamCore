package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 云资源差异请求（C→S）：客户端缺/与清单不符的文件路径列表。
 * 服务端按列表逐个回 cloud_file。
 */
public final class CloudDiff implements Message {

    private final List<String> paths;

    public CloudDiff(List<String> paths) {
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

    public static CloudDiff decode(OdcByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 100000) {
            throw new IllegalStateException("差异数量非法: " + count);
        }
        List<String> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(buf.readString());
        }
        return new CloudDiff(paths);
    }
}
