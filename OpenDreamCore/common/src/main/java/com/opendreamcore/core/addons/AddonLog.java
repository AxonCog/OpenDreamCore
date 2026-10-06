package com.opendreamcore.core.addons;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * addons 底座日志出口（JUL，与 ModelLog 同款：common 层无 slf4j 依赖）。
 */
public final class AddonLog {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore-Addons");

    private AddonLog() {
    }

    public static void info(String msg) {
        LOGGER.info("[Addons] " + msg);
    }

    public static void warn(String msg) {
        LOGGER.log(Level.WARNING, "[Addons] " + msg);
    }
}
