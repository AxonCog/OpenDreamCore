package com.opendreamcore.ui;

import com.opendreamcore.protocol.message.UiEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端 UI 会话：一次打开的页面 = 一个会话。
 * 会话 id 由客户端生成，事件带自增序号，服务端用来防重放/乱序。
 */
public final class UiSession {

    private final String sessionId;
    private final String pageId;
    private long sequence;

    public UiSession(String pageId) {
        this(pageId, newSessionId());
    }

    /** 服务端分配会话 id 时用（多人模式 page_control 下发）。 */
    public UiSession(String pageId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话 id 不能为空");
        }
        this.pageId = pageId;
        this.sessionId = sessionId;
    }

    /** 8 位随机 hex，够区分同一玩家短时间内开的多个页面。 */
    private static String newSessionId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(Integer.toHexString(ThreadLocalRandom.current().nextInt(16)));
        }
        return sb.toString();
    }

    public String sessionId() {
        return sessionId;
    }

    public String pageId() {
        return pageId;
    }

    /** 生成一个事件（序号自增）。 */
    public UiEvent event(String elementId, UiEvent.Trigger trigger, String data) {
        return new UiEvent(sessionId, elementId, trigger, ++sequence, data);
    }
}
