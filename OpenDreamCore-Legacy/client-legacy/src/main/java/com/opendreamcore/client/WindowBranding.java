package com.opendreamcore.client;

import com.opendreamcore.branding.TypewriterSequencer;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * 窗口标题统一在这写。服务端下发原文就写原文，有打字机序列就按帧写。
 * tick() 由各版本的渲染/tick 钩子驱动。
 */
public final class WindowBranding {

    private static final Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    /** 各版本写标题的口子不一样：1.7.10/1.6.4/1.12.2 是 Display.setTitle，1.16.5 走 GLFW。 */
    public interface TitleSink {
        void setTitle(String title);
    }

    private static volatile TitleSink sink;
    /** 读当前标题的口子，给备份原版标题用。1.16.5 的 GLFW 3.2 读不到，传 null，只能停笔不还原。 */
    private static volatile Supplier<String> currentTitle;
    private static volatile TypewriterSequencer sequencer;
    private static volatile String serverTitle;
    /** 原版标题备份，懒抓：第一次真的要覆盖之前才记，那时候窗口肯定已出生。 */
    private static volatile String vanillaTitle;
    /** 上一次真写到窗口的值，调试日志只在变化时打，每 tick 刷屏受不了。 */
    private static volatile String lastWritten;
    /** 菜单期诊断只打一次：用户报『进服前标题是空的』，得知道菜单期我们到底动没动笔。 */
    private static boolean tickDiagnosed;
    /** 缓存目录供给器：各版本注册时给 gameDir，懒解析（构造期窗口/文件系统未必就绪）。 */
    private static volatile java.util.function.Supplier<java.nio.file.Path> cacheDirSupplier;
    /** 标题缓存只读一次：读到就当 serverTitle 用，每 tick 重写链自然把它顶在窗口上。 */
    private static boolean cacheLoaded;

    private WindowBranding() { }

    /** 开局调一次，把写标题的口子交进来。别在这儿抓原版标题——
     * 注册发生在 mod 构造期，窗口八成还没出生，抓到的准是错的。 */
    public static void register(TitleSink titleSink, Supplier<String> titleReader) {
        sink = titleSink;
        currentTitle = titleReader;
    }

    /** 注册缓存目录（各版本 ClientHooks 传 gameDir）：重启游戏后未进服也显示上次服务端标题。 */
    public static void setCacheDir(java.util.function.Supplier<java.nio.file.Path> gameDir) {
        cacheDirSupplier = gameDir;
    }

