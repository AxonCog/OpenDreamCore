package com.opendreamcore.core.descriptor;

import java.util.Locale;

/** 规则下发方式：决定客户端怎么拿到这个系统的规则。 */
public enum Sync {

    /** 全量：进服与 /odc reload 时整份下发。 */
    FULL,

    /** 增量：只发变化的那几条（走 StatePatch）。 */
    DELTA,

    /** 本地：不下发，规则只从本地文件读（纯客户端系统）。 */
    NONE;

    /** 宽松解析：认不出来就按 FULL。 */
    public static Sync parse(String s) {
        if (s == null) {
            return FULL;
        }
        String v = s.trim().toUpperCase(Locale.ROOT);
        for (Sync x : values()) {
            if (x.name().equals(v)) {
                return x;
            }
        }
        return FULL;
    }
}
