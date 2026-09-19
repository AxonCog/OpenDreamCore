package com.opendreamcore.legacy;

import com.opendreamcore.client.LocalOdc;
import com.opendreamcore.client.codc.CodcCommandBridge;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 客户端命令注册点（1.7.10）——那代没有注册事件，直接塞 forge 的
 * ClientCommandHandler.instance。forge-universal 里这类的父类是 SRG 名，
 * registerCommand 源码碰不着，交给反射桥去调。
 *
 * 命令名从命令注册表里取（核心自带 codc + 附属自注的），这边不硬编码。
 */
public final class CommandHandler1710 {

    private CommandHandler1710() { }

    /** 把注册表里的本地命令全塞进客户端命令表，失败只记日志。 */
    static void register() {
        LocalOdc.bind(ClientHooks1710.dispatcher());
        int ok = CodcCommandBridge.registerAll(ClientCommandHandler.instance);
        if (ok == 0) {
            OdcLegacy1710.LOGGER.warn("[OpenDreamCore] 本地命令注册失败（客户端命令表不认这个壳）");
        }
    }
}
