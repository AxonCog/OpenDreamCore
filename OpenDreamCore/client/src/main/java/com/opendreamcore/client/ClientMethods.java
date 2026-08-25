package com.opendreamcore.client;

import com.opendreamcore.client.methods.CameraMethods;
import com.opendreamcore.client.methods.ChatMethods;
import com.opendreamcore.client.methods.DisplayMethods;
import com.opendreamcore.client.methods.KeyMethods;
import com.opendreamcore.client.methods.MessageMethods;
import com.opendreamcore.client.methods.MouseMethods;
import com.opendreamcore.client.methods.MusicMethods;
import com.opendreamcore.client.methods.NetworkMethods;
import com.opendreamcore.client.methods.PlayerMethods;
import com.opendreamcore.client.methods.ScreenMethods;
import com.opendreamcore.client.methods.ScriptMethods;
import com.opendreamcore.client.methods.SoundMethods;
import com.opendreamcore.client.methods.TipMethods;
import com.opendreamcore.client.methods.TimeMethods;
import com.opendreamcore.client.methods.TitleMethods;
import com.opendreamcore.client.methods.UuidMethods;
import com.opendreamcore.client.methods.VarMethods;

/**
 * 客户端脚本命名空间聚合入口（旧版方法桥的客户端侧实现）。
 * 服务端裁决类方法（商店/经济等）在插件侧注册同名命名空间。
 * C1 拆分：各命名空间实现在 client/methods/ 一文件一类，本类仅保留 registerAll 聚合；
 * 共享助手见 methods/ClientMethodSupport。
 */
public final class ClientMethods {

    private ClientMethods() {
    }

    /** 注册全部客户端命名空间（FMLClientSetup 时调用一次）。 */
    public static void registerAll() {
        VarMethods.register();
        PlayerMethods.register();
        ChatMethods.register();
        SoundMethods.register();
        MusicMethods.register();
        ScreenMethods.register();
        ScriptMethods.register();
        NetworkMethods.register();
        TimeMethods.register();
        UuidMethods.register();
        DisplayMethods.register();
        CameraMethods.register();
        MessageMethods.register();
        TipMethods.register();
        KeyMethods.register();
        MouseMethods.register();
        TitleMethods.register();
    }
}