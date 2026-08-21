package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.Ready;
import com.opendreamcore.protocol.message.ReadyAck;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界编辑器行为模块。
 * 交互（射线拾取/拖拽/手柄/框选/微调）、变换（旋转/缩放/描边/层级/透明度/流光）、
 * undo/redo、元素 CRUD/剪贴板、对齐/分布/镜像、模板、保存/放弃。
 * 状态字段暂留 ClientController（渲染与协议层共用），经 cc 前缀访问；后续轮次再迁状态。
 */
final class WorldEditor {

    // ---- 静态常量（必须在 INSTANCE 之前初始化：构造器/实例字段初始化依赖它们）----
    // 这些 static final 必须在 INSTANCE 之前初始化，因为 INSTANCE = new ClientController()
    // 会触发实例字段初始化，而实例字段 toolbarTypeRects 依赖 WORLD_TYPE_CHIPS.length。
    static final String[] WORLD_TYPE_CHIPS = {"text", "rect", "item_slot", "image",
            "slider", "toggle", "checkbox", "dropdown", "progress", "tabs"};
    static final String[] WORLD_TYPE_CHIP_LABELS = {"文本", "矩形", "物品", "图片",
            "滑块", "开关", "复选", "下拉", "进度", "页签"};
    /** 描边颜色循环调色板。 */
    static final String[] BORDER_PALETTE = {"#FFD700", "#4FC3F7", "#66BB6A", "#E57373", "#FFFFFF"};
    /** 描边流光色（第二列：点击 = flow:true + flowColor）。 */
    static final String[] BORDER_FLOW_PALETTE = {"#FF6B6B", "#4FC3F7", "#66BB6A", "#FFD54F", "#BA68C8"};
    /** 流光速度档（第三列：ms/圈；0 = 默认 1200 + hover 加速）。 */
    static final long[] BORDER_FLOW_SPEEDS = {2400, 1200, 500};
    static final String[] BORDER_FLOW_SPEED_LABELS = {"慢", "中", "快"};
    /** 描边样式预设（第四列：实线 / 虚线 / 点线 / 双线）。 */
    static final String[] BORDER_STYLE_LABELS = {"实", "虚", "点", "双"};
    /** 流光段长档（第五列：周长比例 0.1 短 / 0.15 中 / 0.25 长）。 */
    static final float[] BORDER_FLOW_SEGS = {0.1F, 0.15F, 0.25F};
    static final String[] BORDER_FLOW_SEG_LABELS = {"短", "中", "长"};
    /** 描边透明度档（第六列：0.35 淡 / 0.7 中 / 1.0 实）。 */
    static final float[] BORDER_ALPHAS = {0.35F, 0.7F, 1.0F};
    static final String[] BORDER_ALPHA_LABELS = {"淡", "中", "实"};
    /** 流光段数档（第九列：单/双/三 段同时流动）。 */
    static final int[] BORDER_FLOW_SEGMENT_COUNTS = {1, 2, 3};
    static final String[] BORDER_FLOW_SEGMENT_LABELS = {"单", "双", "三"};
    /** 流光段间距档（第十列：密 0.15 / 均 0=等距 / 疏 0.45 周长比例）。 */
    static final float[] BORDER_FLOW_GAPS = {0.15F, 0, 0.45F};
    static final String[] BORDER_FLOW_GAP_LABELS = {"密", "均", "疏"};
    static final int WORLD_UNDO_LIMIT = 64;

    private static final WorldEditor INSTANCE = new WorldEditor();

    /** 子系统入口（单例）。 */
    static WorldEditor get() {
        return INSTANCE;
    }

    /** 宿主控制器（世界页面/编辑状态字段）。 */
    private final ClientController cc = ClientController.get();

    // ---- 世界编辑状态（从 ClientController 迁入，round 4）----
    // 世界面板射线交互状态（悬停元素 + 左键边沿）
    String worldHoverId;
    boolean worldMousePrev;
    // 世界面板拖拽（hologram.draggable / world.drag）：按住沿射线平面移动，松手提交
    String worldDragId;
    net.minecraft.world.phys.Vec3 worldDragBase;
    /** Shift 拖拽锁轴：0 = 无，1 = 锁 x，2 = 锁 y（按主导位移轴锁定）。 */
    int worldDragLockAxis;
    /** 编辑模式对齐参考线 {guideX, guideY}（NaN = 无；拖拽中每帧更新，松手清除）。 */
    double[] worldDragGuides;
    /** 点击涟漪（元素点击反馈）：{x, y, z, startMs}（世界坐标，400ms 衰减）。 */
    final java.util.List<double[]> worldRipples = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 点击弹跳（元素点击后 scale 弹跳）：元素 id → 开始毫秒（300ms）。 */
    final Map<String, Long> worldClickBounces = new ConcurrentHashMap<>();
    /** 剪贴板是否已从磁盘加载（首次复制/粘贴时懒加载，跨会话持久）。 */
    boolean worldClipboardLoaded;
    /** 单属性值剪贴板（属性面板 [复制] → 编辑屏 [粘贴值] 跨元素应用）。 */
    String worldPropClipboard;
    String worldPropClipboardPath;
    /** 当前编辑属性路径（世界侧浮签显示；null = 无编辑屏）。 */
    String worldEditLabel;
    /** 分布 ghost 预览目标盒（对齐屏悬停 dist 按钮驱动：{x, y, w, h} 列表，null = 无）。 */
    java.util.List<double[]> worldDistributeGhost;
    /** 对齐参考包围盒预览（对齐屏悬停对齐模式按钮驱动：{x0,y0,x1,y1}，null = 无）。 */
    double[] worldAlignBoundsPreview;
    /** 跨面板参考预览（跨面模式悬停：参考面板选中元素框，已换算到当前面板坐标；{x,y,w,h}，null = 无）。 */
    double[] worldCrossPreview;
    /** 跨面板参考锚点预览（跨面模式悬停：参考面板锚点偏移，已换算当前面板坐标；{dx,dy,dz}，null = 无）。 */
    double[] worldCrossAnchorPreview;
    /** 编辑模式框选（拖框多选）：{x0, y0, x1, y1}（scaled 屏幕坐标，null = 无框选）。 */
    double[] worldMarquee;
    /** 编辑模式面板整体移动（Alt + 拖拽空白区）：所有元素同偏移。 */
    boolean worldPanelMove;
    net.minecraft.world.phys.Vec3 worldPanelMoveBase;
    /** 锚点拖拽（M + 拖拽空白区）：面板整体随锚点偏移，写 world.offsetX/Y/Z；true = 拖拽中。 */
    boolean worldAnchorDragActive;
    double[] worldAnchorDragBase;
    /** 编组序号（hologram.group 自动命名）。 */
    int worldGroupSeq;
    /** z 层级调整按键边沿（[ / ]）。 */
    boolean worldEditZPrevDown;
    boolean worldEditZPrevUp;
    /** 旋转 90° 按键边沿（R / Shift+R）。 */
    boolean worldEditRPrevDown;
    /** 选区 yaw 微调按键边沿（Y / Shift+Y）。 */
    boolean worldEditYawPrev;
    /** 编辑「干净预览」状态（I 切换：隐藏选中框/锚点/参考线等浮层）。 */
    boolean worldEditPreview;
    boolean worldEditPreviewPrev;
    /** 显示全部隐藏元素按键边沿（J）。 */
    boolean worldEditShowAllPrev;
    /** 右键菜单键盘导航（↑/↓ 光标，Enter 执行）。 */
    int worldCtxCursor;
    boolean worldCtxKeyUpPrev;
    boolean worldCtxKeyDownPrev;
    boolean worldCtxEnterPrev;
    /** 透明度调节按键边沿（O / Shift+O）。 */
    boolean worldEditOPrev;
    /** 文本对齐循环按键边沿（T）。 */
    boolean worldEditTPrev;
    /** 字号步进按键边沿（= / -）。 */
    boolean worldEditScalePrevEq;
    boolean worldEditScalePrevMinus;
    /** 描边按键边沿（B 颜色循环 / , . 宽度）。 */
    boolean worldEditBPrev;
    boolean worldEditCommaPrev;
    boolean worldEditPeriodPrev;
    /** L 流光 hover 加速开关按键边沿。 */
    boolean worldEditLPrev;
    /** Ctrl+,/. 流光速度微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldFlowSpeedHoldTicks;
    /** Shift+,/. 流光段相位微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldFlowPhaseHoldTicks;
    /** Alt+,/. 流光段长微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldFlowSegHoldTicks;
    /** Ctrl+Shift+,/. 流光段间距微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldFlowGapHoldTicks;
    /** Ctrl+Alt+,/. 描边宽度微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldBorderWidthHoldTicks;
    /** Ctrl+Shift+Alt+,/. 副色段相位微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldFlowPhase2HoldTicks;
    /** 9/0 流光段数微调按住计数（≥6 帧 ≈ 300ms 连续微调）。 */
    int worldFlowSegmentsHoldTicks;
    /** 圆角步进按键边沿（; / '）。 */
    boolean worldEditSemiPrev;
    boolean worldEditApoPrev;
    int borderColorIdx;
    /** 编辑模式旋转手柄（选中元素顶部圆形手柄拖拽旋转 hologram.yaw）。 */
    String worldRotateId;
    boolean worldRotatePrevDown;
    /** 编辑模式缩放手柄（右下角方块拖拽改 hologram.width/height）。 */
    String worldResizeId;
    double worldResizeStartDist;
    double worldResizeStartW;
    double worldResizeStartH;
    /** 编辑模式描边手柄（左边缘菱形拖拽调 hologram.border.width，实时视觉预览）。 */
    String worldBorderId;
    double worldBorderStartW;
    net.minecraft.world.phys.Vec3 worldBorderStartPt;
    /** 服务端元素状态覆盖（页/元素 → [可见?, 可用?]，null = 未覆盖；页面重开清除）。 */
    final Map<String, Boolean[]> worldElementStates = new ConcurrentHashMap<>();
    // 按下候选（点击/拖拽区分）
    String worldPressCandidate;
    double worldPressX;
    double worldPressY;
    long worldPressAt;
    /** 当前按下的元素（按下缩放反馈：渲染时 scale * 0.95，松开清除）。 */
    String worldPressedId;
    String worldSliderDragId;
    boolean worldEditMode;
    String worldEditSelected;
    /** 未保存的编辑位置（元素 id → [x, y, z]，保存时统一写回页面文件）。 */
    final Map<String, double[]> worldEditDirty = new ConcurrentHashMap<>();
    /** 进入编辑模式时的位置快照（放弃编辑用）。 */
    final Map<String, double[]> worldEditOriginal = new ConcurrentHashMap<>();
    /** 方向键微调步长（世界单位，工具栏可循环调节）。 */
    double worldEditStep = 0.05;
    /** 编辑模式网格吸附（0 = 关；工具栏循环调节，拖拽提交时优先于元素自身 snap）。 */
    double worldEditSnap = 0;
    long lastWorldNudgeAt;
    /** 未保存的属性编辑（元素 id → 点路径 → 字符串值，保存时与位置一起写回页面文件）。 */
    final Map<String, Map<String, String>> worldEditProps = new ConcurrentHashMap<>();
    /** 进入编辑模式时的属性快照（放弃编辑用）。 */
    final Map<String, Map<String, String>> worldEditOriginalProps = new ConcurrentHashMap<>();
    /** 未保存的删除（元素 id，保存时服务端从页面文件移除整块）。 */
    final java.util.Set<String> worldEditDeletes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 已删除元素的快照（放弃编辑时还原）。 */
    final Map<String, Element> worldEditDeletedElements = new ConcurrentHashMap<>();
    /** 撤消暂存元素（撤销"创建"时暂存，重做时还原；与删除快照分开避免放弃编辑误还原）。 */
    final Map<String, Element> worldEditUndoElements = new ConcurrentHashMap<>();
    /** 编辑撤消/重做历史栈（元素状态快照，上限 WORLD_UNDO_LIMIT）。 */
    final java.util.ArrayDeque<WorldEditOp> worldUndoStack = new java.util.ArrayDeque<>();
    final java.util.ArrayDeque<WorldEditOp> worldRedoStack = new java.util.ArrayDeque<>();
    /** 进入编辑模式时的页面 options 快照（背景/锚点等 world 段；保存时差异写回，放弃时还原）。 */
    Map<String, Object> worldOptionsBaseline;
    /** 待写入的页面标题（对齐屏改标题；null = 未修改；保存时随 EDITOR_WORLD 写回 YAML 顶层）。 */
    String worldEditPageTitle;
    /** 待写入的页面变量编辑（变量名 → 值；__unset__ = 删除；保存时随 EDITOR_WORLD 写回）。 */
    final Map<String, String> worldEditVars = new ConcurrentHashMap<>();
    /** 进入编辑模式时的页面 variables 快照（放弃编辑还原）。 */
    Map<String, Object> worldVariablesBaseline;
    /** 导入元素替换二次确认时间戳（>20 元素需 3 秒内再次触发）。 */
    long worldImportConfirmAt;
    /** 格式刷剪贴板（元素格式 JSON：props+actions；Ctrl+Shift+C 复制 / Ctrl+Shift+V 粘贴）。 */
    String worldFormatClipboard;
    /** 元素 YAML 剪贴板（完整元素块；Ctrl+Shift+E 复制 / Ctrl+Shift+G 粘贴为新元素）。 */
    String worldElementYamlClipboard;
    /** 各面板最近选中元素（pageId → elementId；跨面板对齐目标记忆）。 */
    final java.util.Map<String, String> worldPanelSelections = new java.util.HashMap<>();
    /** 上次聚焦的面板 id（跨面板对齐的参考方）。 */
    String worldLastPanelPid;
    /** Ctrl+Z / Ctrl+Y 按键边沿（编辑模式撤消/重做）。 */
    boolean worldEditUndoPrev;
    boolean worldEditRedoPrev;
    /** 工具栏按钮矩形（scaled 坐标，渲染时更新，点击时判定）。 */
    final int[] toolbarStep = new int[4];
    final int[] toolbarSnap = new int[4];
    final int[] toolbarSave = new int[4];
    final int[] toolbarDiscard = new int[4];
    final int[] toolbarExit = new int[4];
    final int[] toolbarAdd = new int[4];
    final int[] toolbarDelete = new int[4];
    final int[] toolbarText = new int[4];
    final int[] toolbarColor = new int[4];
    final int[] toolbarScale = new int[4];
    final int[] toolbarProps = new int[4];
    final int[] toolbarAlign = new int[4];
    final int[] toolbarUndo = new int[4];
    final int[] toolbarRedo = new int[4];
    /** 工具栏折叠（收起第二~四行，只留信息/步长/吸附/保存/放弃/退出，省视野）。 */
    boolean worldToolbarCollapsed;
    final int[] toolbarCollapse = new int[4];
    /** 历史面板入口（工具栏第三行"历史 N 步"chip，点击打开撤消/重做列表）。 */
    final int[] toolbarHistory = new int[4];
    /** 工具栏拖入创建：可拖拽类型 chips（声明在类顶部，见 INSTANCE 初始化注释）。 */
    final int[][] toolbarTypeRects = new int[WORLD_TYPE_CHIPS.length][4];
    /** 层级面包屑（父链）段矩形 + 对应元素 id（工具栏第二行渲染，点击 = 选中祖先）。 */
    final java.util.List<int[]> toolbarBreadcrumbRects = new java.util.ArrayList<>();
    final java.util.List<String> worldBreadcrumbIds = new java.util.ArrayList<>();
    /** 拖入创建拖拽状态（chip 按下 → 拖到面板 → 松手创建）。 */
    String worldTypeDrag;
    boolean worldTypeDragMoved;
    double worldTypeDragPressX;
    double worldTypeDragPressY;
    net.minecraft.world.phys.Vec3 worldTypeDropPoint;
    /** z 排序拖拽（编辑模式按住 Z + 拖拽元素 = 上下拖动改层级 z；可撤消）。 */
    String worldZScrubId;
    double worldZScrubStartY;
    /** 透明度拖拽（编辑模式按住 O + 拖拽元素 = 上下拖动改 opacity；可撤消）。 */
    String worldOpacityScrubId;
    double worldOpacityScrubStartY;
    /** 编辑模式右键菜单（右键元素打开，点击项执行；左键空白/右键/ESC 关闭）。 */
    String worldCtxId;
    double worldCtxX;
    double worldCtxY;
    boolean worldRightPrev;
    boolean worldEscPrev;
    /** 编辑网格（G 键切换；步长 = 工具栏吸附值，吸附关 = 0.25）。 */
    boolean worldEditGrid;
    boolean worldGridPrev;
    /** 选中元素半透明透视（按住 H；看下层元素用；渲染线程读取）。 */
    boolean worldGhostOn;
    /** 面板背景隐藏（按住 U；看全貌/元素边界用；渲染线程读取）。 */
    boolean worldHideBackground;
    boolean worldFlowKPrev;
    boolean worldFlowStepCommaPrev;
    boolean worldFlowStepPeriodPrev;
    /** 预设循环载入游标（Alt+Ctrl+L 循环全部预设）。 */
    int worldBgPresetIdx;
    /** P 键边沿（锚点模式循环）。 */
    boolean worldModePPrev;
    /** M+方向键锚点微移节流（200ms 重复）。 */
    long worldAnchorNudgeAt;
    /** N 键边沿（锚点偏移复位）。 */
    boolean worldAnchorNPrev;
    /** 锚点微移步进档（0 粗 0.1 / 1 细 0.01 / 2 微 0.001；M+Shift+滚轮循环）。 */
    int worldAnchorStepIdx;
    /** 整体缩放累计系数（Alt+滚轮缩放读数用；仅展示，不参与状态）。 */
    double worldPanelScaleAccum = 1.0;
    long worldPanelScaleAt;
    /** 整体旋转累计角度（Ctrl+Alt+滚轮旋转读数用；仅展示，不参与状态）。 */
    double worldPanelRotateAccum;
    long worldPanelRotateAt;
    /** 元素查找屏（F 键边沿）。 */
    boolean worldFindPrev;
    /** 拖拽距离标注（拖拽中：最近可见元素连线的两端世界坐标 + 间距；{ax,ay,az,bx,by,bz,dist}）。 */
    double[] worldDistAnno;
    boolean toolbarVisible;
    // ---------- 世界悬停光标（GLFW，元素级样式可配） ----------
    long worldHandCursor = -1;
    long worldCrossCursor = -1;
    long worldIbeamCursor = -1;
    /** 页面级（背景/锚点/淡出等）操作计数（会话统计报告用）。 */
    int worldBgOpCount;
    /** 跨元素快速配色：复制的颜色值（copyWorldElementColor 写入）。 */
    String worldCopiedColor;

    void nudgeWorldAnchor(Minecraft mc, boolean left, boolean right, boolean up, boolean down) {
        if (cc.worldPage == null) {
            return;
        }
        double step = cc.worldAnchorStep();
        double dx = left ? -step : right ? step : 0;
        double dy = up ? step : down ? -step : 0;
        double dz = 0;
        boolean shift = cc.shiftHeld(mc);
        if (shift) {
            dy = 0;
            dz = up ? step : down ? -step : 0;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        cc.pushWorldBackgroundUndo("锚点: 微移", "anchor:nudge");
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        double ox = world.get("offsetX") instanceof Number n ? n.doubleValue()
                : world.get("offsetX") == null ? 0 : cc.parseAnchorNum(world.get("offsetX"), 0);
        double oy = world.get("offsetY") instanceof Number n ? n.doubleValue()
                : world.get("offsetY") == null ? 1.6 : cc.parseAnchorNum(world.get("offsetY"), 1.6);
        double oz = world.get("offsetZ") instanceof Number n ? n.doubleValue()
                : world.get("offsetZ") == null ? 3 : cc.parseAnchorNum(world.get("offsetZ"), 3);
        world.put("offsetX", Math.round((ox + dx) * 100) / 100.0);
        world.put("offsetY", Math.round((oy + dy) * 100) / 100.0);
        world.put("offsetZ", Math.round((oz + dz) * 100) / 100.0);
        options.put("world", world);
        cc.updateWorldPanelAnchors(Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    public void alignWorldCross(String mode) {
        if (!worldEditMode || cc.worldPage == null || cc.worldNodes == null || worldEditSelected == null) {
            return;
        }
        if (cc.worldElementLocked(worldEditSelected)) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f选中元素已锁定，先解锁再跨面板对齐"), false);
            return;
        }
        if (!cc.worldCrossAlignAvailable()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f跨面板对齐需先 ◀面板/面板▶ 切换到另一面板（参考其选中元素）"), false);
            return;
        }
        String pid = cc.worldPage.id() == null ? "world" : cc.worldPage.id();
        ClientController.WorldPanel other = cc.findWorldPanel(worldLastPanelPid);
        String otherId = worldPanelSelections.get(worldLastPanelPid);
        var elA = cc.findElement(cc.worldPage, worldEditSelected);
        var elB = cc.findElement(other.page, otherId);
        if (elA == null || elB == null || other.anchor == null) {
            return;
        }
        ClientController.WorldPanel panelA = cc.findWorldPanel(pid);
        if (panelA == null || panelA.anchor == null) {
            return;
        }
        var vars = cc.worldPage.variables();
        Map<?, ?> hA = elA.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        Map<?, ?> hB = elB.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        String tA = String.valueOf(elA.props().get("type"));
        String tB = String.valueOf(elB.props().get("type"));
        double wA = WorldHologram.holoNum(hA, "width", "text".equals(tA) ? 2.0 : 1.0, vars);
        double hhA = WorldHologram.holoNum(hA, "height", "text".equals(tA) ? 0.25 : 1.0, vars);
        double wB = WorldHologram.holoNum(hB, "width", "text".equals(tB) ? 2.0 : 1.0, vars);
        double hhB = WorldHologram.holoNum(hB, "height", "text".equals(tB) ? 0.25 : 1.0, vars);
        double xB = WorldHologram.holoNum(hB, "x", 0, vars);
        double yB = WorldHologram.holoNum(hB, "y", 0, vars);
        double cBx = other.anchor.x + xB;
        double cBy = other.anchor.y + yB;
        double cx = cBx, cy = cBy;
        switch (mode) {
            case "left" -> cx = cBx - wA / 2 + wB / 2;
            case "right" -> cx = cBx + wA / 2 - wB / 2;
            case "hcenter" -> cx = cBx;
            case "top" -> cy = cBy - hhA / 2 + hhB / 2;
            case "bottom" -> cy = cBy + hhA / 2 - hhB / 2;
            case "vcenter" -> cy = cBy;
            default -> {
                return;
            }
        }
        pushWorldUndo("跨面板对齐", "align:" + mode, List.of(worldEditSelected));
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(hA);
        copy.put("x", Math.round((cx - panelA.anchor.x) * 100) / 100.0);
        copy.put("y", Math.round((cy - panelA.anchor.y) * 100) / 100.0);
        elA.props().put("hologram", copy);
        worldEditDirty.put(worldEditSelected, new double[]{
                WorldHologram.holoNum(copy, "x", 0, vars),
                WorldHologram.holoNum(copy, "y", 0, vars),
                WorldHologram.holoNum(copy, "z", 0, vars)});
        cc.refreshCreateBlock(worldEditSelected);
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f跨面板对齐 " + worldEditSelected
                        + " → " + otherId + "（" + mode + "，" + worldLastPanelPid + " 为参考）"), false);
    }

