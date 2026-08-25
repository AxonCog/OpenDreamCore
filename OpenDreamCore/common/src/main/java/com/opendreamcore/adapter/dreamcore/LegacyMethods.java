package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.NamespaceRegistry;

/**
 * DreamCore 旧版 `方法.*` 脚本桥：把旧方法名接到新引擎能力上。
 *
 * 三类实现策略：
 * 1. 委派——目标能力已由客户端命名空间提供（Screen/Music），调用时经
 *    {@link NamespaceRegistry#require} 程序化转发，单一事实来源不复制逻辑；
 * 2. 纯本地——字符串/时间/限幅等待，零环境依赖；
 * 3. Host 注入——需要运行时上下文（当前页面函数/滚轮值/按键/容器槽位），
 *    通过 {@link Host} 接口由客户端在加载旧页面时安装；未安装时安全降级（返回空值）。
 *
 * 注册时机：客户端检测到旧格式页面时 {@link #ensureRegistered()}（幂等）。
 */
public final class LegacyMethods {

    private LegacyMethods() {
    }

    /** 客户端运行时宿主：由 client 模块安装；未安装时用 NOOP 安全降级。 */
    public interface Host {
        void runFunctionAsync(String name);
        double screenHeight();
        double wheelDelta();
        String pressedKey();
        String slotItem(String identifier);
        int slotItemCount(Object item);
        void refreshVariables(String name);

        /** 当前页面存活毫秒（旧版 取界面存活时间，做淡入等逐帧效果用）。 */
        long pageAliveMs();

        /** 物品 lore 文本（旧版 取物品lore；缺省空串）。 */
        default String slotItemLore(Object item) { return ""; }
    }

    private static volatile Host host = noop();

    /** 客户端安装真实宿主（幂等）。 */
    public static void installHost(Host h) {
        if (h != null) {
            host = h;
        }
    }

    private static Host noop() {
        return new Host() {
            @Override public void runFunctionAsync(String name) { }
            @Override public double screenHeight() { return 1080; }
            @Override public double wheelDelta() { return 0; }
            @Override public String pressedKey() { return ""; }
            @Override public String slotItem(String identifier) { return ""; }
            @Override public int slotItemCount(Object item) { return 0; }
            @Override public void refreshVariables(String name) { }
            @Override public long pageAliveMs() { return Long.MAX_VALUE / 2; } // 缺省视为"已过淡入期"，元素可见
        };
    }

    private static volatile boolean registered;

    /** 注册全部旧方法（幂等；registerOrReplace 语义，重复调用无副作用）。 */
    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;

        // —— 委派组：走客户端已注册的命名空间 ——
        reg("关闭界面", args -> ns("Screen", "关闭页面"));
        reg("打开GUI", args -> ns("Screen", "打开页面", args.length > 0 ? args[0] : null));
        reg("播放声音", args -> ns("Music", "播放",
                args.length > 0 ? args[0] : null, 0.8, 0)); // 文件音频：音量 0.8、不循环
        reg("设置组件值", args -> ns("Screen", "设置元素",
                arg(args, 0), legacyPropPath(str(args, 1)), arg(args, 2)));
        reg("取组件值", args -> ns("Screen", "获取元素", arg(args, 0), legacyPropPath(str(args, 1))));
        reg("刷新界面", args -> ns("Screen", "设置变量", "_odc_refresh", System.currentTimeMillis()));

