package com.opendreamcore.client.controller;

import com.opendreamcore.client.methods.ClientMethodSupport;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 键鼠绑定：页面 keybinds/mousebinds 与 HUD 全局热键在这里统一维护和轮询。
 * 页面会话与 HUD 会话的上报路由不同，由 Host 回调交给控制器；控制器只留薄委托。
 */
public final class BindingsService {

    /** 事件回调和运行上下文（都是 ClientController 现有的能力）。 */
    public interface Host {
        /** 全局热键走的 HUD 会话；没挂 HUD 返回 null（服务自动跳过全局段）。 */
        UiSession globalSession();

        /** 页面绑定走的会话（当前 OPEN 页面；没有返回 null）。 */
        UiSession pageSession();

        /** 页面是否打开（决定页面绑定是否轮询）。 */
        boolean pageOpen();

        /** 是否服务端裁决模式（单机模式不上报，只本地执行）。 */
        boolean serverMode();

        /** 事件发出（会话已由上面两个口子给出）。 */
        void sendEvent(UiEvent event);
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(BindingsService.class);

    private final Host host;
    private final Map<String, String> keyBinds = new ConcurrentHashMap<>();
    private final Map<String, Integer> mouseBinds = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mousePrev = new ConcurrentHashMap<>();
    /** 全局热键（HUD 页面 keybinds/mousebinds：常驻生效，页面外也响应；经 HUD 会话路由）。 */
    private final Map<String, String> globalKeyBinds = new ConcurrentHashMap<>();
    private final Map<String, Integer> globalMouseBinds = new ConcurrentHashMap<>();
    private final Map<String, Boolean> globalMousePrev = new ConcurrentHashMap<>();

    public BindingsService(Host host) {
        this.host = host;
    }

    /** 应用页面键鼠绑定（打开页面时调用）。 */
    public void applyBindings(Map<String, Object> options) {
        keyBinds.clear();
        mouseBinds.clear();
        mousePrev.clear();
        if (options == null) {
            return;
        }
        Object keys = options.get("keybinds");
        if (keys instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                keyBinds.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Object mouse = options.get("mousebinds");
        if (mouse instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object button = e.getValue();
                int b = button instanceof Number n ? n.intValue() : 0;
                mouseBinds.put(String.valueOf(e.getKey()), b);
            }
        }
        if (!keyBinds.isEmpty() || !mouseBinds.isEmpty()) {
            LOGGER.info("页面键鼠绑定 {} 键 / {} 鼠标", keyBinds.size(), mouseBinds.size());
        }
    }

    /** 应用全局热键（HUD 页面挂载时调用；常驻生效，页面外也响应）。 */
    public void applyGlobalBindings(Map<String, Object> options) {
        globalKeyBinds.clear();
        globalMouseBinds.clear();
        globalMousePrev.clear();
        if (options == null) {
            return;
        }
        Object keys = options.get("keybinds");
        if (keys instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                globalKeyBinds.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Object mouse = options.get("mousebinds");
        if (mouse instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object button = e.getValue();
                int b = button instanceof Number n ? n.intValue() : 0;
                globalMouseBinds.put(String.valueOf(e.getKey()), b);
            }
        }
        if (!globalKeyBinds.isEmpty() || !globalMouseBinds.isEmpty()) {
            LOGGER.info("全局热键 {} 键 / {} 鼠标", globalKeyBinds.size(), globalMouseBinds.size());
        }
    }

    public void clearBindings() {
        keyBinds.clear();
        mouseBinds.clear();
        mousePrev.clear();
    }

    public void clearGlobalBindings() {
        globalKeyBinds.clear();
        globalMouseBinds.clear();
        globalMousePrev.clear();
    }

    /** 绑定表当前行数（诊断用）。 */
    public int pageBindCount() {
        return keyBinds.size() + mouseBinds.size();
    }

    /** 全局热键当前行数（诊断用）。 */
    public int globalBindCount() {
        return globalKeyBinds.size() + globalMouseBinds.size();
    }

    /**
     * 每 tick 检查绑定（边沿触发，一次按压只上报一次）。全局热键常驻；页面绑定仅页面打开时。
     * 由 ClientController.tickBindings 调用（脚本延迟/动画补间等其余 tick 留在控制器）。
     */
    public void tick() {
        UiSession hud = host.globalSession();
        if (hud != null) {
            for (Map.Entry<String, String> e : globalKeyBinds.entrySet()) {
                var mapping = ClientMethodSupport.keyMapping(e.getValue());
                if (mapping != null && mapping.consumeClick()) {
                    send(hud, "key:" + e.getKey(), "keybind:" + e.getKey());
                }
            }
            var handler = Minecraft.getInstance().mouseHandler;
            for (Map.Entry<String, Integer> e : globalMouseBinds.entrySet()) {
                boolean down = switch (e.getValue()) {
                    case 1 -> handler.isRightPressed();
                    case 2 -> handler.isMiddlePressed();
                    default -> handler.isLeftPressed();
                };
                Boolean prev = globalMousePrev.get(e.getKey());
                if (Boolean.TRUE.equals(down) && !Boolean.TRUE.equals(prev)) {
                    send(hud, "mouse:" + e.getKey() + ":" + e.getValue(),
                            "mousebind:" + e.getKey());
                }
                globalMousePrev.put(e.getKey(), down);
            }
        }
        if (!host.pageOpen()) {
            return;
        }
        UiSession page = host.pageSession();
        for (Map.Entry<String, String> e : keyBinds.entrySet()) {
            var mapping = ClientMethodSupport.keyMapping(e.getValue());
            if (mapping != null && mapping.consumeClick()) {
                send(page, "key:" + e.getKey(), "keybind:" + e.getKey());
            }
        }
        var handler = Minecraft.getInstance().mouseHandler;
        for (Map.Entry<String, Integer> e : mouseBinds.entrySet()) {
            boolean down = switch (e.getValue()) {
                case 1 -> handler.isRightPressed();
                case 2 -> handler.isMiddlePressed();
                default -> handler.isLeftPressed();
            };
            Boolean prev = mousePrev.get(e.getKey());
            if (Boolean.TRUE.equals(down) && !Boolean.TRUE.equals(prev)) {
                send(page, "mouse:" + e.getKey() + ":" + e.getValue(), "mousebind:" + e.getKey());
            }
            mousePrev.put(e.getKey(), down);
        }
    }

    /** KEY 事件发出（服务端裁决模式才发；单机只本地执行）。 */
    private void send(UiSession s, String data, String elementId) {
        if (s == null || !host.serverMode()) {
            return;
        }
        host.sendEvent(s.event(elementId, UiEvent.Trigger.KEY, data));
    }
}