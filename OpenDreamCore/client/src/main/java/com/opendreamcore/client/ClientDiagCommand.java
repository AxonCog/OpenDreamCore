package com.opendreamcore.client;

/**
 * /odc diag：诊断开关（运行时切，不必重启客户端）。
 *
 * <p>为什么单独成类：诊断开关是排查期高频用的小功能，塞进 OdcCommands 的巨型 switch
 * 会把那个类顶过反臃肿门禁的方法行数上限，而它本身只需要两行逻辑。
 */
public final class ClientDiagCommand {

    private ClientDiagCommand() {
    }

    /** 解析参数（on/off/true 开，其余关）；返回给玩家的回执文字。 */
    public static String run(String arg) {
        boolean on = arg != null && ("on".equalsIgnoreCase(arg.trim())
                || "true".equalsIgnoreCase(arg.trim()));
        PerfProbe.setEnabled(on);
        return "诊断开关: " + (PerfProbe.enabled() ? "开（每 30 帧一行 [ODC-perf]）" : "关");
    }
}