package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 动画触发（S→C）：服务端远程触发客户端页面动画（animations 里的命名动画）。
 * PLAY 支持多个名称（顺序播放序列）；STOP/PAUSE/RESUME 单个名称。
 */
public final class UiAnimation implements Message {

    public enum Action {
        PLAY(0), STOP(1), PAUSE(2), RESUME(3);

        final int id;

        Action(int id) {
            this.id = id;
        }

        static Action byId(int id) {
            for (Action action : values()) {
                if (action.id == id) {
                    return action;
                }
            }
            throw new IllegalArgumentException("未知动画动作: " + id);
        }
    }

    private final Action action;
    private final List<String> names;

    public UiAnimation(Action action, String... names) {
        if (action == null) {
            throw new IllegalArgumentException("动画动作不能为空");
        }
        this.action = action;
        this.names = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    this.names.add(name);
                }
            }
        }
    }

    public Action action() {
        return action;
    }

    public List<String> names() {
        return names;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeByte(action.id);
        buf.writeVarInt(names.size());
        for (String name : names) {
            buf.writeString(name);
        }
    }

    public static UiAnimation decode(OdcByteBuf buf) {
        Action action = Action.byId(buf.readByte());
        int count = buf.readVarInt();
        if (count < 0 || count > 100) {
            throw new IllegalStateException("动画名称数非法: " + count);
        }
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = buf.readString();
        }
        return new UiAnimation(action, names);
    }
}
