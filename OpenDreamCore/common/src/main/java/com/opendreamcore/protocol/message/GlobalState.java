package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局状态（S→C）：服务端全局变量（在线人数/服务器名等），客户端 global.xxx 引用。
 * 编码与 StatePatch 相同（整数/小数/布尔/字符串/null）。
 */
public final class GlobalState implements Message {

    private final Map<String, Object> values;

    public GlobalState(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public Map<String, Object> values() {
        return values;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            buf.writeString(entry.getKey());
            Object v = entry.getValue();
            if (v instanceof Number n) {
                double d = n.doubleValue();
                if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) {
                    buf.writeByte(0);
                    buf.writeLong((long) d);
                } else {
                    buf.writeByte(1);
                    buf.writeString(String.valueOf(d));
                }
            } else if (v instanceof Boolean b) {
                buf.writeByte(2);
                buf.writeByte(b ? 1 : 0);
            } else if (v == null) {
                buf.writeByte(3);
            } else {
                buf.writeByte(4);
                buf.writeString(String.valueOf(v));
            }
        }
    }

    public static GlobalState decode(OdcByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 10000) {
            throw new IllegalStateException("全局变量数量非法: " + count);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = buf.readString();
            int type = buf.readByte();
            switch (type) {
                case 0 -> values.put(key, buf.readLong());
                case 1 -> values.put(key, Double.parseDouble(buf.readString()));
                case 2 -> values.put(key, buf.readByte() != 0);
                case 3 -> values.put(key, null);
                default -> values.put(key, buf.readString());
            }
        }
        return new GlobalState(values);
    }
}
