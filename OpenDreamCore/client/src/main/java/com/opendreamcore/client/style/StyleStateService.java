package com.opendreamcore.client.style;

import com.opendreamcore.page.Element;
import com.opendreamcore.ui.RenderNode;
import com.opendreamcore.ui.theme.ElementStyle;
import com.opendreamcore.ui.theme.TransitionSpec;

import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 样式状态服务。
 *
 * 职责：把主题管线随页面下发的"每状态属性覆盖层"在渲染帧合并进组件取值，
 * 并对声明了 transition 的属性做数值/颜色插值——纯渲染时计算，
 * 不改写元素数据模型、不触发重排，天然可逆、无副作用。
 *
 * 状态判定：hover（鼠标在命中区内）/ pressed（悬停且左键按下）/
 * disabled（enabledWhen 为 false）。focus 状态待键盘焦点基础设施建立后接入。
 */
public final class StyleStateService {

    private static final StyleStateService INSTANCE = new StyleStateService();

    public static StyleStateService get() {
        return INSTANCE;
    }

    private StyleStateService() {
        // 把自己注册成布局引擎的状态解析器。common 只认接口不认识我们，
        // 这样它就能在重算时问到"这个元素现在该偏移多少"，而不用 import 任何客户端类。
        com.opendreamcore.ui.LayoutEngine.setStateLayoutResolver(this::layoutOverridesFor);
    }

    /** 当前帧鼠标（由屏幕渲染入口每帧写入；第四位为时间戳，过期视为鼠标不存在）。 */
    private static final ThreadLocal<double[]> MOUSE = ThreadLocal.withInitial(() -> new double[]{-1, -1, 0, 0});

    private static final long MOUSE_TTL_MS = 100;

    /** 每帧开始时更新鼠标与焦点（第三位：左键是否按下，1=按下）。
     *  focusedId 传当前持有键盘焦点的元素 id，没有就传 null——
     *  有了它，主题里的 ":focus" 状态才真正有了灵魂。 */
    public static void beginFrame(double mouseX, double mouseY, boolean leftPressed, String focusedId) {
        double[] m = MOUSE.get();
        m[0] = mouseX;
        m[1] = mouseY;
        m[2] = leftPressed ? 1 : 0;
        m[3] = System.currentTimeMillis();
        FOCUS.set(focusedId == null ? "" : focusedId);
    }

    /** 兼容旧调用点：不关心焦点的渲染路径。 */
    public static void beginFrame(double mouseX, double mouseY, boolean leftPressed) {
        beginFrame(mouseX, mouseY, leftPressed, null);
    }

    // 过渡运行时：key = 元素身份|属性路径 -> 动画记录

    private record Tween(long startAt, Object from, Object to, TransitionSpec spec) {
    }

    private final Map<String, Tween> tweens = new ConcurrentHashMap<>();

    /** 当前帧持有键盘焦点的元素 id（空串 = 无焦点）。 */
    private static final ThreadLocal<String> FOCUS = ThreadLocal.withInitial(() -> "");

    // 状态记忆与布局联动
    // 记每个元素上一帧的激活状态，用于发现"状态变了"；
    // 变化涉及布局属性时请求一次重排，让 x/y/宽高的覆盖真正落地。
    private final Map<String, Set<String>> lastStates = new ConcurrentHashMap<>();
    private volatile long lastRelayoutAt = 0;
    private volatile boolean relayoutRequested = false;

    /** 布局属性集合：这些键的状态覆盖需要重排才能落地。 */
    private static final Set<String> LAYOUT_KEYS = Set.of("x", "y", "width", "height");

    /** 重排请求冷却：同一时间窗内只放行一次，防止 hover 抖动引发重排风暴。 */
    private static final long RELAYOUT_COOLDOWN_MS = 250;

    // 状态判定

    /** 计算节点当前激活的状态集合。 */
    public Set<String> activeStates(RenderNode node) {
        double[] m = MOUSE.get();
        // 鼠标数据过期（如屏幕关闭后 HUD 帧）：视为无鼠标，杜绝幻影悬停
        boolean mouseLive = m[3] > 0 && System.currentTimeMillis() - m[3] <= MOUSE_TTL_MS;
        boolean hover = node.enabled() && mouseLive && m[0] >= 0 && node.contains((int) m[0], (int) m[1]);
        boolean pressed = false;
        if (hover && m[2] > 0) {
            pressed = true;
        }
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (!node.enabled()) {
            out.add(ElementStyle.STATE_DISABLED);
        }
        if (hover) {
            out.add(ElementStyle.STATE_HOVER);
        }
        if (pressed) {
            out.add(ElementStyle.STATE_PRESSED);
        }
        String focus = FOCUS.get();
        if (!focus.isEmpty() && focus.equals(node.id())) {
            out.add(ElementStyle.STATE_FOCUS);
        }
        recordActive(node, out);
        return out;
    }

