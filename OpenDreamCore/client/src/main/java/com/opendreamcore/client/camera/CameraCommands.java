package com.opendreamcore.client.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code /odc camera} 子命令的解析与产出（M1.2）。
 *
 * <p>为什么外置：{@code OdcCommands} 是 500 行以上的存量巨类（在反臃肿基线里），
 * 相机功能一律放在模块内，命令树只做几行转接——这是《新架构设计与扩展规范》里
 * 「新代码必须合规、不往存量巨类里继续长」的落地样例，也是反臃肿门禁能守住的前提。
 *
 * <p>规则清单的行由调用方（ClientController）提供：规则库在它那边，这里不反向依赖。
 */
public final class CameraCommands {

    private CameraCommands() {
    }

    /**
     * 执行 {@code /odc camera <verb> [arg]}。
     *
     * @param rest          "camera" 之后的部分，可为空（空 = list）
     * @param ruleListLines 规则清单行（懒求值，只有 list 才会调）
     * @return 要打到聊天栏的行
     */
    public static List<String> run(String rest, Supplier<List<String>> ruleListLines) {
        String s = rest == null ? "" : rest.trim();
        String verb = s.isEmpty() ? "list" : s.split("\\s+", 2)[0];
        String arg = s.length() > verb.length() ? s.substring(verb.length()).trim() : "";
        CameraPlayback playback = CameraPlayback.get();
        switch (verb.toLowerCase(Locale.ROOT)) {
            case "play":
                return playback.play(arg);
            case "stop":
                return playback.stop();
            case "status":
                return playback.status();
            case "list":
                return ruleListLines.get();
            case "edit":
                return CameraEditSession.get().start(arg);
            case "point":
                return CameraEditSession.get().pointCmd(arg);
            case "preview":
                return CameraEditSession.get().togglePreview();
            case "save":
                return CameraEditSession.get().save();
            case "panel":
                if (!CameraEditSession.get().active()) {
                    return one("§7先 /odc camera edit <id> 开一个编辑会话");
                }
                net.minecraft.client.Minecraft.getInstance().setScreen(new CameraCurveScreen());
                return one("§7曲线面板已打开");
            case "export": {
                String yaml = CameraEditSession.get().exportYaml();
                if (yaml == null) {
                    return one("§7先 /odc camera edit <id> 开一个编辑会话");
                }
                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(yaml);
                return one("§7已把规则 YAML 复制到剪贴板（" + yaml.length() + " 字符）");
            }
            case "import":
                return CameraEditSession.get().importYaml(
                        net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
            case "copy":
                return CameraEditSession.get().copyTo(arg);
            case "undo":
                return one(CameraEditSession.get().undo()
                        ? "§7已撤销（还剩 " + CameraEditSession.get().undoDepth() + " 步）"
                        : "§7没有可撤销的操作");
            case "redo":
                return one(CameraEditSession.get().redo()
                        ? "§7已重做" : "§7没有可重做的操作");
            case "abort":
                return CameraEditSession.get().abort();
            default:
                List<String> out = new ArrayList<>();
                out.add("§7/odc camera list|play <id>|stop|status|edit <id>|point add|preview|save|copy|undo|redo|abort");
                out.add("§7编辑会话内：拖拽全息点移位（Shift 锁轴），Ctrl+Z/Ctrl+Y 撤销重做；point align first|avg|<Y>、point mirror x|z");
                return out;
        }
    }

    private static List<String> one(String msg) {
        List<String> out = new ArrayList<>();
        out.add(msg);
        return out;
    }
}