    /** 缓存文件：gameDir/OpenDreamCore/window-title.txt，一行纯文本。 */
    private static java.nio.file.Path cacheFile() {
        java.util.function.Supplier<java.nio.file.Path> s = cacheDirSupplier;
        if (s == null) {
            return null;
        }
        try {
            java.nio.file.Path dir = s.get();
            return dir == null ? null : dir.resolve("OpenDreamCore").resolve("window-title.txt");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 服务端标题落盘：写的是完整句，不打字机逐字帧（那些太密，不值得动磁盘）。 */
    private static void persist(String title) {
        java.nio.file.Path f = cacheFile();
        if (f == null || title == null || title.isEmpty()) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.write(f, title.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // 磁盘写不进就拉倒，别拖累标题链路
        }
    }

    /** 服务端显式 RESET：连缓存一起清，下次启动回原版（『没title=原版title』语义）。 */
    private static void clearPersisted() {
        java.nio.file.Path f = cacheFile();
        if (f == null) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(f);
        } catch (Throwable ignored) {
        }
    }

    /** 第一次覆盖之前把当前标题存成原版备份；抓不到（窗口没出/读不了/为空）就保持 null。 */
    private static void ensureVanillaCaptured() {
        if (vanillaTitle != null) {
            return;
        }
        Supplier<String> reader = currentTitle;
        if (reader == null) {
            return;
        }
        try {
            String t = reader.get();
            if (t != null && !t.isEmpty()) {
                vanillaTitle = t;
            }
        } catch (Throwable ignored) {
            // 窗口还没出生就抛了，下一 tick 再试，不碍事
        }
    }

    /** 服务端 window_title 消息下发的原文。 */
    public static void onWindowTitle(String title) {
        serverTitle = title;
        if (title != null && !title.isEmpty()) {
            persist(title);
        }
    }

    /** 服务端下发了打字机配置就传个序列器进来，传 null 表示不打了、写原文。
     * 顺带把序列第一句记进缓存：重启后先亮第一句，比闪回原版强。 */
    public static void onSequence(TypewriterSequencer seq) {
        sequencer = seq;
    }

    /** 打字机/轮播场景的持久化：记完整句而非逐字帧。 */
    public static void cacheTitle(String fullText) {
        if (fullText != null && !fullText.isEmpty()) {
            persist(fullText);
        }
    }

    /** 服务端 RESET：标题还给原版，缓存连着一起清。原版标题没抓到就不动笔——宁可保持现状，绝不写空。 */
    public static void resetAndClear() {
        clearPersisted();
        reset();
    }

    /** 断线/还原：标题还给原版，但缓存文件留着——用户重启游戏后未进服
     * 还能亮上次的标题，这正是『标题闪回原版』的修复本体。只有服务端
     * 显式 RESET（resetAndClear）才清文件。 */
    public static void reset() {
        sequencer = null;
        serverTitle = null;
        ensureVanillaCaptured();
        String v = vanillaTitle;
        TitleSink s = sink;
        if (v != null && s != null) {
            try {
                s.setTitle(v);
                LOGGER.info("[ODC] 标题还原原版: {}", v);
                lastWritten = v;
            } catch (Throwable ignored) {
            }
        } else {
            LOGGER.info("[ODC] 标题还原跳过（原版备份={}，口子={}）", v, s == null ? "未注册" : "就绪");
        }
    }

    /**
     * 每 tick 都得重写：原版和别的 mod 会随手把标题改回去，一旦去重，
     * 被覆盖之后就再也恢复不回来了。打字机写帧，没序列就写原文。
     */
    public static void tick() {
        TitleSink s = sink;
        if (s == null) {
            return;
        }
        // 动笔前先确保原版标题已经备份好了——这是第一次真正覆盖前的最后机会
        ensureVanillaCaptured();
        if (!tickDiagnosed) {
            tickDiagnosed = true;
            String now = null;
            try {
                now = currentTitle == null ? null : currentTitle.get();
            } catch (Throwable ignored) {
            }
            LOGGER.info("[ODC] 标题诊断首 tick：口子就绪，当前窗口标题=「{}」，原版备份=「{}」，服务端待写=「{}」",
                    now, vanillaTitle, serverTitle);
        }
        if (!cacheLoaded) {
            cacheLoaded = true;
            if (serverTitle == null || serverTitle.isEmpty()) {
                java.nio.file.Path f = cacheFile();
                if (f != null && java.nio.file.Files.isRegularFile(f)) {
                    try {
                        byte[] raw = java.nio.file.Files.readAllBytes(f);
                        String cached = new String(raw, java.nio.charset.StandardCharsets.UTF_8).trim();
                        if (!cached.isEmpty()) {
                            serverTitle = cached;
                            LOGGER.info("[ODC] 标题缓存恢复（未进服先顶上）: {}", cached);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        TypewriterSequencer seq = sequencer;
        if (seq != null) {
            try {
                String frame = seq.tick(System.currentTimeMillis());
                // 空帧不写：序列器刚起步/空句时会吐空串，写出去窗口名就秃了，
                // 保持上一句或原版才是对的
                if (frame != null && !frame.isEmpty()) {
                    s.setTitle(frame);
                    logWrite(frame);
                }
            } catch (Throwable ignored) {
                // 推进失败别把渲染拖死
            }
            return;
        }
        String t = serverTitle;
        if (t != null && !t.isEmpty()) {
            s.setTitle(t);
            logWrite(t);
        }
    }

    /** 写值变化才打一条：打字机逐字帧太密，全打会把日志淹了。 */
    private static void logWrite(String value) {
        String prev = lastWritten;
        if (value.equals(prev)) {
            return;
        }
        lastWritten = value;
        LOGGER.info("[ODC] 窗口标题写入: {}", value);
    }
}
