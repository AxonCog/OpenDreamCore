package com.opendreamcore.client;

/**
 * 调试输出总开关：默认全关（打印本身有 I/O 开销，且逐帧路径上哪怕
 * 一次性打印也可能在卡顿排查时制造噪声）。加 JVM 参数
 * -Dodc.debug=true 才恢复全部 [ODC-*] 输出。
 */
public final class OdcDebug {
    public static final boolean ON = Boolean.getBoolean("odc.debug");

    private OdcDebug() {
    }

    public static void log(String s) {
        if (ON) {
            System.out.println(s);
        }
    }
}
