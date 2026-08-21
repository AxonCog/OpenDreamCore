package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 状态补丁（S→C）：服务端更新页面变量（金币/血量/进度等），客户端重算布局并重绘。
 * key 支持点路径（"player.health" → 页面变量 player 下的字段）。
 */
public final class StatePatch implements Message {

    private final String sessionId;
    private final Map<String, Object> values;

    public StatePatch(String sessionId, Map<String, Object> values) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw new IllegalArgumentException("会话 id 非法");
        }
        this.sessionId = sessionId;
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public String sessionId() {
        return sessionId;
    }

    public Map<String, Object> values() {
        return values;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(sessionId);
        buf.writeVarInt(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            buf.writeString(entry.getKey());
            Object v = entry.getValue();
            if (v instanceof Number n) {
                double d = n.doubleValue();
                if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) {
                    buf.writeByte(0); // 整数
                    buf.writeLong((long) d);
                } else {
                    buf.writeByte(1); // 小数
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

    public static StatePatch decode(OdcByteBuf buf) {
        String sessionId = buf.readString();
        int count = buf.readVarInt();
        if (count < 0 || count > 10000) {
            throw new IllegalStateException("补丁数量非法: " + count);
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
        return new StatePatch(sessionId, values);
    }
}
