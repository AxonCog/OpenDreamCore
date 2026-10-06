package com.opendreamcore.core.descriptor;

import java.util.Locale;

/** 系统生效侧。 */
public enum Side {

    /** 两边都生效（默认）。 */
    BOTH,

    /** 只在客户端生效。 */
    CLIENT,

    /** 只在服务端生效。 */
    SERVER;

    /** 客户端要不要处理这个系统。 */
    public boolean onClient() {
        return this != SERVER;
    }

    /** 服务端要不要处理这个系统。 */
    public boolean onServer() {
        return this != CLIENT;
    }

    /** 宽松解析：认不出来就按 BOTH（索引已校验，这里是兜底）。 */
    public static Side parse(String s) {
        if (s == null) {
            return BOTH;
        }
        String v = s.trim().toUpperCase(Locale.ROOT);
        for (Side x : values()) {
            if (x.name().equals(v)) {
                return x;
            }
        }
        return BOTH;
    }
}
