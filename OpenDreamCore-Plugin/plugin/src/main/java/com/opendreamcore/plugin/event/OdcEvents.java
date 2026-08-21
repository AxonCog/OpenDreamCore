package com.opendreamcore.plugin.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * OpenDreamCore GUI 事件（供第三方插件监听，配合客户端 ui_event / page_control）：
 * Open/Close 页面生命周期；Button/Input/Slot/Hover/Press/Scroll/Key/Mouse/Chat 元素交互；
 * Layout 布局保存广播。
 * 事件在服务端主线程触发；Button 事件可取消（取消后不执行元素 actions 脚本）。
 */
public final class OdcEvents {

    private OdcEvents() {
    }

    /** 页面事件基类：玩家 + 页面 + 会话 + 元素。 */
    public abstract static class GuiEvent extends Event {

        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final String pageId;
        private final String sessionId;
        private final String elementId;

        GuiEvent(Player player, String pageId, String sessionId, String elementId) {
            super(true);
            this.player = player;
            this.pageId = pageId;
            this.sessionId = sessionId;
            this.elementId = elementId;
        }

        public Player getPlayer() {
            return player;
        }

        /** 页面 id（客户端页面可能为 null）。 */
        public String getPageId() {
            return pageId;
        }

        /** 会话 id（事件路由用）。 */
        public String getSessionId() {
            return sessionId;
        }

        /** 元素 id（生命周期/键鼠事件可能为 null）。 */
        public String getElementId() {
            return elementId;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 页面打开（会话分配后触发）。 */
    public static class OpenEvent extends GuiEvent {
        OpenEvent(Player player, String pageId, String sessionId) {
            super(player, pageId, sessionId, null);
        }
    }

    /** 页面关闭（客户端 page_close 或服务端关闭）。 */
    public static class CloseEvent extends GuiEvent {
        CloseEvent(Player player, String pageId, String sessionId) {
            super(player, pageId, sessionId, null);
        }
    }

    /** 按钮/元素点击（CLICK，非容器槽位）；可取消（取消后不执行 actions 脚本）。 */
    public static class ButtonEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        ButtonEvent(Player player, String pageId, String sessionId, String elementId) {
            super(player, pageId, sessionId, elementId);
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 输入事件（input/area_input/suggestion/dropdown 的 INPUT 触发）。 */
    public static class InputEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String value;

        InputEvent(Player player, String pageId, String sessionId, String elementId, String value) {
            super(player, pageId, sessionId, elementId);
            this.value = value == null ? "" : value;
        }

        /** 当前输入内容。 */
        public String getValue() {
            return value;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 容器槽位点击（chest_slot / hot_slot，CLICK 带槽位号）。 */
    public static class SlotEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final int slot;

        SlotEvent(Player player, String pageId, String sessionId, String elementId, int slot) {
            super(player, pageId, sessionId, elementId);
            this.slot = slot;
        }

        public int getSlot() {
            return slot;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 悬停（HOVER）。 */
    public static class HoverEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();

        HoverEvent(Player player, String pageId, String sessionId, String elementId) {
            super(player, pageId, sessionId, elementId);
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 按压（PRESS，滑块拖动等）。 */
    public static class PressEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String value;

        PressEvent(Player player, String pageId, String sessionId, String elementId, String value) {
            super(player, pageId, sessionId, elementId);
            this.value = value == null ? "" : value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 滚动（SCROLL）。 */
    public static class ScrollEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final int amount;

        ScrollEvent(Player player, String pageId, String sessionId, String elementId, int amount) {
            super(player, pageId, sessionId, elementId);
            this.amount = amount;
        }

        public int getAmount() {
            return amount;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 键盘事件（页面 keybinds 绑定按下，data "key:名称"）。 */
    public static class KeyEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String key;

        KeyEvent(Player player, String pageId, String sessionId, String key) {
            super(player, pageId, sessionId, "keybind:" + key);
            this.key = key;
        }

        /** 绑定名（页面 keybinds 里的键名）。 */
        public String getKey() {
            return key;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 鼠标事件（页面 mousebinds 绑定点击，data "mouse:名称"）。 */
    public static class MouseEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String name;
        private final int button;

        MouseEvent(Player player, String pageId, String sessionId, String name, int button) {
            super(player, pageId, sessionId, "mousebind:" + name);
            this.name = name;
            this.button = button;
        }

        public String getName() {
            return name;
        }

        /** 鼠标按钮（0 左 / 1 右 / 2 中）。 */
        public int getButton() {
            return button;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 聊天输入（chat_input 回车发送，INPUT 触发）。 */
    public static class ChatEvent extends GuiEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String message;

        ChatEvent(Player player, String pageId, String sessionId, String elementId, String message) {
            super(player, pageId, sessionId, elementId);
            this.message = message == null ? "" : message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** 布局保存（客户端编辑器保存 → 广播）。 */
    public static class LayoutEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final String pageId;
        private final List<com.opendreamcore.protocol.message.PageLayout.Entry> entries;

        LayoutEvent(Player player, String pageId, List<com.opendreamcore.protocol.message.PageLayout.Entry> entries) {
            super(true);
            this.player = player;
            this.pageId = pageId;
            this.entries = entries;
        }

        public Player getPlayer() {
            return player;
        }

        public String getPageId() {
            return pageId;
        }

        public List<com.opendreamcore.protocol.message.PageLayout.Entry> getEntries() {
            return entries;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    // ---- 工厂（插件内部用） ----

    public static OpenEvent open(Player player, String pageId, String sessionId) {
        return new OpenEvent(player, pageId, sessionId);
    }

    public static CloseEvent close(Player player, String pageId, String sessionId) {
        return new CloseEvent(player, pageId, sessionId);
    }

    public static ButtonEvent button(Player player, String pageId, String sessionId, String elementId) {
        return new ButtonEvent(player, pageId, sessionId, elementId);
    }

    public static InputEvent input(Player player, String pageId, String sessionId, String elementId, String value) {
        return new InputEvent(player, pageId, sessionId, elementId, value);
    }

    public static SlotEvent slot(Player player, String pageId, String sessionId, String elementId, int slot) {
        return new SlotEvent(player, pageId, sessionId, elementId, slot);
    }

    public static HoverEvent hover(Player player, String pageId, String sessionId, String elementId) {
        return new HoverEvent(player, pageId, sessionId, elementId);
    }

    public static PressEvent press(Player player, String pageId, String sessionId, String elementId, String value) {
        return new PressEvent(player, pageId, sessionId, elementId, value);
    }

    public static ScrollEvent scroll(Player player, String pageId, String sessionId, String elementId, int amount) {
        return new ScrollEvent(player, pageId, sessionId, elementId, amount);
    }

    public static KeyEvent key(Player player, String pageId, String sessionId, String key) {
        return new KeyEvent(player, pageId, sessionId, key);
    }

    public static MouseEvent mouse(Player player, String pageId, String sessionId, String name, int button) {
        return new MouseEvent(player, pageId, sessionId, name, button);
    }

    public static ChatEvent chat(Player player, String pageId, String sessionId, String elementId, String message) {
        return new ChatEvent(player, pageId, sessionId, elementId, message);
    }

    public static LayoutEvent layout(Player player, String pageId,
                                     List<com.opendreamcore.protocol.message.PageLayout.Entry> entries) {
        return new LayoutEvent(player, pageId, entries);
    }
}
