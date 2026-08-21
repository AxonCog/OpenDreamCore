package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * Boss 血条同步（S→C）：服务端创建/更新/移除顶部 Boss 条（客户端 HUD 渲染，可多条叠放）。
 * progress 0-100，color "#RRGGBB"。
 */
public final class BossBarSync implements Message {

    public enum Action {
        ADD(0), UPDATE(1), REMOVE(2);

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
            throw new IllegalArgumentException("未知 Boss 条动作: " + id);
        }
    }

    private final String id;
    private final Action action;
    private final String text;
    private final double progress;
    private final String color;

    public BossBarSync(String id, Action action, String text, double progress, String color) {
        if (id == null || id.isBlank() || id.length() > 32) {
            throw new IllegalArgumentException("Boss 条 id 非法");
        }
        this.id = id;
        this.action = action;
        this.text = text == null ? "" : text;
        this.progress = progress;
        this.color = color == null ? "" : color;
    }

    public String id() {
        return id;
    }

    public Action action() {
        return action;
    }

    public String text() {
        return text;
    }

    public double progress() {
        return progress;
    }

    public String color() {
        return color;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(id);
        buf.writeByte(action.id);
        buf.writeString(text);
        buf.writeString(String.valueOf(progress));
        buf.writeString(color);
    }

    public static BossBarSync decode(OdcByteBuf buf) {
        String id = buf.readString();
        Action action = Action.byId(buf.readByte());
        String text = buf.readString();
        double progress = Double.parseDouble(buf.readString());
        String color = buf.readString();
        return new BossBarSync(id, action, text, progress, color);
    }
}
