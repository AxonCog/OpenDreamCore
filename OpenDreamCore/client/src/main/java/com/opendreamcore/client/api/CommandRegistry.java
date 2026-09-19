package com.opendreamcore.client.api;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/**
 * 命令注册表：codc 命令树从各 target 里抽出来，谁实现谁挂。
 *
 * 用法：附属模组 init 时 register 自己的树；target 的注册点（ClientEvents /
 * FabricEvents / ClientCommandSet 各家）遍历注册，不再硬编码。核心自己的
 * 页面命令也照样经这里走——跟附属一个待遇。
 */
public final class CommandRegistry {

    /** 命令树提供者：返回一棵 brigadier 字面树（根名自己定）。 */
    public interface CommandNode {
        /** 命令根名（不带斜杠），如 "codc"。重名的先到先得，后来的跳过。 */
        String name();

        /** 构建命令树。各 target 的注册点拿到后塞进自家注册通道。 */
        @SuppressWarnings("rawtypes")
        LiteralArgumentBuilder build();
    }

    private static final java.util.List<CommandNode> NODES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.Set<String> TAKEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private CommandRegistry() { }

    /** 注册一棵命令树；根名重名返回 false。 */
    public static boolean register(CommandNode node) {
        if (node == null || node.name() == null || node.name().isBlank()) {
            return false;
        }
        String key = node.name().toLowerCase(java.util.Locale.ROOT);
        if (!TAKEN.add(key)) {
            return false;
        }
        NODES.add(node);
        return true;
    }

    /** 核心注册便捷方法：直接给名字和树。 */
    @SuppressWarnings("rawtypes")
    public static boolean register(String name, java.util.function.Supplier<LiteralArgumentBuilder> tree) {
        return register(new CommandNode() {
            @Override public String name() { return name; }
            @Override @SuppressWarnings("rawtypes")
            public LiteralArgumentBuilder build() { return tree.get(); }
        });
    }

    /** 已注册命令根名（去重后小写）。 */
    public static java.util.Set<String> names() {
        return java.util.Collections.unmodifiableSet(TAKEN);
    }

    /** 遍历用：注册点逐个取树、塞自家通道。 */
    public static java.util.List<CommandNode> nodes() {
        return java.util.Collections.unmodifiableList(NODES);
    }
}
