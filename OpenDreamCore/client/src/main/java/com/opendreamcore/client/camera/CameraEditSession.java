package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraRule;
import com.opendreamcore.camera.CameraStore;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.visual.VisualYaml;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * M1.7 可视化编辑 L1：命令记点编辑会话（目标是「不手写 YAML 就能做出一条运镜」）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>全部路径状态（点/视角/时间/缓动/时长/循环/插值）与撤销栈在 {@link CameraPathModel}，
 *       本类只是命令编排 + MC 交互的门面（反臃肿 R1：单类 ≤500 行）。</li>
 *   <li>自由相机不在本类——编辑/预览阶段的取景复用 M1.6 的 FREE 播放与 M1.2 预览播放。</li>
 *   <li>路径全息在 {@link CameraPathHolo}，鼠标拖拽在 {@link CameraPathDrag}，
 *       2D 曲线面板在 {@link CameraCurveScreen}——都经本门面读写模型。</li>
 *   <li>落盘只写客户端本地 {@code OpenDreamCore/visual/Camera.yml}（多规则同文件，
 *       顶层键即规则 id）——连服后服务端下发整体覆盖，本地文件是单机/连服前的编辑载体。</li>
 * </ul>
 *
 * <p>为什么外置：{@code ClientController}/{@code OdcCommands} 是反臃肿基线巨类，
 * 相机编辑一律放模块内，命令树只做转接。
 */
public final class CameraEditSession {

    private static final CameraEditSession INSTANCE = new CameraEditSession();

    /** 预览规则注入 CameraStore 用的临时 id（下划线开头，正常配置不会撞）。 */
    static final String PREVIEW_ID = "__odc_preview";

    private static final String FILE = "OpenDreamCore/visual/Camera.yml";

    private String editingId;
    private boolean previewOn;
    private final CameraPathModel model = new CameraPathModel();

    private CameraEditSession() {
    }

    public static CameraEditSession get() {
        return INSTANCE;
    }

    /** 开始/切换编辑会话：已有同名 PATH 规则就先把它的关键帧装进来（继续编），否则空白新开。 */
    public List<String> start(String ruleId) {
        String id = ruleId == null || ruleId.trim().isEmpty() ? "editor_path" : ruleId.trim();
        editingId = id;
        previewOn = false;
        model.reset();
        List<String> out = new ArrayList<>();
        CameraRule old = CameraStore.get().byId(id);
        if (old != null && old.type == CameraRule.Type.PATH && !old.keyframes.isEmpty()) {
            model.loadRule(old);
            out.add(line("已载入规则 §f" + id + " §7（" + model.pointsCount()
                    + " 个点），继续编辑或 point add 追加"));
        } else {
            out.add(line("新开编辑 §f" + id + "§7：走到位置后 §f/odc camera point add §7记点"));
        }
        out.add(line("命令：point add|insert <i>|remove <i>|clear|list；preview 预览；save 保存；time <秒> 设时长；abort 放弃"));
        return out;
    }

    /** point 子命令分发。 */
    public List<String> pointCmd(String arg) {
        if (!active()) {
            return err("先 /odc camera edit <id> 开一个编辑会话");
        }
        String a = arg == null ? "list" : arg.trim();
        String verb = a.split("\\s+", 2)[0].toLowerCase();
        String rest = a.length() > verb.length() ? a.substring(verb.length()).trim() : "";
        switch (verb) {
            case "add":
                return addPoint();
            case "insert":
                return insertAt(rest);
            case "remove":
                return removeAt(rest);
            case "clear":
                model.clearPoints();
                return single(line("已清空全部记点"));
            case "time":
                return setTime(rest);
            case "align":
                return align(rest);
            case "mirror":
                return mirror(rest);
            case "list":
            default:
                return listLines();
        }
    }