        // —— 纯本地组 ——
        reg("替换", args -> {
            String s = str(args, 0);
            if (s == null) return "";
            for (int i = 1; i + 1 < args.length; i += 2) {
                s = s.replace(str(args, i), str(args, i + 1));
            }
            return s;
        });
        reg("合并文本", args -> {
            StringBuilder sb = new StringBuilder();
            for (Object a : args) {
                if (a != null) sb.append(a);
            }
            return sb.toString();
        });
        reg("取当前时间格式化", args -> java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        reg("延时", args -> {
            long ms = args.length > 0 && args[0] instanceof Number n ? n.longValue() : 0;
            try {
                Thread.sleep(Math.max(0, Math.min(ms, 200))); // 渲染线程安全上限 200ms
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        // —— Host 组：需要运行时上下文 ——
        reg("异步执行方法", args -> {
            host.runFunctionAsync(str(args, 0));
            return null;
        });
        reg("取屏幕高度", args -> host.screenHeight());
        reg("取屏幕宽度", args -> host.screenHeight() > 0 ? host.screenHeight() * 16 / 9.0 : 1920);
        reg("取滚轮值", args -> host.wheelDelta());
        reg("取当前按下键", args -> host.pressedKey());
        reg("取物品", args -> host.slotItem(str(args, 0)));
        reg("取物品数", args -> args.length > 0 ? host.slotItemCount(args[0]) : 0);
        reg("取界面存活时间", args -> (double) host.pageAliveMs());
        reg("取当前时间", args -> (double) System.currentTimeMillis());
        reg("取物品lore", args -> host.slotItemLore(args.length > 0 ? args[0] : null));
        reg("聊天", args -> ns("Chat", "发送消息", args));
        reg("更新变量值", args -> {
            host.refreshVariables(str(args, 0));
            return null;
        });

        // 模块批次（methods/ 子包，按类别拆分维护）
        com.opendreamcore.adapter.dreamcore.methods.DisplayLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.PlayerLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.EntityLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.UtilityLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ScreenLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.SendLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.MathLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ScreenLegacy2.install();
        com.opendreamcore.adapter.dreamcore.methods.PlayerLegacy2.install();
        com.opendreamcore.adapter.dreamcore.methods.MouseKeyLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.TimeVarLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.YamlLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.MiscLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.CoreMathLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.CoreStringLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.CoreArrayLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.CoreVarLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ScreenOpsLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.PlayerExtLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.DisplayMouseKeyLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ChatSoundScheduleLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ShaderYamlMiscLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.FinalGapLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ItemLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.TimeDelayLegacy.install();
        com.opendreamcore.adapter.dreamcore.methods.ChatLegacy.install();
    }

    // ---------- 工具 ----------

    private static void reg(String name, MethodRegistry.Handler h) {
        MethodRegistry.registerOrReplace(name, h);
    }

    /** 供 methods/ 子包模块挂载自己的批次（公开注册口）。 */
    public static void register(String name, MethodRegistry.Handler h) {
        reg(name, h);
    }

    /** 委派工具：模块里转发到客户端命名空间用。 */
    public static Object delegate(String namespace, String method, Object... args) {
        return ns(namespace, method, args);
    }

    /** 数值参数：非 Number 返回 0。 */
    public static double num(Object[] args, int i) {
        return args != null && i < args.length && args[i] instanceof Number n ? n.doubleValue() : 0;
    }

    /** 字符串参数：null 安全。 */
    public static String argStr(Object[] args, int i) {
        return args != null && i < args.length && args[i] != null ? String.valueOf(args[i]) : null;
    }

    /** 委派到客户端命名空间；任何失败静默返回 null（旧脚本不该拖垮整页）。 */
    private static Object ns(String namespace, String method, Object... args) {
        try {
            return NamespaceRegistry.require(namespace, method).invoke(args);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 参数取值：子包模块共用。 */
    public static String str(Object[] args, int i) {
        return args != null && i < args.length && args[i] != null ? String.valueOf(args[i]) : null;
    }

    public static Object arg(Object[] args, int i) {
        return args != null && i < args.length ? args[i] : null;
    }

    /** Host 委派：物品名。 */
    public static String slotItem(Object[] a, int i) {
        return host.slotItem(argStr(a, i));
    }

    /** Host 委派：物品 lore。 */
    public static String slotLore(Object[] a, int i) {
        return host.slotItemLore(argStr(a, i));
    }

    /** 数值参数快捷。 */
    public static double num2(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    /** 旧组件属性名 → 新元素属性路径（Screen.设置元素 用点路径）。 */
    static String legacyPropPath(String old) {
        if (old == null) return null;
        return switch (old) {
            case "texture" -> "image.src";
            case "textureHovered" -> "image.hoverSrc";
            case "alpha" -> "opacity";
            case "tip" -> "tooltip";
            default -> old;
        };
    }

    /**
     * 旧版裸调用补括号：`方法.关闭界面;` / `方法.取屏幕高度` 这类零参方法不带 () 的写法，
     * 新执行器只会取到方法对象不会调用。对已知零参名补 `()`（已带括号的不动）。
     */
    public static String ensureZeroArgParens(String script) {
        if (script == null || script.isBlank()) {
            return script;
        }
        String out = script;
        for (String m : ZERO_ARG_METHODS) {
            out = out.replaceAll("(方法\\." + m + ")(?!\\s*\\()", "$1()");
        }
        return out;
    }

    /** 旧配置里以裸标识符形式出现的零参方法。 */
    static final String[] ZERO_ARG_METHODS = {
            "关闭界面", "取屏幕高度", "取屏幕宽度", "取滚轮值", "取当前按下键", "取当前时间格式化"
    };
}
