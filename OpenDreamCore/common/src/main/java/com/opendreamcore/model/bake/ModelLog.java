package com.opendreamcore.model.bake;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 模型模块日志出口（代际 glue 与共享层共用）。
 *
 * <p>双通道：System.out + JUL。之前只走 JUL，注释里声称「slf4j 的 JUL 桥会接」——
 * 实测 fabric-1.21.1 客户端 latest.log 里一条 [Model] 都没有（Fabric 的 log4j 配置
 * 不接 JUL handler），而 stdout 会被收进 latest.log（带 [STDOUT]: 前缀）。结果就是
 * 模型模块的规则入库/资产加载/命中取证全部静默，实机排查时什么也看不见。
 */
public final class ModelLog {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore-Model");

    private ModelLog() {
    }

    public static void info(String msg) {
        System.out.println("[ODC-model] " + msg);
        LOGGER.info("[Model] " + msg);
    }

    public static void warn(String msg) {
        System.out.println("[ODC-model][WARN] " + msg);
        LOGGER.log(Level.WARNING, "[Model] " + msg);
    }
}
