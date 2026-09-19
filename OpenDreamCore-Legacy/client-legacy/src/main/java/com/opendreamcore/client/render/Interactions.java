package com.opendreamcore.client.render;

import com.opendreamcore.client.ClientControllerLegacy;
import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.UiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 交互中枢：渲染时登记可点元素的屏幕盒，画完后统一结算点击/拖拽。
 *
 * 帧协议（各版壳负责编排）：
 * beginFrame() → 渲染（期间 add() 登记命中盒）→ process() 结算。
 * 结算顺序从最后登记的盒往回找——后画的盖在上面，谁在上面听谁的。
 *
 * 行为对齐现代端：button 报 CLICK；toggle/checkbox 本地翻值再报；
 * tabs 按格子命中换激活页签再报；slider 按下开始拖、拖动沿途报 INPUT、
 * 松手补一记 CLICK；dropdown 点击循环选项；input 先记焦点（键入批三接）。
 * 本地值改动直接写回 props 源图——现代端就是这么即时反馈的。
 */
public final class Interactions {

    private static final class Hit {
        final Page page;
        final Element e;
        final double x;
        final double y;
        final double w;
        final double h;

        Hit(Page page, Element e, double x, double y, double w, double h) {
            this.page = page;
            this.e = e;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean has(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    /** 进行中的滑块拖拽。 */
    private static final class Drag {
        final Page page;
        final Element e;
        final Map<String, Object> sub;
        final double min;
        final double max;
        final double x;
        final double w;
        double lastSent;

        Drag(Page page, Element e, Map<String, Object> sub, double min, double max, double x, double w) {
            this.page = page;
            this.e = e;
            this.sub = sub;
            this.min = min;
            this.max = max;
            this.x = x;
            this.w = w;
        }

        double valueFromMouse() {
            double ratio = w <= 0 ? 0 : (MouseState.mouseX - x) / w;
            if (ratio < 0) {
                ratio = 0;
            }
            if (ratio > 1) {
                ratio = 1;
            }
            return min + ratio * (max - min);
        }

        void update() {
            double v = valueFromMouse();
            if (Math.abs(v - lastSent) > (max - min) * 0.005) {
                lastSent = v;
                Double boxed = Double.valueOf(v);
                sub.put("value", boxed);
                send(page, e, UiEvent.Trigger.INPUT, String.valueOf(Math.round(v * 100) / 100.0));
            }
        }

        void finish() {
            double v = valueFromMouse();
            sub.put("value", Double.valueOf(v));
            send(page, e, UiEvent.Trigger.CLICK, String.valueOf(Math.round(v * 100) / 100.0));
        }
    }

    private static final List<Hit> HITS = new ArrayList<>();
    private static Drag drag;
    /** 当前聚焦的输入框（Hit 里带 page+element，键入时改 props 直接拿）。 */
    private static Hit focused;
    /** 上一帧悬停的元素（边沿检测：变了才报 HOVER 事件 + 播 hoverSound）。 */
    private static Hit hovered;

    private Interactions() {
    }

    /** 这轮值得登记命中盒的类型（点击有行为的才登记）。 */
    public static boolean interactive(String type) {
        return "button".equals(type) || "toggle".equals(type) || "checkbox".equals(type)
                || "tabs".equals(type) || "slider".equals(type) || "dropdown".equals(type)
                || "suggestion".equals(type) || "input".equals(type) || "areainput".equals(type);
    }

    /** 帧开始：命中盒全部作废。 */
    public static void beginFrame() {
        HITS.clear();
    }

    /** 渲染途中登记一个可交互盒（绝对屏幕逻辑坐标）。 */
    public static void add(Page page, Element e, double x, double y, double w, double h) {
        if (page != null && e != null && w > 0 && h > 0 && HITS.size() < 512) {
            HITS.add(new Hit(page, e, x, y, w, h));
        }
    }

    /** 当前聚焦输入框（渲染侧读它画光标）。 */
    public static String focusedId() {
        return focused == null ? null : focused.e.id();
    }

    /** 是否有输入框持有焦点（KeyboardBridge 的前置判断）。 */
    public static boolean hasInputFocus() {
        return focused != null;
    }

    /** 页面关 / 点空处等统一清焦入口。 */
    public static void clearInputFocus() {
        focused = null;
    }

    /** 帧尾结算：拖拽推进 + 点击分发。 */
    public static void process() {
        if (drag != null) {
            if (MouseState.leftDown) {
                drag.update();
            } else {
                drag.finish();
                drag = null;
            }
        }
        updateHover();
        boolean released = MouseState.takeRelease();
        if (released && drag == null) {
            return; // 普通抬起，没什么要补的
        }
        // 从最上层往回找认领点击的盒子
        for (int i = HITS.size() - 1; i >= 0; i--) {
            Hit hit = HITS.get(i);
            if (MouseState.takeClick(hit.x, hit.y, hit.w, hit.h)) {
                act(hit);
                return;
            }
        }
        // 点空处：输入框丢焦（现代端同款：点别处不继续编辑）
        clearInputFocus();
    }

    /** 悬停边沿：鼠标移进新元素才报 HOVER + 播 hoverSound；移空重置。 */
    private static void updateHover() {
        Hit top = null;
        for (int i = HITS.size() - 1; i >= 0; i--) {
            if (HITS.get(i).has(MouseState.mouseX, MouseState.mouseY)) {
                top = HITS.get(i);
                break;
            }
        }
        if (top == hovered) {
            return;
        }
        hovered = top;
        if (top != null) {
            playHoverSound(top);
            send(top.page, top.e, UiEvent.Trigger.HOVER, null);
        }
    }

    private static void act(Hit hit) {
        Map<String, Object> p = hit.e.props();
        String type = Painters.normType(hit.e.type());
        if ("button".equals(type)) {
            clearInputFocus();
            send(hit.page, hit.e, UiEvent.Trigger.CLICK, null);
            playClickSound(hit);
        } else if ("toggle".equals(type)) {
            clearInputFocus();
            Map<String, Object> sub = Painters.mapOf(p.get("toggle"));
            boolean v = !Painters.bool(sub.get("value"));
            sub.put("value", Boolean.valueOf(v));
            send(hit.page, hit.e, UiEvent.Trigger.CLICK, String.valueOf(v));
            playClickSound(hit);
        } else if ("checkbox".equals(type)) {
            clearInputFocus();
            Map<String, Object> sub = Painters.mapOf(p.get("checkbox"));
            boolean v = !(Painters.bool(sub.get("value")) || Painters.bool(sub.get("checked")));
            sub.put("value", Boolean.valueOf(v));
            if (sub.containsKey("checked")) {
                sub.put("checked", Boolean.valueOf(v));
            }
            send(hit.page, hit.e, UiEvent.Trigger.CLICK, String.valueOf(v));
            playClickSound(hit);
        } else if ("tabs".equals(type)) {
            clearInputFocus();
            Map<String, Object> sub = Painters.mapOf(p.get("tabs"));
            Object options = sub.get("options");
            if (options instanceof List && !((List<?>) options).isEmpty()) {
                List<?> opts = (List<?>) options;
                double cell = hit.w / opts.size();
                int idx = cell <= 0 ? 0 : (int) Math.min(opts.size() - 1,
                        Math.max(0, (MouseState.pendingClickX() - hit.x) / cell));
                String picked = String.valueOf(opts.get(idx));
                sub.put("active", picked);
                send(hit.page, hit.e, UiEvent.Trigger.CLICK, picked);
                playClickSound(hit);
            }
        } else if ("slider".equals(type)) {
            clearInputFocus();
            Map<String, Object> sub = Painters.mapOf(p.get("slider"));
            double min = Painters.num(sub.get("min"), 0);
            double max = Painters.num(sub.get("max"), 100);
            drag = new Drag(hit.page, hit.e, sub, min, max, hit.x, hit.w);
            drag.update();
            playClickSound(hit);
        } else if ("dropdown".equals(type) || "suggestion".equals(type)) {
            clearInputFocus();
            Map<String, Object> sub = Painters.mapOf(p.get("dropdown"));
            if (sub.isEmpty()) {
                sub = Painters.mapOf(p.get("suggestion"));
            }
            Object options = sub.get("options");
            if (options instanceof List && !((List<?>) options).isEmpty()) {
                List<?> opts = (List<?>) options;
                String current = Painters.str(sub.get("value"));
                int next = 0;
                for (int i = 0; i < opts.size(); i++) {
                    if (String.valueOf(opts.get(i)).equals(current)) {
                        next = (i + 1) % opts.size();
                        break;
                    }
                }
                if (current.isEmpty() && !opts.isEmpty()) {
                    next = 0;
                }
                String picked = String.valueOf(opts.get(next));
                sub.put("value", picked);
                send(hit.page, hit.e, UiEvent.Trigger.CLICK, picked);
                playClickSound(hit);
            }
        } else if ("input".equals(type) || "areainput".equals(type)) {
            focused = hit;
        }
    }

    /** 键入链路（KeyboardBridge.type → 这里）：追加字符、写回 props、回传 INPUT。 */
    public static void typeInto(char c) {
        Hit h = focused;
        if (h == null) {
            return;
        }
        String type = Painters.normType(h.e.type());
        if (!"input".equals(type) && !"areainput".equals(type)) {
            return;
        }
        writeValue(h, textOf(h) + c);
    }

    /** 退格（KeyboardBridge.backspace）：删末字符并回传 INPUT。 */
    public static void backspace() {
        Hit h = focused;
        if (h == null) {
            return;
        }
        String t = textOf(h);
        if (t.isEmpty()) {
            return;
        }
        writeValue(h, t.substring(0, t.length() - 1));
    }

    /** 回车（KeyboardBridge.enter）：多行框插换行，单行框丢焦。 */
    public static void enter() {
        Hit h = focused;
        if (h == null) {
            return;
        }
        String type = Painters.normType(h.e.type());
        if ("areainput".equals(type)) {
            writeValue(h, textOf(h) + "\n");
        } else {
            clearInputFocus();
        }
    }

    /** 焦点输入框当前文本（先顶层 value，再 input 段的 value/text）。 */
    private static String textOf(Hit h) {
        Map<String, Object> p = h.e.props();
        Object v = p.get("value");
        if (v == null && p.get("input") instanceof Map) {
            v = ((Map<?, ?>) p.get("input")).get("value");
        }
        if (v == null && p.get("input") instanceof Map) {
            v = ((Map<?, ?>) p.get("input")).get("text");
        }
        return Painters.str(v);
    }

    /** 写回输入框值 + 回传 INPUT（值改动即时反馈，跟现代端同一语义）。 */
    private static void writeValue(Hit h, String value) {
        Map<String, Object> p = h.e.props();
        if (p.containsKey("value")) {
            p.put("value", value);
        } else if (p.get("input") instanceof Map) {
            ((Map<String, Object>) p.get("input")).put("value", value);
        } else {
            Map<String, Object> sub = new java.util.HashMap<>();
            sub.put("value", value);
            p.put("input", sub);
        }
        send(h.page, h.e, UiEvent.Trigger.INPUT, value);
    }

    /** 点击音效：元素 clickSound（含 hologram 段）优先，缺省原版按钮声。 */
    private static void playClickSound(Hit hit) {
        Map<String, Object> p = hit.e.props();
        Object cs = p == null ? null : p.get("clickSound");
        if (cs == null && p != null && p.get("hologram") instanceof Map) {
            cs = ((Map<?, ?>) p.get("hologram")).get("clickSound");
        }
        if (cs == null || !playSoundSpec(cs)) {
            com.opendreamcore.client.spi.LegacySoundBridge b =
                    com.opendreamcore.client.spi.LegacySoundBridge.Host.current();
            if (b != null) {
                b.play("", 1.0, 1.0); // 空 id = 原版 UI 按钮声
            }
        }
    }

    /** 悬停音效：元素 hoverSound/hoveredSound，其次页面 options.hoverSound。 */
    private static void playHoverSound(Hit hit) {
        Map<String, Object> p = hit.e.props();
        Object hs = p == null ? null : p.get("hoverSound");
        if (hs == null) {
            hs = p == null ? null : p.get("hoveredSound");
        }
        if (hs == null && hit.page != null && hit.page.options() != null) {
            hs = hit.page.options().get("hoverSound");
        }
        if (hs != null) {
            playSoundSpec(hs);
        }
    }

    /** 音效规格解析：字符串 = 资源 id；Map = {sound, volume, pitch}。 */
    private static boolean playSoundSpec(Object raw) {
        if (raw == null) {
            return false;
        }
        String id;
        double vol = 1.0;
        double pitch = 1.0;
        if (raw instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) raw;
            Object sid = m.get("sound");
            if (sid == null) {
                return false;
            }
            id = String.valueOf(sid);
            vol = Painters.num(m.get("volume"), 1.0);
            pitch = Painters.num(m.get("pitch"), 1.0);
        } else {
            id = String.valueOf(raw);
        }
        id = id.trim();
        if (id.isEmpty() || "null".equalsIgnoreCase(id)) {
            return false;
        }
        com.opendreamcore.client.spi.LegacySoundBridge b =
                com.opendreamcore.client.spi.LegacySoundBridge.Host.current();
        if (b != null) {
            b.play(id, vol, pitch);
        }
        return true;
    }

    /** 组一帧 ui_event 发给服务器（会话 id 用页面 OPEN 时登记的那份）。 */
    private static void send(Page page, Element e, UiEvent.Trigger trigger, String data) {
        String sessionId = MessageDispatcher.sessionOf(page.id());
        ClientControllerLegacy.sendUiEvent(sessionId, e.id(), trigger, data);
    }
}