    /** 记一个点：玩家当前位置（脚下 +1.62 眼高）+ 当前视角。 */
    private List<String> addPoint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return err("还没进入世界");
        }
        model.addPoint(eyePos(mc), eyeRot(mc));
        return single(line("记点 §f#" + model.pointsCount() + " §7"
                + fmt(model.pointAt(model.pointsCount() - 1))));
    }

    private List<String> insertAt(String rest) {
        int i = parseIndex(rest);
        if (i < 0 || i > model.pointsCount()) {
            return err("用法: point insert <序号 1~" + (model.pointsCount() + 1) + ">");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return err("还没进入世界");
        }
        model.insertAt(i - 1, eyePos(mc), eyeRot(mc));
        return single(line("插入 §f#" + i + " §7，现共 " + model.pointsCount() + " 点"));
    }

    private List<String> removeAt(String rest) {
        int i = parseIndex(rest);
        if (i < 1 || i > model.pointsCount()) {
            return err("用法: point remove <序号 1~" + model.pointsCount() + ">");
        }
        model.removeAt(i - 1);
        return single(line("已删 §f#" + i + " §7，现共 " + model.pointsCount() + " 点"));
    }

    private List<String> setTime(String rest) {
        try {
            model.setDuration(Double.parseDouble(rest.trim()));
        } catch (Exception e) {
            return err("用法: point time <秒>（0 = 自动每点 1 秒）");
        }
        return single(line("路径时长 = " + (model.durationValue() > 0
                ? model.durationValue() + "s" : "自动")));
    }

    private List<String> listLines() {
        List<String> out = new ArrayList<>();
        out.add(line("编辑中 §f" + editingId + "§7：共 " + model.pointsCount() + " 点"
                + (model.durationValue() > 0 ? "，时长 " + model.durationValue() + "s" : "，时长自动")));
        List<double[]> pts = model.pointsView();
        for (int i = 0; i < pts.size(); i++) {
            out.add(line("  §f#" + (i + 1) + " §7" + fmt(pts.get(i))
                    + (model.rotOf(i + 1) != null ? " 视角 " + fmt2(model.rotOf(i + 1)) : "")));
        }
        return out;
    }

    /** 预览开关：开 = 把临时规则注入规则库并复用整条播放链（含全息曲线），关 = 停播并拔掉临时规则。 */
    public List<String> togglePreview() {
        List<String> out = new ArrayList<>();
        if (previewOn) {
            previewOn = false;
            out.addAll(CameraPlayback.get().stop());
            CameraStore.get().remove(PREVIEW_ID);
            out.add(line("预览已关"));
            return out;
        }
        if (model.pointsCount() < 2) {
            return err("至少记 2 个点再预览");
        }
        CameraStore.get().apply(Collections.singletonMap(PREVIEW_ID, VisualYaml.dump(ruleYaml())));
        out.addAll(CameraPlayback.get().play(PREVIEW_ID));
        previewOn = true;
        return out;
    }

    /** 保存：合并进本地 Camera.yml（多规则同文件），并整文件回灌规则库（设置键沿用既有语义）。 */
    public List<String> save() {
        if (!active()) {
            return err("没有进行中的编辑会话");
        }
        if (model.pointsCount() < 2) {
            return err("至少 2 个点才够一条路径，先 point add");
        }
        List<String> out = new ArrayList<>();
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE);
            Map<String, Object> root = readRoot(file, out);
            root.put(editingId, ruleYaml());
            Files.createDirectories(file.getParent());
            Files.write(file, VisualYaml.dump(root).getBytes(StandardCharsets.UTF_8));
            applyRoot(root);
            out.add(line("已保存到 §f" + FILE + " §7（规则 §f" + editingId + "§7，"
                    + model.pointsCount() + " 点）。播放: §f/odc camera play " + editingId));
        } catch (Exception e) {
            return err("保存失败: " + e);
        }
        return out;
    }

    /** 放弃编辑：关预览、拔临时规则、清会话。 */
    public List<String> abort() {
        List<String> out = new ArrayList<>();
        if (previewOn) {
            previewOn = false;
            out.addAll(CameraPlayback.get().stop());
            CameraStore.get().remove(PREVIEW_ID);
        }
        editingId = null;
        model.reset();
        out.add(line("编辑会话已放弃"));
        return out;
    }

    // ---------------------------------------------------------------- 编辑操作（L2：对齐/镜像/复制）

    /** 对齐：把所有点抬/降到同一高度（first=第 1 点 / avg=平均 / 数字=指定 Y）。 */
    public List<String> align(String mode) {
        Double y = model.resolveAlign(mode);
        if (y == null) {
            return err("用法: point align first|avg|<Y>（至少 2 个点）");
        }
        model.alignTo(y);
        return single(line("已对齐到 Y=" + round3(y) + "（Ctrl+Z 可撤销）"));
    }

    /** 镜像：以玩家所在 X/Z 为轴翻转全部点。 */
    public List<String> mirror(String axis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return err("还没进入世界");
        }
        boolean mx = "x".equalsIgnoreCase(axis);
        boolean mz = "z".equalsIgnoreCase(axis);
        if (!mx && !mz) {
            return err("用法: point mirror x|z（以玩家所在位置为轴）");
        }
        model.mirror(mx, mc.player.position().x, mc.player.position().z);
        return single(line("已沿" + (mx ? "X" : "Z") + "轴镜像（Ctrl+Z 可撤销）"));
    }

    /** 复制当前路径为新规则（落盘 + 实时生效），当前会话继续编辑原路径。 */
    public List<String> copyTo(String newIdRaw) {
        if (model.pointsCount() < 2) {
            return err("至少 2 个点才能复制");
        }
        String newId = newIdRaw == null || newIdRaw.trim().isEmpty()
                ? editingId + "_copy" : newIdRaw.trim();
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE);
            List<String> quiet = new ArrayList<>();
            Map<String, Object> root = readRoot(file, quiet);
            root.put(newId, ruleYaml());
            Files.createDirectories(file.getParent());
            Files.write(file, VisualYaml.dump(root).getBytes(StandardCharsets.UTF_8));
            applyRoot(root);
            return single(line("已复制为 §f" + newId + " §7。播放: §f/odc camera play " + newId));
        } catch (Exception e) {
            return err("复制失败: " + e);
        }
    }

    /** 读本地 Camera.yml（多规则同文件）；文件缺失/损坏时给提示并返回空根（不删原文件）。 */
    private Map<String, Object> readRoot(Path file, List<String> out) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return root;
        }
        try {
            Map<String, Object> parsed = YamlParser.lenient().parse(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (parsed != null) {
                root.putAll(parsed);
            }
        } catch (Exception bad) {
            out.add(line("§e原文件解析失败，按新文件重建（原文件未删）: " + bad.getMessage()));
            root = new LinkedHashMap<>();
        }
        return root;
    }

    /** 把根表回灌规则库（apply 的 id→YAML 文本映射与磁盘文件同构）。 */
    private void applyRoot(Map<String, Object> root) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            if (e.getValue() instanceof Map) {
                texts.put(e.getKey(), VisualYaml.dump((Map<String, Object>) e.getValue()));
            }
        }
        CameraStore.get().apply(texts);
    }

    // ---------------------------------------------------------------- 撤销/重做 + 拖拽（门面，实现在模型）

    /** 任何改变点列的操作前调用；拖拽由 CameraPathDrag 在拾取命中时调。 */
    void pushUndo() {
        model.pushUndo();
    }

    public boolean undo() {
        return model.undo();
    }

    public boolean redo() {
        return model.redo();
    }

    /** 剩余可撤销步数（提示用）。 */
    public int undoDepth() {
        return model.undoDepth();
    }

    /** 拖拽用：直接改第 i 个点（0 基，压栈由拖拽方在按下时做）。 */
    void setPoint(int i, double x, double y, double z) {
        model.setPoint(i, x, y, z);
    }

    int pointsCount() {
        return model.pointsCount();
    }

    double[] pointAt(int i) {
        return model.pointAt(i);
    }

    /** 当前会话的规则表（拖拽落子后重注入预览用）。 */
    public Map<String, Object> ruleYaml() {
        return model.toRuleMap();
    }

    // ---------------------------------------------------------------- 面板数据门面（CameraCurveScreen 用）

    public List<Double> timesView() {
        return model.timesView();
    }

    public List<String> easesView() {
        return model.easesView();
    }

    public double durationValue() {
        return model.durationValue();
    }

    public boolean loopEnabled() {
        return model.loopEnabled();
    }

    public boolean smoothInterp() {
        return model.smoothInterp();
    }

    public void setKeyframeTime(int i, double t) {
        model.setKeyframeTime(i, t);
    }

    public void dragKeyframeTime(int i, double t) {
        model.dragKeyframeTime(i, t);
    }

    public String cycleEase(int i) {
        return model.cycleEase(i);
    }

    public void setDuration(double seconds) {
        model.setDuration(seconds);
    }

    public void setLoop(boolean on) {
        model.setLoop(on);
    }

    public void setSmooth(boolean on) {
        model.setSmooth(on);
    }

    /** 导出当前规则 YAML 文本（面板「导出」/命令共用）；无会话返回 null。 */
    public String exportYaml() {
        return active() ? VisualYaml.dump(ruleYaml()) : null;
    }

    /** 导入 YAML 片段（剪贴板）：解析成功即替换会话内容（Ctrl+Z 可撤回）。 */
    public List<String> importYaml(String text) {
        if (!active()) {
            return err("先 /odc camera edit <id> 开一个编辑会话");
        }
        Map<String, Object> m;
        try {
            m = YamlParser.lenient().parse(text);
        } catch (Exception e) {
            return err("YAML 解析失败: " + e.getMessage());
        }
        if (m == null || m.isEmpty()) {
            return err("剪贴板内容不是有效的 YAML 表");
        }
        CameraPathModel.PathData d = CameraPathModel.parseRuleMap(m);
        if (d == null) {
            return err("导入片段至少要 2 个带 pos 的关键帧");
        }
        pushUndo();
        model.load(d);
        return single(line("已导入 §f" + model.pointsCount() + " §7个关键帧（Ctrl+Z 可撤回）"));
    }

    // ---------------------------------------------------------------- 数据出口

    public boolean active() {
        return editingId != null;
    }

    public boolean previewing() {
        return previewOn;
    }

    /** 只读快照（holo 渲染帧用；元素是内部数组的拷贝引用，编辑线程同主线程，无并发问题）。 */
    public List<double[]> pointsView() {
        return model.pointsView();
    }

    public String editedId() {
        return editingId;
    }

    /** 第 i 个点（1 基序号）的视角；越界返回 null（holo 朝向杆用）。 */
    public double[] rotOf(int i) {
        return model.rotOf(i);
    }

    /** 预览开着时把当前会话内容重注入临时规则（面板/拖拽落子后调用）。 */
    public void rearmPreview() {
        if (previewOn) {
            CameraStore.get().apply(Collections.singletonMap(
                    PREVIEW_ID, VisualYaml.dump(ruleYaml())));
        }
    }

    // ---------------------------------------------------------------- 杂项

    private static double[] eyePos(Minecraft mc) {
        return new double[]{
                mc.player.position().x, mc.player.position().y + 1.62, mc.player.position().z};
    }

    private static double[] eyeRot(Minecraft mc) {
        return new double[]{mc.player.getYRot(), mc.player.getXRot()};
    }

    private static int parseIndex(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String fmt(double[] p) {
        return "(" + round3(p[0]) + ", " + round3(p[1]) + ", " + round3(p[2]) + ")";
    }

    private static String fmt2(double[] r) {
        return "(" + round3(r[0]) + "°, " + round3(r[1]) + "°)";
    }

    private static String line(String s) {
        return "§7[运镜] " + s;
    }

    private static List<String> err(String msg) {
        List<String> out = new ArrayList<>();
        out.add(line("§c" + msg));
        return out;
    }

    private static List<String> single(String msg) {
        List<String> out = new ArrayList<>();
        out.add(msg);
        return out;
    }
}
