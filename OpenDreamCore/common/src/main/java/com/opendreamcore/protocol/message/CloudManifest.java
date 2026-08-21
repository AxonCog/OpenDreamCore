package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 云资源清单（S→C）：资源目录全部文件的路径 + 大小 + SHA-256。
 * 客户端对比本地缓存，差异项用 cloud_diff 回请求。
 */
public final class CloudManifest implements Message {

    /** 清单条目。 */
    public record Entry(String path, long size, String sha256) {
    }

    private final List<Entry> entries;

    public CloudManifest(List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeString(entry.path());
            buf.writeLong(entry.size());
            buf.writeString(entry.sha256());
        }
    }

    public static CloudManifest decode(OdcByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 100000) {
            throw new IllegalStateException("清单条目数非法: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String path = buf.readString();
            long size = buf.readLong();
            String sha256 = buf.readString();
            entries.add(new Entry(path, size, sha256));
        }
        return new CloudManifest(entries);
    }
}
