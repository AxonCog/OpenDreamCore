package com.opendreamcore.client;

/**
 * 帧级热点探针（odc.debug 开启时计数，每 120 帧打一行）。
 *
 * <p>头顶名牌/血条卡顿这类问题靠读代码猜不出来——同名牌页里 rect/text 各走哪条路径、
 * 表达式求值和文本插值每帧被调了多少次、总共吃掉多少毫秒，这里一次说清。
 * 关闭时（默认）只有一次布尔判断，不产生开销。
 */
public final class PerfProbe {

    /** 采样间隔（帧）：开诊断时每 30 帧打一行，两秒内就能看到。 */
    private static final int WINDOW = 30;

    /** 运行时开关：-Dodc.debug=true / <游戏目录>/OpenDreamCore/diag.txt 存在 / `/odc diag on` 任一即可开。 */
    private static volatile boolean enabled = detect();

    private static boolean detect() {
        if (Boolean.getBoolean("odc.debug")) {
            return true;
        }
        try {
            java.nio.file.Path p = net.minecraft.client.Minecraft.getInstance().gameDirectory
                    .toPath().resolve("OpenDreamCore").resolve("diag.txt");
            return java.nio.file.Files.exists(p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 诊断总开关（默认关，零开销）。 */
    public static boolean enabled() {
        return enabled;
    }

    /** `/odc diag on|off` 运行时切；返回切完后的状态。 */
    public static boolean setEnabled(boolean on) {
        enabled = on;
        if (on) {
            com.opendreamcore.client.OdcDebug.log("[ODC-perf] 诊断已开启（/odc diag off 关掉）");
        } else {
            com.opendreamcore.client.OdcDebug.log("[ODC-perf] 诊断已关闭");
        }
        return enabled;
    }

    /** 启动横幅：告诉用户诊断到底开没开，免得再加 -D 也没输出还以为坏了。 */
    private static volatile boolean bannerShown;

    public static void banner() {

        if (bannerShown) {
            return;
        }
        bannerShown = true;        System.out.println("[ODC] 诊断开关：" + (enabled ? "开（每 " + WINDOW + " 帧一行 [ODC-perf]）"
                : "关（-Dodc.debug=true 或建 OpenDreamCore/diag.txt 或 /odc diag on 可开）"));
    }

    public static long frames;
    public static long headTags;          // 本周期绘制的实体名牌数
    public static long rects;             // 世界 rect 绘制次数
    public static long texts;             // 世界文本绘制次数
    public static long interpolations;    // 文本插值调用次数
    public static long exprEvals;         // 表达式求值次数

    public static long msRenderFrame;     // renderFrame 总耗时
    public static long msInterpolate;     // 插值累计
    public static long msExprEval;        // 求值累计
    public static long msRect;            // rect 绘制累计

    private PerfProbe() {
    }

    public static void countHeadTag() {
        if (enabled()) {
            headTags++;
        }
    }

    public static void countRect() {
        if (enabled()) {
            rects++;
        }
    }

    public static void countText() {
        if (enabled()) {
            texts++;
        }
    }

    public static void countInterpolation() {
        if (enabled()) {
            interpolations++;
        }
    }

    public static void countExprEval() {
        if (enabled()) {
            exprEvals++;
        }
    }

    /** 每帧末尾调一次；到 120 帧打一行并清零。 */
    public static void frame() {
        if (!enabled()) {
            return;
        }
        frames++;
        if (frames < WINDOW) {
            return;
        }
        com.opendreamcore.client.OdcDebug.log("[ODC-perf] 帧数=" + frames
                + " 名牌=" + headTags
                + " rect=" + rects
                + " text=" + texts
                + " 插值=" + interpolations + "(" + msInterpolate + "ms)"
                + " 表达式=" + exprEvals + "(" + msExprEval + "ms)"
                + " 解析缓存=" + com.opendreamcore.script.engine.DreamLang.parseCacheSize()
                + " rect绘制=" + msRect + "ms"
                + " renderFrame=" + msRenderFrame + "ms");
        frames = 0;
        headTags = 0;
        rects = 0;
        texts = 0;
        interpolations = 0;
        exprEvals = 0;
        msRenderFrame = 0;
        msInterpolate = 0;
        msExprEval = 0;
        msRect = 0;
    }
}