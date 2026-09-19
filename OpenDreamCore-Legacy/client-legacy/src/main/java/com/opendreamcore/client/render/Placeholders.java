package com.opendreamcore.client.render;

import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.client.PageVariables;
import com.opendreamcore.client.spi.PlayerInfoSource;
import com.opendreamcore.page.Page;

import java.util.Map;

/**
 * 文字占位符替换：页面文本里写 {player.name}/{player.x}/{vars.xxx} 这类标记，
 * 落笔前替换成真值。数据来源：
 * player.*  → PlayerInfoSource 口子（各 target 注册的版本实现）
 * vars.xxx  → 运行时变量优先（PageVariables 存的会话/脚本变量），
 *               翻不到再查页面自己的 variables 段（YAML 里的静态默认值）
 * global.*  → 服务端 global_state 推来的全局变量
 * 认不出的标记原样留着——宁可让用户看见 {xxx} 也别替他抹掉信息。
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * 当前渲染堆栈绑定的实体上下文（名牌/血条页逐只渲染时由导演压入，
     * 画完弹出）。{entity.health} 这类标记全从这里取数；没有上下文时
     * 标记原样保留——普通页面里写了实体变量也没关系，不炸。
     */
    private static final ThreadLocal<Object> ENTITY = new ThreadLocal<Object>();

    /** 导演侧：渲染某只生物头顶页面前压入上下文，finally 里弹。 */
    public static void pushEntity(com.opendreamcore.client.spi.EntitySource.Snapshot s) {
        ENTITY.set(s);
    }

    public static void popEntity() {
        ENTITY.remove();
    }

    /**
     * 表达式求值用的实体作用域（成员链 entity.health 才能通，必须给个
     * 成员可解的对象）。没有上下文返回 null，调用方自己跳过。
     */
    public static java.util.Map<String, Object> entityScopeMap() {
        Object raw = ENTITY.get();
        if (!(raw instanceof com.opendreamcore.client.spi.EntitySource.Snapshot)) {
            return null;
        }
        com.opendreamcore.client.spi.EntitySource.Snapshot s =
                (com.opendreamcore.client.spi.EntitySource.Snapshot) raw;
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", s.name);
        m.put("type", s.typeId);
        m.put("health", s.health);
        m.put("max_health", s.maxHealth);
        m.put("health_ratio", s.maxHealth > 0 ? s.health / s.maxHealth : 0.0);
        m.put("height", s.height);
        m.put("x", s.x);
        m.put("y", s.y);
        m.put("z", s.z);
        return m;
    }

    public static String apply(String text, Page page) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        // 全角空格在老版位图字体里没有字形，落出来是豆腐块——进门先归一
        if (text.indexOf('　') >= 0) {
            text = text.replace("　", "  ");
        }
        if (text.indexOf('{') < 0 || text.indexOf('}') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 32);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                int close = text.indexOf('}', i + 1);
                if (close > i + 1) {
                    String key = text.substring(i + 1, close).trim();
                    String value = resolve(key, page);
                    if (value != null) {
                        out.append(value);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 单个标记的取值；null = 不认识，调用方原样保留。 */
    private static String resolve(String key, Page page) {
        // 实体域：只在名牌/血条页的逐实体渲染堆栈里生效
        if (key.startsWith("entity.")) {
            Object raw = ENTITY.get();
            if (!(raw instanceof com.opendreamcore.client.spi.EntitySource.Snapshot)) {
                return null;
            }
            com.opendreamcore.client.spi.EntitySource.Snapshot s =
                    (com.opendreamcore.client.spi.EntitySource.Snapshot) raw;
            String f = key.substring(7);
            if ("name".equals(f)) {
                return s.name;
            }
            if ("type".equals(f)) {
                return s.typeId;
            }
            if ("health".equals(f)) {
                return trimNum(s.health);
            }
            if ("max_health".equals(f)) {
                return trimNum(s.maxHealth);
            }
            if ("health_ratio".equals(f)) {
                return trimNum(s.maxHealth > 0 ? s.health / s.maxHealth : 0.0);
            }
            if ("height".equals(f)) {
                return trimNum(s.height);
            }
            if ("x".equals(f)) {
                return trimNum(s.x);
            }
            if ("y".equals(f)) {
                return trimNum(s.y);
            }
            if ("z".equals(f)) {
                return trimNum(s.z);
            }
            return null;
        }
        if (key.startsWith("player.")) {
            PlayerInfoSource src = PlayerInfoSource.Host.current();
            String field = key.substring(7);
            if ("name".equals(field)) {
                return src.name();
            }
            if ("dimension".equals(field)) {
                return src.dimension();
            }
            if ("x".equals(field)) {
                return trimNum(src.x());
            }
            if ("y".equals(field)) {
                return trimNum(src.y());
            }
            if ("z".equals(field)) {
                return trimNum(src.z());
            }
            if ("yaw".equals(field)) {
                return trimNum(src.yaw());
            }
            if ("pitch".equals(field)) {
                return trimNum(src.pitch());
            }
            // 常用派生键：走 extras（各版塞什么就有什么），再补几个通用计算值
            if ("gamemode".equals(field)) {
                Object v = src.extras().get("gamemode");
                return v == null ? null : String.valueOf(v);
            }
            if ("online_time".equals(field)) {
                long joined = RenderSupport.joinedAt();
                if (joined <= 0) {
                    return "0";
                }
                long sec = (System.currentTimeMillis() - joined) / 1000;
                return trimNum(sec / 60.0);
            }
            if ("level".equals(field)) {
                Object v = src.extras().get("level");
                return v == null ? null : String.valueOf(v);
            }
            // health/ping/tps 等杂项：各版在 extras 里放什么就替换什么
            Object extra = src.extras().get(field);
            return extra == null ? null : String.valueOf(extra);
        }
        if (key.startsWith("vars.")) {
            String name = key.substring(5);
            Object v = PageVariables.get(page == null ? null : page.id(), name);
            if (v == null && page != null && page.variables() != null) {
                v = page.variables().get(name);
            }
            if (v == null) {
                v = PageVariables.get(null, name);
            }
            return v == null ? null : String.valueOf(v);
        }
        // system./query. 域：时间和窗口（现代端 ClientPlaceholders 同款键位）
        if (key.startsWith("system.")) {
            String f = key.substring(7);
            if ("time".equals(f)) {
                return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            }
            if ("date".equals(f)) {
                return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
            }
            if ("millis".equals(f)) {
                return String.valueOf(System.currentTimeMillis());
            }
            return null;
        }
        if (key.startsWith("query.")) {
            String f = key.substring(6);
            if ("width".equals(f) || "screen_w".equals(f)) {
                return trimNum(RenderSupport.winW);
            }
            if ("height".equals(f) || "screen_h".equals(f)) {
                return trimNum(RenderSupport.winH);
            }
            if ("fps".equals(f)) {
                Object v = RenderSupport.extra("fps");
                return v == null ? null : String.valueOf(v);
            }
            Object v = RenderSupport.extra(f);
            return v == null ? null : String.valueOf(v);
        }
        // global. 域：服务端 global_state 推来的全局变量（与 vars. 同语义，源头在服务端）
        if (key.startsWith("global.")) {
            Object v = MessageDispatcher.globalValue(key.substring(7));
            return v == null ? null : String.valueOf(v);
        }
        // 顶层裸名字也当变量查一把：{title_text} 和 {vars.title_text} 等价，
        // 服务端全局变量排在最后兜底，跟现代端取值顺序一致
        if (!key.contains(".")) {
            Object v = PageVariables.get(page == null ? null : page.id(), key);
            if (v == null && page != null && page.variables() != null) {
                v = page.variables().get(key);
            }
            if (v == null) {
                v = PageVariables.get(null, key);
            }
            if (v == null) {
                v = MessageDispatcher.globalValue(key);
            }
            return v == null ? null : String.valueOf(v);
        }
        return null;
    }

    /** 坐标类数字留 1 位小数就够看，10.0 这种尾巴别糊在屏幕上。 */
    private static String trimNum(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }
}
