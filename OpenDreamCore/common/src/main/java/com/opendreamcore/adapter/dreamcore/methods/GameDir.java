package com.opendreamcore.adapter.dreamcore.methods;

import java.io.File;

/**
 * 游戏目录解析。
 *
 * 不直接 import net.minecraft.client.Minecraft——common 必须保持零 MC 依赖，
 * 否则服务端插件编译不过。这里走反射取实例，客户端行为与直接引用完全一致；
 * 反射失败（服务端等无客户端环境场合）回退工作目录，调用方自行处理路径不存在。
 */
final class GameDir {

    private GameDir() {
    }

    static File get() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object inst = mc.getMethod("getInstance").invoke(null);
            return (File) inst.getClass().getField("gameDirectory").get(inst);
        } catch (Throwable t) {
            return new File(".");
        }
    }
}
