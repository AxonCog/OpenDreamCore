package com.opendreamcore.legacy;

import com.opendreamcore.client.LocalOdc;
import com.opendreamcore.client.codc.CodcCommandBridge;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 客户端命令注册点（1.6.4）。和 1.7.10 同款套路——那代也没有注册事件，
 * 只有 forge 自己的 ClientCommandHandler.instance；注册走反射桥，
 * 各代的 ICommand 形状交给代理去适配。
 *
 * 命令名从命令注册表里取（核心自带 codc + 附属自注的），这边不硬编码。
 */
public final class CommandHandler164 {

    private CommandHandler164() { }

    /** 把注册表里的本地命令全塞进客户端命令表，失败只记日志。 */
    static void register() {
        LocalOdc.bind(ClientHooks164.dispatcher());
        int ok = CodcCommandBridge.registerAll(ClientCommandHandler.instance);
        if (ok == 0) {
            OdcLegacy164.LOGGER.warn("[OpenDreamCore] 本地命令注册失败（客户端命令表不认这个壳）");
        }
    }
}
