package com.opendreamcore.legacy;

import com.opendreamcore.client.LocalOdc;
import com.opendreamcore.client.codc.CodcCommandBridge;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 客户端命令注册点（1.12.2）。这代 forge 有自己的 ClientCommandHandler.instance，
 * 塞进去就是纯客户端命令，不占服务器权限。
 *
 * 命令名不再硬编码：核心自带的 codc 在 LocalOdc 静态块里进了命令注册表，
 * 附属模组自注的命令也在同一张表里，这儿统一遍历挂上去。
 */
public final class CommandHandler1212 {

    private CommandHandler1212() { }

    /** 把注册表里的本地命令全塞进客户端命令表，失败只记日志。 */
    static void register() {
        LocalOdc.bind(ClientHooks.dispatcher());
        int ok = CodcCommandBridge.registerAll(ClientCommandHandler.instance);
        if (ok == 0) {
            OdcLegacy.LOGGER.warn("[OpenDreamCore] 本地命令注册失败（客户端命令表不认这个壳）");
        }
    }
}
