package com.opendreamcore.client.render;

import com.opendreamcore.client.PerfProbe;

/** 批次诊断：抓创建者调用点。只在诊断开关打开时用（回溯整条栈不是免费的）。 */
public final class BufferDiag {

    private BufferDiag() {
    }

    /** 抓 begin() 时的调用者签名，用来定位「创建了但没 draw/close」的批次。 */
    public static String captureCreator() {
        if (!PerfProbe.enabled()) {
            return null;
        }
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            // [0]=new Throwable, [1]=本方法, [2]=CompatBuffer 构造函数, [3]=CompatRender.begin, [4]=真实调用方
            for (int i = 4; i < Math.min(st.length, 9); i++) {
                StackTraceElement e = st[i];
                String cn = e.getClassName();
                // 我们自己的类太多层没意义，从第一个非本包的帧开始记
                if (!cn.startsWith("com.opendreamcore.client")) {
                    return cn + "." + e.getMethodName() + "(" + e.getLineNumber() + ")";
                }
            }
            return "unknown";
        } catch (Throwable t) {
            return "unknown";
        }
    }
}