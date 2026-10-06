package com.opendreamcore.core.registry;

import java.util.Locale;

/**
 * 索引条目种类。加一种「可被索引的东西」= 这里加一个枚举值 + 生成器加一段扫描。
 */
public enum ServiceKind {

    /** {@code @OdcSystem} —— 配置系统。 */
    SYSTEM,

    /** {@code @OdcMethod} —— 脚本方法（三语名）。 */
    METHOD,

    /** {@code @OdcCapability} —— 能力实现。 */
    CAPABILITY;

    /** 解析；认不出来返回 null（调用方记 WARN 并跳过该行）。 */
    public static ServiceKind parse(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim().toUpperCase(Locale.ROOT);
        for (ServiceKind k : values()) {
            if (k.name().equals(v)) {
                return k;
            }
        }
        return null;
    }
}