    void cycleWorldDropdown(RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "dropdown");
        List<?> options = spec.get("options") instanceof List<?> list ? list : List.of();
        if (options.isEmpty()) {
            return;
        }
        String key = cc.wkey(cc.worldPage.id() == null ? "world" : cc.worldPage.id(), node.id());
        Integer current = cc.worldDropdown.get(key);
        int next = (current == null ? 0 : current + 1) % options.size();
        cc.worldDropdown.put(key, next);
        String value = String.valueOf(options.get(next));
        if (cc.worldSession != null && cc.isServerMode()) {
            cc.sendEvent(cc.worldSession.event(node.id(), UiEvent.Trigger.INPUT, value));
        } else {
            String script = node.source() != null ? node.source().actions().get("input") : null;
            if (script != null && !script.isBlank()) {
                cc.runLocalAction(cc.worldPage, script, value);
            }
        }
    }

    void switchWorldTab(RenderNode node, net.minecraft.client.Camera camera, Minecraft mc) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "tabs");
        List<?> options = spec.get("options") instanceof List<?> l ? l : List.of();
        if (options.isEmpty() || cc.worldPage == null) {
            return;
        }
        // 复用滑块求交：射线与过元素中心的相机面求交 → 局部 X → 比例 → 下标
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1));
        net.minecraft.world.phys.Vec3 center = cc.worldElementCenter(node, camera);
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        double t = ((center.x - ray[0]) * n.x + (center.y - ray[1]) * n.y + (center.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return;
        }
        double hx = ray[0] + ray[3] * t - center.x;
        double hy = ray[1] + ray[4] * t - center.y;
        double hz = ray[2] + ray[5] * t - center.z;
        var right = rot.transformDirection(new org.joml.Vector3f(1, 0, 0));
        double localX = hx * right.x + hy * right.y + hz * right.z;
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        double w = WorldHologram.holoNum(holo, "width", 3, cc.worldPage.variables());
        double ratio = w > 0 ? (localX + w / 2) / w : 0;
        int idx = (int) Math.max(0, Math.min(options.size() - 1, Math.floor(ratio * options.size())));
        String value = String.valueOf(options.get(idx));
        String pageId = cc.worldPage.id() == null ? "world" : cc.worldPage.id();
        String prev = cc.worldTab.get(pageId);
        cc.worldTab.put(pageId, value);
        ClientController.WorldPanel panel = cc.findWorldPanel(pageId);
        if (panel != null) {
            panel.tabSwitchAt = System.currentTimeMillis(); // 触发页签内容淡入过渡（按面板）
        }
        cc.runTabChangeLifecycle(cc.worldPage, value, prev); // 页签切换生命周期（functions.onTabChange）
        if (cc.worldSession != null && cc.isServerMode()) {
            cc.sendEvent(cc.worldSession.event(node.id(), UiEvent.Trigger.INPUT, value));
        } else {
            String script = node.source() != null ? node.source().actions().get("input") : null;
            if (script != null && !script.isBlank()) {
                cc.runLocalAction(cc.worldPage, script, value);
            }
        }
    }

    void updateWorldSlider(net.minecraft.client.Camera camera, Minecraft mc) {
        RenderNode node = cc.findWorldNode(worldSliderDragId);
        if (node == null || cc.worldPage == null) {
            return;
        }
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1));
        net.minecraft.world.phys.Vec3 center = cc.worldElementCenter(node, camera);
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        double t = ((center.x - ray[0]) * n.x + (center.y - ray[1]) * n.y + (center.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return;
        }
        double hx = ray[0] + ray[3] * t - center.x;
        double hy = ray[1] + ray[4] * t - center.y;
        double hz = ray[2] + ray[5] * t - center.z;
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        Map<?, ?> spec = UiRenderer.propsMap(node, "slider");
        boolean vertical = UiRenderer.bool(spec.get("vertical"), false);
        double w = WorldHologram.holoNum(holo, "width", 1.5, cc.worldPage.variables());
        double h = WorldHologram.holoNum(holo, "height", 0.15, cc.worldPage.variables());
        double local;
        if (vertical) {
            var up = rot.transformDirection(new org.joml.Vector3f(0, 1, 0));
            local = hx * up.x + hy * up.y + hz * up.z;
        } else {
            var right = rot.transformDirection(new org.joml.Vector3f(1, 0, 0));
            local = hx * right.x + hy * right.y + hz * right.z;
        }
        double range = vertical ? h : w;
        double ratio = range > 0 ? (local + range / 2) / range : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        double value = min + (max - min) * ratio;
        double step = UiRenderer.num(spec.get("step"), 0);
        if (step > 0) {
            value = min + Math.round((value - min) / step) * step;
            value = Math.max(min, Math.min(max, value));
        }
        cc.worldSlider.put(cc.wkey(cc.worldPage.id() == null ? "world" : cc.worldPage.id(), node.id()),
                Math.round(value * 100) / 100.0);
    }

    void commitWorldSlider() {
        String id = worldSliderDragId;
        worldSliderDragId = null;
        Double value = cc.worldSlider.get(cc.wkey(cc.worldPage.id() == null ? "world" : cc.worldPage.id(), id));
        if (value == null || cc.worldPage == null) {
            return;
        }
        RenderNode node = cc.findWorldNode(id);
        if (node == null) {
            return;
        }
        if (cc.worldSession != null && cc.isServerMode()) {
            cc.sendEvent(cc.worldSession.event(id, UiEvent.Trigger.INPUT, String.valueOf(value)));
        } else {
            String script = node.source() != null ? node.source().actions().get("input") : null;
            if (script != null && !script.isBlank()) {
                cc.runLocalAction(cc.worldPage, script, String.valueOf(value));
            }
        }
    }

    static double worldHitDepth(RenderNode node, net.minecraft.client.Camera camera,
                                        net.minecraft.world.phys.Vec3 anchor) {
        try {
            Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
            double x = WorldHologram.holoNum(holo, "x", 0, Map.of());
            double y = WorldHologram.holoNum(holo, "y", 0, Map.of());
            double z = WorldHologram.holoNum(holo, "z", 0, Map.of());
            net.minecraft.world.phys.Vec3 cam = camera.getPosition();
            double dx = anchor.x + x - cam.x;
            double dy = anchor.y + y - cam.y;
            double dz = anchor.z + z - cam.z;
            return dx * dx + dy * dy + dz * dz;
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    /**
     * 世界 3D 面板射线交互：跨面板射线拾取（最近命中）→ 悬停/点击触发本地 actions 脚本。
     * 页级 options.world.interact: true 或元素 hologram.interact: true 才参与拾取。
     */
    void tickWorldInteraction(net.minecraft.client.Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.screen != null) {
            return; // 屏幕（含属性输入屏）打开时不进行世界交互
        }
        // 跨面板拾取：所有打开的面板各自射线，取相机最近命中；悬停面板自动聚焦
        RenderNode hit = null;
        ClientController.WorldPanel hitPanel = null;
        double bestDepth = Double.MAX_VALUE;
        try {
            for (ClientController.WorldPanel panel : cc.worldPanels) {
                String pid = panel.page.id() == null ? "world" : panel.page.id();
                RenderNode h = WorldHologram.raycast(panel.nodes, panel.page.options(), camera, mc,
                        cc.worldTabActive(pid), pid, panel.page.variables(), panel.anchor);
                if (h != null) {
                    double d = worldHitDepth(h, camera, panel.anchor);
                    if (d < bestDepth) {
                        bestDepth = d;
                        hit = h;
                        hitPanel = panel;
                    }
                }
            }
        } catch (Exception e) {
            return; // 拾取失败不拖垮帧
        }
        if (hitPanel != null && cc.worldPage != hitPanel.page) {
            cc.focusWorldPanel(hitPanel); // 悬停哪块面板 → 交互聚焦哪块
        }
        String now = hit == null ? null : hit.id();
        if (now == null) {
            // 全部离开：各面板悬停清空 + 聚焦恢复默认光标
            if (worldHoverId != null) {
                for (ClientController.WorldPanel panel : cc.worldPanels) {
                    panel.hoverId = null;
                    panel.pendingHoverId = null;
                }
                worldHoverId = null;
                cc.pendingHoverId = null;
                cc.setWorldCursor(null); // 恢复默认光标
            }
        } else if (hitPanel != null && !now.equals(hitPanel.hoverId)) {
            // 新悬停目标：清掉其余面板悬停 + 更新本面板
            for (ClientController.WorldPanel panel : cc.worldPanels) {
                if (panel != hitPanel) {
                    panel.hoverId = null;
                    panel.pendingHoverId = null;
                }
            }
            hitPanel.hoverId = now;
            worldHoverId = now;
            cc.pendingHoverId = now;
            cc.setWorldCursor(hit); // 悬停世界元素 → 元素级光标（缺省手型）
            cc.playWorldHoverSound(); // hoverSound 反馈（悬停新元素时播放）
            if (cc.worldSession != null && cc.isServerMode()) {
                // 服务端世界页面：悬停节流合并（快速扫过多个元素每 50ms 至多 1 包，发最新的）
                hitPanel.pendingHoverId = now;
                return;
            }
            if (hit != null) {
                String script = hit.source() != null ? hit.source().actions().get("hover") : null;
                if (script != null && !script.isBlank()) {
                    cc.runLocalAction(hitPanel.page, script);
                }
            }
        }
        // 悬停节流 flush：超过节流窗口且有待上报 → 发最新悬停目标（聚焦面板会话）
        if (cc.worldSession != null && cc.isServerMode() && cc.pendingHoverId != null
                && System.currentTimeMillis() - cc.lastHoverSentAt >= cc.HOVER_THROTTLE_MS) {
            cc.sendEvent(cc.worldSession.event(cc.pendingHoverId, UiEvent.Trigger.HOVER, null));
            cc.lastHoverSentAt = System.currentTimeMillis();
            cc.pendingHoverId = null;
            String pid = cc.worldPage == null || cc.worldPage.id() == null ? "world" : cc.worldPage.id();
            ClientController.WorldPanel fp = cc.findWorldPanel(pid);
            if (fp != null) {
                fp.pendingHoverId = null;
            }
        }
        // 左键交互（点击/拖拽/滑块区分）：短按 = 点击；长按（>250ms）或位移（>5px）= 拖拽；slider 按下即拖值
        boolean down = mc.mouseHandler.isLeftPressed();
        long nowMs = System.currentTimeMillis();
        // 编辑模式右键菜单：右键元素打开；右键空白处 = 关闭
        long winId = mc.getWindow().getWindow();
        boolean rdown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(winId,
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1;
        if (worldEditMode && rdown && !worldRightPrev) {
            if (hit != null && hit.enabled() && cc.worldElementEnabled(cc.worldPage.id(), hit.id())) {
                double[] mouse = cc.scaledMouse(mc);
                worldCtxId = hit.id();
                worldCtxX = mouse[0];
                worldCtxY = mouse[1];
                cc.worldCtxRects.clear();
            } else {
                worldCtxId = null; // 空白右键 = 关闭菜单
                cc.worldCtxRects.clear();
            }
        }
        worldRightPrev = rdown;
        // ESC 关闭右键菜单
        boolean esc = org.lwjgl.glfw.GLFW.glfwGetKey(winId, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == 1;
        if (worldEditMode && worldCtxId != null && esc && !worldEscPrev) {
            worldCtxId = null;
            cc.worldCtxRects.clear();
        }
        worldEscPrev = esc;
        // 菜单打开：左键命中菜单项 → 执行；点空白 → 关闭（优先于一切元素交互）
        if (worldCtxId != null) {
            if (down && !worldMousePrev) {
                double[] mouse = cc.scaledMouse(mc);
                boolean hitItem = false;
                for (int i = 0; i < cc.worldCtxRects.size(); i++) {
                    if (cc.inside((int) mouse[0], (int) mouse[1], cc.worldCtxRects.get(i))) {
                        cc.runWorldContextAction(i);
                        hitItem = true;
                        break;
                    }
                }
                if (!hitItem) {
                    worldCtxId = null;
                    cc.worldCtxRects.clear();
                }
                worldMousePrev = true;
                return;
            }
            if (!down) {
                worldMousePrev = false;
            }
            return; // 菜单打开期间阻断元素交互
        }
        // 描边色板点击（选中元素带描边时手柄旁色板：改色 / 关闭 / 流光副色 Shift）
        if (down && !worldMousePrev && !cc.worldBorderPaletteRects.isEmpty()) {
            double[] mouse = cc.scaledMouse(mc);
            for (int i = 0; i < cc.worldBorderPaletteRects.size(); i++) {
                if (cc.inside((int) mouse[0], (int) mouse[1], cc.worldBorderPaletteRects.get(i))) {
                    cc.applyBorderPaletteClick(i, cc.shiftHeld(mc));
                    worldMousePrev = true;
                    return;
                }
            }
        }
        // 编辑模式旋转手柄（拖拽中 / 按下检测，优先于世界射线交互）
        if (worldEditMode && worldRotateId != null) {
            if (down) {
                updateWorldRotate(mc);
            } else {
                commitWorldRotate();
            }
            worldMousePrev = down;
            return;
        }
        if (down && !worldMousePrev && worldEditMode && worldEditSelected != null
                && !WorldHologram.locked(cc.findWorldNode(worldEditSelected))
                && hitRotateHandle(camera, mc)) {
            worldRotateId = worldEditSelected;
            pushWorldUndo("旋转拖拽", null, List.of(worldEditSelected)); // 拖拽前快照（拖完可撤消）
            worldMousePrev = true;
            return;
        }
        // 编辑模式缩放手柄（右下角，拖拽改尺寸）
        if (worldEditMode && worldResizeId != null) {
            if (down) {
                updateWorldResize(mc);
            } else {
                commitWorldResize();
            }
            worldMousePrev = down;
            return;
        }
        if (down && !worldMousePrev && worldEditMode && worldEditSelected != null
                && !WorldHologram.locked(cc.findWorldNode(worldEditSelected))
                && hitResizeHandle(camera, mc)) {
            worldResizeId = worldEditSelected;
            pushWorldUndo("缩放拖拽", null, List.of(worldEditSelected)); // 拖拽前快照（拖完可撤消）
            RenderNode node = cc.findWorldNode(worldEditSelected);
            if (node != null) {
                Object raw = node.props().get("hologram");
                Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
                var vars = cc.worldPage.variables();
                worldResizeStartW = WorldHologram.holoNum(holo, "width",
                        "text".equals(node.type()) ? 2.0 : 1.0, vars);
                worldResizeStartH = WorldHologram.holoNum(holo, "height",
                        "text".equals(node.type()) ? 0.25 : 1.0, vars);
                worldResizeStartDist = resizeMouseDist(camera, mc);
            }
            worldMousePrev = true;
            return;
        }
        // 编辑模式描边手柄（左边缘菱形，拖拽调边框宽度；选中元素带 hologram.border 才可拖）
        if (worldEditMode && worldBorderId != null) {
            if (down) {
                updateWorldBorder(mc);
            } else {
                commitWorldBorder();
            }
            worldMousePrev = down;
            return;
        }
        if (down && !worldMousePrev && worldEditMode && worldEditSelected != null
                && !WorldHologram.locked(cc.findWorldNode(worldEditSelected))
                && hitBorderHandle(camera, mc)) {
            worldBorderId = worldEditSelected;
            pushWorldUndo("描边拖拽", null, List.of(worldEditSelected)); // 拖拽前快照（拖完可撤消）
            RenderNode node = cc.findWorldNode(worldEditSelected);
            if (node != null) {
                worldBorderStartW = cc.worldBorderWidthOf(node);
                double[][] pair = borderHandleWorld(camera, mc);
                if (pair != null) {
                    worldBorderStartPt = new net.minecraft.world.phys.Vec3(pair[1][0], pair[1][1], pair[1][2]);
                }
            }
            worldMousePrev = true;
            return;
        }
        // 编辑模式工具栏（保存/放弃/退出）优先于世界射线交互
        if (worldEditMode && handleWorldEditToolbar(mc, down, down && !worldMousePrev)) {
            worldMousePrev = down;
            return;
        }
        // 编辑模式拖入创建（按住类型 chip 拖到面板释放 = 在落点创建元素；未拖拽松手 = 打开类型屏）
        if (worldEditMode && worldTypeDrag != null) {
            if (down) {
                double[] mouse = cc.scaledMouse(mc);
                if (!worldTypeDragMoved) {
                    double dx = mouse[0] - worldTypeDragPressX;
                    double dy = mouse[1] - worldTypeDragPressY;
                    if (dx * dx + dy * dy > 36) {
                        worldTypeDragMoved = true;
                    }
                }
                if (worldTypeDragMoved) {
                    worldTypeDropPoint = typeDropPoint(camera, mc);
                }
            } else {
                if (worldTypeDragMoved && worldTypeDropPoint != null) {
                    // 松手：射线落点（锚点平面交点）→ 锚点相对坐标 → 创建元素
                    net.minecraft.world.phys.Vec3 anchor = cc.focusedWorldAnchor(camera);
                    double[] rel = new double[]{worldTypeDropPoint.x - anchor.x, worldTypeDropPoint.y - anchor.y};
                    createWorldElementAt(worldTypeDrag, rel[0], rel[1]);
                } else {
                    mc.setScreen(new WorldEditTypeScreen());
                }
                worldTypeDrag = null;
                worldTypeDragMoved = false;
                worldTypeDropPoint = null;
            }
            worldMousePrev = down;
            return;
        }
        // 锚点拖拽（M + 空白拖拽：面板整体随锚点偏移，写 offsetX/Y/Z；一步撤消）
        if (worldEditMode && worldAnchorDragActive) {
            if (down) {
                updateWorldAnchorDrag(camera, mc);
            } else {
                commitWorldAnchorDrag();
            }
            worldMousePrev = down;
            return;
        }
        // 编辑模式面板整体移动（Alt + 空白拖拽：所有元素同偏移；优先于框选）
        if (worldEditMode && worldPanelMove) {
            if (down) {
                updateWorldPanelMove(camera, mc);
            } else {
                commitWorldPanelMove();
            }
            worldMousePrev = down;
            return;
        }
        // 编辑模式框选（空白处按下拖框 → 多选；边拖边更新，松手提交；拖框时框内元素实时高亮）
        if (worldEditMode && worldMarquee != null) {
            if (down) {
                double[] mouse = cc.scaledMouse(mc);
                worldMarquee[2] = mouse[0];
                worldMarquee[3] = mouse[1];
                updateWorldMarqueePreview(camera, mc);
            } else {
                commitWorldMarquee(camera, mc);
            }
            worldMousePrev = down;
            return;
        }
        // z 排序拖拽（编辑模式按住 Z + 拖元素：上下拖动改层级 z，松手提交）
        if (worldZScrubId != null) {
            if (down) {
                double dy = mc.mouseHandler.ypos() - worldZScrubStartY;
                for (Map.Entry<String, Double> e : cc.worldZScrubBase.entrySet()) {
                    var element = cc.findElement(cc.worldPage, e.getKey());
                    if (element == null) {
                        continue;
                    }
                    Object raw = element.props().get("hologram");
                    if (!(raw instanceof Map<?, ?> holo)) {
                        continue;
                    }
                    Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
                    copy.put("z", Math.round((e.getValue() + dy * 0.01) * 100) / 100.0);
                    element.props().put("hologram", copy);
                }
            } else {
                commitWorldZScrub();
            }
            worldMousePrev = down;
            return;
        }
        // 透明度拖拽（编辑模式按住 O + 拖元素：上下拖动改 opacity 0~1，松手提交）
        if (worldOpacityScrubId != null) {
            if (down) {
                double dy = mc.mouseHandler.ypos() - worldOpacityScrubStartY;
                for (Map.Entry<String, Double> e : cc.worldOpacityScrubBase.entrySet()) {
                    var element = cc.findElement(cc.worldPage, e.getKey());
                    if (element == null) {
                        continue;
                    }
                    double next = Math.round(Math.max(0, Math.min(1, e.getValue() + dy * 0.005)) * 100) / 100.0;
                    element.props().put("opacity", next);
                }
            } else {
                commitWorldOpacityScrub();
            }
            worldMousePrev = down;
            return;
        }
        if (down && !worldMousePrev && worldEditMode && hit == null) {
            if (cc.mKeyHeld(mc)) {
                // M + 空白拖拽 = 锚点拖拽（面板整体随锚点偏移；可撤消）
                startWorldAnchorDrag(camera, mc);
            } else if (cc.altHeld(mc)) {
                if (cc.worldEditMulti.size() >= 2) {
                    // Alt + 空白拖拽（多选激活）= 只移动选中集
                    startWorldPanelMove(camera, mc, new java.util.ArrayList<>(cc.worldEditMulti));
                } else {
                    // Alt + 空白拖拽 = 整体移动面板
                    startWorldPanelMove(camera, mc);
                }
            } else {
                double[] mouse = cc.scaledMouse(mc);
                cc.worldMarqueePreview.clear();
                worldMarquee = new double[]{mouse[0], mouse[1], mouse[0], mouse[1]};
            }
            worldMousePrev = true;
            return;
        }
        if (worldDragId != null) {
            // 拖拽中：跟随射线平面移动 / 松手提交
            if (down) {
                updateWorldDrag(camera, mc);
            } else {
                commitWorldDrag();
            }
            worldMousePrev = down;
            return;
        }
        if (worldSliderDragId != null) {
            // 滑块拖拽中：沿 X 轴改值 / 松手上报
            if (down) {
                updateWorldSlider(camera, mc);
            } else {
                commitWorldSlider();
            }
            worldMousePrev = down;
            return;
        }
        // 新按下：编辑模式按住 Z + 元素 = z 排序拖拽；slider 立即进入滑块拖拽；其他记录候选
        if (down && !worldMousePrev && hit != null && hit.enabled()
                && cc.worldElementEnabled(cc.worldPage.id(), hit.id())) {
            worldPressedId = cc.wkey(cc.worldPage.id() == null ? "world" : cc.worldPage.id(), hit.id());
            if (worldEditMode && cc.zHeld(mc) && !WorldHologram.locked(cc.findWorldNode(hit.id()))) {
                startWorldZScrub(hit.id());
                worldMousePrev = true;
                return;
            }
            if (worldEditMode && cc.oHeld(mc) && !WorldHologram.locked(cc.findWorldNode(hit.id()))) {
                startWorldOpacityScrub(hit.id());
                worldMousePrev = true;
                return;
            }
            // 按下缩放反馈（渲染 scale * 0.95）
            if (!worldEditMode && "slider".equals(hit.type())) {
                worldSliderDragId = hit.id();
                updateWorldSlider(camera, mc);
                worldMousePrev = true;
                return;
            }
            worldPressCandidate = hit.id();
            worldPressX = mc.mouseHandler.xpos();
            worldPressY = mc.mouseHandler.ypos();
            worldPressAt = nowMs;
        }
        // 按住候选：超时或位移 → 升级为拖拽（编辑模式全部元素可拖；锁定元素除外）
        if (down && worldPressCandidate != null
                && !WorldHologram.locked(cc.findWorldNode(worldPressCandidate))
                && (worldEditMode
                || WorldHologram.draggable(cc.findWorldNode(worldPressCandidate), cc.worldPage.options()))) {
            double mx = mc.mouseHandler.xpos();
            double my = mc.mouseHandler.ypos();
            boolean timeout = nowMs - worldPressAt > 250;
            boolean moved = (mx - worldPressX) * (mx - worldPressX) + (my - worldPressY) * (my - worldPressY) > 25;
            if (timeout || moved) {
                RenderNode cand = cc.findWorldNode(worldPressCandidate);
                if (cand != null) {
                    String dragId = cand.id();
                    if (worldEditMode && cc.ctrlDown(mc)) {
                        // Ctrl 拖拽复制 / Ctrl+Alt 拖拽复制整组：创建副本（原元素不动），拖的是副本
                        String copyId = copyElementForDrag(cand.id());
                        if (copyId != null) {
                            dragId = copyId;
                            cand = cc.findWorldNode(copyId);
                        }
                    }
                    if (cand == null) {
                        worldPressCandidate = null;
                        return;
                    }
                    worldDragId = dragId;
                    worldDragBase = cc.worldElementCenter(cand, camera);
                    String pageKey = cc.worldPage.id() == null ? "world" : cc.worldPage.id();
                    cc.worldDragOffsets.put(cc.wkey(pageKey, dragId), new double[]{0, 0, 0});
                    // 面板组联动：hologram.group 相同的元素一起拖（相同相对偏移）
                    String group = cc.worldGroupOf(dragId);
                    if (group != null) {
                        for (String member : cc.worldGroupMembers(group)) {
                            cc.worldDragOffsets.put(cc.wkey(pageKey, member), new double[]{0, 0, 0});
                        }
                    }
                    // 多选批量拖拽：选中集内全部元素一起拖（相同相对偏移）
                    if (cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(dragId)) {
                        for (String member : cc.worldEditMulti) {
                            cc.worldDragOffsets.put(cc.wkey(pageKey, member), new double[]{0, 0, 0});
                        }
                    }
                    if (worldEditMode) {
                        worldEditSelected = dragId; // 拖拽即选中
                    }
                }
                worldPressCandidate = null;
            }
        }
        // 松手（未进入拖拽）：触发点击（编辑模式下 = 选中元素；双击 text = 就地编辑）
        if (!down && worldMousePrev && worldPressCandidate != null) {
            worldMousePrev = false; // 先重置：确保后续 return 路径不会卡住下次按下
            RenderNode pressed = cc.findWorldNode(worldPressCandidate);
            worldPressCandidate = null;
            if (pressed != null && pressed.enabled() && cc.worldElementEnabled(cc.worldPage.id(), pressed.id())) {
                String clickKey = cc.wkey(cc.worldPage.id() == null ? "world" : cc.worldPage.id(), pressed.id());
                long prevClick = worldClickBounces.getOrDefault(clickKey, 0L);
                long nowClick = System.currentTimeMillis();
                worldClickBounces.put(clickKey, nowClick); // 点击弹跳
                boolean doubleClick = nowClick - prevClick < 350 && nowClick - prevClick > 50;
                spawnWorldRipple(pressed, camera, mc); // 点击涟漪反馈
                cc.playWorldElementClickSound(pressed); // 元素级点击音效（clickSound）
                if (worldEditMode) {
                    if (doubleClick && !WorldHologram.locked(cc.findWorldNode(pressed.id()))) {
                        // 双击 = 快捷编辑：text = 就地编辑内容；其它 = 属性编辑屏（锁定元素跳过）
                        worldEditSelected = pressed.id();
                        if ("text".equals(pressed.type())) {
                            cc.openPropEditor("text.content", "编辑文本内容（双击就地编辑）");
                        } else {
                            var el = cc.findElement(cc.worldPage, pressed.id());
                            if (el != null) {
                                Minecraft.getInstance().setScreen(
                                        new WorldEditPropsScreen(pressed.id(), el));
                            }
                        }
                        return;
                    }
                    if (cc.ctrlDown(mc)) {
                        // Ctrl+点击：多选切换
                        if (!cc.worldEditMulti.add(pressed.id())) {
                            cc.worldEditMulti.remove(pressed.id());
                        }
                    } else {
                        cc.worldEditMulti.clear(); // 普通点击回到单选
                    }
                    worldEditSelected = pressed.id();
                    return;
                }
                // 世界开关/复选框：点击切换状态 → INPUT true/false 上报
                // 世界开关/复选框：点击切换状态 → INPUT true/false 上报
                if ("toggle".equals(pressed.type()) || "checkbox".equals(pressed.type())) {
                    String tkey = cc.wkey(cc.worldPage.id() == null ? "world" : cc.worldPage.id(), pressed.id());
                    boolean next = !Boolean.TRUE.equals(cc.worldToggle.get(tkey));
                    cc.worldToggle.put(tkey, next);
                    if (cc.worldSession != null && cc.isServerMode()) {
                        cc.sendEvent(cc.worldSession.event(pressed.id(), UiEvent.Trigger.INPUT, String.valueOf(next)));
                    } else {
                        String script = pressed.source() != null ? pressed.source().actions().get("input") : null;
                        if (script != null && !script.isBlank()) {
                            cc.runLocalAction(cc.worldPage, script, String.valueOf(next));
                        }
                    }
                    return;
                }
                // 世界下拉：点击切到下一个选项 → INPUT 上报选项值
                if ("dropdown".equals(pressed.type())) {
                    cycleWorldDropdown(pressed);
                    return;
                }
                // 世界页签：点击页签切换激活项 → INPUT 上报选项值
                if ("tabs".equals(pressed.type())) {
                    switchWorldTab(pressed, camera, mc);
                    return;
                }
                if (cc.worldSession != null && cc.isServerMode()) {
                    cc.sendEvent(cc.worldSession.event(pressed.id(), UiEvent.Trigger.CLICK, null));
                } else {
                    String script = pressed.source() != null ? pressed.source().actions().get("click") : null;
                    if (script != null && !script.isBlank()) {
                        cc.runLocalAction(cc.worldPage, script);
                    }
                }
            }
        }
        if (!down) {
            worldPressCandidate = null;
            worldPressedId = null;
            worldMousePrev = false; // 松手重置：允许下一次按下检测
        }
        // 编辑模式：方向键微调选中元素（←→ = x，↑↓ = y，Shift+↑↓ = z，按住自动重复）
        if (worldEditMode && worldEditSelected != null) {
            nudgeWorldEdit(mc, nowMs);
        }
        // M+方向键：锚点微移（offsetX/Y ±0.1；Shift+↑↓ = offsetZ；按住 200ms 重复）
        if (worldEditMode && cc.worldPage != null && cc.mKeyHeld(mc)) {
            long win = mc.getWindow().getWindow();
            boolean left = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) == 1;
            boolean right = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) == 1;
            boolean up = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_UP) == 1;
            boolean dn = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) == 1;
            if (left || right || up || dn) {
                long repeat = worldAnchorNudgeAt == 0 ? 0 : 200;
                if (nowMs - worldAnchorNudgeAt >= repeat) {
                    worldAnchorNudgeAt = nowMs;
                    nudgeWorldAnchor(mc, left, right, up, dn);
                }
            } else {
                worldAnchorNudgeAt = 0;
            }
        } else {
            worldAnchorNudgeAt = 0;
        }
        // Ctrl+C / Ctrl+V：复制粘贴选中元素（编辑模式）；Ctrl+Z / Ctrl+Y(/Shift+Z)：撤消/重做
        if (worldEditMode && cc.ctrlDown(mc)) {
            long win = mc.getWindow().getWindow();
            boolean c = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_C) == 1;
            boolean v = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_V) == 1;
            boolean z = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_Z) == 1;
            boolean y = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_Y) == 1;
            if (c && !cc.worldEditCtrlCPrev) {
                copyWorldElement();
            }
            if (v && !cc.worldEditCtrlVPrev) {
                pasteWorldElementClipboard();
            }
            if (z && !worldEditUndoPrev) {
                if (cc.shiftHeld(mc)) {
                    redoWorldEdit();
                } else {
                    undoWorldEdit();
                }
            }
            if (y && !worldEditRedoPrev) {
                redoWorldEdit();
            }
            cc.worldEditCtrlCPrev = c;
            cc.worldEditCtrlVPrev = v;
            worldEditUndoPrev = z;
            worldEditRedoPrev = y;
        } else {
            cc.worldEditCtrlCPrev = false;
            cc.worldEditCtrlVPrev = false;
            worldEditUndoPrev = false;
            worldEditRedoPrev = false;
        }
        // [ / ]：z 层级下移/上移（选中元素，编辑模式）；Ctrl+[ / ] = 选区尺寸 ±10%
        if (worldEditMode && worldEditSelected != null) {
            long win = mc.getWindow().getWindow();
            boolean zDown = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET) == 1;
            boolean zUp = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET) == 1;
            if (cc.ctrlDown(mc)) {
                if (zDown && !worldEditZPrevDown) {
                    cc.scaleWorldSelection(1.1); // Ctrl+[ = 选区放大 10%
                }
                if (zUp && !worldEditZPrevUp) {
                    cc.scaleWorldSelection(1 / 1.1); // Ctrl+] = 选区缩小 10%
                }
            } else {
                if (zDown && !worldEditZPrevDown) {
                    adjustWorldElementZ(-1);
                }
                if (zUp && !worldEditZPrevUp) {
                    adjustWorldElementZ(1);
                }
            }
            worldEditZPrevDown = zDown;
            worldEditZPrevUp = zUp;
            // R / Shift+R：旋转 90° / -90°
            boolean r = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_R) == 1;
            if (r && !worldEditRPrevDown) {
                rotateWorldElement90(cc.shiftHeld(mc) ? -90 : 90);
            }
            worldEditRPrevDown = r;
            // I：干净预览切换（隐藏选中框/锚点/参考线等编辑浮层，纯看效果）
            boolean preview = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_I) == 1;
            if (preview && !worldEditPreviewPrev) {
                worldEditPreview = !worldEditPreview;
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§b[OpenDreamCore] §f编辑预览: "
                                + (worldEditPreview ? "开（编辑浮层隐藏）" : "关")), false);
            }
            worldEditPreviewPrev = preview;
            // J：显示全部运行时隐藏元素
            boolean showAll = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_J) == 1;
            if (showAll && !worldEditShowAllPrev) {
                cc.showAllWorldElements();
            }
            worldEditShowAllPrev = showAll;
            // Y / Shift+Y：选区 yaw 微调 +5° / -5°（绕包围盒中心，与 Ctrl+Alt+滚轮同管线）
            boolean yaw = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_Y) == 1;
            if (yaw && !worldEditYawPrev) {
                cc.rotateWorldSelection(cc.shiftHeld(mc) ? -5 : 5);
            }
            worldEditYawPrev = yaw;
            // O / Shift+O：透明度 ±0.1（0~1 钳制）
            boolean o = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_O) == 1;
            if (o && !worldEditOPrev) {
                adjustWorldOpacity(cc.shiftHeld(mc) ? -0.1 : 0.1);
            }
            worldEditOPrev = o;
            // T：文本对齐循环（left → center → right）
            boolean t = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_T) == 1;
            if (t && !worldEditTPrev) {
                cycleWorldTextAlign();
            }
            worldEditTPrev = t;
            // = / -：文本字号步进（hologram.scale ± 0.005）
            boolean eq = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL) == 1;
            boolean minus = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS) == 1;
            if (eq && !worldEditScalePrevEq) {
                adjustWorldTextScale(1);
            }
            if (minus && !worldEditScalePrevMinus) {
                adjustWorldTextScale(-1);
            }
            worldEditScalePrevEq = eq;
            worldEditScalePrevMinus = minus;
            // Ctrl+Shift 按住：,/. 让位给流光段间距微调；Ctrl+Alt：宽度；Ctrl：速度；Shift：相位；Alt：段长
            boolean lc = org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == 1;
            boolean shKey = org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
            boolean altKey = org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == 1;
            // B：描边颜色循环；, / .：描边宽度 ±0.01（流光暂停时 ,/. 让位给单帧步进；Ctrl/Shift/Alt 依次让位给速度/相位/段长）
            boolean b = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_B) == 1;
            boolean comma = !cc.worldFlowPaused && !lc && !shKey && !altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean period = !cc.worldFlowPaused && !lc && !shKey && !altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (b && !worldEditBPrev) {
                cycleWorldBorderColor();
            }
            if (comma && !worldEditCommaPrev) {
                adjustWorldBorderWidth(-0.01);
            }
            if (period && !worldEditPeriodPrev) {
                adjustWorldBorderWidth(0.01);
            }
            worldEditBPrev = b;
            worldEditCommaPrev = comma;
            worldEditPeriodPrev = period;
            // L：流光 hover 加速开关（悬停加速/拉长段 开⇄关）
            boolean lKey = org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_L) == 1;
            if (lKey && !worldEditLPrev) {
                cc.toggleWorldFlowHoverBoost();
            }
            worldEditLPrev = lKey;
            // Ctrl+, / Ctrl+.：流光速度实时微调（±50ms；按住 300ms 后每帧连续微调；暂停时也可调）
            boolean cCtrl = lc && !shKey && !altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean cPeriod = lc && !shKey && !altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (cCtrl || cPeriod) {
                worldFlowSpeedHoldTicks++;
                if (worldFlowSpeedHoldTicks == 1 || worldFlowSpeedHoldTicks % 6 == 0) {
                    adjustWorldFlowSpeed(cCtrl ? -1 : 1);
                }
            } else {
                worldFlowSpeedHoldTicks = 0;
            }
            // Shift+, / Shift+.：流光段相位微调（±5% 周长；按住 300ms 后连续；暂停时也可调）
            boolean sComma = shKey && !lc && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean sPeriod = shKey && !lc && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (sComma || sPeriod) {
                worldFlowPhaseHoldTicks++;
                if (worldFlowPhaseHoldTicks == 1 || worldFlowPhaseHoldTicks % 6 == 0) {
                    adjustWorldFlowPhase(sComma ? -1 : 1);
                }
            } else {
                worldFlowPhaseHoldTicks = 0;
            }
            // Ctrl+Shift+, / Ctrl+Shift+.：流光段间距微调（±0.03 周长比例；按住 300ms 后连续；暂停时也可调）
            boolean csComma = lc && shKey && !altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean csPeriod = lc && shKey && !altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (csComma || csPeriod) {
                worldFlowGapHoldTicks++;
                if (worldFlowGapHoldTicks == 1 || worldFlowGapHoldTicks % 6 == 0) {
                    adjustWorldFlowGap(csComma ? -1 : 1);
                }
            } else {
                worldFlowGapHoldTicks = 0;
            }
            // Ctrl+Alt+, / Ctrl+Alt+.：描边宽度微调（±0.005；按住 300ms 后连续；暂停时也可调）
            boolean caComma = lc && altKey && !shKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean caPeriod = lc && altKey && !shKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (caComma || caPeriod) {
                worldBorderWidthHoldTicks++;
                if (worldBorderWidthHoldTicks == 1 || worldBorderWidthHoldTicks % 6 == 0) {
                    adjustWorldBorderWidth(caComma ? -0.005 : 0.005);
                }
            } else {
                worldBorderWidthHoldTicks = 0;
            }
            // Ctrl+Shift+Alt+, / Ctrl+Shift+Alt+.：副色段独立相位微调（±5% 周长；按住 300ms 后连续；暂停时也可调）
            boolean csaComma = lc && shKey && altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean csaPeriod = lc && shKey && altKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (csaComma || csaPeriod) {
                worldFlowPhase2HoldTicks++;
                if (worldFlowPhase2HoldTicks == 1 || worldFlowPhase2HoldTicks % 6 == 0) {
                    adjustWorldFlowPhase2(csaComma ? -1 : 1);
                }
            } else {
                worldFlowPhase2HoldTicks = 0;
            }
            // 9 / 0：流光段数快捷微调（±1 段，1~8 钳制；按住 300ms 后连续；暂停时也可调）
            boolean nineKey = org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_9) == 1;
            boolean zeroKey = org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_0) == 1;
            if (nineKey || zeroKey) {
                worldFlowSegmentsHoldTicks++;
                if (worldFlowSegmentsHoldTicks == 1 || worldFlowSegmentsHoldTicks % 6 == 0) {
                    adjustWorldFlowSegments(nineKey ? 1 : -1);
                }
            } else {
                worldFlowSegmentsHoldTicks = 0;
            }
            // Alt+, / Alt+.：流光段长微调（±0.02 周长比例；按住 300ms 后连续；暂停时也可调）
            boolean aComma = altKey && !lc && !shKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean aPeriod = altKey && !lc && !shKey && org.lwjgl.glfw.GLFW.glfwGetKey(win,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (aComma || aPeriod) {
                worldFlowSegHoldTicks++;
                if (worldFlowSegHoldTicks == 1 || worldFlowSegHoldTicks % 6 == 0) {
                    adjustWorldFlowSeg(aComma ? -1 : 1);
                }
            } else {
                worldFlowSegHoldTicks = 0;
            }
            // ; / '：rect 圆角 ±0.05（0~1 钳制）
            boolean semi = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_SEMICOLON) == 1;
            boolean apo = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_APOSTROPHE) == 1;
            if (semi && !worldEditSemiPrev) {
                adjustWorldRectRadius(-0.05);
            }
            if (apo && !worldEditApoPrev) {
                adjustWorldRectRadius(0.05);
            }
            worldEditSemiPrev = semi;
            worldEditApoPrev = apo;
        } else {
            worldEditZPrevDown = false;
            worldEditZPrevUp = false;
            worldEditRPrevDown = false;
            worldEditOPrev = false;
            worldEditTPrev = false;
            worldEditScalePrevEq = false;
            worldEditScalePrevMinus = false;
            worldEditBPrev = false;
            worldEditCommaPrev = false;
            worldEditPeriodPrev = false;
            worldEditLPrev = false;
            worldEditSemiPrev = false;
            worldEditApoPrev = false;
            worldFlowSpeedHoldTicks = 0;
            worldFlowPhaseHoldTicks = 0;
            worldFlowSegHoldTicks = 0;
            worldFlowGapHoldTicks = 0;
            worldBorderWidthHoldTicks = 0;
            worldFlowPhase2HoldTicks = 0;
            worldFlowSegmentsHoldTicks = 0;
        }
        // G：编辑网格显隐切换（锚点平面网格，步长 = 吸附值或 0.25）
        boolean gKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_G) == 1;
        if (worldEditMode && gKey && !worldGridPrev) {
            worldEditGrid = !worldEditGrid;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(worldEditGrid
                            ? "§b[OpenDreamCore] §f编辑网格已开启（步长 " + worldEditGridStep() + "，G 关闭）"
                            : "§e[OpenDreamCore] §f编辑网格已关闭"), false);
        }
        worldGridPrev = gKey;
        // F：元素查找屏（按 id 子串过滤，点击/Enter 定位选中）
        boolean fKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_F) == 1;
        if (worldEditMode && fKey && !worldFindPrev) {
            Minecraft.getInstance().setScreen(new WorldEditFindScreen());
        }
        worldFindPrev = fKey;
        // H：选中元素半透明透视（按住看下层元素）
        boolean hKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_H) == 1;
        worldGhostOn = worldEditMode && worldEditSelected != null && hKey
                && !WorldHologram.locked(cc.findWorldNode(worldEditSelected));
        // U：隐藏面板背景（按住看全貌）
        boolean uKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_U) == 1;
        worldHideBackground = worldEditMode && uKey;
        // K：流光动画暂停/恢复（定格便于对齐观察）
        boolean kKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_K) == 1;
        if (worldEditMode && kKey && !worldFlowKPrev) {
            cc.worldFlowPaused = !cc.worldFlowPaused;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(cc.worldFlowPaused
                            ? "§b[OpenDreamCore] §f流光动画已暂停（K 恢复）"
                            : "§e[OpenDreamCore] §f流光动画已恢复"), false);
        }
        worldFlowKPrev = kKey;
        // 暂停状态 ,/. 单帧步进流光（±50ms 边沿触发，逐帧观察段位；Ctrl/Shift/Alt 按住时让位给速度/相位/段长微调）
        if (worldEditMode && cc.worldFlowPaused) {
            boolean lcStep = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == 1;
            boolean shStep = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
            boolean altStep = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == 1;
            boolean stepComma = !lcStep && !shStep && !altStep && org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA) == 1;
            boolean stepPeriod = !lcStep && !shStep && !altStep && org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD) == 1;
            if (stepComma && !worldFlowStepCommaPrev) {
                cc.worldFlowClock -= 50;
            }
            if (stepPeriod && !worldFlowStepPeriodPrev) {
                cc.worldFlowClock += 50;
            }
            worldFlowStepCommaPrev = stepComma;
            worldFlowStepPeriodPrev = stepPeriod;
        } else {
            worldFlowStepCommaPrev = false;
            worldFlowStepPeriodPrev = false;
        }
        // 右键菜单键盘导航（↑/↓ 光标，Enter 执行）
        if (worldEditMode && worldCtxId != null) {
            long cwin = mc.getWindow().getWindow();
            boolean cup = org.lwjgl.glfw.GLFW.glfwGetKey(cwin, org.lwjgl.glfw.GLFW.GLFW_KEY_UP) == 1;
            boolean cdown = org.lwjgl.glfw.GLFW.glfwGetKey(cwin, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) == 1;
            int nItems = cc.worldContextItems().size();
            if (cup && !worldCtxKeyUpPrev) {
                worldCtxCursor = ((worldCtxCursor - 1) % nItems + nItems) % nItems;
            }
            if (cdown && !worldCtxKeyDownPrev) {
                worldCtxCursor = (worldCtxCursor + 1) % nItems;
            }
            worldCtxKeyUpPrev = cup;
            worldCtxKeyDownPrev = cdown;
            boolean cEnter = org.lwjgl.glfw.GLFW.glfwGetKey(cwin, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(cwin, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) == 1;
            if (cEnter && !worldCtxEnterPrev) {
                int act = worldCtxCursor;
                worldCtxCursor = 0;
                cc.runWorldContextAction(act);
            }
            worldCtxEnterPrev = cEnter;
        } else {
            worldCtxKeyUpPrev = false;
            worldCtxKeyDownPrev = false;
            worldCtxEnterPrev = false;
        }
        // P：锚点跟随模式循环（聚焦面板：跟随 → 固定 → 平滑）
        boolean pKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_P) == 1;
        if (worldEditMode && pKey && !worldModePPrev && cc.worldPage != null) {
            cc.cycleWorldAnchorMode();
        }
        worldModePPrev = pKey;
        // N：锚点偏移复位（移除 offsetX/Y/Z → 默认 0/1.6/3）
        boolean nKey = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_N) == 1;
        if (worldEditMode && nKey && !worldAnchorNPrev && cc.worldPage != null) {
            cc.resetWorldAnchor();
        }
        worldAnchorNPrev = nKey;
        worldMousePrev = down;
    }

    double worldEditGridStep() {
        return worldEditSnap > 0 ? worldEditSnap : 0.25;
    }

    void adjustWorldRectRadius(double delta) {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null || !"rect".equals(String.valueOf(element.props().get("type")))) {
            return;
        }
        Object raw = element.props().get("rect");
        double radius = 0;
        if (raw instanceof Map<?, ?> spec) {
            radius = UiRenderer.num(spec.get("radius"), 0);
        }
        double next = Math.round(Math.max(0, Math.min(1, radius + delta)) * 100) / 100.0;
        applyWorldEditProp(worldEditSelected, "rect.radius", String.valueOf(next));
    }

    void cycleWorldBorderColor() {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        int total = BORDER_PALETTE.length + 1; // 最后一位 = 关闭描边
        borderColorIdx = (borderColorIdx + 1) % total;
        if (borderColorIdx >= BORDER_PALETTE.length) {
            closeWorldBorder(element);
            return;
        }
        String color = BORDER_PALETTE[borderColorIdx];
        applyWorldBorder(element, color, null);
    }

    void closeWorldBorder(Element element) {
        if (element == null || cc.worldPage == null) {
            return;
        }
        pushWorldUndo("描边", "border", List.of(element.id()));
        Object raw = element.props().get("hologram");
        if (raw instanceof Map<?, ?> holo) {
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.remove("border");
            element.props().put("hologram", copy);
        }
        worldEditProps.computeIfAbsent(element.id(), k -> new ConcurrentHashMap<>())
                .put("hologram.border", "__unset__");
        cc.refreshCreateBlock(element.id());
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边已关闭"), false);
    }

    void adjustWorldBorderWidth(double delta) {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        Object border = cc.elementBorder(element);
        double width = 0.02;
        if (border instanceof Map<?, ?> bm) {
            Object w = bm.get("width");
            if (w instanceof Number n) {
                width = n.doubleValue();
            }
        }
        double next = Math.round(Math.max(0.005, Math.min(0.2, width + delta)) * 100) / 100.0;
        applyWorldBorder(element, null, next);
    }

    void applyWorldBorder(Element element, String color, Double width) {
        applyWorldBorder(element, color, width, true);
    }

    void applyWorldBorder(Element element, String color, Double width, boolean recordUndo) {
        if (recordUndo) {
            pushWorldUndo("描边", "border", List.of(element.id()));
        }
        Object border = cc.elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        if (color != null) {
            m.put("color", color);
        }
        if (width != null) {
            m.put("width", width);
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        cc.writeWorldBorder(element, m);
    }

    void adjustWorldTextScale(int dir) {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null || !"text".equals(String.valueOf(element.props().get("type")))) {
            return;
        }
        Object raw = element.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        var vars = cc.worldPage.variables();
        double scale = WorldHologram.holoNum(holo, "scale", 0.02, vars);
        double next = Math.round(Math.max(0.002, Math.min(0.5, scale + 0.005 * dir)) * 1000) / 1000.0;
        applyWorldEditProp(worldEditSelected, "hologram.scale", String.valueOf(next));
    }

    void cycleWorldTextAlign() {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null || !"text".equals(String.valueOf(element.props().get("type")))) {
            return;
        }
        String current = cc.elementPropValue(element, "text.align");
        String next = "left".equals(current) ? "center" : "right".equals(current) ? "left" : "right";
        applyWorldEditProp(worldEditSelected, "text.align", next);
    }

    void adjustWorldOpacity(double delta) {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        Object raw = element.props().get("opacity");
        double opacity = raw instanceof Number n ? n.doubleValue() : 1.0;
        double next = Math.round(Math.max(0, Math.min(1, opacity + delta)) * 100) / 100.0;
        applyWorldEditProp(worldEditSelected, "opacity", String.valueOf(next));
    }

    void rotateWorldElement90(int delta) {
        if (worldEditSelected == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        pushWorldUndo("旋转", "yaw", List.of(worldEditSelected));
        Object raw = element.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
        Object yawObj = holo.get("yaw");
        double yaw = yawObj instanceof Number n ? n.doubleValue() : 0;
        double next = Math.round((yaw + delta) * 10) / 10.0;
        copy.put("yaw", next);
        element.props().put("hologram", copy);
        worldEditProps.computeIfAbsent(worldEditSelected, k -> new ConcurrentHashMap<>())
                .put("hologram.yaw", String.valueOf(next));
        cc.refreshCreateBlock(worldEditSelected);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f旋转: yaw = " + next + "°"), false);
    }

    void adjustWorldElementZ(int delta) {
        if (cc.worldPage == null || worldEditSelected == null) {
            return;
        }
        List<String> targets = cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(worldEditSelected)
                ? new java.util.ArrayList<>(cc.worldEditMulti) : List.of(worldEditSelected);
        java.util.List<String> alive = new java.util.ArrayList<>();
        java.util.Map<String, Integer> nextZ = new java.util.LinkedHashMap<>();
        for (String id : targets) {
            if (cc.worldElementLocked(id)) {
                continue; // 锁定元素不参与层级步进
            }
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("z");
            int z = raw instanceof Number n ? n.intValue() : 0;
            int next = Math.max(0, Math.min(99, z + delta));
            if (next != z) {
                alive.add(id);
                nextZ.put(id, next);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        pushWorldUndo("层级步进", "zstep", alive); // 连续按 [ / ] 合并为一步
        for (String id : alive) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            element.props().put("z", nextZ.get(id));
            worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("z", String.valueOf(nextZ.get(id)));
            cc.refreshCreateBlock(id);
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
    }

    void startWorldPanelMove(net.minecraft.client.Camera camera, Minecraft mc) {
        startWorldPanelMove(camera, mc, null);
    }

    void startWorldPanelMove(net.minecraft.client.Camera camera, Minecraft mc,
                                     List<String> targets) {
        if (cc.worldNodes == null || cc.worldNodes.isEmpty()) {
            return;
        }
        java.util.Set<String> ids;
        if (targets != null && !targets.isEmpty()) {
            ids = new java.util.HashSet<>(targets);
        } else {
            ids = new java.util.HashSet<>();
            collectWorldIds(cc.worldNodes, ids);
        }
        if (ids.isEmpty()) {
            return;
        }
        List<String> all = new java.util.ArrayList<>(ids);
        int skippedMove = all.size();
        all = cc.filterLocked(all); // 锁定元素不参与整体移动
        skippedMove -= all == null ? 0 : all.size();
        if (all == null || all.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f全部元素已锁定，无法整体移动"), false);
            return;
        }
        pushWorldUndo("整体移动", null, all); // 移动前快照（可撤消）
        cc.worldPanelMoveOrig.clear();
        var vars = cc.worldPage.variables();
        for (String id : all) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            cc.worldPanelMoveOrig.put(id, new double[]{
                    WorldHologram.holoNum(holo, "x", 0, vars),
                    WorldHologram.holoNum(holo, "y", 0, vars),
                    WorldHologram.holoNum(holo, "z", 0, vars)});
        }
        worldPanelMoveBase = cc.focusedWorldAnchor(camera); // 平面基准点（锚点即可，偏移相对量）
        worldPanelMove = true;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f"
                        + (targets != null ? "多选整体移动（拖拽空白处，松手提交）"
                        : "面板整体移动（拖拽空白处，松手提交）")
                        + (skippedMove > 0 ? "；跳过 " + skippedMove + " 锁定" : "")), false);
    }

    void updateWorldPanelMove(net.minecraft.client.Camera camera, Minecraft mc) {
        if (!worldPanelMove || worldPanelMoveBase == null) {
            return;
        }
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1));
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        double t = ((worldPanelMoveBase.x - ray[0]) * n.x + (worldPanelMoveBase.y - ray[1]) * n.y
                + (worldPanelMoveBase.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return;
        }
        double dx = ray[0] + ray[3] * t - worldPanelMoveBase.x;
        double dy = ray[1] + ray[4] * t - worldPanelMoveBase.y;
        double dz = ray[2] + ray[5] * t - worldPanelMoveBase.z;
        for (Map.Entry<String, double[]> e : cc.worldPanelMoveOrig.entrySet()) {
            var element = cc.findElement(cc.worldPage, e.getKey());
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            double[] orig = e.getValue();
            copy.put("x", Math.round((orig[0] + dx) * 100) / 100.0);
            copy.put("y", Math.round((orig[1] + dy) * 100) / 100.0);
            copy.put("z", Math.round((orig[2] + dz) * 100) / 100.0);
            element.props().put("hologram", copy);
        }
    }

    void commitWorldPanelMove() {
        worldPanelMove = false;
        worldPanelMoveBase = null;
        if (cc.worldPanelMoveOrig.isEmpty()) {
            return;
        }
        var vars = cc.worldPage.variables();
        for (String id : cc.worldPanelMoveOrig.keySet()) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            worldEditDirty.put(id, new double[]{
                    WorldHologram.holoNum(holo, "x", 0, vars),
                    WorldHologram.holoNum(holo, "y", 0, vars),
                    WorldHologram.holoNum(holo, "z", 0, vars)});
            cc.refreshCreateBlock(id);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f面板整体移动（" + cc.worldPanelMoveOrig.size() + " 个元素）"), false);
        cc.worldPanelMoveOrig.clear();
    }

    void startWorldAnchorDrag(net.minecraft.client.Camera camera, Minecraft mc) {
        if (cc.worldPage == null || cc.worldNodes == null) {
            return;
        }
        cc.pushWorldBackgroundUndo("锚点: 拖拽", "anchor:drag"); // 拖拽全程合并为一步
        worldAnchorDragBase = cc.anchorPlaneHit(camera, mc);
        worldAnchorDragActive = worldAnchorDragBase != null;
    }

    void updateWorldAnchorDrag(net.minecraft.client.Camera camera, Minecraft mc) {
        double[] cur = cc.anchorPlaneHit(camera, mc);
        if (cur == null || worldAnchorDragBase == null || cc.worldPage == null) {
            return;
        }
        double dx = cur[0] - worldAnchorDragBase[0];
        double dy = cur[1] - worldAnchorDragBase[1];
        double dz = cur[2] - worldAnchorDragBase[2];
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        double ox = world.get("offsetX") instanceof Number n ? n.doubleValue()
                : world.get("offsetX") == null ? 0 : cc.parseAnchorNum(world.get("offsetX"), 0);
        double oy = world.get("offsetY") instanceof Number n ? n.doubleValue()
                : world.get("offsetY") == null ? 1.6 : cc.parseAnchorNum(world.get("offsetY"), 1.6);
        double oz = world.get("offsetZ") instanceof Number n ? n.doubleValue()
                : world.get("offsetZ") == null ? 3 : cc.parseAnchorNum(world.get("offsetZ"), 3);
        world.put("offsetX", Math.round((ox + dx) * 100) / 100.0);
        world.put("offsetY", Math.round((oy + dy) * 100) / 100.0);
        world.put("offsetZ", Math.round((oz + dz) * 100) / 100.0);
        options.put("world", world);
        worldAnchorDragBase = cur; // 增量基准跟随（连续 update 同一步撤消）
        cc.updateWorldPanelAnchors(camera);
    }

    void commitWorldAnchorDrag() {
        worldAnchorDragActive = false;
        worldAnchorDragBase = null;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f锚点已拖拽（offsetX/Y/Z 已更新，Ctrl+Z 可撤）"), false);
    }

    void startWorldZScrub(String hitId) {
        if (cc.worldPage == null) {
            return;
        }
        List<String> targets = new java.util.ArrayList<>();
        if (cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(hitId)) {
            targets.addAll(cc.worldEditMulti);
        } else {
            targets.add(hitId);
        }
        pushWorldUndo("层级调整", null, targets); // 拖拽前快照（可撤消）
        var vars = cc.worldPage.variables();
        cc.worldZScrubBase.clear();
        for (String id : targets) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            cc.worldZScrubBase.put(id, WorldHologram.holoNum(holo, "z", 0, vars));
        }
        if (cc.worldZScrubBase.isEmpty()) {
            return;
        }
        worldZScrubId = hitId;
        worldZScrubStartY = Minecraft.getInstance().mouseHandler.ypos();
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f层级调整（上下拖动改 z，松手提交）"), false);
    }

    void commitWorldZScrub() {
        String anchor = worldZScrubId;
        List<String> targets = new java.util.ArrayList<>(cc.worldZScrubBase.keySet());
        worldZScrubId = null;
        cc.worldZScrubBase.clear();
        if (anchor == null || cc.worldPage == null || targets.isEmpty()) {
            return;
        }
        var vars = cc.worldPage.variables();
        int done = 0;
        for (String id : targets) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            worldEditDirty.put(id, new double[]{
                    WorldHologram.holoNum(holo, "x", 0, vars),
                    WorldHologram.holoNum(holo, "y", 0, vars),
                    WorldHologram.holoNum(holo, "z", 0, vars)});
            cc.refreshCreateBlock(id);
            done++;
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f层级调整完成（" + done + " 个元素）"), false);
    }

    void startWorldOpacityScrub(String hitId) {
        if (cc.worldPage == null) {
            return;
        }
        List<String> targets = cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(hitId)
                ? new java.util.ArrayList<>(cc.worldEditMulti) : List.of(hitId);
        pushWorldUndo("透明度调整", null, targets); // 拖拽前快照（可撤消）
        cc.worldOpacityScrubBase.clear();
        for (String id : targets) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("opacity");
            cc.worldOpacityScrubBase.put(id, raw instanceof Number n ? n.doubleValue() : 1.0);
        }
        if (cc.worldOpacityScrubBase.isEmpty()) {
            return;
        }
        worldOpacityScrubId = hitId;
        worldOpacityScrubStartY = Minecraft.getInstance().mouseHandler.ypos();
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f透明度调整（上下拖动，松手提交）"), false);
    }

    void commitWorldOpacityScrub() {
        String anchor = worldOpacityScrubId;
        List<String> targets = new java.util.ArrayList<>(cc.worldOpacityScrubBase.keySet());
        worldOpacityScrubId = null;
        cc.worldOpacityScrubBase.clear();
        if (anchor == null || cc.worldPage == null || targets.isEmpty()) {
            return;
        }
        int done = 0;
        for (String id : targets) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("opacity");
            double v = raw instanceof Number n ? n.doubleValue() : 1.0;
            worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("opacity", String.valueOf(Math.round(v * 100) / 100.0));
            cc.refreshCreateBlock(id);
            done++;
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f透明度调整完成（" + done + " 个元素）"), false);
    }

    void updateWorldDrag(net.minecraft.client.Camera camera, Minecraft mc) {
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1)); // 相机朝向 = billboard 法线
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        double t = ((worldDragBase.x - ray[0]) * n.x + (worldDragBase.y - ray[1]) * n.y
                + (worldDragBase.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return;
        }
        double hx = ray[0] + ray[3] * t - worldDragBase.x;
        double hy = ray[1] + ray[4] * t - worldDragBase.y;
        double hz = ray[2] + ray[5] * t - worldDragBase.z;
        // Shift 拖拽锁轴：按当前位移主导轴锁定（|dx|>|dy| 锁 x，否则锁 y），松开 Shift 重新评估
        boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
        if (shift) {
            if (worldDragLockAxis == 0 && (Math.abs(hx) > 0.02 || Math.abs(hy) > 0.02)) {
                worldDragLockAxis = Math.abs(hx) >= Math.abs(hy) ? 1 : 2;
            }
            if (worldDragLockAxis == 1) {
                hy = 0;
            } else if (worldDragLockAxis == 2) {
                hx = 0;
            }
        } else {
            worldDragLockAxis = 0;
        }
        // 编辑模式对齐吸附：中心/边对齐参考线（元素中心或边与其它元素中心/边接近 → 吸附 + 参考线）
        String group = cc.worldGroupOf(worldDragId);
        // 拖拽联动集：面板组 + 多选选中集（一起拖的成员，吸附跳过 + 偏移广播共用）；锁定成员不联动
        java.util.Set<String> moving = new java.util.LinkedHashSet<>();
        if (group != null) {
            moving.addAll(cc.filterLocked(new java.util.ArrayList<>(cc.worldGroupMembers(group))));
        }
        if (cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(worldDragId)) {
            moving.addAll(cc.filterLocked(new java.util.ArrayList<>(cc.worldEditMulti)));
        }
        moving.add(worldDragId); // 拖拽发起元素必然未锁定（拖拽起点已拦截）
        worldDragGuides = null;
        if (worldEditMode) {
            net.minecraft.world.phys.Vec3 anchor = cc.focusedWorldAnchor(camera);
            double cx = worldDragBase.x + hx - anchor.x;
            double cy = worldDragBase.y + hy - anchor.y;
            java.util.Set<String> skip = new java.util.HashSet<>(moving);
            List<double[]> centers = new java.util.ArrayList<>();
            collectWorldCenters(cc.worldNodes, cc.worldTabActive(cc.worldPage.id()), cc.worldPage.variables(), null, skip, centers);
            List<double[]> edges = new java.util.ArrayList<>();
            collectWorldEdges(cc.worldNodes, cc.worldTabActive(cc.worldPage.id()), cc.worldPage.variables(), null, skip, edges);
            // 拖拽元素自身尺寸（边吸附基准）；多选批量拖拽 → 用选中集包围盒作为吸附基准
            RenderNode dragged = cc.findWorldNode(worldDragId);
            double ew = 2.0, eh = 0.25;
            if (dragged != null) {
                Object raw = dragged.props().get("hologram");
                Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
                var vars = cc.worldPage.variables();
                ew = WorldHologram.holoNum(holo, "width", "text".equals(dragged.type()) ? 2.0 : 1.0, vars);
                eh = WorldHologram.holoNum(holo, "height", "text".equals(dragged.type()) ? 0.25 : 1.0, vars);
            }
            double[] batchBox = cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(worldDragId)
                    ? cc.worldSelectionBounds(cc.worldEditMulti) : null;
            double tol = 0.06;
            double bestDx = Double.MAX_VALUE, bestDy = Double.MAX_VALUE;
            Double gx = null, gy = null;
            // 中心吸附候选
            for (double[] c : centers) {
                double dxc = c[0] - cx;
                if (Math.abs(dxc) < tol && Math.abs(dxc) < Math.abs(bestDx)) {
                    bestDx = dxc;
                    gx = c[0];
                }
                double dyc = c[1] - cy;
                if (Math.abs(dyc) < tol && Math.abs(dyc) < Math.abs(bestDy)) {
                    bestDy = dyc;
                    gy = c[1];
                }
            }
            // 边吸附候选：拖拽元素左/中/右边 vs 参考竖线，上/中/下边 vs 参考横线（取最小调整）；
            // 批量时 = 选中集包围盒左/中/右边（盒中心 = 拖前中心 + 当前偏移）
            double[] xs;
            double[] ys;
            if (batchBox != null) {
                double bw = batchBox[2] - batchBox[0];
                double bh = batchBox[3] - batchBox[1];
                double bcx = (batchBox[0] + batchBox[2]) / 2 + hx;
                double bcy = (batchBox[1] + batchBox[3]) / 2 + hy;
                xs = new double[]{bcx - bw / 2, bcx, bcx + bw / 2};
                ys = new double[]{bcy - bh / 2, bcy, bcy + bh / 2};
            } else {
                xs = new double[]{cx - ew / 2, cx, cx + ew / 2};
                ys = new double[]{cy - eh / 2, cy, cy + eh / 2};
            }
            for (double[] line : edges) {
                if (line[0] == 0) {
                    for (double e : xs) {
                        double d = line[1] - e;
                        if (Math.abs(d) < tol && Math.abs(d) < Math.abs(bestDx)) {
                            bestDx = d;
                            gx = line[1];
                        }
                    }
                } else {
                    for (double e : ys) {
                        double d = line[1] - e;
                        if (Math.abs(d) < tol && Math.abs(d) < Math.abs(bestDy)) {
                            bestDy = d;
                            gy = line[1];
                        }
                    }
                }
            }
            if (bestDx != Double.MAX_VALUE) {
                if (batchBox != null) {
                    hx += bestDx; // 盒整体平移吸附
                } else {
                    hx = cx + bestDx + anchor.x - worldDragBase.x;
                }
            }
            if (bestDy != Double.MAX_VALUE) {
                if (batchBox != null) {
                    hy += bestDy;
                } else {
                    hy = cy + bestDy + anchor.y - worldDragBase.y;
                }
            }
            if (gx != null || gy != null) {
                worldDragGuides = new double[]{gx == null ? Double.NaN : gx, gy == null ? Double.NaN : gy};
            }
        }
        String pageKey = cc.worldPage.id() == null ? "world" : cc.worldPage.id();
        cc.worldDragOffsets.put(cc.wkey(pageKey, worldDragId), new double[]{hx, hy, hz});
        // 联动集广播：同偏移到组内成员 + 多选成员（渲染实时跟随）
        for (String member : moving) {
            cc.worldDragOffsets.put(cc.wkey(pageKey, member), new double[]{hx, hy, hz});
        }
    }

    static void collectWorldCenters(List<RenderNode> nodes, String activeTab,
                                            java.util.Map<String, Object> vars, double[] parentOffset,
                                            java.util.Set<String> skip, List<double[]> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            Object raw = node.props().get("hologram");
            double bx = parentOffset == null ? 0 : parentOffset[0];
            double by = parentOffset == null ? 0 : parentOffset[1];
            double bz = parentOffset == null ? 0 : parentOffset[2];
            double[] childOffset = parentOffset;
            if (raw instanceof Map<?, ?> holo) {
                double x = bx + WorldHologram.holoNum(holo, "x", 0, vars);
                double y = by + WorldHologram.holoNum(holo, "y", 0, vars);
                if (!skip.contains(node.id())) {
                    out.add(new double[]{x, y});
                }
                childOffset = new double[]{x, y, bz + WorldHologram.holoNum(holo, "z", 0, vars)};
            }
            collectWorldCenters(node.children(), activeTab, vars, childOffset, skip, out);
        }
    }

    static void collectWorldEdges(List<RenderNode> nodes, String activeTab,
                                          java.util.Map<String, Object> vars, double[] parentOffset,
                                          java.util.Set<String> skip, List<double[]> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            Object raw = node.props().get("hologram");
            double bx = parentOffset == null ? 0 : parentOffset[0];
            double by = parentOffset == null ? 0 : parentOffset[1];
            double bz = parentOffset == null ? 0 : parentOffset[2];
            double[] childOffset = parentOffset;
            if (raw instanceof Map<?, ?> holo) {
                double x = bx + WorldHologram.holoNum(holo, "x", 0, vars);
                double y = by + WorldHologram.holoNum(holo, "y", 0, vars);
                if (!skip.contains(node.id())) {
                    double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, vars);
                    double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, vars);
                    out.add(new double[]{0, x - w / 2});
                    out.add(new double[]{0, x});
                    out.add(new double[]{0, x + w / 2});
                    out.add(new double[]{1, y - h / 2});
                    out.add(new double[]{1, y});
                    out.add(new double[]{1, y + h / 2});
                }
                childOffset = new double[]{x, y, bz + WorldHologram.holoNum(holo, "z", 0, vars)};
            }
            collectWorldEdges(node.children(), activeTab, vars, childOffset, skip, out);
        }
    }

    void commitWorldDrag() {
        String pageKey = cc.worldPage == null || cc.worldPage.id() == null ? "world" : cc.worldPage.id();
        double[] off = cc.worldDragOffsets.remove(cc.wkey(pageKey, worldDragId));
        String id = worldDragId;
        worldDragId = null;
        worldDragBase = null;
        worldDragLockAxis = 0;
        worldDragGuides = null;
        if (off == null || cc.worldPage == null) {
            return;
        }
        // 联动落位：面板组 + 多选选中集一起落位（各算各的 snap/偏移）
        String group = cc.worldGroupOf(id);
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        if (group != null) {
            targets.addAll(cc.worldGroupMembers(group));
        }
        if (cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(id)) {
            targets.addAll(cc.worldEditMulti);
        }
        targets.add(id);
        pushWorldUndo("拖拽", null, new java.util.ArrayList<>(targets)); // 拖拽落位前快照（可撤消）
        for (String targetId : targets) {
            commitWorldDragElement(targetId, off);
        }
        for (String targetId : targets) {
            cc.worldDragOffsets.remove(cc.wkey(pageKey, targetId));
        }
    }

    void commitWorldDragElement(String elementId, double[] off) {
        var element = cc.findElement(cc.worldPage, elementId);
        if (element == null) {
            return;
        }
        double nx = 0, ny = 0, nz = 0;
        Object raw = element.props().get("hologram");
        if (raw instanceof Map<?, ?> h) {
            Map<Object, Object> holo = new java.util.LinkedHashMap<>(h);
            var vars = cc.worldPage.variables();
            // 网格吸附：hologram.snap（世界单位，0 = 关）→ 偏移对齐网格
            double snap = cc.numOf(holo.get("snap"), 0);
            double sx = snap > 0 ? Math.round(off[0] / snap) * snap : off[0];
            double sy = snap > 0 ? Math.round(off[1] / snap) * snap : off[1];
            double sz = snap > 0 ? Math.round(off[2] / snap) * snap : off[2];
            nx = WorldHologram.holoNum(holo, "x", 0, vars) + sx;
            ny = WorldHologram.holoNum(holo, "y", 0, vars) + sy;
            nz = WorldHologram.holoNum(holo, "z", 0, vars) + sz;
            // 编辑模式：工具栏网格吸附优先（作用于绝对位置）
            if (worldEditMode && worldEditSnap > 0) {
                nx = Math.round(nx / worldEditSnap) * worldEditSnap;
                ny = Math.round(ny / worldEditSnap) * worldEditSnap;
                nz = Math.round(nz / worldEditSnap) * worldEditSnap;
            }
            holo.put("x", nx);
            holo.put("y", ny);
            holo.put("z", nz);
            element.props().put("hologram", holo);
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        // 编辑模式：本地记录（保存时统一写回页面文件，不打扰服务端运行时状态）
        if (worldEditMode) {
            worldEditDirty.put(elementId, new double[]{nx, ny, nz});
            cc.refreshCreateBlock(elementId); // 新建/复制元素的 YAML 块同步最终位置
        } else if (cc.worldSession != null && cc.isServerMode()) {
            // 服务端世界页面：落点上报裁决（INPUT "drag:x,y,z"，服务端同步给同页玩家）
            cc.sendEvent(cc.worldSession.event(elementId, UiEvent.Trigger.INPUT,
                    "drag:" + Math.round(nx * 100) / 100.0 + "," + Math.round(ny * 100) / 100.0 + ","
                            + Math.round(nz * 100) / 100.0));
        }
    }

    void spawnWorldRipple(RenderNode node, net.minecraft.client.Camera camera, Minecraft mc) {
        try {
            double[] ray = WorldHologram.mouseRayWorld(mc, camera);
            var rot = new org.joml.Matrix4f().rotation(camera.rotation());
            var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1));
            net.minecraft.world.phys.Vec3 center = cc.worldElementCenter(node, camera);
            double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
            if (Math.abs(denom) < 1e-9) {
                return;
            }
            double t = ((center.x - ray[0]) * n.x + (center.y - ray[1]) * n.y
                    + (center.z - ray[2]) * n.z) / denom;
            if (t < 0) {
                return;
            }
            double color = 0;
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> holo && holo.get("rippleColor") != null) {
                color = UiStyle.color(holo.get("rippleColor"), 0);
            }
            worldRipples.add(new double[]{ray[0] + ray[3] * t, ray[1] + ray[4] * t,
                    ray[2] + ray[5] * t, (double) System.currentTimeMillis(), color});
        } catch (Exception ignored) {
            // 涟漪失败不打断点击
        }
    }

    boolean hitRotateHandle(net.minecraft.client.Camera camera, Minecraft mc) {
        double[][] pair = cc.rotateHandleWorld(camera, mc);
        if (pair == null) {
            return false;
        }
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        double[] s = cc.project(camera, new net.minecraft.world.phys.Vec3(pair[1][0], pair[1][1], pair[1][2]),
                scaledW, scaledH);
        if (s == null) {
            return false;
        }
        double scale = scaledW / (double) window.getScreenWidth();
        double mx = mc.mouseHandler.xpos() * scale;
        double my = mc.mouseHandler.ypos() * scale;
        double dx = mx - s[0];
        double dy = my - s[1];
        return dx * dx + dy * dy < 100; // 10px 内
    }

    void updateWorldRotate(Minecraft mc) {
        RenderNode node = cc.findWorldNode(worldRotateId);
        if (node == null || cc.worldPage == null) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        double[] center = cc.project(camera, cc.worldElementCenter(node, camera), scaledW, scaledH);
        if (center == null) {
            return;
        }
        double scale = scaledW / (double) window.getScreenWidth();
        double mx = mc.mouseHandler.xpos() * scale;
        double my = mc.mouseHandler.ypos() * scale;
        double angle = Math.toDegrees(Math.atan2(my - center[1], mx - center[0]));
        double yaw = Math.round((angle + 90) * 10) / 10.0; // 手柄初始在正上方（-90°）
        if (cc.shiftHeld(mc)) {
            yaw = Math.round(yaw / 15.0) * 15.0; // Shift = 15° 步进吸附
        }
        Object raw = node.props().get("hologram");
        if (raw instanceof Map<?, ?> holo) {
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("yaw", yaw);
            var element = cc.findElement(cc.worldPage, worldRotateId);
            if (element != null) {
                element.props().put("hologram", copy);
            }
        }
    }

    void commitWorldRotate() {
        String id = worldRotateId;
        worldRotateId = null;
        if (id == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, id);
        if (element == null) {
            return;
        }
        Object raw = element.props().get("hologram");
        if (raw instanceof Map<?, ?> holo && holo.get("yaw") != null) {
            Object yaw = holo.get("yaw");
            worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("hologram.yaw", String.valueOf(yaw));
            cc.refreshCreateBlock(id);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f旋转: " + id + " yaw = " + yaw + "°"), false);
        }
    }

    boolean hitResizeHandle(net.minecraft.client.Camera camera, Minecraft mc) {
        double[][] pair = cc.resizeHandleWorld(camera, mc);
        if (pair == null) {
            return false;
        }
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        double[] s = cc.project(camera, new net.minecraft.world.phys.Vec3(pair[1][0], pair[1][1], pair[1][2]),
                scaledW, scaledH);
        if (s == null) {
            return false;
        }
        double scale = scaledW / (double) window.getScreenWidth();
        double mx = mc.mouseHandler.xpos() * scale;
        double my = mc.mouseHandler.ypos() * scale;
        double dx = mx - s[0];
        double dy = my - s[1];
        return dx * dx + dy * dy < 100;
    }

    double resizeMouseDist(net.minecraft.client.Camera camera, Minecraft mc) {
        RenderNode node = cc.findWorldNode(worldEditSelected);
        if (node == null) {
            return 1;
        }
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        double[] center = cc.project(camera, cc.worldElementCenter(node, camera), scaledW, scaledH);
        if (center == null) {
            return 1;
        }
        double scale = scaledW / (double) window.getScreenWidth();
        double mx = mc.mouseHandler.xpos() * scale;
        double my = mc.mouseHandler.ypos() * scale;
        double dx = mx - center[0];
        double dy = my - center[1];
        double dist = Math.sqrt(dx * dx + dy * dy);
        return dist > 4 ? dist : 1;
    }

    void updateWorldResize(Minecraft mc) {
        RenderNode node = cc.findWorldNode(worldResizeId);
        if (node == null || cc.worldPage == null) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        double dist = resizeMouseDist(camera, mc);
        if (worldResizeStartDist <= 0) {
            worldResizeStartDist = dist;
        }
        double ratio = dist / worldResizeStartDist;
        double w = Math.max(0.1, worldResizeStartW * ratio);
        double h = Math.max(0.1, worldResizeStartH * ratio);
        Object raw = node.props().get("hologram");
        if (raw instanceof Map<?, ?> holo) {
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("width", Math.round(w * 100) / 100.0);
            copy.put("height", Math.round(h * 100) / 100.0);
            var element = cc.findElement(cc.worldPage, worldResizeId);
            if (element != null) {
                element.props().put("hologram", copy);
            }
        }
    }

    void commitWorldResize() {
        String id = worldResizeId;
        worldResizeId = null;
        worldResizeStartDist = 0;
        if (id == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, id);
        if (element == null) {
            return;
        }
        Object raw = element.props().get("hologram");
        if (raw instanceof Map<?, ?> holo && holo.get("width") != null && holo.get("height") != null) {
            Map<String, String> props = worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
            props.put("hologram.width", String.valueOf(holo.get("width")));
            props.put("hologram.height", String.valueOf(holo.get("height")));
            cc.refreshCreateBlock(id);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f尺寸: " + id + " " + holo.get("width")
                            + " × " + holo.get("height")), false);
        }
    }

    double[][] borderHandleWorld(net.minecraft.client.Camera camera, Minecraft mc) {
        RenderNode node = cc.findWorldNode(worldEditSelected);
        if (node == null) {
            return null;
        }
        Object raw = node.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo) || holo.get("border") == null) {
            return null; // 无描边元素不显示手柄
        }
        var vars = cc.worldPage.variables();
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, vars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, vars);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var right = rot.transformDirection(new org.joml.Vector3f(1, 0, 0));
        var up = rot.transformDirection(new org.joml.Vector3f(0, 1, 0));
        double lift = 0.18;
        net.minecraft.world.phys.Vec3 center = cc.worldElementCenter(node, camera);
        net.minecraft.world.phys.Vec3 handle = center.add(right.x * (-w / 2 - lift), right.y * (-w / 2 - lift),
                right.z * (-w / 2 - lift)).add(up.x * 0, up.y * 0, up.z * 0);
        return new double[][]{{center.x, center.y, center.z}, {handle.x, handle.y, handle.z}};
    }

    boolean hitBorderHandle(net.minecraft.client.Camera camera, Minecraft mc) {
        double[][] pair = borderHandleWorld(camera, mc);
        if (pair == null) {
            return false;
        }
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        double[] s = cc.project(camera, new net.minecraft.world.phys.Vec3(pair[1][0], pair[1][1], pair[1][2]),
                scaledW, scaledH);
        if (s == null) {
            return false;
        }
        double scale = scaledW / (double) window.getScreenWidth();
        double mx = mc.mouseHandler.xpos() * scale;
        double my = mc.mouseHandler.ypos() * scale;
        double dx = mx - s[0];
        double dy = my - s[1];
        return dx * dx + dy * dy < 100; // 10px 内
    }

    void updateWorldBorder(Minecraft mc) {
        RenderNode node = cc.findWorldNode(worldBorderId);
        if (node == null || cc.worldPage == null || worldBorderStartPt == null) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1)); // billboard 法线
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        double t = ((worldBorderStartPt.x - ray[0]) * n.x + (worldBorderStartPt.y - ray[1]) * n.y
                + (worldBorderStartPt.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return;
        }
        double hx = ray[0] + ray[3] * t - worldBorderStartPt.x;
        double hy = ray[1] + ray[4] * t - worldBorderStartPt.y;
        double hz = ray[2] + ray[5] * t - worldBorderStartPt.z;
        var right = rot.transformDirection(new org.joml.Vector3f(1, 0, 0));
        double dr = hx * right.x + hy * right.y + hz * right.z;
        double width = Math.round(Math.max(0.002, Math.min(0.3, worldBorderStartW + dr)) * 1000) / 1000.0;
        Object raw = node.props().get("hologram");
        if (raw instanceof Map<?, ?> holo && holo.get("border") != null) {
            Object borderRaw = holo.get("border");
            Map<Object, Object> copy = borderRaw instanceof Map<?, ?> bm
                    ? new java.util.LinkedHashMap<>(bm) : new java.util.LinkedHashMap<>();
            if (!(borderRaw instanceof Map)) {
                copy.put("color", String.valueOf(borderRaw)); // 字符串描边 → 转 {color, width} 保留颜色
            }
            copy.put("width", width);
            Map<Object, Object> hcopy = new java.util.LinkedHashMap<>(holo);
            hcopy.put("border", copy);
            var element = cc.findElement(cc.worldPage, worldBorderId);
            if (element != null) {
                element.props().put("hologram", hcopy);
            }
        }
    }

    void adjustWorldFlowSpeed(int dir) {
        if (cc.worldPage == null) {
            return;
        }
        Element element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        long ms = 1200;
        Object border = cc.elementBorder(element);
        if (border instanceof Map<?, ?> bm && bm.get("flowSpeed") instanceof Number n) {
            ms = n.longValue();
        }
        ms = Math.max(100, ms + dir * 50L);
        cc.applyBorderFlowSpeed(element, ms);
    }

    void adjustWorldFlowPhase(int dir) {
        if (cc.worldPage == null) {
            return;
        }
        Element element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = cc.elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        double phase = 0;
        if (m.get("flowPhase") instanceof Number n) {
            phase = n.doubleValue();
        }
        phase = (phase + dir * 0.05) % 1.0;
        if (phase < 0) {
            phase += 1.0;
        }
        // 关键位磁吸：接近 0/25/50/75% 时吸附（±0.04 阈值）
        double snapped = Math.round(phase * 4) / 4.0;
        if (Math.abs(phase - snapped) < 0.04) {
            phase = snapped;
        }
        m.put("flow", true);
        m.put("flowPhase", Math.round(phase * 100) / 100.0);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        cc.writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段相位: " + Math.round(phase * 100)
                        + "%（周长比例，保存后写回页面文件）"), false);
    }

    void adjustWorldFlowSeg(int dir) {
        if (cc.worldPage == null) {
            return;
        }
        Element element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = cc.elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        double seg = 0;
        if (m.get("flowSeg") instanceof Number n) {
            seg = n.doubleValue();
        }
        seg = Math.round((seg + dir * 0.02) * 100) / 100.0;
        seg = Math.max(0.02, Math.min(0.45, seg));
        m.put("flow", true);
        m.put("flowSeg", seg);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        cc.writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段长: " + seg
                        + "（周长比例，保存后写回页面文件）"), false);
    }

    void adjustWorldFlowGap(int dir) {
        if (cc.worldPage == null) {
            return;
        }
        Element element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = cc.elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        double gap = 0;
        if (m.get("flowSegGap") instanceof Number n) {
            gap = n.doubleValue();
        }
        gap = Math.round((gap + dir * 0.03) * 100) / 100.0;
        gap = Math.max(0.03, Math.min(0.6, gap));
        m.put("flow", true);
        m.put("flowSegGap", gap);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        cc.writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段间距: " + gap
                        + "（周长比例，保存后写回页面文件）"), false);
    }

    private void adjustWorldFlowPhase2(int dir) {        if (cc.worldPage == null) {
            return;
        }
        Element element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = cc.elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        double phase = 0;
        if (m.get("flowPhase2") instanceof Number n) {
            phase = n.doubleValue();
        }
        phase = (phase + dir * 0.05) % 1.0;
        if (phase < 0) {
            phase += 1.0;
        }
        // 关键位磁吸：接近 0/25/50/75% 时吸附（±0.04 阈值）
        double snapped = Math.round(phase * 4) / 4.0;
        if (Math.abs(phase - snapped) < 0.04) {
            phase = snapped;
        }
        m.put("flow", true);
        m.put("flowPhase2", Math.round(phase * 100) / 100.0);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        cc.writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f副色段相位: " + Math.round(phase * 100)
                        + "%（周长比例，双色流光对侧独立偏移）"), false);
    }

    private void adjustWorldFlowSegments(int dir) {        if (cc.worldPage == null) {
            return;
        }
        Element element = cc.findElement(cc.worldPage, worldEditSelected);
        if (element == null) {
            return;
        }
        pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = cc.elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        int n = 0;
        if (m.get("flowSegments") instanceof Number num) {
            n = num.intValue();
        }
        n = Math.max(1, n + dir);
        n = Math.max(1, Math.min(8, n));
        m.put("flow", true);
        m.put("flowSegments", n);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        cc.writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段数: " + n
                        + "（保存后写回页面文件）"), false);
    }

    void commitWorldBorder() {
        String id = worldBorderId;
        worldBorderId = null;
        worldBorderStartPt = null;
        worldBorderStartW = 0;
        if (id == null || cc.worldPage == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, id);
        RenderNode node = cc.findWorldNode(id);
        if (element == null || node == null) {
            return;
        }
        double width = cc.worldBorderWidthOf(node);
        if (width < 0) {
            return;
        }
        applyWorldBorder(element, null, width, false); // 按下时已快照（"描边拖拽"），此处不重复记录
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边: " + id + " 宽度 "
                        + Math.round(width * 1000) / 1000.0), false);
    }

    net.minecraft.world.phys.Vec3 typeDropPoint(net.minecraft.client.Camera camera, Minecraft mc) {
        net.minecraft.world.phys.Vec3 anchor = cc.focusedWorldAnchor(camera);
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1));
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return null;
        }
        double t = ((anchor.x - ray[0]) * n.x + (anchor.y - ray[1]) * n.y
                + (anchor.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return null;
        }
        return new net.minecraft.world.phys.Vec3(ray[0] + ray[3] * t, ray[1] + ray[4] * t, ray[2] + ray[5] * t);
    }

    void commitWorldMarquee(net.minecraft.client.Camera camera, Minecraft mc) {
        double[] m = worldMarquee;
        worldMarquee = null;
        cc.worldMarqueePreview.clear();
        if (m == null || cc.worldPage == null || cc.worldNodes == null) {
            return;
        }
        double x0 = Math.min(m[0], m[2]), x1 = Math.max(m[0], m[2]);
        double y0 = Math.min(m[1], m[3]), y1 = Math.max(m[1], m[3]);
        if (x1 - x0 < 3 && y1 - y0 < 3) {
            return; // 太小不算框选
        }
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        var vars = cc.worldPage.variables();
        net.minecraft.world.phys.Vec3 anchor = cc.focusedWorldAnchor(camera);
        java.util.List<String> hits = new java.util.ArrayList<>();
        cc.collectMarqueeHits(cc.worldNodes, camera, anchor, vars, x0, y0, x1, y1, scaledW, scaledH,
                cc.worldTabActive(cc.worldPage.id()), hits);
        if (hits.isEmpty()) {
            cc.worldEditMulti.clear(); // 空框 = 清空多选
            return;
        }
        // 组展开：命中元素所在组（>1 成员）整组纳入（保持组语义；操作时仍按锁定规则跳过）
        java.util.Set<String> expanded = new java.util.LinkedHashSet<>(hits);
        for (String id : hits) {
            String grp = cc.worldGroupOf(id);
            if (grp != null && cc.worldGroupMembers(grp).size() > 1) {
                expanded.addAll(cc.worldGroupMembers(grp));
            }
        }
        java.util.List<String> finalHits = new java.util.ArrayList<>(expanded);
        cc.worldEditMulti.addAll(finalHits);
        worldEditSelected = finalHits.get(finalHits.size() - 1);
    }

    void updateWorldMarqueePreview(net.minecraft.client.Camera camera, Minecraft mc) {
        cc.worldMarqueePreview.clear();
        if (worldMarquee == null || cc.worldPage == null || cc.worldNodes == null) {
            return;
        }
        double[] m = worldMarquee;
        double x0 = Math.min(m[0], m[2]), x1 = Math.max(m[0], m[2]);
        double y0 = Math.min(m[1], m[3]), y1 = Math.max(m[1], m[3]);
        if (x1 - x0 < 3 && y1 - y0 < 3) {
            return;
        }
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        java.util.List<String> hits = new java.util.ArrayList<>();
        cc.collectMarqueeHits(cc.worldNodes, camera, cc.focusedWorldAnchor(camera), cc.worldPage.variables(),
                x0, y0, x1, y1, scaledW, scaledH, cc.worldTabActive(cc.worldPage.id()), hits);
        cc.worldMarqueePreview.addAll(hits);
    }

    void nudgeWorldEdit(Minecraft mc, long nowMs) {
        if (WorldHologram.locked(cc.findWorldNode(worldEditSelected))) {
            return;
        }
        long win = mc.getWindow().getWindow();
        boolean left = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) == 1;
        boolean right = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) == 1;
        boolean up = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_UP) == 1;
        boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) == 1;
        if (!left && !right && !up && !down) {
            lastWorldNudgeAt = 0;
            return;
        }
        long repeat = lastWorldNudgeAt == 0 ? 0 : 200;
        if (nowMs - lastWorldNudgeAt < repeat) {
            return;
        }
        lastWorldNudgeAt = nowMs;
        double step = worldEditStep;
        double dx = 0, dy = 0, dz = 0;
        if (left) {
            dx -= step;
        }
        if (right) {
            dx += step;
        }
        if (up) {
            dy += step;
        }
        if (down) {
            dy -= step;
        }
        boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
        if (shift) {
            dy = 0; // Shift：↑↓ 改深度 z
            dz = up ? step : down ? -step : 0;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        // 多选批量微调：选中集内全部元素同偏移（否则单元素）；锁定元素跳过
        java.util.List<String> targets = cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(worldEditSelected)
                ? new java.util.ArrayList<>(cc.worldEditMulti) : List.of(worldEditSelected);
        targets = cc.filterLocked(targets);
        if (targets == null || targets.isEmpty()) {
            return; // 全部锁定：方向键微调不生效
        }
        moveWorldEditElements(targets, dx, dy, dz);
    }

    public void copyWorldPropValue(String path, String value) {
        worldPropClipboard = value;
        worldPropClipboardPath = path;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已复制属性 " + path + " = "
                        + cc.shortText(value) + "（编辑属性时点 [粘贴值] 应用）"), false);
    }

    void moveWorldEditElement(String elementId, double dx, double dy, double dz) {
        moveWorldEditElements(List.of(elementId), dx, dy, dz);
    }

    void moveWorldEditElements(List<String> elementIds, double dx, double dy, double dz) {
        if (cc.worldPage == null || elementIds.isEmpty()) {
            return;
        }
        java.util.List<String> alive = new java.util.ArrayList<>();
        for (String id : elementIds) {
            var el = cc.findElement(cc.worldPage, id);
            if (el != null && !WorldHologram.locked(cc.findWorldNode(id))) {
                alive.add(id);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        pushWorldUndo("微调", "nudge", alive);
        for (String id : alive) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                    raw instanceof Map<?, ?> h ? (Map<?, ?>) h : Map.of());
            var vars = cc.worldPage.variables();
            double nx = WorldHologram.holoNum(holo, "x", 0, vars) + dx;
            double ny = WorldHologram.holoNum(holo, "y", 0, vars) + dy;
            double nz = WorldHologram.holoNum(holo, "z", 0, vars) + dz;
            if (cc.setWorldElementPos(id, nx, ny, nz)) {
                worldEditDirty.put(id, new double[]{nx, ny, nz});
                cc.refreshCreateBlock(id);
            }
        }
    }

    public void saveWorldEdits() {
        if (!worldEditMode || cc.worldPage == null) {
            return;
        }
        if (cc.worldPanelLocked()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板已锁定，禁止保存（Ctrl+点击锁按钮解锁面板）"), false);
            return;
        }
        String pageId = cc.worldPage.id();
        Map<String, String> opts = cc.diffWorldOptions(); // 背景/锚点等页面级选项差异（相对进入编辑时基线）
        String title = worldEditPageTitle;        // 页面标题待写（对齐屏改标题）
        Map<String, String> varsProps = worldEditVars.isEmpty()
                ? Map.of() : new java.util.LinkedHashMap<>(worldEditVars); // 页面变量待写
        if (worldEditDirty.isEmpty() && worldEditProps.isEmpty() && worldEditDeletes.isEmpty()
                && opts.isEmpty() && title == null && varsProps.isEmpty()) {
            return;
        }
        List<com.opendreamcore.protocol.message.WorldLayout.Entry> entries = new java.util.ArrayList<>();
        // 删除：__delete__ 特殊 props
        worldEditDeletes.forEach(elementId -> entries.add(
                new com.opendreamcore.protocol.message.WorldLayout.Entry(elementId, 0, 0, 0,
                        java.util.Map.of("__delete__", "1"))));
        worldEditDirty.forEach((elementId, pos) -> entries.add(
                new com.opendreamcore.protocol.message.WorldLayout.Entry(elementId, pos[0], pos[1], pos[2],
                        worldEditProps.get(elementId))));
        // 只有属性没有位置的元素也要提交（保留当前位置）
        worldEditProps.forEach((elementId, props) -> {
            if (worldEditDirty.containsKey(elementId) || worldEditDeletes.contains(elementId)) {
                return;
            }
            var element = cc.findElement(cc.worldPage, elementId);
            if (element == null) {
                return;
            }
            Object raw = element.props().get("hologram");
            Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
            var vars = cc.worldPage.variables();
            entries.add(new com.opendreamcore.protocol.message.WorldLayout.Entry(elementId,
                    WorldHologram.holoNum(holo, "x", 0, vars),
                    WorldHologram.holoNum(holo, "y", 0, vars),
                    WorldHologram.holoNum(holo, "z", 0, vars), props));
        });
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.WorldLayout(pageId, entries, opts, title, varsProps).encode(buf);
        cc.sendRaw(com.opendreamcore.protocol.Protocol.EDITOR_WORLD, buf.toByteArray());
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f正在写入世界面板 " + pageId
                        + "（" + entries.size() + " 项"
                        + (opts.isEmpty() ? "" : " + " + opts.size() + " 键选项")
                        + (title == null ? "" : " + 标题")
                        + (varsProps.isEmpty() ? "" : " + " + varsProps.size() + " 变量")
                        + "）…"), false);
        // 提交后清空未保存状态：dirty 集合 / 页面级基线 / 标题 / 变量（标题 ●未保存 标记随之熄灭）
        worldEditDirty.clear();
        worldEditProps.clear();
        worldEditDeletes.clear();
        worldEditDeletedElements.clear();
        worldEditPageTitle = null;
        worldEditVars.clear();
        worldOptionsBaseline = cc.snapshotWorldOptions();
        worldVariablesBaseline = cc.snapshotWorldVariables();
    }

    public void copyWorldElementFormat() {
        if (!worldEditMode || cc.worldPage == null || worldEditSelected == null) {
            return;
        }
        var el = cc.findElement(cc.worldPage, worldEditSelected);
        if (el == null) {
            return;
        }
        java.util.Map<String, Object> fmt = new java.util.LinkedHashMap<>();
        fmt.put("type", el.type());
        fmt.put("props", el.props());
        if (!el.actions().isEmpty()) {
            fmt.put("actions", el.actions());
        }
        String json = cc.toJsonValue(fmt);
        worldFormatClipboard = json;
        Minecraft.getInstance().keyboardHandler.setClipboard(json);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已复制格式: " + el.type()
                        + " · " + el.props().size() + " 属性（Ctrl+Shift+V 粘贴到目标）"), false);
    }

    public void pasteWorldElementFormat() {
        if (!worldEditMode || cc.worldPage == null || worldEditSelected == null) {
            return;
        }
        String json = worldFormatClipboard;
        if (json == null || json.isBlank()) {
            json = Minecraft.getInstance().keyboardHandler.getClipboard();
        }
        if (json == null || json.isBlank()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f格式剪贴板为空（先 Ctrl+Shift+C 复制）"), false);
            return;
        }
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(json);
            if (!(parsed instanceof Map<?, ?> root)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f格式剪贴板不是 JSON 对象"), false);
                return;
            }
            String srcType = root.get("type") == null ? null : String.valueOf(root.get("type"));
            Map<String, String> paths = new java.util.LinkedHashMap<>();
            if (root.get("props") instanceof Map<?, ?> pm) {
                cc.flattenPropsToPaths("", pm, paths);
            }
            Map<String, String> actions = new java.util.LinkedHashMap<>();
            if (root.get("actions") instanceof Map<?, ?> am) {
                am.forEach((k, v) -> actions.put(String.valueOf(k), String.valueOf(v)));
            }
            if (paths.isEmpty() && actions.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§e[OpenDreamCore] §f格式为空（无属性无动作）"), false);
                return;
            }
            // 目标集合：多选/组/单选；仅同类型
            java.util.List<String> targets = new java.util.ArrayList<>();
            String grp = cc.worldGroupOf(worldEditSelected);
            if (grp != null && cc.worldGroupMembers(grp).size() > 1) {
                targets.addAll(cc.worldGroupMembers(grp));
            } else if (cc.worldEditMulti.size() >= 2) {
                targets.addAll(cc.worldEditMulti);
            } else {
                targets.add(worldEditSelected);
            }
            java.util.List<String> alive = new java.util.ArrayList<>();
            for (String id : targets) {
                var el = cc.findElement(cc.worldPage, id);
                if (el != null && (srcType == null || srcType.equals(el.type()))) {
                    alive.add(id);
                }
            }
            if (alive.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§e[OpenDreamCore] §f无同类型（" + srcType + "）目标元素"), false);
                return;
            }
            pushWorldUndo("粘贴格式", "batchfmt", alive); // 连续格式粘贴合并
            for (String id : alive) {
                var element = cc.findElement(cc.worldPage, id);
                if (element == null) {
                    continue;
                }
                for (java.util.Map.Entry<String, String> p : paths.entrySet()) {
                    cc.setElementPropPath(element, p.getKey(), p.getValue());
                    worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                            .put(p.getKey(), p.getValue());
                }
                for (java.util.Map.Entry<String, String> a : actions.entrySet()) {
                    element.actions().put(a.getKey(), a.getValue());
                    worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                            .put("actions." + a.getKey(), a.getValue());
                }
                cc.refreshCreateBlock(id);
            }
            cc.invalidateLayout(cc.worldPage);
            cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f已粘贴格式到 " + alive.size()
                            + " 个元素（" + paths.size() + " 属性 + " + actions.size()
                            + " 动作，保存后写回页面文件）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f格式粘贴失败: " + e.getMessage()), false);
        }
    }

    public void copyWorldElementYaml() {
        if (!worldEditMode || cc.worldPage == null || worldEditSelected == null) {
            return;
        }
        var el = cc.findElement(cc.worldPage, worldEditSelected);
        if (el == null) {
            return;
        }
        String yaml = cc.elementYamlBlockFromProps(worldEditSelected, el);
        worldElementYamlClipboard = yaml;
        Minecraft.getInstance().keyboardHandler.setClipboard(yaml);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f元素 YAML 已复制（Ctrl+Shift+G 粘贴为新元素）"), false);
    }

    public void pasteWorldElementYaml() {
        if (!worldEditMode || cc.worldPage == null) {
            return;
        }
        String yaml = worldElementYamlClipboard;
        if (yaml == null || yaml.isBlank()) {
            yaml = Minecraft.getInstance().keyboardHandler.getClipboard();
        }
        if (yaml == null || yaml.isBlank()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f元素 YAML 剪贴板为空（先 Ctrl+Shift+E 复制）"), false);
            return;
        }
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(yaml);
            if (!(parsed instanceof Map<?, ?> m)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f剪贴板不是元素 YAML（需 id/type 顶层键）"), false);
                return;
            }
            java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> root.put(String.valueOf(k), v));
            String type = root.get("type") == null ? "rect" : String.valueOf(root.get("type"));
            Map<String, Object> props = new java.util.LinkedHashMap<>();
            if (root.get("props") instanceof Map<?, ?> pm) {
                pm.forEach((k, v) -> props.put(String.valueOf(k), v));
            }
            Map<String, String> actions = new java.util.LinkedHashMap<>();
            if (root.get("actions") instanceof Map<?, ?> am) {
                am.forEach((k, v) -> actions.put(String.valueOf(k), String.valueOf(v)));
            }
            String base = root.get("id") == null ? "element" : String.valueOf(root.get("id"));
            String newId = base;
            int n = 1;
            while (cc.findElement(cc.worldPage, newId) != null) {
                newId = base + "_" + (n++);
            }
            java.util.List<Element> kids = new java.util.ArrayList<>();
            if (root.get("children") instanceof List<?> cl) {
                for (Object o : cl) {
                    if (o instanceof Map<?, ?> cm) {
                        Element c = cc.elementFromJsonMap(cm);
                        if (c != null) {
                            kids.add(c);
                        }
                    }
                }
            }
            Element el = new Element(newId, type, null, props, null, null, actions, kids, null);
            pushWorldUndo("创建 " + newId, null, List.of(newId)); // 创建前快照（不存在 → 撤消即移除）
            java.util.List<Element> all = new java.util.ArrayList<>(cc.worldPage.elements());
            all.add(el);
            Page np = new Page(cc.worldPage.id(), cc.worldPage.title(), cc.worldPage.match(),
                    cc.worldPage.displayMode(), cc.worldPage.variables(), all,
                    cc.worldPage.functions(), cc.worldPage.options());
            String pid = cc.worldPage.id() == null ? "world" : cc.worldPage.id();
            ClientController.WorldPanel panel = cc.findWorldPanel(pid);
            if (panel != null) {
                panel.page = np;
                panel.nodes = cc.layoutPage(np, 800, 600);
            }
            cc.worldPage = np;
            cc.worldNodes = panel == null ? null : panel.nodes;
            worldEditProps.computeIfAbsent(newId, k -> new ConcurrentHashMap<>())
                    .put("__create__", cc.elementYamlBlockFromProps(newId, el));
            cc.refreshCreateBlock(newId);
            cc.invalidateLayout(np);
            worldEditSelected = newId;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f已粘贴为新元素 " + newId
                            + "（" + type + "，" + props.size() + " 属性，保存后写回页面文件）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f元素粘贴失败: " + e.getMessage()), false);
        }
    }

    public void alignWorldAnchorCross() {
        if (!worldEditMode || cc.worldPage == null) {
            return;
        }
        if (!cc.worldCrossAlignAvailable()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f跨面板锚点对齐需先 ◀面板/面板▶ 切换到另一面板"), false);
            return;
        }
        ClientController.WorldPanel other = cc.findWorldPanel(worldLastPanelPid);
        ClientController.WorldPanel panelA = cc.findWorldPanel(cc.worldPage.id() == null ? "world" : cc.worldPage.id());
        if (other == null || other.anchor == null || panelA == null || panelA.anchor == null) {
            return;
        }
        double dx = other.anchor.x - panelA.anchor.x;
        double dy = other.anchor.y - panelA.anchor.y;
        double dz = other.anchor.z - panelA.anchor.z;
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001 && Math.abs(dz) < 0.001) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f两面板锚点已在同一位置"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("锚点: 跨面板对齐", "anchor:cross");
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        double ox = world.get("offsetX") instanceof Number n ? n.doubleValue()
                : world.get("offsetX") == null ? 0 : cc.parseAnchorNum(world.get("offsetX"), 0);
        double oy = world.get("offsetY") instanceof Number n ? n.doubleValue()
                : world.get("offsetY") == null ? 1.6 : cc.parseAnchorNum(world.get("offsetY"), 1.6);
        double oz = world.get("offsetZ") instanceof Number n ? n.doubleValue()
                : world.get("offsetZ") == null ? 3 : cc.parseAnchorNum(world.get("offsetZ"), 3);
        world.put("offsetX", Math.round((ox + dx) * 100) / 100.0);
        world.put("offsetY", Math.round((oy + dy) * 100) / 100.0);
        world.put("offsetZ", Math.round((oz + dz) * 100) / 100.0);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f锚点已对齐到面板 " + worldLastPanelPid
                        + "（Δ " + Math.round(dx * 100) / 100.0 + ", "
                        + Math.round(dy * 100) / 100.0 + ", "
                        + Math.round(dz * 100) / 100.0 + "；可 Ctrl+Z 撤）"), false);
    }

    public void saveWorldTemplate(String name) {
        if (!worldEditMode || cc.worldPage == null || worldEditSelected == null) {
            return;
        }
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 32) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f模板名需 1~32 字符"), false);
            return;
        }
        java.util.List<String> targets = new java.util.ArrayList<>();
        String grp = cc.worldGroupOf(worldEditSelected);
        if (grp != null && cc.worldGroupMembers(grp).size() > 1) {
            targets.addAll(cc.worldGroupMembers(grp));
        } else if (cc.worldEditMulti.size() >= 2) {
            targets.addAll(cc.worldEditMulti);
        } else {
            targets.add(worldEditSelected);
        }
        java.util.List<String> blocks = new java.util.ArrayList<>();
        for (String id : targets) {
            var el = cc.findElement(cc.worldPage, id);
            if (el != null) {
                blocks.add(cc.elementYamlBlockFromProps(id, el));
            }
        }
        if (blocks.isEmpty()) {
            return;
        }
        try {
            java.nio.file.Path f = cc.worldTemplatesFile();
            java.nio.file.Files.createDirectories(f.getParent());
            java.util.Map<String, Object> all = new java.util.LinkedHashMap<>();
            if (java.nio.file.Files.exists(f)) {
                String body = java.nio.file.Files.readString(f).trim();
                Object parsed = body.isEmpty() ? null : new org.yaml.snakeyaml.Yaml().load(body);
                if (parsed instanceof Map<?, ?> pm) {
                    pm.forEach((k, v) -> all.put(String.valueOf(k), v));
                }
            }
            all.put(n, blocks);
            java.nio.file.Files.writeString(f, cc.toJsonValue(all));
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f模板已保存: " + n
                            + "（" + blocks.size() + " 个元素）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f模板保存失败: " + e.getMessage()), false);
        }
    }

    public void pasteWorldTemplate(String name) {
        if (!worldEditMode || cc.worldPage == null) {
            return;
        }
        try {
            java.nio.file.Path f = cc.worldTemplatesFile();
            if (!java.nio.file.Files.exists(f)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f无模板文件（先 Ctrl+Shift+T 保存）"), false);
                return;
            }
            Object parsed = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(f));
            if (!(parsed instanceof Map<?, ?> pm)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f模板文件格式异常"), false);
                return;
            }
            Object blocksObj = pm.get(name);
            if (!(blocksObj instanceof List<?> blocks) || blocks.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f模板不存在: " + name), false);
                return;
            }
            java.util.List<Element> created = new java.util.ArrayList<>();
            java.util.List<String> failedNested = new java.util.ArrayList<>();
            cc.expandTemplateBlocks(pm, name, 0, new java.util.ArrayList<>(), 0, 0, created, failedNested);
            if (created.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f模板内容解析失败"
                                + (failedNested.isEmpty() ? "" : "（嵌套缺失: " + String.join(", ", failedNested) + "）")),
                        false);
                return;
            }
            java.util.List<String> newIds = new java.util.ArrayList<>();
            for (Element e : created) {
                newIds.add(e.id());
            }
            pushWorldUndo("模板: " + name, null, newIds); // 创建前快照（不存在 → 撤消即移除）
            java.util.List<Element> all = new java.util.ArrayList<>(cc.worldPage.elements());
            all.addAll(created);
            Page np = new Page(cc.worldPage.id(), cc.worldPage.title(), cc.worldPage.match(),
                    cc.worldPage.displayMode(), cc.worldPage.variables(), all,
                    cc.worldPage.functions(), cc.worldPage.options());
            String pid = cc.worldPage.id() == null ? "world" : cc.worldPage.id();
            ClientController.WorldPanel panel = cc.findWorldPanel(pid);
            if (panel != null) {
                panel.page = np;
                panel.nodes = cc.layoutPage(np, 800, 600);
            }
            cc.worldPage = np;
            cc.worldNodes = panel == null ? null : panel.nodes;
            for (Element e : created) {
                worldEditProps.computeIfAbsent(e.id(), k -> new ConcurrentHashMap<>())
                        .put("__create__", cc.elementYamlBlockFromProps(e.id(), e));
                cc.refreshCreateBlock(e.id());
            }
            cc.invalidateLayout(np);
            worldEditSelected = created.get(0).id();
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f模板已粘贴: " + name
                            + "（" + created.size() + " 个元素；可 Ctrl+Z 撤）"), false);
            if (!failedNested.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§7[OpenDreamCore] §f嵌套缺失已跳过: "
                                + String.join(", ", failedNested)), false);
            }
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f模板粘贴失败: " + e.getMessage()), false);
        }
    }

    public void deleteWorldTemplate(String name) {
        try {
            java.nio.file.Path f = cc.worldTemplatesFile();
            if (!java.nio.file.Files.exists(f)) {
                return;
            }
            Object parsed = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(f));
            if (!(parsed instanceof Map<?, ?> pm)) {
                return;
            }
            java.util.Map<String, Object> all = new java.util.LinkedHashMap<>();
            pm.forEach((k, v) -> all.put(String.valueOf(k), v));
            if (all.remove(name) != null) {
                java.nio.file.Files.writeString(f, cc.toJsonValue(all));
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§a[OpenDreamCore] §f模板已删除: " + name), false);
            }
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f模板删除失败: " + e.getMessage()), false);
        }
    }

    public void discardWorldEdits() {        if (worldEditDirty.isEmpty() && worldEditProps.isEmpty() && worldEditDeletes.isEmpty()
                && cc.diffWorldOptions().isEmpty() && worldEditPageTitle == null
                && worldEditVars.isEmpty()) {
            return;
        }
        worldEditOriginal.forEach((elementId, pos) -> cc.setWorldElementPos(elementId, pos[0], pos[1], pos[2]));
        worldEditOriginalProps.forEach((elementId, props) -> {
            var element = cc.findElement(cc.worldPage, elementId);
            if (element == null) {
                return;
            }
            props.forEach((path, value) -> cc.setElementPropPath(element, path, value));
        });
        // 取消删除：从快照还原元素
        worldEditDeletedElements.forEach((elementId, element) -> {
            if (!cc.containsElement(cc.worldPage.elements(), elementId)) {
                cc.worldPage.elements().add(element);
            }
        });
        cc.restoreWorldOptions(worldOptionsBaseline); // 背景/锚点等页面级选项还原到进入编辑时
        worldEditPageTitle = null; // 标题待写取消
        if (worldVariablesBaseline != null) { // 变量还原到进入编辑时
            cc.worldPage.variables().clear();
            cc.worldPage.variables().putAll((Map<String, Object>) cc.deepCopy(worldVariablesBaseline));
        }
        worldEditVars.clear(); // 变量待写取消
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        worldEditDirty.clear();
        worldEditProps.clear();
        worldEditDeletes.clear();
        worldEditDeletedElements.clear();
        cc.clearWorldUndo(); // 放弃编辑 → 撤消/重做历史清空（全部还原为快照）
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f已放弃未保存的世界面板编辑"), false);
    }

    WorldEditEntry captureWorldEditEntry(String elementId) {
        Element el = cc.findElement(cc.worldPage, elementId);
        if (el == null) {
            return new WorldEditEntry(elementId, false, null, null, null, false, null, -1);
        }
        String parentId = null;
        int index = -1;
        for (int i = 0; i < cc.worldPage.elements().size(); i++) {
            Element top = cc.worldPage.elements().get(i);
            if (top == el) {
                parentId = null;
                index = i;
                break;
            }
            int ci = cc.childIndexOf(top, el);
            if (ci >= 0) {
                parentId = top.id();
                index = ci;
                break;
            }
        }
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        el.props().forEach((k, v) -> props.put(k, cc.deepCopy(v)));
        Map<String, String> actions = new java.util.LinkedHashMap<>(el.actions());
        Map<String, String> pending = worldEditProps.get(elementId) == null
                ? null : new java.util.LinkedHashMap<>(worldEditProps.get(elementId));
        return new WorldEditEntry(elementId, true, props, actions, pending, worldEditDirty.containsKey(elementId),
                parentId, index);
    }

    void pushWorldUndo(String label, String key, List<String> elementIds) {
        if (cc.worldPage == null || !worldEditMode || elementIds.isEmpty()) {
            return;
        }
        if (key != null && !worldUndoStack.isEmpty()) {
            WorldEditOp last = worldUndoStack.peek();
            if (key.equals(last.key) && last.entries.size() == elementIds.size()) {
                boolean same = true;
                for (int i = 0; i < elementIds.size(); i++) {
                    if (!elementIds.get(i).equals(last.entries.get(i).id)) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    return; // 连续同类小步（属性输入/步进键）合并
                }
            }
        }
        List<WorldEditEntry> entries = new java.util.ArrayList<>();
        for (String id : elementIds) {
            entries.add(captureWorldEditEntry(id));
        }
        worldUndoStack.push(new WorldEditOp(label, key, entries));
        worldRedoStack.clear();
        while (worldUndoStack.size() > WORLD_UNDO_LIMIT) {
            worldUndoStack.removeLast();
        }
    }

    public void undoWorldEdit() {
        if (!worldEditMode || worldUndoStack.isEmpty() || cc.worldPage == null) {
            return;
        }
        WorldEditOp op = worldUndoStack.pop();
        List<WorldEditEntry> current = new java.util.ArrayList<>();
        for (WorldEditEntry e : op.entries) {
            current.add(captureWorldEditEntry(e.id));
        }
        worldRedoStack.push(new WorldEditOp(op.label, null, current, cc.snapshotWorldOptions()));
        restoreWorldEditEntries(op.entries);
        cc.restoreWorldOptions(op.worldOptions);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f已撤消: " + op.label), false);
    }

    public void redoWorldEdit() {
        if (!worldEditMode || worldRedoStack.isEmpty() || cc.worldPage == null) {
            return;
        }
        WorldEditOp op = worldRedoStack.pop();
        List<WorldEditEntry> current = new java.util.ArrayList<>();
        for (WorldEditEntry e : op.entries) {
            current.add(captureWorldEditEntry(e.id));
        }
        worldUndoStack.push(new WorldEditOp(op.label, null, current, cc.snapshotWorldOptions()));
        restoreWorldEditEntries(op.entries);
        cc.restoreWorldOptions(op.worldOptions);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f已重做: " + op.label), false);
    }

    void restoreWorldEditEntries(List<WorldEditEntry> entries) {
        for (WorldEditEntry e : entries) {
            Element el = cc.findElement(cc.worldPage, e.id);
            if (e.exists) {
                if (el == null) {
                    Element stored = worldEditDeletedElements.remove(e.id);
                    if (stored == null) {
                        stored = worldEditUndoElements.remove(e.id);
                    }
                    if (stored == null) {
                        continue;
                    }
                    List<Element> siblings;
                    if (e.parentId == null) {
                        siblings = cc.worldPage.elements();
                    } else {
                        Element parent = cc.findElement(cc.worldPage, e.parentId);
                        siblings = parent == null ? null : parent.children();
                    }
                    if (siblings == null) {
                        cc.worldPage.elements().add(stored);
                    } else {
                        int idx = e.index >= 0 ? Math.min(e.index, siblings.size()) : siblings.size();
                        siblings.add(idx, stored);
                    }
                    el = stored;
                }
                if (e.props != null) {
                    el.props().clear();
                    el.props().putAll(e.props);
                }
                if (e.actions != null) {
                    el.actions().clear();
                    el.actions().putAll(e.actions);
                }
                if (e.pending == null || e.pending.isEmpty()) {
                    worldEditProps.remove(e.id);
                } else {
                    worldEditProps.put(e.id, new java.util.LinkedHashMap<>(e.pending));
                }
                if (e.dirty) {
                    Object raw = el.props().get("hologram");
                    if (raw instanceof Map<?, ?> holo) {
                        var vars = cc.worldPage.variables();
                        worldEditDirty.put(e.id, new double[]{
                                WorldHologram.holoNum(holo, "x", 0, vars),
                                WorldHologram.holoNum(holo, "y", 0, vars),
                                WorldHologram.holoNum(holo, "z", 0, vars)});
                    }
                } else {
                    worldEditDirty.remove(e.id);
                }
                worldEditDeletes.remove(e.id);
                worldEditDeletedElements.remove(e.id);
            } else {
                if (el != null) {
                    worldEditUndoElements.put(e.id, el); // 撤销"创建"暂存，供重做还原
                    cc.removeElementRecursive(cc.worldPage.elements(), e.id);
                }
                worldEditDirty.remove(e.id);
                worldEditProps.remove(e.id);
                worldEditDeletes.remove(e.id);
            }
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
    }

    boolean handleWorldEditToolbar(Minecraft mc, boolean down, boolean pressEdge) {
        if (!toolbarVisible || !pressEdge) {
            return false;
        }
        double scale = mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        int mx = (int) (mc.mouseHandler.xpos() * scale);
        int my = (int) (mc.mouseHandler.ypos() * scale);
        if (cc.inside(mx, my, toolbarStep)) {
            // 微调步长循环：0.01 → 0.05 → 0.1 → 0.5 → 1
            worldEditStep = worldEditStep < 0.02 ? 0.05 : worldEditStep < 0.1 ? 0.1
                    : worldEditStep < 0.5 ? 0.5 : worldEditStep < 1 ? 1.0 : 0.01;
            return true;
        }
        if (cc.inside(mx, my, toolbarCollapse)) {
            worldToolbarCollapsed = !worldToolbarCollapsed;
            return true;
        }
        if (cc.inside(mx, my, toolbarSnap)) {
            // 网格吸附循环：关 → 0.05 → 0.1 → 0.5 → 1
            worldEditSnap = worldEditSnap <= 0 ? 0.05 : worldEditSnap < 0.1 ? 0.1
                    : worldEditSnap < 0.5 ? 0.5 : worldEditSnap < 1 ? 1.0 : 0;
            return true;
        }
        if (cc.inside(mx, my, toolbarSave)) {
            saveWorldEdits();
            return true;
        }
        if (cc.inside(mx, my, toolbarDiscard)) {
            discardWorldEdits();
            return true;
        }
        if (cc.inside(mx, my, toolbarExit)) {
            if (cc.hasPendingWorldEdits()) {
                Minecraft.getInstance().setScreen(new WorldEditExitConfirmScreen()); // 有未保存草稿 → 确认
            } else {
                cc.exitWorldEditMode();
            }
            return true;
        }
        if (cc.inside(mx, my, toolbarText)) {
            cc.openPropEditor("text.content", "编辑文本内容");
            return true;
        }
        if (cc.inside(mx, my, toolbarColor)) {
            cc.openPropEditor("text.color", "编辑文本颜色（#RRGGBB）");
            return true;
        }
        if (cc.inside(mx, my, toolbarScale)) {
            cc.openPropEditor("hologram.scale", "编辑缩放（世界单位/像素）");
            return true;
        }
        if (cc.inside(mx, my, toolbarProps)) {
            cc.openPropsScreen();
            return true;
        }
        if (cc.inside(mx, my, toolbarAlign)) {
            cc.openAlignScreen();
            return true;
        }
        if (cc.inside(mx, my, toolbarAdd)) {
            mc.setScreen(new WorldEditTypeScreen());
            return true;
        }
        if (cc.inside(mx, my, toolbarDelete)) {
            deleteWorldElement();
            return true;
        }
        if (cc.inside(mx, my, toolbarUndo)) {
            undoWorldEdit();
            return true;
        }
        if (cc.inside(mx, my, toolbarRedo)) {
            redoWorldEdit();
            return true;
        }
        if (cc.inside(mx, my, toolbarHistory)) {
            Minecraft.getInstance().setScreen(new WorldEditHistoryScreen());
            return true;
        }
        // 层级面包屑：点击父链节点 = 选中该祖先
        for (int i = 0; i < toolbarBreadcrumbRects.size(); i++) {
            if (cc.inside(mx, my, toolbarBreadcrumbRects.get(i)) && i < worldBreadcrumbIds.size()) {
                cc.selectWorldElement(worldBreadcrumbIds.get(i));
                return true;
            }
        }
        // 类型 chips：按下开始拖入创建（按住拖到面板释放 = 在该位置创建元素）
        for (int i = 0; i < WORLD_TYPE_CHIPS.length; i++) {
            if (cc.inside(mx, my, toolbarTypeRects[i])) {
                worldTypeDrag = WORLD_TYPE_CHIPS[i];
                worldTypeDragMoved = false;
                worldTypeDragPressX = mx;
                worldTypeDragPressY = my;
                worldTypeDropPoint = null;
                return true;
            }
        }
        return false;
    }

    public void alignWorldElement(String elementId, String mode) {
        if (cc.worldPage == null || elementId == null) {
            return;
        }
        var element = cc.findElement(cc.worldPage, elementId);
        if (element == null) {
            return;
        }
        // 批量（多选 ≥ 2）或面板组：整体对齐（集合包围盒按模式对齐，成员保持相对关系）
        List<String> members = null;
        String group = cc.worldGroupOf(elementId);
        if (group != null && cc.worldGroupMembers(group).size() > 1) {
            members = cc.worldGroupMembers(group);
        } else if (cc.worldEditMulti.size() >= 2) {
            members = new java.util.ArrayList<>(cc.worldEditMulti);
        }
        if (members != null) {
            alignGroup(elementId, members, mode);
            return;
        }
        double[] bounds = WorldHologram.visibleBounds(cc.worldNodes, cc.worldTabActive(cc.worldPage.id()), cc.worldPage.variables());
        if (bounds == null) {
            return;
        }
        Object raw = element.props().get("hologram");
        if (!(raw instanceof Map<?, ?> h)) {
            return;
        }
        Map<Object, Object> holo = new java.util.LinkedHashMap<>(h);
        var vars = cc.worldPage.variables();
        String type = String.valueOf(element.props().get("type"));
        double w = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
        double hh = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
        double x = WorldHologram.holoNum(holo, "x", 0, vars);
        double y = WorldHologram.holoNum(holo, "y", 0, vars);
        switch (mode) {
            case "left" -> x = bounds[0] + w / 2;
            case "right" -> x = bounds[2] - w / 2;
            case "hcenter" -> x = (bounds[0] + bounds[2]) / 2;
            case "top" -> y = bounds[1] + hh / 2;
            case "bottom" -> y = bounds[3] - hh / 2;
            case "vcenter" -> y = (bounds[1] + bounds[3]) / 2;
            default -> {
                return;
            }
        }
        pushWorldUndo("对齐", "align:" + mode, List.of(elementId)); // 单元素对齐可撤消（同模式连续合并）
        holo.put("x", x);
        holo.put("y", y);
        element.props().put("hologram", holo);
        worldEditDirty.put(elementId, new double[]{x, y, WorldHologram.holoNum(holo, "z", 0, vars)});
        cc.refreshCreateBlock(elementId);
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
    }

    void alignGroup(String elementId, List<String> members, String mode) {
        int skipped = members == null ? 0 : members.size();
        members = cc.filterLocked(members);
        skipped -= members == null ? 0 : members.size();
        if (members == null || members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        pushWorldUndo("对齐", null, members);
        double[] bounds = WorldHologram.visibleBounds(cc.worldNodes, cc.worldTabActive(cc.worldPage.id()), cc.worldPage.variables());
        if (bounds == null) {
            return;
        }
        var vars = cc.worldPage.variables();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        java.util.List<Element> els = new java.util.ArrayList<>();
        for (String memberId : members) {
            var el = cc.findElement(cc.worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double w = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
            double h = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
            double x = WorldHologram.holoNum(holo, "x", 0, vars);
            double y = WorldHologram.holoNum(holo, "y", 0, vars);
            minX = Math.min(minX, x - w / 2);
            maxX = Math.max(maxX, x + w / 2);
            minY = Math.min(minY, y - h / 2);
            maxY = Math.max(maxY, y + h / 2);
            els.add(el);
        }
        if (els.isEmpty()) {
            return;
        }
        double dx = 0, dy = 0;
        switch (mode) {
            case "left" -> dx = bounds[0] - minX;
            case "right" -> dx = bounds[2] - maxX;
            case "hcenter" -> dx = (bounds[0] + bounds[2]) / 2 - (minX + maxX) / 2;
            case "top" -> dy = bounds[1] - minY;
            case "bottom" -> dy = bounds[3] - maxY;
            case "vcenter" -> dy = (bounds[1] + bounds[3]) / 2 - (minY + maxY) / 2;
            default -> {
                return;
            }
        }
        for (Element el : els) {
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", WorldHologram.holoNum(holo, "x", 0, vars) + dx);
            copy.put("y", WorldHologram.holoNum(holo, "y", 0, vars) + dy);
            el.props().put("hologram", copy);
            worldEditDirty.put(el.id(), new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            cc.refreshCreateBlock(el.id());
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        // 批量对齐读数：移动距离汇总（= 单元素位移 × 成员数）
        if (dx != 0 || dy != 0) {
            double dist = Math.round(Math.hypot(dx, dy) * 100) / 100.0;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f批量对齐: " + els.size()
                            + " 元素，总位移 " + Math.round(dist * els.size() * 100) / 100.0
                            + "（单元素 " + dist + "；Ctrl+Z 撤消"
                            + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
        }
    }

    public void alignWorldYaw() {
        if (!worldEditMode || cc.worldPage == null || worldEditSelected == null) {
            return;
        }
        java.util.List<String> members = new java.util.ArrayList<>();
        String grp = cc.worldGroupOf(worldEditSelected);
        if (grp != null && cc.worldGroupMembers(grp).size() > 1) {
            members.addAll(cc.worldGroupMembers(grp));
        } else if (cc.worldEditMulti.size() >= 2) {
            members.addAll(cc.worldEditMulti);
        } else {
            members.add(worldEditSelected);
        }
        int skipped = members.size();
        members = cc.filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        var first = cc.findElement(cc.worldPage, members.get(0));
        if (first == null) {
            return;
        }
        Object fRaw = first.props().get("hologram");
        if (!(fRaw instanceof Map<?, ?> fHolo)) {
            return;
        }
        double targetYaw = WorldHologram.holoNum(fHolo, "yaw", 0, cc.worldPage.variables());
        pushWorldUndo("统一旋转", null, members);
        int changed = cc.applyYawToMembers(members, targetYaw);
        if (changed > 0) {
            cc.invalidateLayout(cc.worldPage);
            cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f统一旋转: " + changed + " 个元素 → yaw "
                        + Math.round(targetYaw * 100) / 100.0 + "°（Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    public void alignWorldAll(String mode) {
        if (!worldEditMode || cc.worldPage == null || cc.worldNodes == null) {
            return;
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        collectWorldIds(cc.worldNodes, ids);
        List<String> all = new java.util.ArrayList<>(ids);
        if (all.isEmpty()) {
            return;
        }
        alignGroup(all.get(0), all, mode);
    }

    public void mirrorWorldSelection(String elementId, String axis) {
        if (cc.worldPage == null || elementId == null) {
            return;
        }
        List<String> members = null;
        String group = cc.worldGroupOf(elementId);
        if (group != null && cc.worldGroupMembers(group).size() > 1) {
            members = cc.worldGroupMembers(group);
        } else if (cc.worldEditMulti.size() >= 2) {
            members = new java.util.ArrayList<>(cc.worldEditMulti);
        }
        if (members == null) {
            members = List.of(elementId);
        }
        int skippedMir = members.size();
        members = cc.filterLocked(members);
        skippedMir -= members.size();
        if (members == null || members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        pushWorldUndo("镜像", null, members);
        double[] bounds = WorldHologram.visibleBounds(cc.worldNodes, cc.worldTabActive(cc.worldPage.id()), cc.worldPage.variables());
        if (bounds == null) {
            return;
        }
        boolean horizontal = "x".equals(axis);
        double center = horizontal ? (bounds[0] + bounds[2]) / 2 : (bounds[1] + bounds[3]) / 2;
        var vars = cc.worldPage.variables();
        for (String memberId : members) {
            var el = cc.findElement(cc.worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            if (horizontal) {
                copy.put("x", Math.round((2 * center - WorldHologram.holoNum(holo, "x", 0, vars)) * 100) / 100.0);
            } else {
                copy.put("y", Math.round((2 * center - WorldHologram.holoNum(holo, "y", 0, vars)) * 100) / 100.0);
            }
            Object yaw = holo.get("yaw");
            if (yaw instanceof Number n) {
                copy.put("yaw", Math.round(-n.doubleValue() * 10) / 10.0);
            }
            el.props().put("hologram", copy);
            worldEditDirty.put(memberId, new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            if (copy.get("yaw") != null) {
                worldEditProps.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>())
                        .put("hologram.yaw", String.valueOf(copy.get("yaw")));
            }
            cc.refreshCreateBlock(memberId);
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f" + (horizontal ? "水平" : "垂直")
                        + "镜像: " + members.size() + " 元素（yaw 取反；Ctrl+Z 撤消"
                        + (skippedMir > 0 ? "；跳过 " + skippedMir + " 锁定" : "") + "）"), false);
    }

    public void mirrorWorldCross(String axis) {
        if (!worldEditMode || cc.worldPage == null || cc.worldNodes == null) {
            return;
        }
        if (!cc.worldCrossAlignAvailable()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f跨面板镜像需先 ◀面板/面板▶ 切换到另一面板"), false);
            return;
        }
        List<String> members = null;
        if (worldEditSelected != null) {
            String group = cc.worldGroupOf(worldEditSelected);
            if (group != null && cc.worldGroupMembers(group).size() > 1) {
                members = cc.worldGroupMembers(group);
            } else if (cc.worldEditMulti.size() >= 2) {
                members = new java.util.ArrayList<>(cc.worldEditMulti);
            }
        }
        if (members == null) {
            members = List.of(worldEditSelected);
        }
        int skippedMirX = members.size();
        members = cc.filterLocked(members);
        skippedMirX -= members.size();
        if (members == null || members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        ClientController.WorldPanel other = cc.findWorldPanel(worldLastPanelPid);
        ClientController.WorldPanel panelA = cc.findWorldPanel(cc.worldPage.id() == null ? "world" : cc.worldPage.id());
        if (other == null || other.anchor == null || panelA == null || panelA.anchor == null) {
            return;
        }
        double[] ob = WorldHologram.visibleBounds(other.nodes,
                cc.worldTabActive(worldLastPanelPid), other.page.variables());
        if (ob == null) {
            return;
        }
        boolean horizontal = "x".equals(axis);
        // 参考轴中心（世界坐标）→ 当前面板 holo 坐标
        double centerWorld = horizontal ? (ob[0] + ob[2]) / 2 + other.anchor.x
                : (ob[1] + ob[3]) / 2 + other.anchor.y;
        double center = centerWorld - (horizontal ? panelA.anchor.x : panelA.anchor.y);
        pushWorldUndo("跨面板镜像", "mirrorcross", members);
        var vars = cc.worldPage.variables();
        for (String memberId : members) {
            var el = cc.findElement(cc.worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            if (horizontal) {
                copy.put("x", Math.round((2 * center - WorldHologram.holoNum(holo, "x", 0, vars)) * 100) / 100.0);
            } else {
                copy.put("y", Math.round((2 * center - WorldHologram.holoNum(holo, "y", 0, vars)) * 100) / 100.0);
            }
            Object yaw = holo.get("yaw");
            if (yaw instanceof Number n) {
                copy.put("yaw", Math.round(-n.doubleValue() * 10) / 10.0);
            }
            el.props().put("hologram", copy);
            worldEditDirty.put(memberId, new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            if (copy.get("yaw") != null) {
                worldEditProps.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>())
                        .put("hologram.yaw", String.valueOf(copy.get("yaw")));
            }
            cc.refreshCreateBlock(memberId);
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f跨面板" + (horizontal ? "水平" : "垂直")
                        + "镜像: " + members.size() + " 元素（参考 " + worldLastPanelPid + "；Ctrl+Z 撤消"
                        + (skippedMirX > 0 ? "；跳过 " + skippedMirX + " 锁定" : "") + "）"), false);
    }

    public void distributeWorldGroup(String elementId, String axis) {
        if (cc.worldPage == null || elementId == null) {
            return;
        }
        List<String> members = null;
        String group = cc.worldGroupOf(elementId);
        if (group != null && cc.worldGroupMembers(group).size() > 1) {
            members = cc.worldGroupMembers(group);
        } else if (cc.worldEditMulti.size() >= 2) {
            members = new java.util.ArrayList<>(cc.worldEditMulti);
        }
        if (members == null || members.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f分布需要多选（Ctrl+点击 ≥ 2）或面板组（hologram.group ≥ 2）"), false);
            return;
        }
        int skippedDist = members.size();
        members = cc.filterLocked(members);
        skippedDist -= members.size();
        if (members == null || members.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f分布目标不足 2 个（锁定已跳过）"), false);
            return;
        }
        pushWorldUndo("分布", null, members);
        double[] bounds = WorldHologram.visibleBounds(cc.worldNodes, cc.worldTabActive(cc.worldPage.id()), cc.worldPage.variables());
        if (bounds == null) {
            return;
        }
        var vars = cc.worldPage.variables();
        boolean horizontal = "x".equals(axis);
        // 按当前轴中心排序 + 收集尺寸
        java.util.List<Object[]> sorted = new java.util.ArrayList<>();
        for (String memberId : members) {
            var el = cc.findElement(cc.worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double size = WorldHologram.holoNum(holo, horizontal ? "width" : "height",
                    "text".equals(type) ? (horizontal ? 2.0 : 0.25) : 1.0, vars);
            double center = WorldHologram.holoNum(holo, horizontal ? "x" : "y", 0, vars);
            sorted.add(new Object[]{el, holo, center, size});
        }
        if (sorted.size() < 2) {
            return;
        }
        sorted.sort(java.util.Comparator.comparingDouble(o -> (double) o[2]));
        double lo = horizontal ? bounds[0] : bounds[1];
        double hi = horizontal ? bounds[2] : bounds[3];
        double totalSize = 0;
        for (Object[] o : sorted) {
            totalSize += (double) o[3];
        }
        double gap = (hi - lo - totalSize) / (sorted.size() - 1);
        double cursor = lo;
        double moved = 0;
        for (Object[] o : sorted) {
            double center = cursor + (double) o[3] / 2;
            moved += Math.abs(center - (double) o[2]);
            Map<?, ?> holo = (Map<?, ?>) o[1];
            Element el = (Element) o[0];
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            if (horizontal) {
                copy.put("x", center);
            } else {
                copy.put("y", center);
            }
            el.props().put("hologram", copy);
            worldEditDirty.put(el.id(), new double[]{
                    horizontal ? center : WorldHologram.holoNum(holo, "x", 0, vars),
                    horizontal ? WorldHologram.holoNum(holo, "y", 0, vars) : center,
                    WorldHologram.holoNum(holo, "z", 0, vars)});
            cc.refreshCreateBlock(el.id());
            cursor += (double) o[3] + gap;
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f"
                        + (horizontal ? "横向分布" : "纵向分布") + ": " + sorted.size()
                        + " 元素，累计移动 " + Math.round(moved * 100) / 100.0
                        + "（Ctrl+Z 撤消" + (skippedDist > 0 ? "；跳过 " + skippedDist + " 锁定" : "") + "）"), false);
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
    }

    public void distributeWorldCross(String axis) {
        if (!worldEditMode || cc.worldPage == null || cc.worldNodes == null) {
            return;
        }
        if (!cc.worldCrossAlignAvailable()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f跨面板分布需先 ◀面板/面板▶ 切换到另一面板"), false);
            return;
        }
        List<String> members = null;
        if (worldEditSelected != null) {
            String group = cc.worldGroupOf(worldEditSelected);
            if (group != null && cc.worldGroupMembers(group).size() > 1) {
                members = cc.worldGroupMembers(group);
            } else if (cc.worldEditMulti.size() >= 2) {
                members = new java.util.ArrayList<>(cc.worldEditMulti);
            }
        }
        if (members == null || members.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f分布需要多选（Ctrl+点击 ≥ 2）或面板组（hologram.group ≥ 2）"), false);
            return;
        }
        int skippedDistX = members.size();
        members = cc.filterLocked(members);
        skippedDistX -= members.size();
        if (members == null || members.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f分布目标不足 2 个（锁定已跳过）"), false);
            return;
        }
        ClientController.WorldPanel other = cc.findWorldPanel(worldLastPanelPid);
        ClientController.WorldPanel panelA = cc.findWorldPanel(cc.worldPage.id() == null ? "world" : cc.worldPage.id());
        if (other == null || other.anchor == null || panelA == null || panelA.anchor == null) {
            return;
        }
        double[] ob = WorldHologram.visibleBounds(other.nodes,
                cc.worldTabActive(worldLastPanelPid), other.page.variables());
        if (ob == null) {
            return;
        }
        boolean horizontal = "x".equals(axis);
        double lo = (horizontal ? ob[0] : ob[1]) + (horizontal ? other.anchor.x : other.anchor.y)
                - (horizontal ? panelA.anchor.x : panelA.anchor.y);
        double hi = (horizontal ? ob[2] : ob[3]) + (horizontal ? other.anchor.x : other.anchor.y)
                - (horizontal ? panelA.anchor.x : panelA.anchor.y);
        pushWorldUndo("跨面板分布", "distcross", members);
        var vars = cc.worldPage.variables();
        java.util.List<Object[]> sorted = new java.util.ArrayList<>();
        for (String memberId : members) {
            var el = cc.findElement(cc.worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double size = WorldHologram.holoNum(holo, horizontal ? "width" : "height",
                    "text".equals(type) ? (horizontal ? 2.0 : 0.25) : 1.0, vars);
            double center = WorldHologram.holoNum(holo, horizontal ? "x" : "y", 0, vars);
            sorted.add(new Object[]{el, holo, center, size});
        }
        if (sorted.size() < 2) {
            return;
        }
        sorted.sort(java.util.Comparator.comparingDouble(o -> (double) o[2]));
        double totalSize = 0;
        for (Object[] o : sorted) {
            totalSize += (double) o[3];
        }
        double gap = (hi - lo - totalSize) / (sorted.size() - 1);
        double cursor = lo;
        double moved = 0;
        for (Object[] o : sorted) {
            double center = cursor + (double) o[3] / 2;
            moved += Math.abs(center - (double) o[2]);
            Map<?, ?> holo = (Map<?, ?>) o[1];
            Element el = (Element) o[0];
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            if (horizontal) {
                copy.put("x", Math.round(center * 100) / 100.0);
            } else {
                copy.put("y", Math.round(center * 100) / 100.0);
            }
            el.props().put("hologram", copy);
            worldEditDirty.put(el.id(), new double[]{
                    horizontal ? center : WorldHologram.holoNum(holo, "x", 0, vars),
                    horizontal ? WorldHologram.holoNum(holo, "y", 0, vars) : center,
                    WorldHologram.holoNum(holo, "z", 0, vars)});
            cc.refreshCreateBlock(el.id());
            cursor += (double) o[3] + gap;
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f跨面板"
                        + (horizontal ? "横向分布" : "纵向分布") + ": " + sorted.size()
                        + " 元素，参考 " + worldLastPanelPid + " 范围（Ctrl+Z 撤消"
                        + (skippedDistX > 0 ? "；跳过 " + skippedDistX + " 锁定" : "") + "）"), false);
    }

    public void applyWorldEditProp(String elementId, String path, String value) {
        if (cc.worldPage == null || !worldEditMode) {
            return;
        }
        var element = cc.findElement(cc.worldPage, elementId);
        if (element == null) {
            return;
        }
        pushWorldUndo("属性 " + path, "prop:" + path, List.of(elementId)); // 连续同属性输入合并为一撤消步
        cc.setElementPropPath(element, path, value);
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>()).put(path, value);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f" + path + " = " + value + "（保存后写回页面文件）"), false);
    }

    public void applyWorldEditPropBatch(List<String> elementIds, String path, String value) {
        if (cc.worldPage == null || !worldEditMode || elementIds.isEmpty()) {
            return;
        }
        List<String> alive = new java.util.ArrayList<>();
        for (String id : elementIds) {
            if (cc.findElement(cc.worldPage, id) != null) {
                alive.add(id);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        pushWorldUndo("批量属性 " + path, "batchprop:" + path, alive); // 连续同路径批量合并
        for (String id : alive) {
            var element = cc.findElement(cc.worldPage, id);
            if (element == null) {
                continue;
            }
            cc.setElementPropPath(element, path, value);
            worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(path, value);
            cc.refreshCreateBlock(id);
        }
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f已批量设置 " + path + " = " + value
                        + "（" + alive.size() + " 个元素，保存后写回页面文件）"), false);
    }

    String createWorldElementCopy(String sourceId) {
        var src = cc.findElement(cc.worldPage, sourceId);
        if (src == null) {
            return null;
        }
        return pasteWorldElement(src);
    }

    void copyWorldElement() {
        if (cc.worldPage == null) {
            return;
        }
        List<String> sources;
        if (cc.worldEditMulti.size() >= 2) {
            sources = new java.util.ArrayList<>(cc.worldEditMulti);
        } else if (worldEditSelected != null) {
            String g = cc.worldGroupOf(worldEditSelected);
            if (g != null && cc.worldGroupMembers(g).size() >= 2) {
                sources = cc.worldGroupMembers(g);
            } else {
                sources = List.of(worldEditSelected);
            }
        } else {
            return;
        }
        cc.worldClipboard.clear();
        for (String id : sources) {
            var el = cc.findElement(cc.worldPage, id);
            if (el != null) {
                cc.worldClipboard.add(cc.copyElementTree(el, el.id(), new java.util.HashMap<>()));
            }
        }
        if (cc.worldClipboard.isEmpty()) {
            return;
        }
        saveWorldClipboard();
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已复制 " + cc.worldClipboard.size()
                        + " 个元素（Ctrl+V 粘贴，重启保留）"), false);
    }

    void saveWorldClipboard() {
        try {
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "opendreamcore");
            java.io.File file = new java.io.File(dir, "clipboard.json");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (Element e : cc.worldClipboard) {
                list.add(cc.elementToClipboardMap(e));
            }
            java.nio.file.Files.writeString(file.toPath(),
                    new com.google.gson.Gson().toJson(list), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    void pasteWorldElementClipboard() {
        cc.ensureWorldClipboardLoaded();
        if (cc.worldClipboard.isEmpty() || cc.worldPage == null) {
            return;
        }
        // 预生成全部新 id + 共享 idMap（剪贴板元素间的 parent 引用重映射）
        java.util.Set<String> existing = new java.util.HashSet<>();
        collectWorldIds(cc.worldNodes, existing);
        List<String> newIds = new java.util.ArrayList<>();
        java.util.Map<String, String> idMap = new java.util.HashMap<>();
        int n = 1;
        for (Element clip : cc.worldClipboard) {
            String id;
            do {
                id = "el_" + n++;
            } while (existing.contains(id));
            existing.add(id);
            newIds.add(id);
            idMap.put(clip.id(), id);
        }
        pushWorldUndo("粘贴 " + cc.worldClipboard.size() + " 元素", null, newIds); // 创建前快照（撤消 = 整批移除）
        java.util.Set<String> reserved = new java.util.HashSet<>(newIds);
        for (int i = 0; i < cc.worldClipboard.size(); i++) {
            String pasted = pasteWorldElementInto(cc.worldClipboard.get(i), newIds.get(i), idMap, reserved);
            if (pasted == null) {
                continue;
            }
            // 跨页粘贴：parent 不在本次粘贴集内 → 断开（避免挂到不存在的父元素）
            var pastedEl = cc.findElement(cc.worldPage, pasted);
            if (pastedEl != null) {
                Object p = pastedEl.props().get("parent");
                if (p != null && !idMap.containsKey(String.valueOf(p))) {
                    pastedEl.props().remove("parent");
                }
            }
        }
        cc.worldEditMulti.clear();
        cc.worldEditMulti.addAll(newIds);
        worldEditSelected = newIds.get(newIds.size() - 1);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已粘贴 " + cc.worldClipboard.size()
                        + " 个元素（保存后写入页面文件）"), false);
    }

    String pasteWorldElement(Element src) {
        String newId = cc.uniqueWorldElementId();
        pushWorldUndo("创建 " + newId, null, List.of(newId)); // 创建前快照（不存在 → 撤消即移除）
        return pasteWorldElementInto(src, newId);
    }

    String pasteWorldElementInto(Element src, String newId) {
        return pasteWorldElementInto(src, newId, new java.util.HashMap<>(), java.util.Set.of());
    }

    String pasteWorldElementInto(Element src, String newId, java.util.Map<String, String> idMap) {
        return pasteWorldElementInto(src, newId, idMap, java.util.Set.of());
    }

    String pasteWorldElementInto(Element src, String newId, java.util.Map<String, String> idMap,
                                         java.util.Set<String> reserved) {
        Element copy = cc.copyElementTree(src, newId, idMap, new java.util.HashSet<>(reserved));
        // 位置微偏移（副本与原件错开，避免完全重叠不可见）
        Object raw = copy.props().get("hologram");
        if (raw instanceof Map<?, ?> h) {
            Map<Object, Object> holo = new java.util.LinkedHashMap<>(h);
            var vars = cc.worldPage.variables();
            holo.put("x", WorldHologram.holoNum(holo, "x", 0, vars) + 0.15);
            holo.put("y", WorldHologram.holoNum(holo, "y", 0, vars) - 0.15);
            copy.props().put("hologram", holo);
        }
        cc.worldPage.elements().add(copy);
        worldEditProps.computeIfAbsent(newId, k -> new ConcurrentHashMap<>())
                .put("__create__", cc.elementYamlBlockFromProps(newId, copy));
        worldEditSelected = newId;
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        return newId;
    }

    public void createWorldElement(String type, String content) {
        createWorldElement(type, content, null, null);
    }

    public void createWorldElementAt(String type, double x, double y) {
        createWorldElement(type, null, Math.round(x * 100) / 100.0, Math.round(y * 100) / 100.0);
    }

    void createWorldElement(String type, String content, Double placeX, Double placeY) {
        if (cc.worldPage == null || !worldEditMode) {
            return;
        }
        String id = cc.uniqueWorldElementId();
        pushWorldUndo("创建 " + id, null, List.of(id)); // 创建前快照（不存在 → 撤消即移除）
        Map<String, Object> holo = cc.defaultWorldHolo(type);
        if (placeX != null) {
            holo.put("x", placeX);
        }
        if (placeY != null) {
            holo.put("y", placeY);
        }
        Map<String, Object> spec = cc.defaultWorldSpec(type);
        if ("text".equals(type) && content != null && !content.isEmpty()) {
            spec.put("content", content);
        }
        Element element = new Element(id, type, null, new java.util.LinkedHashMap<>(), null, null, null,
                List.of(), null);
        element.props().put("hologram", holo);
        element.props().put(type, spec);
        cc.worldPage.elements().add(element);
        String block = cc.elementYamlBlock(id, type, holo, spec);
        worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put("__create__", block);
        worldEditSelected = id;
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已创建元素 " + id + "（保存后写入页面文件）"), false);
    }

    public void deleteWorldElement() {
        if (cc.worldPage == null) {
            return;
        }
        if (cc.worldEditMulti.size() >= 2) {
            // 批量删除
            List<String> batch = new java.util.ArrayList<>(cc.worldEditMulti);
            pushWorldUndo("批量删除", null, batch);
            for (String id : batch) {
                Element removed = cc.findElement(cc.worldPage, id);
                if (removed == null || !cc.removeElementRecursive(cc.worldPage.elements(), id)) {
                    continue;
                }
                worldEditDeletedElements.put(id, removed);
                worldEditDeletes.add(id);
                worldEditDirty.remove(id);
                worldEditProps.remove(id);
            }
            cc.worldEditMulti.clear();
            worldEditSelected = null;
            cc.invalidateLayout(cc.worldPage);
            cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f已批量删除（保存后从页面文件移除）"), false);
            return;
        }
        if (worldEditSelected == null) {
            return;
        }
        String id = worldEditSelected;
        pushWorldUndo("删除", null, List.of(id));
        Element removed = cc.findElement(cc.worldPage, id);
        if (removed == null || !cc.removeElementRecursive(cc.worldPage.elements(), id)) {
            return;
        }
        worldEditDeletedElements.put(id, removed);
        worldEditDeletes.add(id);
        worldEditDirty.remove(id);
        worldEditProps.remove(id);
        worldEditSelected = null;
        cc.invalidateLayout(cc.worldPage);
        cc.worldNodes = cc.layoutPage(cc.worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f已删除元素 " + id + "（保存后从页面文件移除）"), false);
    }

    /**
     * Ctrl 拖拽复制（多选 = 批量复制选中集 / Ctrl+Alt = 复制整组）：返回被按元素的副本 id（原件不动）。
     * 批量语义：一次快照一步撤消；整组选中的组重链接到新组名（副本组不串原件）；
     * 副本集接管多选 → 拖拽时副本集整体联动。
     */
    String copyElementForDrag(String sourceId) {
        if (cc.worldEditMulti.size() >= 2 && cc.worldEditMulti.contains(sourceId)) {
            return cc.copyElementsForDrag(new java.util.ArrayList<>(cc.worldEditMulti), sourceId, true);
        }
        if (!cc.altHeld(Minecraft.getInstance())) {
            return createWorldElementCopy(sourceId);
        }
        String group = cc.worldGroupOf(sourceId);
        if (group == null || cc.worldGroupMembers(group).size() < 2) {
            return createWorldElementCopy(sourceId);
        }
        return cc.copyElementsForDrag(cc.worldGroupMembers(group), sourceId, true);
    }

    static void collectWorldIds(List<RenderNode> nodes, java.util.Set<String> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            out.add(node.id());
            collectWorldIds(node.children(), out);
        }
    }

    public com.opendreamcore.page.Element findWorldElement(String id) {
        return cc.worldPage == null ? null : cc.findElement(cc.worldPage, id);
    }

}
