package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 页面控制（S→C）：打开/关闭/子页/移动。
 */
public final class PageControl implements Message {

    public enum Action {
        OPEN(0),
        CLOSE(1),
        SUB_OPEN(2),
        SUB_CLOSE(3),
        MOVE(4);

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
            throw new IllegalArgumentException("未知页面动作: " + id);
        }
    }

    private final Action action;
    private final String pageId;
    private final String sessionId;
    private final String parentSessionId;

    public PageControl(Action action, String pageId, String sessionId, String parentSessionId) {
        if (action == null) {
            throw new IllegalArgumentException("页面动作不能为空");
        }
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        this.action = action;
        this.pageId = pageId;
        this.sessionId = sessionId;
        this.parentSessionId = parentSessionId;
    }

    public Action action() {
        return action;
    }

    public String pageId() {
        return pageId;
    }

    /** 会话 id（可空，客户端生成或服务端分配）。 */
    public String sessionId() {
        return sessionId;
    }

    /** 子页父会话（SUB_OPEN/SUB_CLOSE 用）。 */
    public String parentSessionId() {
        return parentSessionId;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeByte(action.id());
        buf.writeString(pageId);
        // 可空字符串约定：空串 = null
        buf.writeString(sessionId == null ? "" : sessionId);
        buf.writeString(parentSessionId == null ? "" : parentSessionId);
    }

    public static PageControl decode(OdcByteBuf buf) {
        Action action = Action.byId(buf.readByte());
        String pageId = buf.readString();
        String sessionId = readNullable(buf);
        String parentSessionId = readNullable(buf);
        return new PageControl(action, pageId, sessionId, parentSessionId);
    }

    private static String readNullable(OdcByteBuf buf) {
        String s = buf.readString();
        return s.isEmpty() ? null : s;
    }
}