    /**
     * 记录本帧激活状态；与上帧不同且涉及布局属性时，标记一次重排请求。
     * 状态覆盖层改颜色/文字是即时的，但改 x/y/宽高必须走一轮布局才有意义。
     */
    private void recordActive(RenderNode node, Set<String> states) {
        Element element = node.source();
        if (element == null || element.style() == null) {
            return;
        }
        String key = Integer.toHexString(System.identityHashCode(element));
        seenById.put(element.id(), element);          // 布局回调按 id 反查
        Set<String> prev = lastStates.put(key, states);
        if (prev != null && !prev.equals(states) && touchesLayoutKeys(element.style(), states, prev)) {
            relayoutRequested = true;
        }
    }

    /** 新旧状态并集所涉及的覆盖层里，是否有布局属性。 */
    private boolean touchesLayoutKeys(com.opendreamcore.ui.theme.ElementStyle style,
                                      Set<String> now, Set<String> before) {
        return declaresLayout(style, now) || declaresLayout(style, before);
    }

    private boolean declaresLayout(com.opendreamcore.ui.theme.ElementStyle style, Set<String> states) {
        if (states == null || states.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Map<String, Object>> e : style.stateOverlays().entrySet()) {
            if (states.containsAll(java.util.Arrays.asList(e.getKey().split("\\+")))) {
                for (String k : e.getValue().keySet()) {
                    if (LAYOUT_KEYS.contains(k)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 渲染帧见过的元素：id → Element（布局回调时反查用）。 */
    private final Map<String, com.opendreamcore.page.Element> seenById = new ConcurrentHashMap<>();

    /** 布局引擎回调入口。 */
    public Map<String, Object> layoutOverridesFor(String elementId) {
        com.opendreamcore.page.Element element = seenById.get(elementId);
        if (element == null) {
            return Map.of();
        }
        var style = element.style();
        if (style == null) {
            return Map.of();
        }
        Set<String> states = lastStates.get(Integer.toHexString(System.identityHashCode(element)));
        if (states == null || states.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : style.stateOverlays().entrySet()) {
            if (!states.containsAll(java.util.Arrays.asList(e.getKey().split("\\+")))) {
                continue;
            }
            for (String k : LAYOUT_KEYS) {
                Object v = e.getValue().get(k);
                if (v != null) {
                    out.put(k, v);   // 数值直接给 Layout 用（mergeLayout 会转字符串）
                }
            }
        }
        return out;
    }

    /**
     * 消费一次重排请求（带冷却）。屏幕渲染帧末尾调用：
     * 返回 true 就触发一次 refreshCurrent，让布局属性的状态覆盖落地。
     */
    public boolean consumePendingRelayout() {
        if (!relayoutRequested) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastRelayoutAt < RELAYOUT_COOLDOWN_MS) {
            return false;   // 冷却期内先憋着，下一窗口再说
        }
        lastRelayoutAt = now;
        relayoutRequested = false;
        return true;
    }

    /**
     * opacity/scale/rotation 布局期就被固化成 RenderNode 字段了，
     * 状态覆盖层若想实时改它们，就在变换应用点（每帧）调这里拿修正值。
     *
     * 返回：三元素数组 {scale, alpha, rotationDeg}；无覆盖时原样返回入参
     */
    public double[] applyRootTransform(RenderNode node, double scale, double alpha, double rotation) {
        Element element = node.source();
        ElementStyle style = element == null ? null : element.style();
        if (style == null || style.stateOverlays().isEmpty()) {
            return new double[]{scale, alpha, rotation};
        }
        Set<String> active = activeStates(node);
        double ns = scale, na = alpha, nr = rotation;
        for (Map.Entry<String, Map<String, Object>> e : style.stateOverlays().entrySet()) {
            if (!active.containsAll(java.util.Arrays.asList(e.getKey().split("\\+")))) {
                continue;
            }
            for (Map.Entry<String, Object> d : e.getValue().entrySet()) {
                // 只认无点路径的根级键；带点路径是组件规格的事，不归我们管
                if (d.getKey().contains(".")) {
                    continue;
                }
                Object v = d.getValue();
                switch (d.getKey()) {
                    case "scale" -> ns = asDouble(v, ns);
                    case "opacity" -> na = asDouble(v, na);
                    case "rotation" -> nr = asDouble(v, nr);
                    default -> { }
                }
            }
        }
        return new double[]{ns, na, nr};
    }

    private static double asDouble(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 取节点在指定规格名下"叠加了状态覆盖与过渡插值"的最终取值表。
     * base 为空时也可能返回覆盖内容（主题只定义了该规格的属性）。
     */
    public Map<String, Object> styledSpec(RenderNode node, String key, Map<?, ?> base) {
        Element element = node.source();
        ElementStyle style = element == null ? null : element.style();
        Map<String, Map<String, Object>> overlays = style == null ? null : style.stateOverlays();
        boolean noOverlay = overlays == null || overlays.isEmpty();

        // 收集所有命中的覆盖声明（多状态同时激活时按声明序后者胜）
        Map<String, Object> merged = null;
        if (!noOverlay) {
            Set<String> active = activeStates(node);
            for (Map.Entry<String, Map<String, Object>> e : overlays.entrySet()) {
                if (active.containsAll(java.util.Arrays.asList(e.getKey().split("\\+")))) {
                    for (Map.Entry<String, Object> d : e.getValue().entrySet()) {
                        String prop = d.getKey();
                        // 点路径 "button.textColor" 只并入对应规格；无点路径并入所有规格
                        int dot = prop.indexOf('.');
                        if (dot < 0 || prop.substring(0, dot).equals(key)) {
                            String localKey = dot < 0 ? prop : prop.substring(dot + 1);
                            if (merged == null) {
                                merged = new LinkedHashMap<>();
                            }
                            merged.put(localKey, d.getValue());
                        }
                    }
                }
            }
        }
        if (merged == null) {
            return base == null ? Map.of() : castMap(base);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (base != null) {
            for (Map.Entry<?, ?> e : base.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Object> e : merged.entrySet()) {
            out.put(e.getKey(), animate(element, key + "." + e.getKey(), e.getValue(),
                    out.get(e.getKey()), style, now));
        }
        return out;
    }

    /**
     * 过渡插值：若该属性声明了 transition 且新旧值同为可插值类型，
     * 返回当前动画时刻的中间值；否则直接返回目标值。
     */
    private Object animate(Element element, String pathKey, Object target,
                           Object current, ElementStyle style, long now) {
        if (style == null || target == null || current == null) {
            finishTween(element, pathKey);
            return target;
        }
        TransitionSpec spec = findTransition(style.transitions(), rootProp(pathKey));
        if (spec == null || spec.duration() <= 0) {
            finishTween(element, pathKey);
            return target;
        }
        String key = identity(element) + "|" + pathKey;
        Tween tween = tweens.get(key);
        if (tween == null || !tween.to().equals(target)) {
            tween = new Tween(now, current, target, spec);
            tweens.put(key, tween);
        }
        float progress = (now - tween.startAt()) / (spec.duration() * 1000f) - spec.delay();
        if (progress >= 1f) {
            tweens.remove(key);
            return target;
        }
        if (progress <= 0f) {
            return tween.from();
        }
        float eased = spec.ease().apply(progress);
        Object from = tween.from();
        if (from instanceof Number fn && target instanceof Number tn) {
            return fn.doubleValue() + (tn.doubleValue() - fn.doubleValue()) * eased;
        }
        Integer fc = tryColor(from);
        Integer tc = tryColor(target);
        if (fc != null && tc != null) {
            return lerpColor(fc, tc, eased);
        }
        return target; // 离散值：过渡期满直接切换
    }

    private void finishTween(Element element, String pathKey) {
        tweens.remove(identity(element) + "|" + pathKey);
    }

    private static String identity(Element element) {
        return Integer.toHexString(System.identityHashCode(element));
    }

    private static String rootProp(String dottedKey) {
        int dot = dottedKey.indexOf('.');
        String rest = dot < 0 ? dottedKey : dottedKey.substring(dot + 1);
        // 规格前缀可能多层（spec.prop 或 prop），transition 声明用最内层属性名即可命中
        int inner = rest.indexOf('.');
        return inner < 0 ? rest : rest.substring(inner + 1);
    }

    private static TransitionSpec findTransition(List<TransitionSpec> specs, String property) {
        return TransitionSpec.find(specs, property);
    }

    /** 解析 #RRGGBB / #AARRGGBB 颜色字符串；非颜色返回 null。 */
    static Integer tryColor(Object v) {
        if (!(v instanceof String s)) {
            return null;
        }
        String t = s.trim();
        if (!t.startsWith("#")) {
            return null;
        }
        try {
            String hex = t.substring(1);
            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static int lerpColor(int from, int to, float t) {
        int a = (int) ((from >>> 24) + (((to >>> 24) - (from >>> 24)) * t));
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
