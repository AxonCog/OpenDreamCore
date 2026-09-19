package com.opendreamcore.client.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 命令注册表（老壳四版共用）：本地客户端命令的出处，谁注册谁挂。
 *
 * 一条命令 = 一个名字 + 一个执行体。核心自带的 codc 先注册进来，附属模组想加
 * 自己的本地命令（可视化编辑器之类）也走这里；各版的注册点统一遍历这张表，
 * 不再各自硬编码命令名。Tab 补全同样从这儿取名。
 */
public final class LegacyCommandRegistry {

    /** 一行回显口子（各版实现成聊天栏输出）。 */
    public interface Out {
        void line(String msg);
    }

    /** 命令执行体：args 是去掉命令名后的剩余参数（可能为空串）。 */
    public interface Handler {
        void run(String args, Out out);
    }

    /** 一条本地命令。 */
    public static final class Command {
        private final String name;
        private final String usage;
        private final Handler handler;

        Command(String name, String usage, Handler handler) {
            this.name = name;
            this.usage = usage == null ? "" : usage;
            this.handler = handler;
        }

        public String name() {
            return name;
        }

        /** 一行用法说明（help 列表用，可空）。 */
        public String usage() {
            return usage;
        }

        public void execute(String args, Out out) {
            handler.run(args == null ? "" : args, out);
        }
    }

    private static final List<Command> COMMANDS = new CopyOnWriteArrayList<Command>();

    private LegacyCommandRegistry() { }

    /** 注册一条本地命令（带用法说明）。 */
    public static boolean register(String name, String usage, Handler handler) {
        if (name == null || name.trim().isEmpty() || handler == null || get(name) != null) {
            return false;
        }
        COMMANDS.add(new Command(name.trim(), usage, handler));
        return true;
    }

    /** 注册一条本地命令。 */
    public static boolean register(String name, Handler handler) {
        return register(name, "", handler);
    }

    /** 按名字取命令，没有返回 null。 */
    public static Command get(String name) {
        if (name == null) {
            return null;
        }
        for (Command c : COMMANDS) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** 摘掉一条命令，返回是否真删了。 */
    public static boolean unregister(String name) {
        for (Command c : COMMANDS) {
            if (c.name().equalsIgnoreCase(name)) {
                return COMMANDS.remove(c);
            }
        }
        return false;
    }

    /** 聊天栏原始输入进来走这里：命中注册表就执行。返回 false 表示没人接（该照常发服务器）。 */
    public static boolean dispatch(String message, Out out) {
        String msg = message == null ? "" : message.trim();
        if (msg.isEmpty() || msg.charAt(0) != '/') {
            return false;
        }
        String body = msg.substring(1);
        int sp = body.indexOf(' ');
        String name = sp < 0 ? body : body.substring(0, sp);
        String args = sp < 0 ? "" : body.substring(sp + 1).trim();
        return run(name, args, out);
    }

    /** 按名字执行一条命令（各版命令注册点的真正入口：命令名从注册表取，不硬编码实现）。 */
    public static boolean run(String name, String args, Out out) {
        Command command = get(name);
        if (command == null) {
            return false;
        }
        command.execute(args, out);
        return true;
    }

    /** 已注册命令名（Tab 补全、help 列表用）。 */
    public static List<String> names() {
        List<String> out = new ArrayList<String>();
        for (Command c : COMMANDS) {
            out.add(c.name());
        }
        return out;
    }

    /** 遍历用：全部命令（只读）。 */
    public static List<Command> commands() {
        return Collections.unmodifiableList(COMMANDS);
    }
}
