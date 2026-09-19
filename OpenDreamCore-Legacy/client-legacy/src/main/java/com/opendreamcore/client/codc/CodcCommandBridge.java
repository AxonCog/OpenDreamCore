package com.opendreamcore.client.codc;

import com.opendreamcore.client.spi.ChatNotifier;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

/**
 * codc 客户端命令注册桥（老壳四版共用）。
 *
 * 为什么全反射：1.6.4/1.7.10/1.12.2/1.16.5 的 ICommand 压根不是同一个形状
 * （getCommandName vs getName、processCommand vs execute、能不能用命令的签名
 * 每代都在换），共享模块编译时只能看见一个版本的 jar，接口实现写不出来。
 * 动态代理现编一个 ICommand，方法按名字分派，哪个版本来了都能接住。
 *
 * 1.16.5 用不上这座桥：那代的 "/codc" 在 ClientHooks1165 的聊天拦截里
 * 直接消化（没有客户端命令表可注册）。其余三版都从这儿走。
 *
 * 注册走各家客户端命令表的 registerCommand/addCommand，找不到就算了：
 * 少一条诊断命令不至于连累页面渲染。
 */
public final class CodcCommandBridge {

    /** 子命令原文交给它执行，回显走 ChatNotifier，不往这儿塞。 */
    public interface Runner {
        void run(String args);
    }

    private CodcCommandBridge() { }

    /**
     * 把注册表里的本地命令全塞进客户端命令表（名字从表里取，不硬编码）。
     *
     * 核心自带的 codc 在 LocalOdc 静态块里已经进表，附属模组自注的也跟着一起挂。
     *
     * table：ClientCommandHandler.instance（或各家等价物），null 直接失败
     * 返回：挂上去几条
     */
    public static int registerAll(Object table) {
        if (table == null) {
            return 0;
        }
        int ok = 0;
        for (com.opendreamcore.client.api.LegacyCommandRegistry.Command c
                : com.opendreamcore.client.api.LegacyCommandRegistry.commands()) {
            if (register(table, c.name(), args -> c.execute(args, CHAT_OUT))) {
                ok++;
            }
        }
        return ok;
    }

    /** 聊天栏回显口（注册表 Out 的实现，各家 SPI 自己接）。 */
    public static final com.opendreamcore.client.api.LegacyCommandRegistry.Out CHAT_OUT =
            text -> {
                ChatNotifier n = ChatNotifier.Host.current();
                if (n != null) {
                    n.say(text);
                } else {
                    System.out.println("[ODC] " + text);
                }
            };

    /**
     * 把一条命令塞进客户端命令表。
     *
     * table：ClientCommandHandler.instance（或各家等价物），null 直接失败
     * 返回：是否挂上了
     */
    public static boolean register(Object table, String cmdName, Runner runner) {
        if (table == null) {
            return false;
        }
        Object cmd = proxy(cmdName, runner);
        for (String name : new String[] {"registerCommand", "addCommand", "register"}) {
            for (Method m : table.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != 1) {
                    continue;
                }
                if (!m.getParameterTypes()[0].isInstance(cmd)) {
                    continue;
                }
                try {
                    m.invoke(table, cmd);
                    return true;
                } catch (Throwable ignored) {
                    // 换下一个候选方法
                }
            }
        }
        return false;
    }

    /** 反射命令壳：新版旧版的方法名都给个落脚处。 */
    private static Object proxy(String cmdName, Runner runner) {
        final String name = cmdName == null || cmdName.trim().isEmpty() ? "codc" : cmdName.trim();
        Class<?> iface = commandInterface();
        if (iface == null) {
            throw new IllegalStateException("找不到 ICommand 接口，这代没客户端命令通道");
        }
        InvocationHandler handler = (self, method, args) -> {
            switch (method.getName()) {
                case "getCommandName":
                case "getName":
                    return name;
                case "getCommandUsage":
                case "getUsage":
                    return "/" + name + " [help|open|close|hud|list|state|dump|version]";
                case "getRequiredPermissionLevel":
                case "getPermission":
                    return 0;
                case "canCommandSenderUseCommand":
                case "canUse":
                case "checkPermission":
                    return true;
                case "getCommandAliases":
                case "getAliases":
                    return Collections.emptyList();
                case "isUsernameIndex":
                    return false;
                case "processCommand":
                case "execute": {
                    runner.run(joinStrings(args));
                    return null;
                }
                case "addTabCompletionOptions":
                case "listSuggestions":
                    return Collections.emptyList();
                case "compareTo":
                    return 0;
                case "equals":
                    return self == args[0];
                case "hashCode":
                    return System.identityHashCode(self);
                case "toString":
                    return name;
                default: {
                    // 剩下没碰到的默认方法（1.16 有几个）照常转发
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
                }
            }
        };
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, handler);
    }

    /** execute(MinecraftServer, CommandSourceStack, String[]) 这种签名：取末尾的 String[]。 */
    private static String joinStrings(Object[] args) {
        if (args == null) {
            return "";
        }
        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof String[]) {
                return String.join(" ", (String[]) args[i]);
            }
        }
        return "";
    }

    /** 各家类名轮询：1.16 的 ICommand 也在 net.minecraft.command，但 1.6/1.7 的表不同包名风险留个候选 */
    private static Class<?> commandInterface() {
        for (String name : new String[] {
                "net.minecraft.command.ICommand",
        }) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                // 换下一个
            }
        }
        return null;
    }

    /** 回显：有玩家走聊天栏（各版 SPI ChatNotifier 自家实现），没玩家退回日志。 */
    public static void echo(String text) {
        ChatNotifier n = ChatNotifier.Host.current();
        if (n != null) {
            n.say(text);
        } else {
            System.out.println("[ODC] " + text);
        }
    }
}
