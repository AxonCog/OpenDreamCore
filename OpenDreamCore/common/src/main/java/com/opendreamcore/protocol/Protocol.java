package com.opendreamcore.protocol;

/**
 * 协议常量。通道命名空间 opendreamcore:*，自研二进制协议。
 */
public final class Protocol {

    /** 协议版本（不兼容变更递增）。 */
    public static final int VERSION = 1;

    public static final String NAMESPACE = "opendreamcore";

    // 通道名
    public static final String READY = "ready";
    public static final String READY_ACK = "ready_ack";
    public static final String PAGE_SYNC = "page_sync";
    public static final String PAGE_CONTROL = "page_control";
    public static final String STATE_PATCH = "state_patch";
    public static final String GLOBAL_STATE = "global_state";
    public static final String UI_EVENT = "ui_event";
    public static final String UI_EVENT_ACK = "ui_event_ack";
    public static final String ITEM_ACTION = "item_action";
    public static final String ITEM_ACTION_RESULT = "item_action_result";
    public static final String CUSTOM_PACKET = "custom_packet";
    public static final String CONTAINER_REGISTRY = "container_registry";
    public static final String CONTAINER_OPEN = "container_open";
    public static final String CONTAINER_OPEN_RESULT = "container_open_result";
    public static final String TOOLTIP_REGISTRY = "tooltip_registry";
    public static final String TOOLTIP_RESYNC = "tooltip_resync";
    public static final String CLOUD_MANIFEST = "cloud_manifest";
    public static final String CLOUD_DIFF = "cloud_diff";
    public static final String CLOUD_FILE = "cloud_file";
    public static final String CLOUD_DELETE = "cloud_delete";
    public static final String CLOUD_DONE = "cloud_done";
    public static final String EDITOR_LEASE = "editor_lease";
    public static final String EDITOR_SNAPSHOT = "editor_snapshot";
    public static final String EDITOR_SAVE = "editor_save";
    public static final String EDITOR_WORLD = "editor_world";
    public static final String EDITOR_WORLD_ACK = "editor_world_ack";
    public static final String PAGE_LAYOUT = "page_layout";
    public static final String CONTAINER_SYNC = "container_sync";
    public static final String PAGE_CLOSE = "page_close";
    public static final String CHAT_MESSAGE = "chat_message";
    public static final String UI_EFFECT = "ui_effect";
    public static final String BOSS_BAR = "boss_bar";
    public static final String NAME_TAG = "name_tag";
    public static final String ITEM_TIP = "item_tip";
    public static final String HUD_SYNC = "hud_sync";
    public static final String MUSIC = "music";
    public static final String CONFIG_PUSH = "config_push";
    public static final String UI_ANIMATION = "ui_animation";
    public static final String WORLD_TAB = "world_tab";
    public static final String WORLD_ELEMENT_STATE = "world_element_state";

    /** 能力位。 */
    public static final int CAPABILITY_LOCAL_UI = 1 << 0;
    public static final int CAPABILITY_CLOUD = 1 << 1;
    public static final int CAPABILITY_TOOLTIP = 1 << 2;
    public static final int CAPABILITY_EDITOR = 1 << 3;

    /** 单包体上限。 */
    public static final int MAX_PACKET_BYTES = 1 << 20;

    private Protocol() {
    }
}
