package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 聊天通道消息（S→C）：服务端把消息写进指定通道，客户端 chat_display 按通道渲染。
 * action：ADD 追加 / EDIT 改内容 / REMOVE 删单条 / CLEAR 清空通道。
 * text 为 legacy 格式串（含 §/& 颜色码），客户端用 RichText 解析渲染。
 */
public final class ChatMessage implements Message {

    public enum Action {
        ADD(0), EDIT(1), REMOVE(2), CLEAR(3);

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
            throw new IllegalArgumentException("未知聊天动作: " + id);
        }
    }

    private final String channel;
    private final Action action;
    private final long id;
    private final String text;

    public ChatMessage(String channel, Action action, long id, String text) {
        if (channel == null || channel.isBlank() || channel.length() > 32) {
            throw new IllegalArgumentException("通道名非法");
        }
        this.channel = channel;
        this.action = action;
        this.id = id;
        this.text = text == null ? "" : text;
    }

    public String channel() {
        return channel;
    }

    public Action action() {
        return action;
    }

    /** 消息 id（ADD 由服务端分配；REMOVE/EDIT 指定目标）。 */
    public long id() {
        return id;
    }

    public String text() {
        return text;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(channel);
        buf.writeByte(action.id);
        buf.writeVarLong(id);
        buf.writeString(text);
    }

    public static ChatMessage decode(OdcByteBuf buf) {
        String channel = buf.readString();
        Action action = Action.byId(buf.readByte());
        long id = buf.readVarLong();
        String text = buf.readString();
        return new ChatMessage(channel, action, id, text);
    }
}
