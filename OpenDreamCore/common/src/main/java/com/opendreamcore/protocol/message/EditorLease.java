package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 编辑器租约（双向）：客户端请求/释放编辑权，服务端授予/拒绝。
 * 同一页面同时只允许一个编辑者，防止互相覆盖。
 */
public final class EditorLease implements Message {

    public enum Action {
        REQUEST(0),
        RELEASE(1),
        GRANT(2),
        DENY(3);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Action byId(int id) {
            for (Action a : values()) {
                if (a.id == id) {
                    return a;
                }
            }
            throw new IllegalArgumentException("未知租约动作: " + id);
        }
    }

    private final Action action;
    private final String pageId;
    private final String holder;

    public EditorLease(Action action, String pageId, String holder) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        this.action = action;
        this.pageId = pageId;
        this.holder = holder;
    }

    public Action action() {
        return action;
    }

    public String pageId() {
        return pageId;
    }

    /** 持有者（玩家名，S→C 授予时有效）。 */
    public String holder() {
        return holder;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeByte(action.id());
        buf.writeString(pageId);
        buf.writeString(holder == null ? "" : holder);
    }

    public static EditorLease decode(OdcByteBuf buf) {
        Action action = Action.byId(buf.readByte());
        String pageId = buf.readString();
        String holder = buf.readString();
        return new EditorLease(action, pageId, holder.isEmpty() ? null : holder);
    }
}
