package com.opendreamcore.client;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.message.PageControl;

import java.util.List;

/**
 * "codc" 本地命令的执行体（老壳四版共用）。
 *
 * 语义和现代端 OdcCommands 一项项对过：open/close/hud 全在本地生效，
 * list 列已入库页面，state/dump 诊断，version 报版本。服务器职责
 * （push/reload 配置/脚本）仍然只归插件的 /odc，这边不发任何转发。
 */
public final class LocalOdc {
    private LocalOdc() {
    }

    static {
        // codc 也进命令注册表，跟附属自注的命令一个待遇
        com.opendreamcore.client.api.LegacyCommandRegistry.register("codc", "本地诊断与页面控制", new LocalOdcRunner());
    }

    /**
     * codc 入口。各版聊天拦截/命令壳直接调 dispatch 那个口子；这个类留着
     * 让老调用点不改也能跑，行为一模一样。
     */
    public static final class LocalOdcRunner
            implements com.opendreamcore.client.api.LegacyCommandRegistry.Handler {
        @Override
        public void run(String args, com.opendreamcore.client.api.LegacyCommandRegistry.Out out) {
            Echo echo = new Echo() {
                @Override
                public void line(String msg) {
                    out.line(msg);
                }
            };
            exec(defaultDispatcher, args == null ? "" : args, echo);
        }
    }

    /** 各版开局把自己那个 dispatcher 交进来，注册表执行时要用。 */
    public static void bind(MessageDispatcher dispatcher) {
        defaultDispatcher = dispatcher;
    }

    /** 进服后首次调一次：把本地页面包扫进库存（附属自注的页面源一起跑）。 */
    public static void loadLocalPagesOnce(MessageDispatcher dispatcher) {
        if (dispatcher == null || localPagesLoaded.get()) {
            return;
        }
        localPagesLoaded.set(true);
        try {
            dispatcher.loadLocalPages();
        } catch (Exception ignored) {
            // 本地页没扫成不算错，服务端推的页照样能用
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean localPagesLoaded =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static volatile MessageDispatcher defaultDispatcher;

    /** 各版入口把 chat 输出灌到这。 */
    public interface Echo {
        void line(String msg);
    }

    /** 无参 help 时顺带报本页动作。 */
    public static void exec(MessageDispatcher dispatcher, String args, Echo out) {
        String a = args == null ? "" : args.trim();
        int sp = a.indexOf(' ');
        String head = sp < 0 ? a : a.substring(0, sp);
        String rest = sp < 0 ? "" : a.substring(sp + 1).trim();
        if (head.isEmpty() || head.equals("help")) {
            help(out);
            return;
        }
        if (head.equals("version")) {
            out.line("§6[ODC]§r codc " + modVersion()
                    + " / 协议 v" + com.opendreamcore.protocol.Protocol.VERSION
                    + "（服务端 " + dispatcher.serverVersion() + "）");
            return;
        }
        if (head.equals("list")) {
            List<String> ids = dispatcher.pageIds();
            out.line("§6[ODC]§r 已入库页面 (" + ids.size() + "): "
                    + (ids.isEmpty() ? "§8(无)" : String.join(", ", ids)));
            return;
        }
        if (head.equals("state")) {
            for (String line : Diagnostics.state(dispatcher, activePageId.get())) {
                out.line(line);
            }
            return;
        }
        if (head.equals("dump")) {
            if (rest.isEmpty()) {
                out.line("§c用法: /codc dump <页面id>");
                return;
            }
            out.line(Diagnostics.dump(dispatcher, rest));
            return;
        }
        if (head.equals("open")) {
            if (rest.isEmpty()) {
                out.line("§c用法: /codc open <页面id>");
                return;
            }
            if (dispatcher.page(rest) == null) {
                out.line("§c本地没有页面 " + rest + "（服务器推下来或放 gameDir/OpenDreamCore/UI/ 下才会有，/codc list 看清单）");
                return;
            }
            control(dispatcher, PageControl.Action.OPEN, rest);
            out.line("§a[ODC]§r 已打开本地页面 " + rest);
            return;
        }
        if (head.equals("close")) {
            String id = rest.isEmpty() ? activePageId.get() : rest;
            if (id == null || id.isEmpty()) {
                out.line("§c没有打开中的页面");
                return;
            }
            control(dispatcher, PageControl.Action.CLOSE, id);
            out.line("§a[ODC]§r 已关闭 " + id);
            return;
        }
        if (head.equals("hud")) {
            // 老壳的 HUD 由服务器 hud_sync 挂载，本地只做卸载；
            // 挂载/切换归服务器 /odc hud push
            if (dispatcher.hudPageIds().isEmpty()) {
                out.line("§c当前没有常驻 HUD");
                return;
            }
            for (String id : dispatcher.hudPageIds()) {
                dispatcher.unmountHud(id);
                out.line("§a[ODC]§r 已卸载 HUD " + id + "（服务器再推 /odc hud push 会回来）");
            }
            return;
        }
        if (head.equals("edit")) {
            out.line("§6[ODC]§r 可视化编辑器只在现代端（1.20.1+）提供，老壳版本没这套");
            return;
        }
        if (head.equals("reload")) {
            // 本地页面包重扫：把页面源注册表里的源全跑一遍（核心自带 YAML 目录源 + 附属自注的）
            int n = dispatcher.loadLocalPages();
            out.line("§a[ODC]§r 本地页面重拉完成（" + n + " 页）；服务端推的页面重进服刷新（服务端重载用 /odc reload）");
            return;
        }
        out.line("§c未知子命令 " + head + "，/codc 看清单");
    }

    /** 当前打开页 id：target 注册渲染回调时顺手写这里。 */
    public static final java.util.concurrent.atomic.AtomicReference<String> activePageId =
            new java.util.concurrent.atomic.AtomicReference<>("");

    /** 各版 ScreenBridge Host 填：当前打开的隐形页面（没开给 null/空）。 */
    public static final java.util.concurrent.atomic.AtomicReference<String> screenPageId =
            new java.util.concurrent.atomic.AtomicReference<>("");

    private static void control(MessageDispatcher dispatcher, PageControl.Action action, String pageId) {
        PageControl pc = new PageControl(action, pageId,
                MessageDispatcher.sessionOf(pageId), "");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        pc.encode(buf);
        dispatcher.dispatch("page_control", buf.toByteArray());
    }

    private static String modVersion() {
        try {
            String v = com.opendreamcore.client.ClientControllerLegacy.class
                    .getPackage().getImplementationVersion();
            return v == null ? "dev" : v;
        } catch (Throwable t) {
            return "dev";
        }
    }

    private static void help(Echo out) {
        out.line("§6[ODC]§r codc 本地命令（不经过服务器；服务器命令是 /odc）");
        out.line("§f/codc open <页面id>§r - 打开已推送到本地的页面");
        out.line("§f/codc close [页面id]§r - 关闭当前或指定页面");
        out.line("§f/codc hud§r - 卸载常驻 HUD（挂载归服务器）");
        out.line("§f/codc list§r - 已入库页面清单");
        out.line("§f/codc state§r - 会话/页面/可见性一览");
        out.line("§f/codc dump <页面id>§r - 页面原始 YAML 落盘 odc-dump/");
        out.line("§f/codc version§r - 模组与协议版本");
        out.line("§f/codc reload§r - 重扫本地页面（gameDir/OpenDreamCore/UI/ 与附属页面源）");
    }
}
