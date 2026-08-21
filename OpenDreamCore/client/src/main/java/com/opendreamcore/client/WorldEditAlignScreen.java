package com.opendreamcore.client;

/**
 * 世界编辑器·对齐/分布/编组面板
 * 仅同包可见；通过 ClientController.get() 公共 API 操作世界编辑状态。
 */
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

    final class WorldEditAlignScreen extends net.minecraft.client.gui.screens.Screen {
        private static final String[][] MODES = {{"left", "左对齐"}, {"right", "右对齐"},
                {"hcenter", "水平居中"}, {"top", "顶对齐"}, {"bottom", "底对齐"}, {"vcenter", "垂直居中"},
                {"dist_x", "横向分布"}, {"dist_y", "纵向分布"},
                {"size_w", "统一宽度"}, {"size_h", "统一高度"},
                {"mirror_x", "水平镜像"}, {"mirror_y", "垂直镜像"},
                {"group", "编组"}, {"ungroup", "解组"},
                {"yaw", "统一旋转"}};
        /** 模式 → 基础标签（状态标记用）。 */
        private static final java.util.Map<String, String> MODES_LABELS = new java.util.HashMap<>();
        static {
            for (String[] m : MODES) {
                MODES_LABELS.put(m[0], m[1]);
            }
        }

        /** hex 解析（#RGB / #RRGGBB / #AARRGGBB → 0xRRGGBB；失败 0）。 */
        private static int parseHexColor(String s) {
            try {
                String body = s.trim();
                if (body.startsWith("#")) {
                    body = body.substring(1);
                }
                if (body.length() == 3) { // #RGB 缩写展开
                    body = body.substring(0, 1).repeat(2) + body.substring(1, 2).repeat(2)
                            + body.substring(2, 3).repeat(2);
                }
                if (body.length() == 8) {
                    body = body.substring(2);
                }
                return body.length() == 6 ? 0xFF000000 | Integer.parseInt(body, 16) : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** 模式按钮 tooltip（说明基准与批量行为）。 */
        private static String modeTooltip(String mode) {            return switch (mode) {
                case "left", "right", "hcenter", "top", "bottom", "vcenter" ->
                        "相对可见元素包围盒对齐；组/多选 = 整组整体移动（悬停预览基准框）";
                case "dist_x", "dist_y" ->
                        "按轴排序等间隙分布（含各自尺寸，悬停预览结果）";
                case "size_w", "size_h" ->
                        "统一为成员最大尺寸，位置不变（悬停预览结果）";
                case "mirror_x", "mirror_y" ->
                        "悬停预览翻转结果（Shift+悬停 = 双轴同时预览，拖拽中 ghost 跟随）";
                case "group" -> "多选元素编组（Ctrl+点击/框选 ≥2），组内联动移动/对齐";
                case "ungroup" -> "移除所选元素所在组（其余成员保留组）";
                case "yaw" -> "统一旋转：全部成员 yaw（hologram.yaw）对齐到首元素（跨面 = 参考面板选中元素；范围=全部 = 全部可见元素）";
                default -> "";
            };
        }
        private String elementId; // 当前对齐目标（◀面板/面板▶ 切换时随面板更新）
        private net.minecraft.client.gui.components.Button mirrorXBtn;
        private net.minecraft.client.gui.components.Button mirrorYBtn;
        private net.minecraft.client.gui.components.Button distXBtn;
        private net.minecraft.client.gui.components.Button distYBtn;
        private net.minecraft.client.gui.components.Button sizeWBtn;
        private net.minecraft.client.gui.components.Button sizeHBtn;
        private final java.util.Map<String, net.minecraft.client.gui.components.Button> modeBtns =
                new java.util.HashMap<>();
        private net.minecraft.client.gui.components.Button borderBtn;
        private net.minecraft.client.gui.components.Button gradientBtn;
        private net.minecraft.client.gui.components.Button[] radiusBtns;
        private net.minecraft.client.gui.components.Button[] paddingBtns;
        private net.minecraft.client.gui.components.Button[] borderWidthBtns;
        private net.minecraft.client.gui.components.EditBox hexBox;
        private net.minecraft.client.gui.components.Button gradDirBtn;
        private net.minecraft.client.gui.components.Button[] fadeBtns;
        private net.minecraft.client.gui.components.Button fadeRangeBtn;
        /** 吸色模式：下一次左键点击 = 取该像素色为面板背景（取色时不画遮罩）。 */
        private boolean picking;
        /** 吸色模式实时取色（渲染帧跟随鼠标采样；点击时直接使用）。 */
        private String pickLiveHex;
        /** 键位速查面板开关（? 按钮 / Esc 关闭）。 */
        private boolean helpOpen;
        /** 对齐范围：true = 全部可见元素（左/右/中/顶/底/中 对齐应用到整组包围盒；分布/尺寸/镜像仍按选区）。 */
        private boolean scopeAll;
        /** 跨面板对齐：true = 六个对齐模式对齐到上次聚焦面板的选中元素（含锚点差换算）。 */
        private boolean crossPanel;
        /** 长按连续步进状态（循环类控件按住 400ms 后每 250ms 触发一次）。 */
        private net.minecraft.client.gui.components.AbstractWidget repeatWidget;
        private long repeatAt;
        private net.minecraft.client.gui.components.Button bgAlphaBtn;
        private net.minecraft.client.gui.components.Button hoverColorBtn;
        private net.minecraft.client.gui.components.Button lockBtn;
        /** 键位速查面板内容（多行）。 */
        private static final String[] HELP_LINES = {
                "对齐模式: 数字键 1-9/0 + Q/W/E/R（链式保持打开，Esc 关闭） · 左下 范围:自动/全部 · 跨面:关/开（对齐/分布/镜像/尺寸以另一面板为参考） · 点世界元素=重选目标 · Ctrl+Shift+A=跨面板锚点对齐",
                "背景色板: 点击应用 · 末 2 格为最近使用 · Shift+点击=收藏 · 第二行左起为收藏色板（Shift+点击移除）",
                "边框: 点击开/关 · Shift=辉光循环 · Shift双击=辉光强度 · Ctrl=边框色",
                "渐变方向: 点击=上下⇄左右 · Shift=双色互换 · Ctrl=中段色 · Ctrl+Shift=中段位置",
                "  Alt=预设循环 · Alt双击=随机配色 · Alt+Shift=提亮 · Ctrl+Alt=压暗",
                "色条: 点击=随机 · 循环=换色 · Shift点击=hex编辑（支持#AARRGGBB） · 右键=复制 · Alt=明暗 · 双击=与主色互换 · 色α=透明度循环",
                "吸色: 点击=取为背景 · Ctrl+点击=元素文本色（多选/组批量） · 右键/Esc取消",
                "A/B 对比: B=暂存 A · N=切换（可Ctrl+Z撤） · Ctrl+Shift+N=清空 · Ctrl+Shift+B=快照转预设",
                "复制/粘贴: Ctrl+C=YAML · Alt+Ctrl+C=JSON · Ctrl+V=粘贴背景 · Ctrl+Shift+C/V=格式刷（同类型批量） · Ctrl+Shift+E/G=元素YAML复制/粘贴为新元素 · Ctrl+Shift+T=存模板 · 模板▽=模板管理",
                "预设: Alt+Ctrl+S=存 · Alt+Ctrl+L=循环 · Alt+Shift+Ctrl+L=删 · Alt+Ctrl+R=重命名",
                "撤消/重做: Ctrl+Z / Ctrl+Y（元素+背景+锚点一体） · 顶部: 标题/页签/变量/面板/重置/导入/导出/克隆",
        };
        /** 大成员数确认时间戳（>20 元素操作二次触发确认）。 */
        private long confirmAt;
        /** 摘要闪烁反馈时间戳（操作应用后 400ms）。 */
        private long flashAt;
        /** 会话内操作计数（摘要行尾回显）。 */
        private int opCount;
        /** Alt 单击时间戳（双击 = 随机配色）。 */
        private long lastAltClickAt;
        /** Shift 单击时间戳（双击 = 辉光强度）。 */
        private long lastShiftClickAt;
        /** 预设重命名输入中（hex 格复用；回车提交）。 */
        private boolean renamePending;
        /** hex 编辑目标键（色条 Shift+点击 设置；null = 背景主色）。 */
        private String hexTargetKey;
        /** 背景 A 快照（B 暂存 / N 对比切换）。 */
        private String bgSnapshotA;
        /** 当前展示侧（true = A 快照已应用；false = 当前值）。 */
        private boolean bgSnapshotSideA;

        WorldEditAlignScreen(String elementId) {
            super(Component.literal("对齐/分布 · " + elementId));
            this.elementId = elementId;
            this.sessionBgBase = ClientController.get().worldBgOpCount();
        }

        @Override
        protected void init() {
            // 紧凑布局：小窗口（<500px 高）压缩网格行距与底部控制行距；<400px 高再缩一档；<1000px 宽压缩横向步进
            int cols = 2, bw = 150, bh = 24, gap = 10;
            this.narrow = this.width < 1000;
            if (this.height < 400) {
                bh = 18;
                gap = 4;
                this.rBase = 12;
                this.rStep = 18;
            } else if (this.height < 500) {
                bh = 20;
                gap = 6;
                this.rBase = 15;
                this.rStep = 20;
            }
            int totalW = cols * bw + (cols - 1) * gap;
            int x0 = this.width / 2 - totalW / 2;
            int y0 = this.height / 2 - MODES.length / cols * (bh + gap) / 2;
            for (int i = 0; i < MODES.length; i++) {
                int row = i / cols, col = i % cols;
                String mode = MODES[i][0];
                String label = MODES[i][1];
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(label), btn -> runMode(mode))
                        .bounds(x0 + col * (bw + gap), y0 + row * (bh + gap), bw, bh)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal(modeTooltip(mode))))
                        .build());
                if ("mirror_x".equals(mode)) {
                    this.mirrorXBtn = (net.minecraft.client.gui.components.Button) this.children()
                            .get(this.children().size() - 1);
                } else if ("mirror_y".equals(mode)) {
                    this.mirrorYBtn = (net.minecraft.client.gui.components.Button) this.children()
                            .get(this.children().size() - 1);
                } else if ("dist_x".equals(mode)) {
                    this.distXBtn = (net.minecraft.client.gui.components.Button) this.children()
                            .get(this.children().size() - 1);
                } else if ("dist_y".equals(mode)) {
                    this.distYBtn = (net.minecraft.client.gui.components.Button) this.children()
                            .get(this.children().size() - 1);
                } else if ("size_w".equals(mode)) {
                    this.sizeWBtn = (net.minecraft.client.gui.components.Button) this.children()
                            .get(this.children().size() - 1);
                } else if ("size_h".equals(mode)) {
                    this.sizeHBtn = (net.minecraft.client.gui.components.Button) this.children()
                            .get(this.children().size() - 1);
                }
                this.modeBtns.put(mode,
                        (net.minecraft.client.gui.components.Button) this.children()
                                .get(this.children().size() - 1));
            }
            // 页面标题编辑（顶部一行：改标题按钮；保存时随 EDITOR_WORLD 写回 YAML 顶层 title）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("改标题"), btn -> {
                        String cur = ClientController.get().worldPageTitle();
                        if (cur == null && ClientController.get().worldPage != null
                                && ClientController.get().worldPage.title() != null) {
                            cur = ClientController.get().worldPage.title();
                        }
                        WorldEditAlignScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                                "页面标题（Enter 提交 · 保存后写回 YAML）",
                                cur == null ? "" : cur, v -> ClientController.get().setWorldPageTitle(v)));
                    }).bounds(x0 + 72, y0 - 28, 60, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("修改页面标题（YAML 顶层 title 键）；保存后写回页面文件生效")))
                    .build());
            // 页签循环切换（世界页签栏 tabs 元素；编辑中切换激活页签以对齐不同页签元素）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("◀"), btn -> {
                        this.tabSwitchCount++;
                        ClientController.get().cycleWorldTab(-1);
                    })
                    .bounds(x0 + 2, y0 - 28, 28, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("上一个页签（切换激活页签；无 tabs 元素提示）")))
                    .build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("▶"), btn -> {
                        this.tabSwitchCount++;
                        ClientController.get().cycleWorldTab(1);
                    })
                    .bounds(x0 + 34, y0 - 28, 28, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("下一个页签（切换激活页签；无 tabs 元素提示）")))
                    .build());
            // 多面板循环切换（聚焦下一块世界面板；元素选择同 id 优先，否则该页首个）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("◀面板"), btn -> {
                        this.panelSwitchCount++;
                        String next = ClientController.get().cycleWorldEditFocus(this.elementId, -1);
                        if (next != null) {
                            this.elementId = next;
                        }
                    }).bounds(x0 + 138, y0 - 28, 52, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("上一块世界面板（多面板同屏时切换对齐/编辑目标；选择保留同 id 元素）")))
                    .build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("面板▶"), btn -> {
                        this.panelSwitchCount++;
                        String next = ClientController.get().cycleWorldEditFocus(this.elementId, 1);
                        if (next != null) {
                            this.elementId = next;
                        }
                    }).bounds(x0 + 194, y0 - 28, 52, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("下一块世界面板（多面板同屏时切换对齐/编辑目标；选择保留同 id 元素）")))
                    .build());
            // 页签标签编辑（| 或 , 分隔输入；保存后写回页面文件）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("页签▽"), btn -> {
                        java.util.List<String> cur = ClientController.get().worldTabLabels();
                        WorldEditAlignScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                                "页签标签（| 或 , 分隔多个 · 保存后写回 YAML）",
                                String.join("|", cur), v -> ClientController.get().setWorldTabLabels(v)));
                    }).bounds(x0 + 250, y0 - 28, 52, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("编辑页签标签（tabs 元素 options；| 或 , 分隔；保存后写回页面文件）")))
                    .build());
            // 页面变量编辑（变量屏：行点击改值、Shift+点击删除、＋新增；保存后写回页面文件）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("变量▽"), btn -> {
                        WorldEditAlignScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditVarsScreen());
                    }).bounds(x0 + 306, y0 - 28, 52, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("编辑页面变量（variables 段；点击改值 · Shift+点击删除 · ＋新增；保存后写回页面文件）")))
                    .build());
            // 辅助按钮行（锁/模板/导入/导出/重置/克隆）：窄窗口移至底部第二排（避免与标题/网格重叠），宽窗口右上
            int auxY = this.narrow ? this.height - 44 : 4;
            int auxX0 = this.narrow ? 8 : this.width - 504;
            // 重置页面级修改（背景/锚点/淡出/标题/变量回到进入编辑时；元素编辑保留）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("重置页面级"), btn -> ClientController.get().resetWorldPageState())
                    .bounds(auxX0 + 336, auxY, 80, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("重置页面级修改：背景/锚点/淡出/标题/变量全部回到进入编辑时（元素编辑保留，撤消历史清空）")))
                    .build());
            // 锁定/解锁选中元素（多选/组 = 批量；Alt+点击 = 解锁页面全部；Ctrl+点击 = 面板整体锁定；可撤消）
            this.lockBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("锁:关"), btn -> {                        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                            ClientController.get().toggleWorldPanelLock();
                            return;
                        }
                        if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                            ClientController.get().unlockWorldAll();
                            return;
                        }
                        java.util.List<String> targets = new java.util.ArrayList<>();
                        String grp = ClientController.get().worldGroupOf(this.elementId);
                        if (grp != null && ClientController.get().worldGroupMembers(grp).size() > 1) {
                            targets.addAll(ClientController.get().worldGroupMembers(grp));
                        } else if (ClientController.get().worldEditMulti.size() >= 2) {
                            targets.addAll(ClientController.get().worldEditMulti);
                        }
                        if (targets.size() >= 2) {
                            ClientController.get().toggleWorldElementLockBatch(targets);
                        } else {
                            ClientController.get().toggleWorldElementLock(this.elementId);
                        }
                    })
                    .bounds(auxX0, auxY, 80, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("锁定/解锁选中元素（多选/组 = 批量；Alt+点击 = 解锁页面全部；Ctrl+点击 = 面板整体锁定/解锁；hologram.locked：锁定时不可拖拽/旋转/缩放；可 Ctrl+Z 撤）")))
                    .build();
            this.addRenderableWidget(this.lockBtn);
            // 元素模板（保存/粘贴命名模板；管理屏）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("模板▽"), btn -> {
                        WorldEditAlignScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditTemplatesScreen());
                    }).bounds(auxX0 + 84, auxY, 80, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("元素模板（_templates.json）：＋存当前选中集为命名模板 / 点击粘贴（id 冲突加后缀）/ Shift+点击删除；Ctrl+Shift+T 快速保存；嵌套：块内写 __template: 名称 引用其它模板（可加 __dx/__dy 整体偏移）")))
                    .build());
            // 导出当前页面运行时状态（含运行时编辑）为 JSON：写文件 + 剪贴板
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("导出"), btn -> ClientController.get().exportWorldPageJson())
                    .bounds(auxX0 + 252, auxY, 80, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("导出当前页面运行时状态（含编辑）为 JSON：写入 OpenDreamCore/UI/_page_snapshots/ 并复制到剪贴板")))
                    .build());
            // 从剪贴板导入页面级状态（options/title/variables/elements；保存后写回；Shift = 追加模式）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("导入"), btn -> {
                        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                            ClientController.get().importWorldPageJsonAppend();
                        } else {
                            ClientController.get().importWorldPageJson();
                        }
                    })
                    .bounds(auxX0 + 168, auxY, 80, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("从剪贴板导入页面状态（导出生成的 JSON：options/title/variables/elements 整体替换；保存后写回）；Shift+点击 = 追加模式（仅元素段，id 冲突加后缀，不替换现有）")))
                    .build());
            // 克隆当前聚焦面板为独立新面板（x +2.5 偏移；保存写为独立页面）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("克隆"), btn -> ClientController.get().cloneWorldPanel())
                    .bounds(auxX0 + 420, auxY, 80, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("克隆当前聚焦面板为独立新面板：id 加 _copy 后缀、元素/选项/变量深拷贝、x 偏移 +2.5；保存写为独立页面文件（服务端模式自动申请租约）")))
                    .build());
            // 键位速查面板（? 开关）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("?"), btn -> this.helpOpen = !this.helpOpen)
                    .bounds(this.width - 40, this.height - 24, 32, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("键位速查（全部修饰键组合一览；Esc 关闭）")))
                    .build());
            // 对齐范围切换（自动 ⇄ 全部可见；分布/尺寸/镜像仍按选区）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("范围:自动"), btn -> {
                        this.scopeAll = !this.scopeAll;
                        btn.setMessage(Component.literal(this.scopeAll ? "范围:全部" : "范围:自动"));
                    }).bounds(8, this.height - 24, 72, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("对齐范围：自动（单选/组/多选按现状）⇄ 全部可见（六个对齐模式应用到全部元素整体；分布/尺寸/镜像仍按选区）")))
                    .build());
            // 跨面板对齐开关（六对齐模式对齐到上次聚焦面板的选中元素；含锚点差换算）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("跨面:关"), btn -> {
                        this.crossPanel = !this.crossPanel;
                        btn.setMessage(Component.literal(this.crossPanel ? "跨面:开" : "跨面:关"));
                        if (this.crossPanel && !ClientController.get().worldCrossAlignAvailable()) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§e[OpenDreamCore] §f先用 ◀面板/面板▶ 切到另一面板以确定参考元素"), false);
                        }
                    }).bounds(84, this.height - 24, 72, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("跨面板对齐：开 = 对齐/分布/镜像/统一尺寸模式以上次聚焦面板为参考（对齐到其选中元素、分布到其可见范围、绕其范围中心镜像、尺寸统一为其选中元素值；含锚点差；一步撤消）")))
                    .build());
            // 悬停高亮色循环（world.hoverColor；渲染/交互共用；可撤消）
            this.hoverColorBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("悬停色"), btn -> ClientController.get().cycleWorldHoverColor())
                    .bounds(160, this.height - 24, 72, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("悬停高亮色循环：亮蓝→金→青→白→红→绿→默认（world.hoverColor；可 Ctrl+Z 撤；按住连续循环）")))
                    .build();
            this.addRenderableWidget(this.hoverColorBtn);
            // 收藏色板导入/导出（剪贴板）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("色板出"), btn -> WorldBackgroundEditor.get().exportWorldPalette())
                    .bounds(236, this.height - 24, 64, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("导出收藏色板为 JSON 到剪贴板（分享/备份）")))
                    .build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("色板入"), btn -> WorldBackgroundEditor.get().importWorldPalette())
                    .bounds(304, this.height - 24, 64, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("从剪贴板导入收藏色板（hex 数组；合并去重上限 16）")))
                    .build());
            // 背景预设管理屏（列表/载入/删除/保存当前）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("预设▽"), btn -> {
                        WorldEditAlignScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditPresetsScreen());
                    }).bounds(372, this.height - 24, 52, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("背景预设管理（列表：点击载入 · Shift+点击删除 · ＋保存当前；文件 _bg_presets.json）")))
                    .build());
            // 面板背景色板（底部一行 10 格：无/深蓝/近黑/蓝灰/青灰/深棕/深绿/深紫 + 最近使用 2 格）
            java.util.List<String> recents = ClientController.get().worldRecentBackgrounds();
            String[] bgColors = {null, "#10151F", "#1E2A38", "#2A3A52", "#37474F", "#4E342E",
                    "#1B5E20", "#4A148C",
                    recents.size() > 0 ? recents.get(0) : "#B71C1C",
                    recents.size() > 1 ? recents.get(1) : "#212121"};
            int bgY = y0 + (MODES.length + 1) / 2 * (bh + gap) + 8;
            for (int i = 0; i < bgColors.length; i++) {
                BgSwatch sw = new BgSwatch(x0 + i * (this.narrow ? 22 : 28), bgY, 24, 14, bgColors[i]);
                sw.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal((bgColors[i] == null
                                ? "移除背景（无）" : (i >= 8 ? "最近使用: " : "背景色 ") + bgColors[i])
                                + "（Shift+点击 = 收藏到自定义色板）")));
                this.addRenderableWidget(sw);
            }
            // 自定义收藏色板（第二行左起，最多 8 格；点击应用，Shift+点击移除收藏）
            java.util.List<String> palette = WorldBackgroundEditor.get().worldPaletteColors();
            for (int i = 0; i < palette.size() && i < 8; i++) {
                BgSwatch sw = new BgSwatch(x0 + 4 + i * (this.narrow ? 22 : 28), bgY + this.rBase, 24, 14, palette.get(i), true);
                sw.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("收藏色 " + palette.get(i) + "（点击应用 · Shift+点击移除收藏）")));
                this.addRenderableWidget(sw);
            }
            // 面板边框开关（background.border 增删；Shift+点击 = 辉光颜色循环；Shift+双击 = 辉光强度循环）
            this.borderBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("边框"), btn -> {
                        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                            long now = System.currentTimeMillis();
                            if (now - this.lastShiftClickAt < 350) {
                                ClientController.get().cycleWorldPanelBorderGlowSize();
                                this.lastShiftClickAt = 0;
                            } else {
                                ClientController.get().cycleWorldPanelBorderGlow();
                                this.lastShiftClickAt = now;
                            }
                        } else if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                            ClientController.get().cycleWorldPanelBorderColor();
                        } else {
                            ClientController.get().toggleWorldPanelBorder();
                        }
                    })
                    .bounds(x0 + bgColors.length * 28 + (this.narrow ? 4 : 8), bgY - 3, this.narrow ? 44 : 56, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("边框开/关；Shift+点击 = 辉光颜色；Shift+双击 = 辉光强度；Ctrl+点击 = 边框色 6 色循环")))
                    .build();
            this.addRenderableWidget(this.borderBtn);
            // 面板透明度档（淡 0.5 / 中 0.8 / 实 1.0）
            float[] alphas = {0.5F, 0.8F, 1.0F};
            String[] alphaLabels = {"淡", "中", "实"};
            this.alphaBtns = new net.minecraft.client.gui.components.Button[alphas.length];
            for (int i = 0; i < alphas.length; i++) {
                final float a = alphas[i];
                this.alphaBtns[i] = net.minecraft.client.gui.components.Button.builder(
                        Component.literal("α" + alphaLabels[i]), btn ->
                                ClientController.get().setWorldPanelAlpha(a))
                        .bounds(x0 + bgColors.length * 28 + (this.narrow ? 48 : 68) + i * (this.narrow ? 40 : 48),
                                bgY - 3, this.narrow ? 36 : 44, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("面板透明度档位；按住拖拽 = 微调（20%~100%，可撤消）")))
                        .build();
                this.addRenderableWidget(this.alphaBtns[i]);
            }
            // 面板背景渐变开关（background.gradient 增删）
            this.gradientBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("渐变"), btn ->
                            WorldBackgroundEditor.get().toggleWorldPanelGradient())
                    .bounds(x0 + bgColors.length * 28 + (this.narrow ? 168 : 212), bgY - 3,
                            this.narrow ? 48 : 54, 20).build();
            this.addRenderableWidget(this.gradientBtn);
            // 自定义背景色 hex 输入格（#RRGGBB / #AARRGGBB，回车应用）
            this.hexBox = new net.minecraft.client.gui.components.EditBox(this.font,
                    x0 + bgColors.length * 28 + (this.narrow ? 216 : 270), bgY - 3,
                    this.narrow ? 64 : 84, 20,
                    Component.literal("hex"));
            this.hexBox.setMaxLength(9);
            this.hexBox.setFilter(s -> s.matches("[#0-9a-fA-F]*"));
            this.hexBox.setHint(Component.literal("#hex / #aahex"));
            this.addRenderableWidget(this.hexBox);
            // 背景色透明度循环（color 的 AARRGGBB 前缀；FF→CC→99→66→33→FF；可撤消；第二行收藏色板右侧）
            this.bgAlphaBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("色α"), btn ->
                            WorldBackgroundEditor.get().cycleWorldBackgroundAlpha())
                    .bounds(x0 + (this.narrow ? 178 : 236), bgY + this.rBase, this.narrow ? 40 : 44, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("背景色透明度循环 FF→CC→99→66→33→FF（AARRGGBB 前缀；可撤消；按住连续循环）")))
                    .build();
            this.addRenderableWidget(this.bgAlphaBtn);
            // 渐变方向循环（上下 ⇄ 左右；第二行首；Shift=互换；Ctrl=中段色；Ctrl+Shift=中段位置；Alt=预设；Alt+Shift=提亮；Ctrl+Alt=压暗）
            this.gradDirBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("向:上下"), btn -> {
                        if (net.minecraft.client.gui.screens.Screen.hasAltDown()
                                && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                            ClientController.get().nudgeWorldPanelBrightness(true);
                        } else if (net.minecraft.client.gui.screens.Screen.hasAltDown()
                                && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                            ClientController.get().nudgeWorldPanelBrightness(false);
                        } else if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                            // Alt 单击 = 预设循环；Alt 双击（350ms 内）= 随机配色
                            long now = System.currentTimeMillis();
                            if (now - this.lastAltClickAt < 350) {
                                WorldBackgroundEditor.get().randomWorldBackground();
                                this.lastAltClickAt = 0;
                            } else {
                                WorldBackgroundEditor.get().cycleWorldPanelGradientPreset();
                                this.lastAltClickAt = now;
                            }
                        } else if (net.minecraft.client.gui.screens.Screen.hasShiftDown()
                                && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                            WorldBackgroundEditor.get().cycleWorldPanelGradientMidPos();
                        } else if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                            WorldBackgroundEditor.get().swapWorldPanelGradientColors();
                        } else if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                            WorldBackgroundEditor.get().cycleWorldPanelGradientMid();
                        } else {
                            WorldBackgroundEditor.get().cycleWorldPanelGradientDir();
                        }
                    })
                    .bounds(x0 + bgColors.length * 28 + (this.narrow ? 0 : 4), bgY + this.rBase,
                            this.narrow ? 52 : 60, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("点击 = 方向上下⇄左右；Shift = 双色互换；Ctrl = 中段色；Ctrl+Shift = 中段位置；Alt = 预设；Alt 双击 = 随机配色；Alt+Shift = 提亮+10%；Ctrl+Alt = 压暗-10%")))
                    .build();
            this.addRenderableWidget(this.gradDirBtn);
            // 面板背景圆角快捷档（无 0 / 小 0.15 / 中 0.35 / 大 0.7 世界单位；第二行）
            double[] radii = {0, 0.15, 0.35, 0.7};
            String[] radiusLabels = {"无", "小", "中", "大"};
            this.radiusBtns = new net.minecraft.client.gui.components.Button[radii.length];
            for (int i = 0; i < radii.length; i++) {
                final double r = radii[i];
                this.radiusBtns[i] = net.minecraft.client.gui.components.Button.builder(
                        Component.literal("R" + radiusLabels[i]), btn ->
                                ClientController.get().setWorldPanelRadius(r))
                        .bounds(x0 + bgColors.length * 28 + (this.narrow ? 56 : 68) + i * (this.narrow ? 46 : 52),
                                bgY + this.rBase, this.narrow ? 44 : 48, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("圆角档位；按住拖拽 = 微调（0~2，可撤消）")))
                        .build();
                this.addRenderableWidget(this.radiusBtns[i]);
            }
            // 面板背景 padding 快捷档（无 0 / 窄 0.1 / 中 0.25 / 大 0.5 世界单位；第三行）
            double[] pads = {0, 0.1, 0.25, 0.5};
            String[] padLabels = {"无", "窄", "中", "大"};
            this.paddingBtns = new net.minecraft.client.gui.components.Button[pads.length];
            for (int i = 0; i < pads.length; i++) {
                final double p = pads[i];
                this.paddingBtns[i] = net.minecraft.client.gui.components.Button.builder(
                        Component.literal("P" + padLabels[i]), btn ->
                                ClientController.get().setWorldPanelPadding(p))
                        .bounds(x0 + bgColors.length * 28 + (this.narrow ? 56 : 68) + i * (this.narrow ? 46 : 52),
                                bgY + this.rBase + this.rStep, this.narrow ? 44 : 48, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("padding 档位；按住拖拽 = 微调（0~1，可撤消）")))
                        .build();
                this.addRenderableWidget(this.paddingBtns[i]);
            }
            // 面板背景边框宽度档（细 0.01 / 中 0.02 / 粗 0.04 / 特粗 0.08 世界单位；第四行）
            double[] bws = {0.01, 0.02, 0.04, 0.08};
            String[] bwLabels = {"细", "中", "粗", "特"};
            this.borderWidthBtns = new net.minecraft.client.gui.components.Button[bws.length];
            for (int i = 0; i < bws.length; i++) {
                final double t = bws[i];
                this.borderWidthBtns[i] = net.minecraft.client.gui.components.Button.builder(
                        Component.literal("B" + bwLabels[i]), btn ->
                                ClientController.get().setWorldPanelBorderWidth(t))
                        .bounds(x0 + bgColors.length * 28 + (this.narrow ? 56 : 68) + i * (this.narrow ? 46 : 52),
                                bgY + this.rBase + 2 * this.rStep, this.narrow ? 44 : 48, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("边框宽度档位；按住拖拽 = 微调（0.005~0.2，可撤消）")))
                        .build();
                this.addRenderableWidget(this.borderWidthBtns[i]);
            }
            // 吸色器（点按后进入取色模式：点世界任意像素 → 背景色）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("吸色"), btn -> this.picking = true)
                    .bounds(x0 + bgColors.length * 28 + (this.narrow ? 56 : 68) + bws.length * (this.narrow ? 46 : 52) + 4,
                            bgY + this.rBase + 2 * this.rStep, this.narrow ? 52 : 60, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("点击后在世界画面任意位置点按取色为面板背景（Esc 取消）")))
                    .build());
            // 面板淡出距离档（近 4 / 中 8 / 远 16 米 / 关 0；第五行）
            double[] fades = {4, 8, 16, 0};
            String[] fadeLabels = {"近", "中", "远", "关"};
            this.fadeBtns = new net.minecraft.client.gui.components.Button[fades.length];
            for (int i = 0; i < fades.length; i++) {
                final double d = fades[i];
                this.fadeBtns[i] = net.minecraft.client.gui.components.Button.builder(
                        Component.literal("F" + fadeLabels[i]), btn ->
                                ClientController.get().setWorldPanelFadeDistance(d))
                        .bounds(x0 + bgColors.length * 28 + (this.narrow ? 56 : 68) + i * (this.narrow ? 46 : 52),
                                bgY + this.rBase + 3 * this.rStep, 48, 20).build();
                this.addRenderableWidget(this.fadeBtns[i]);
            }
            // 淡出带宽度循环档（陡 1 / 中 3 / 缓 6 米）
            this.fadeRangeBtn = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("带中"), btn ->
                            ClientController.get().cycleWorldPanelFadeRange())
                    .bounds(x0 + bgColors.length * 28 + (this.narrow ? 56 : 68) + fades.length * (this.narrow ? 46 : 52) + 4,
                            bgY + this.rBase + 3 * this.rStep, this.narrow ? 52 : 60, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("淡出带宽度循环：陡 1 / 中 3 / 缓 6 米；按住拖拽 = 微调淡出距离（0.5~12 米）")))
                    .build();
            this.addRenderableWidget(this.fadeRangeBtn);
        }

        /** 数值拖拽微调状态（按住 α/R/P/B/带 按钮横向拖拽：1=圆角 2=padding 3=边框宽 4=淡出距离 5=面板透明度）。 */
        private int scrubKind;
        private double scrubStartX;
        private double scrubBase;
        private net.minecraft.client.gui.components.Button[] alphaBtns;
        /** 紧凑布局行距（小窗口：rBase/rStep 缩小；其余同公式）。 */
        private int rBase = 21;
        private int rStep = 24;
        /** 窄窗口横向紧凑（<1000px 宽：色板步进/控制区偏移/档位步进/控件宽度收缩）。 */
        private boolean narrow;

        /** 鼠标所在行的微调目标：0 = 无，1=圆角 2=padding 3=边框宽 4=淡出距离 5=面板透明度。 */
        private int scrubKindAt(double mouseX, double mouseY) {
            if (this.fadeRangeBtn != null && this.fadeRangeBtn.isHoveredOrFocused()) {
                return 4;
            }
            for (net.minecraft.client.gui.components.Button b : this.radiusBtns) {
                if (b != null && b.isHoveredOrFocused()) {
                    return 1;
                }
            }
            for (net.minecraft.client.gui.components.Button b : this.paddingBtns) {
                if (b != null && b.isHoveredOrFocused()) {
                    return 2;
                }
            }
            for (net.minecraft.client.gui.components.Button b : this.borderWidthBtns) {
                if (b != null && b.isHoveredOrFocused()) {
                    return 3;
                }
            }
            for (net.minecraft.client.gui.components.Button b : this.alphaBtns) {
                if (b != null && b.isHoveredOrFocused()) {
                    return 5;
                }
            }
            return 0;
        }

        private double scrubBaseValue(int kind) {
            var cc = ClientController.get();
            return switch (kind) {
                case 1 -> cc.worldPanelRadius();
                case 2 -> cc.worldPanelPadding();
                case 3 -> cc.worldPanelBorderWidth();
                case 5 -> cc.worldPanelAlpha();
                default -> cc.worldPanelFadeDistance();
            };
        }

        private double scrubStep(int kind) {
            return switch (kind) {
                case 1 -> 0.01;  // 圆角
                case 2 -> 0.005; // padding
                case 3 -> 0.001; // 边框宽度
                case 5 -> 0.005; // 面板透明度
                default -> 0.05; // 淡出距离
            };
        }

        private double scrubClamp(int kind, double v) {
            return switch (kind) {
                case 1 -> Math.max(0, Math.min(2, v));
                case 2 -> Math.max(0, Math.min(1, v));
                case 3 -> Math.max(0.005, Math.min(0.2, v));
                case 5 -> Math.max(0.2, Math.min(1, v));
                default -> Math.max(0.5, Math.min(12, v));
            };
        }

        private void applyScrub(int kind, double v) {
            var cc = ClientController.get();
            v = Math.round(scrubClamp(kind, v) * 1000) / 1000.0;
            switch (kind) {
                case 1 -> cc.setWorldPanelRadius(v, true);
                case 2 -> cc.setWorldPanelPadding(v, true);
                case 3 -> cc.setWorldPanelBorderWidth(v, true);
                case 5 -> cc.setWorldPanelAlpha((float) v, true);
                default -> cc.setWorldPanelFadeDistance(v, true);
            }
        }

        private String scrubLabel(int kind) {
            return switch (kind) {
                case 1 -> "圆角";
                case 2 -> "padding";
                case 3 -> "边框宽度";
                case 5 -> "面板透明度";
                default -> "淡出距离";
            };
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0) {
                int kind = scrubKindAt(mouseX, mouseY);
                if (kind != 0) {
                    if (this.scrubKind != kind) {
                        this.scrubKind = kind;
                        this.scrubStartX = mouseX;
                        this.scrubBase = scrubBaseValue(kind);
                    }
                    applyScrub(kind, this.scrubBase + (mouseX - this.scrubStartX) * scrubStep(kind));
                    return true;
                }
            }
            this.scrubKind = 0;
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && this.scrubKind != 0) {
                int kind = this.scrubKind;
                this.scrubKind = 0;
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§b[OpenDreamCore] §f" + scrubLabel(kind)
                                + ": " + scrubBaseValue(kind) + "（可 Ctrl+Z 撤）"), false);
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        /** 长按连续步进：循环类控件（色条/色α/悬停色）按住 400ms 后每 250ms 触发一次。 */
        @Override
        public void tick() {
            super.tick();
            var mc = Minecraft.getInstance();
            if (mc.screen != this || mc.player == null || !mc.mouseHandler.isLeftPressed()) {
                this.repeatWidget = null;
                return;
            }
            double mx = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
            double my = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
            net.minecraft.client.gui.components.AbstractWidget hovered = null;
            for (var child : this.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.isHoveredOrFocused() && w.isActive() && isRepeatable(w)) {
                    hovered = w;
                    break;
                }
            }
            if (hovered == null) {
                this.repeatWidget = null;
                return;
            }
            if (this.repeatWidget != hovered) {
                this.repeatWidget = hovered;
                this.repeatAt = System.currentTimeMillis() + 400;
                return;
            }
            if (System.currentTimeMillis() >= this.repeatAt) {
                hovered.onClick(mx, my);
                this.repeatAt = System.currentTimeMillis() + 250;
            }
        }

        /** 可长按循环的控件（连续触发有意义的循环/随机类操作）。 */
        private boolean isRepeatable(net.minecraft.client.gui.components.AbstractWidget w) {
            return w instanceof ColorStrip || w == this.bgAlphaBtn || w == this.hoverColorBtn;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // 页面标题读数（顶部；有待写标题金色提示"待保存"）
            int colsT = 2, bwT = 150, bhT = 24, gapT = 10;
            int x0T = this.width / 2 - (colsT * bwT + (colsT - 1) * gapT) / 2;
            int y0T = this.height / 2 - MODES.length / colsT * (bhT + gapT) / 2;
            String ptT = ClientController.get().worldPageTitle();
            String shownT = ptT != null ? ptT + "（待保存）"
                    : (ClientController.get().worldPage == null ? null : ClientController.get().worldPage.title());
            if (shownT == null) {
                shownT = "未命名";
            }
            String pidT = ClientController.get().worldPage == null || ClientController.get().worldPage.id() == null
                    ? "world" : ClientController.get().worldPage.id();
            String tabT = ClientController.get().worldTabActive(pidT);
            String panelT = "";
            if (ClientController.get().worldPanels.size() > 1) {
                int piT = ClientController.get().worldPanels.indexOf(
                        ClientController.get().findWorldPanel(pidT));
                if (piT >= 0) {
                    panelT = "面板 " + (piT + 1) + "/" + ClientController.get().worldPanels.size() + " · ";
                }
            }
            String titleLine = panelT + "标题: " + shownT + (tabT == null ? "" : " · 页签: " + tabT);
            g.drawString(this.font, titleLine, x0T, y0T - 34, ptT != null ? 0xFFFFD54F : 0xFF90A4AE);
            // 镜像按钮悬停 → 世界侧翻转 ghost 预览（Shift+悬停 = 双轴同时预览）
            boolean mhx = this.mirrorXBtn != null && this.mirrorXBtn.isHovered();
            boolean mhy = this.mirrorYBtn != null && this.mirrorYBtn.isHovered();
            boolean dual = net.minecraft.client.gui.screens.Screen.hasShiftDown() && (mhx || mhy);
            ClientController.get().setWorldMirrorPreview("x", dual || mhx);
            ClientController.get().setWorldMirrorPreview("y", dual || mhy);
            // 跨面板参考预览（跨面模式：悬停任一模式按钮显示参考面板选中元素框）
            if (this.crossPanel) {
                boolean crossHover = false;
                for (String m : new String[]{"left", "right", "hcenter", "top", "bottom", "vcenter",
                        "dist_x", "dist_y", "size_w", "size_h", "mirror_x", "mirror_y"}) {
                    net.minecraft.client.gui.components.Button b = this.modeBtns.get(m);
                    if (b != null && b.isHovered()) {
                        crossHover = true;
                        break;
                    }
                }
                ClientController.get().setWorldCrossPreview(crossHover);
            } else {
                ClientController.get().setWorldCrossPreview(false);
            }
            // 分布按钮悬停 → 世界侧分布 ghost 预览
            if (this.distXBtn != null && this.distXBtn.isHovered()) {
                ClientController.get().setWorldDistributeGhost("x", this.elementId);
            } else if (this.distYBtn != null && this.distYBtn.isHovered()) {
                ClientController.get().setWorldDistributeGhost("y", this.elementId);
            } else {
                ClientController.get().setWorldDistributeGhost(null, this.elementId);
            }
            // 统一尺寸按钮悬停 → 世界侧统一尺寸 ghost 预览
            if (this.sizeWBtn != null && this.sizeWBtn.isHovered()) {
                ClientController.get().setWorldSizeGhost("w", this.elementId);
            } else if (this.sizeHBtn != null && this.sizeHBtn.isHovered()) {
                ClientController.get().setWorldSizeGhost("h", this.elementId);
            } else {
                ClientController.get().setWorldSizeGhost(null, this.elementId);
            }
            // 对齐模式按钮悬停 → 高亮可见包围盒（对齐参考基准）
            {
                boolean alignHover = false;
                for (String m : new String[]{"left", "right", "hcenter", "top", "bottom", "vcenter"}) {
                    net.minecraft.client.gui.components.Button btn = this.modeBtns.get(m);
                    if (btn != null && btn.isHovered()) {
                        alignHover = true;
                        break;
                    }
                }
                if (alignHover) {
                    var ccA = ClientController.get();
                    var pgA = ccA.worldPage;
                    if (pgA != null) {
                        double[] b = WorldHologram.visibleBounds(ccA.worldNodes,
                                ccA.worldTabActive(pgA.id()), pgA.variables());
                        ClientController.get().setWorldAlignBoundsPreview(b);
                    }
                } else {
                    ClientController.get().setWorldAlignBoundsPreview(null);
                }
            }
            // 模式按钮当前状态标记（左/右/中/顶/底/中/宽/高/编组 已应用打 ✓）
            if (!this.modeBtns.isEmpty()) {
                var cc3 = ClientController.get();
                var pg3 = cc3.worldPage;
                if (pg3 != null) {
                    java.util.List<String> mems = null;
                    String grp3 = cc3.worldGroupOf(this.elementId);
                    if (grp3 != null && cc3.worldGroupMembers(grp3).size() > 1) {
                        mems = cc3.worldGroupMembers(grp3);
                    } else if (cc3.worldEditMulti.size() >= 2) {
                        mems = new java.util.ArrayList<>(cc3.worldEditMulti);
                    }
                    if (mems == null) {
                        mems = java.util.List.of(this.elementId);
                    }
                    double l0 = Double.NaN, r0 = Double.NaN, cx0 = Double.NaN;
                    double t0 = Double.NaN, b0 = Double.NaN, cy0 = Double.NaN;
                    double w0 = Double.NaN, h0 = Double.NaN;
                    boolean okL = true, okR = true, okCX = true, okT = true, okB = true, okCY = true;
                    boolean okW = true, okH = true;
                    for (String mid : mems) {
                        com.opendreamcore.page.Element el = ClientController.findElement(pg3, mid);
                        if (el == null) {
                            okL = okR = okCX = okT = okB = okCY = okW = okH = false;
                            break;
                        }
                        Object raw = el.props().get("hologram");
                        if (!(raw instanceof Map<?, ?> h)) {
                            okL = okR = okCX = okT = okB = okCY = okW = okH = false;
                            break;
                        }
                        var vars3 = pg3.variables();
                        double x = WorldHologram.holoNum(h, "x", 0, vars3);
                        double y = WorldHologram.holoNum(h, "y", 0, vars3);
                        double w = WorldHologram.holoNum(h, "width",
                                "text".equals(el.type()) ? 2.0 : 1.0, vars3);
                        double hh = WorldHologram.holoNum(h, "height",
                                "text".equals(el.type()) ? 0.25 : 1.0, vars3);
                        double l = x - w / 2, r = x + w / 2;
                        double t = y - hh / 2, b = y + hh / 2;
                        if (Double.isNaN(l0)) {
                            l0 = l; r0 = r; cx0 = x; t0 = t; b0 = b; cy0 = y; w0 = w; h0 = hh;
                        } else {
                            if (Math.abs(l - l0) >= 0.01) okL = false;
                            if (Math.abs(r - r0) >= 0.01) okR = false;
                            if (Math.abs(x - cx0) >= 0.01) okCX = false;
                            if (Math.abs(t - t0) >= 0.01) okT = false;
                            if (Math.abs(b - b0) >= 0.01) okB = false;
                            if (Math.abs(y - cy0) >= 0.01) okCY = false;
                            if (Math.abs(w - w0) >= 0.01) okW = false;
                            if (Math.abs(hh - h0) >= 0.01) okH = false;
                        }
                    }
                    boolean inGroup = grp3 != null && cc3.worldGroupMembers(grp3).size() > 1;
                    for (java.util.Map.Entry<String, net.minecraft.client.gui.components.Button> e : this.modeBtns.entrySet()) {
                        String m = e.getKey();
                        boolean mark = switch (m) {
                            case "left" -> okL;
                            case "right" -> okR;
                            case "hcenter" -> okCX;
                            case "top" -> okT;
                            case "bottom" -> okB;
                            case "vcenter" -> okCY;
                            case "size_w" -> okW;
                            case "size_h" -> okH;
                            case "group", "ungroup" -> inGroup;
                            default -> false;
                        };
                        String base = MODES_LABELS.get(m);
                        if (base != null) {
                            e.getValue().setMessage(Component.literal(base + (mark ? "✓" : "")));
                        }
                    }
                }
            }
            // 边框开关实时状态
            if (this.borderBtn != null) {
                this.borderBtn.setMessage(Component.literal(
                        "边框:" + (ClientController.get().hasWorldPanelBorder() ? "开" : "关")));
            }
            // 渐变开关实时状态
            if (this.gradientBtn != null) {
                this.gradientBtn.setMessage(Component.literal(
                        "渐变:" + (WorldBackgroundEditor.get().hasWorldPanelGradient() ? "开" : "关")));
            }
            // 渐变方向实时回显（含中段色/位置标记）
            if (this.gradDirBtn != null) {
                String mid = WorldBackgroundEditor.get().worldPanelGradientMid();
                double mpos = 0.5;
                Object worldObj2 = ClientController.get().worldPage == null ? null
                        : ClientController.get().worldPage.options().get("world");
                if (worldObj2 instanceof Map<?, ?> w2) {
                    Object bg2 = w2.get("background");
                    if (bg2 instanceof Map<?, ?> bm2 && bm2.get("gradientMidPos") instanceof Number np) {
                        mpos = np.doubleValue();
                    }
                }
                String label = "向:" + (WorldBackgroundEditor.get().worldPanelGradientHorizontal() ? "左右" : "上下");
                if (mid != null) {
                    label += "·中" + (Math.abs(mpos - 0.5) < 0.01 ? "" : String.format(java.util.Locale.ROOT, "%.1f", mpos));
                }
                this.gradDirBtn.setMessage(Component.literal(label));
            }
            // 圆角快捷档实时回显（当前档打 ✓）
            if (this.radiusBtns != null) {
                double cur = ClientController.get().worldPanelRadius();
                double[] radii = {0, 0.15, 0.35, 0.7};
                String[] radiusLabels = {"无", "小", "中", "大"};
                for (int i = 0; i < this.radiusBtns.length; i++) {
                    this.radiusBtns[i].setMessage(Component.literal(
                            "R" + radiusLabels[i] + (Math.abs(cur - radii[i]) < 0.001 ? "✓" : "")));
                }
            }
            // padding 快捷档实时回显（当前档打 ✓；0 档 = 默认 0.25 视为 中 档）
            if (this.paddingBtns != null) {
                double cur = ClientController.get().worldPanelPadding();
                double[] pads = {0, 0.1, 0.25, 0.5};
                String[] padLabels = {"无", "窄", "中", "大"};
                for (int i = 0; i < this.paddingBtns.length; i++) {
                    this.paddingBtns[i].setMessage(Component.literal(
                            "P" + padLabels[i] + (Math.abs(cur - pads[i]) < 0.001 ? "✓" : "")));
                }
            }
            // 边框宽度档实时回显（当前档打 ✓）
            if (this.borderWidthBtns != null) {
                double cur = ClientController.get().worldPanelBorderWidth();
                double[] bws = {0.01, 0.02, 0.04, 0.08};
                String[] bwLabels = {"细", "中", "粗", "特"};
                for (int i = 0; i < this.borderWidthBtns.length; i++) {
                    this.borderWidthBtns[i].setMessage(Component.literal(
                            "B" + bwLabels[i] + (Math.abs(cur - bws[i]) < 0.001 ? "✓" : "")));
                }
            }
            // 淡出距离档实时回显（当前档打 ✓）
            if (this.fadeBtns != null) {
                double cur = ClientController.get().worldPanelFadeDistance();
                double[] fades = {4, 8, 16, 0};
                String[] fadeLabels = {"近", "中", "远", "关"};
                for (int i = 0; i < this.fadeBtns.length; i++) {
                    this.fadeBtns[i].setMessage(Component.literal(
                            "F" + fadeLabels[i] + (Math.abs(cur - fades[i]) < 0.001 ? "✓" : "")));
                }
            }
            // 淡出带宽度实时回显（陡 1 / 中 3 / 缓 6）
            if (this.fadeRangeBtn != null) {
                double fr = ClientController.get().worldPanelFadeRange();
                String frLabel = Math.abs(fr - 1) < 0.01 ? "陡" : (Math.abs(fr - 6) < 0.01 ? "缓" : "中");
                this.fadeRangeBtn.setMessage(Component.literal("带:" + frLabel));
            }
            // 锁定按钮状态实时回显（锁:开/锁:关）
            if (this.lockBtn != null) {
                boolean locked = ClientController.get().worldElementLocked(this.elementId);
                this.lockBtn.setMessage(Component.literal(locked ? "锁:开" : "锁:关"));
            }
            // 吸色模式：不画遮罩，世界画面完全可见便于取色
            if (!this.picking) {
                g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            } else {
                this.pickLiveHex = ClientController.sampleWorldHex(mouseX, mouseY); // 实时取色预览
                g.fill(0, 0, this.width, 24, 0xCC10151F);
                g.fill(0, 22, this.width, 24, 0xFFFFB300);
                String hexNow = this.pickLiveHex == null ? "#??????" : this.pickLiveHex;
                String hint = "点击取色 " + hexNow + " 为背景 · Ctrl+点击 = 元素文本色（多选/组批量） · 右键/Esc 取消";
                g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2,
                        8, 0xFFFFE082);
                if (this.pickLiveHex != null) {
                    try {
                        int rgb = (int) Long.parseLong(this.pickLiveHex.substring(1), 16);
                        g.fill(this.width / 2 + this.font.width(hint) / 2 + 8, 4, 16, 16, 0xFF000000 | rgb);
                    } catch (Exception ignored) {
                        // 色值解析失败不画色块
                    }
                }
            }
            String title2 = this.title.getString();
            var ccT = ClientController.get();
            if (ccT.worldPage != null) {
                title2 = title2 + " · " + (ccT.worldPage.id() == null ? "world" : ccT.worldPage.id());
            }
            if (ccT.worldPanelLocked()) {
                title2 += " · 面锁"; // 面板整体锁定标记
            }
            if (this.crossPanel && ccT.worldCrossAlignAvailable()) {
                String refTab = ccT.worldTabActive(WorldEditor.get().worldLastPanelPid);
                if (refTab != null) {
                    title2 += " · 参考页签: " + refTab;
                }
            }
            if (ClientController.get().worldPendingSummary() != null) {
                title2 += " ●未保存"; // 标题行未保存标记（红点提示）
            }
            if (this.bgSnapshotA != null) {
                title2 += this.bgSnapshotSideA ? " · [A]" : " · [B]";
            }
            g.drawString(this.font, title2, this.width / 2 - this.font.width(title2) / 2,
                    6, 0xFFE0E0E0);
            // 当前值摘要行（选中元素/组/多选 实时回显；操作后 400ms 闪烁反馈）
            boolean flash = this.flashAt > 0 && System.currentTimeMillis() - this.flashAt < 400;
            int sumColor = flash ? 0xFFFFE082 : 0xFF80CBC4;
            var cc = ClientController.get();
            var page = cc.worldPage;
            if (page != null) {
                java.util.List<String> members = null;
                String grp = cc.worldGroupOf(this.elementId);
                if (grp != null && cc.worldGroupMembers(grp).size() > 1) {
                    members = cc.worldGroupMembers(grp);
                } else if (cc.worldEditMulti.size() >= 2) {
                    members = new java.util.ArrayList<>(cc.worldEditMulti);
                }
                if (members != null) {
                    java.util.Set<String> ids = new java.util.HashSet<>(members);
                    double[] b = cc.worldSelectionBounds(ids);
                    if (b != null) {
                        String sum = String.format(java.util.Locale.ROOT,
                                "%d 元素 · 包围盒 %.2f×%.2f · 中心 %.2f, %.2f",
                                members.size(), b[2] - b[0], b[3] - b[1],
                                (b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
                        if (this.opCount > 0) {
                            sum += " · 已操作 " + this.opCount + " 次";
                        }
                        sum += " · 撤消栈 " + WorldEditor.get().worldUndoStack.size() + " 步";
                        int lockedCount = 0;
                        for (String mid : members) {
                            if (ClientController.get().worldElementLocked(mid)) {
                                lockedCount++;
                            }
                        }
                        if (lockedCount > 0) {
                            sum += " · 锁定 " + lockedCount + "/" + members.size();
                        }
                        this.lastSummary = sum;
                        g.drawString(this.font, sum,
                                this.width / 2 - this.font.width(sum) / 2, 20, sumColor);
                    }
                } else {
                    com.opendreamcore.page.Element el = ClientController.findElement(page, this.elementId);
                    if (el != null) {
                        Object raw = el.props().get("hologram");
                        if (raw instanceof Map<?, ?> h) {
                            var vars = page.variables();
                            double sx = WorldHologram.holoNum(h, "x", 0, vars);
                            double sy = WorldHologram.holoNum(h, "y", 0, vars);
                            double sw = WorldHologram.holoNum(h, "width",
                                    "text".equals(el.type()) ? 2.0 : 1.0, vars);
                            double sh = WorldHologram.holoNum(h, "height",
                                    "text".equals(el.type()) ? 0.25 : 1.0, vars);
                            double syaw = WorldHologram.holoNum(h, "yaw", 0, vars);
                            String sum = String.format(java.util.Locale.ROOT,
                                    "x %.2f  y %.2f  w %.2f  h %.2f  yaw %+.1f°", sx, sy, sw, sh, syaw);
                            if (this.opCount > 0) {
                                sum += " · 已操作 " + this.opCount + " 次";
                            }
                            sum += " · 撤消栈 " + WorldEditor.get().worldUndoStack.size() + " 步";
                            if (ClientController.get().worldElementLocked(this.elementId)) {
                                sum += " · 已锁定";
                            }
                            this.lastSummary = sum;
                            g.drawString(this.font, sum,
                                    this.width / 2 - this.font.width(sum) / 2, 20, sumColor);
                        }
                    }
                }
            }
            String sub = "对齐相对可见包围盒 · 组/多选整体操作 · 1-9/0/Q/W/E/R/T 快捷触发 · Esc 关闭";
            var ccS = ClientController.get();
            if (ccS.worldPage != null) {
                String tab = ccS.worldTabActive(ccS.worldPage.id());
                if (tab != null) {
                    sub += " · Tab:" + tab;
                }
                // 面板位置（当前聚焦面板 n/N，多面板同屏时定位）
                if (ccS.worldPanels.size() > 1) {
                    String curPid = ccS.worldPage.id() == null ? "world" : ccS.worldPage.id();
                    int curIdx = 0;
                    for (int i = 0; i < ccS.worldPanels.size(); i++) {
                        String pid2 = ccS.worldPanels.get(i).page.id() == null
                                ? "world" : ccS.worldPanels.get(i).page.id();
                        if (pid2.equals(curPid)) {
                            curIdx = i;
                            break;
                        }
                    }
                    sub += " · 面板 " + (curIdx + 1) + "/" + ccS.worldPanels.size();
                }
            }
            sub += " · B=背景暂存 N=对比";
            g.drawString(this.font, sub, this.width / 2 - 150, this.height / 2 - 78, 0xFF90A4AE);
            int rowsOff = MODES.length / 2; // 与 init 同公式
            int totalW = 2 * 150 + 10;
            int x0 = this.width / 2 - totalW / 2;
            int y0 = this.height / 2 - rowsOff * (24 + 10) / 2;
            int bgY = y0 + rowsOff * (24 + 10) + 8;
            // 大成员数确认窗口期：模式网格闪烁黄框 + 顶部提示（3 秒）
            if (this.confirmAt > 0 && System.currentTimeMillis() - this.confirmAt < 3000) {
                String cmsg = "确认窗口期：3 秒内再次触发执行（" + memberCount() + " 元素）";
                g.drawString(this.font, cmsg, this.width / 2 - this.font.width(cmsg) / 2,
                        32, 0xFFFFB300);
                if (((System.currentTimeMillis() / 200) & 1) == 0) {
                    int rowsC = MODES.length / 2;
                    int gw = 2 * 150 + 10;
                    int gx0 = this.width / 2 - gw / 2;
                    int gy0 = this.height / 2 - rowsC * (24 + 10) / 2;
                    int gh = rowsC * (24 + 10);
                    g.fill(gx0 - 2, gy0 - 2, gx0 + gw + 2, gy0 - 1, 0xFFFFB300);
                    g.fill(gx0 - 2, gy0 + gh + 1, gx0 + gw + 2, gy0 + gh + 2, 0xFFFFB300);
                    g.fill(gx0 - 2, gy0 - 2, gx0 - 1, gy0 + gh + 2, 0xFFFFB300);
                    g.fill(gx0 + gw + 1, gy0 - 2, gx0 + gw + 2, gy0 + gh + 2, 0xFFFFB300);
                }
            } else {
                // 渐变色值摘要（无确认窗口期时显示）+ 渐变条小样
                String gsum = WorldBackgroundEditor.get().worldPanelGradientSummary();
                if (gsum != null) {
                    String gl = "渐变 " + gsum;
                    int gtx = this.width / 2 - this.font.width(gl) / 2;
                    g.drawString(this.font, gl, gtx, 32, 0xFF80CBC4);
                    String[] parts = gsum.split("→");
                    if (parts.length == 2) {
                        int c1 = parseHexColor(parts[0]);
                        int c2 = parseHexColor(parts[1]);
                        if (c1 != 0 && c2 != 0) {
                            int bx = gtx - 26;
                            for (int i = 0; i < 20; i++) {
                                float t = i / 19.0F;
                                int ar = (c1 >> 16) & 0xFF, ag = (c1 >> 8) & 0xFF, ab = c1 & 0xFF;
                                int br = (c2 >> 16) & 0xFF, bg2 = (c2 >> 8) & 0xFF, bb = c2 & 0xFF;
                                int cr = Math.round(ar + (br - ar) * t);
                                int cg = Math.round(ag + (bg2 - ag) * t);
                                int cb = Math.round(ab + (bb - ab) * t);
                                g.fill(bx + i, 32, bx + i + 1, 40,
                                        0xFF000000 | (cr << 16) | (cg << 8) | cb);
                            }
                        }
                    }
                }
            }
            g.drawString(this.font, "背景:", x0 - 42, bgY + 3, 0xFF90A4AE);
            // 背景样式总览四色条控件（底/渐/框/辉；悬停 tooltip 显 hex）
            String[] stripKeys = {"color", "gradient", "border", "borderGlow"};
            String[] stripLabels = {"背景色", "渐变色", "边框色", "辉光色"};
            for (int i = 0; i < 4; i++) {
                final String key = stripKeys[i];
                ColorStrip cs = new ColorStrip(x0 + 4 + i * 24, bgY + this.rBase + 4, 20, 8, key,
                        stripLabels[i]);
                this.addRenderableWidget(cs);
            }
            g.drawString(this.font, "底/渐/框/辉", x0 + 4, bgY + this.rBase + this.rStep - 9, 0xFF78909C);
            g.drawString(this.font, "圆角:", x0 - 42, bgY + this.rBase + 3, 0xFF90A4AE);
            // 圆角视觉小样（14×14 圆角轮廓，R 档实时）
            {
                double rr = ClientController.get().worldPanelRadius();
                int rpix = (int) Math.round(Math.min(5.0, rr * 12));
                int rx2 = x0 + 296, ry2 = bgY + this.rBase + 4;
                g.fill(rx2 + 1, ry2 + 1, rx2 + 13, ry2 + 13, 0xFF1E2A38);
                g.fill(rx2 + 1 + rpix, ry2, rx2 + 13 - rpix, ry2 + 1, 0xFF90A4AE);
                g.fill(rx2 + 1 + rpix, ry2 + 13, rx2 + 13 - rpix, ry2 + 14, 0xFF90A4AE);
                g.fill(rx2, ry2 + 1 + rpix, rx2 + 1, ry2 + 13 - rpix, 0xFF90A4AE);
                g.fill(rx2 + 13, ry2 + 1 + rpix, rx2 + 14, ry2 + 13 - rpix, 0xFF90A4AE);
            }
            g.drawString(this.font, "填充:", x0 - 42, bgY + this.rBase + this.rStep + 3, 0xFF90A4AE);
            g.drawString(this.font, "边宽:", x0 - 42, bgY + this.rBase + 2 * this.rStep + 3, 0xFF90A4AE);
            g.drawString(this.font, "淡出:", x0 - 42, bgY + this.rBase + 3 * this.rStep + 3, 0xFF90A4AE);
            // 模式按钮键位角标（右下角小字 1-9/0/Q/W/E/R）
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "Q", "W", "E", "R", "T"};
            int kRows = MODES.length / 2;
            int kx0 = this.width / 2 - (2 * 150 + 10) / 2;
            int ky0 = this.height / 2 - kRows * (24 + 10) / 2;
            for (int i = 0; i < MODES.length; i++) {
                int bx = kx0 + (i % 2) * (150 + 10);
                int by = ky0 + (i / 2) * (24 + 10);
                g.drawString(this.font, keys[i],
                        bx + 150 - this.font.width(keys[i]) - 2, by + 16, 0x6680CBC4);
            }
            // 键位速查面板（? 按钮开关；Esc 关闭）
            if (this.helpOpen) {
                int hpw = 430;
                int hph = HELP_LINES.length * 10 + 28;
                int hx = this.width / 2 - hpw / 2;
                int hy = this.height / 2 - hph / 2;
                g.fill(hx - 4, hy - 4, hx + hpw + 4, hy + hph + 4, 0xEE000000);
                g.fill(hx - 4, hy - 4, hx + hpw + 4, hy - 2, 0xFFFFB300);
                g.drawString(this.font, "键位速查（Esc 关闭）", hx, hy + 2, 0xFFFFD54F);
                for (int i = 0; i < HELP_LINES.length; i++) {
                    g.drawString(this.font, HELP_LINES[i], hx + 6, hy + 16 + i * 10, 0xFFE0E0E0);
                }
            }
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        /** 当前摘要读数（Ctrl+点击复制到剪贴板）。 */
        private String lastSummary;
        /** 会话操作统计（类别 → 次数；关闭时汇总报告）。 */
        private final java.util.Map<String, Integer> opStats = new java.util.LinkedHashMap<>();
        /** 会话内操作统计（模式名 → 次数；关闭时明细报告用）。 */
        private final java.util.Map<String, Integer> modeStats = new java.util.LinkedHashMap<>();
        /** 会话涉及元素（去重；统计报告用）。 */
        private final java.util.Set<String> touchedElements = new java.util.HashSet<>();
        /** 打开时的页面级操作计数基线（关闭时差值 = 本次会话背景/面板操作数）。 */
        private final int sessionBgBase;
        /** 会话内面板切换次数（◀面板/面板▶）。 */
        private int panelSwitchCount;
        /** 会话内页签切换次数（◀/▶）。 */
        private int tabSwitchCount;
        /** 会话内跨面操作次数（跨面开启时的模式操作）。 */
        private int crossOpCount;

        /** 模式 → 统计类别。 */
        private static String opCategory(String mode) {
            if ("yaw".equals(mode)) {
                return "旋转";
            }
            if (mode.startsWith("dist_")) {
                return "分布";
            }
            if (mode.startsWith("size_")) {
                return "尺寸";
            }
            if (mode.startsWith("mirror_")) {
                return "镜像";
            }
            if ("group".equals(mode) || "ungroup".equals(mode)) {
                return "编组";
            }
            return "对齐";
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Ctrl+点击摘要行 = 复制读数到剪贴板（坐标/尺寸/统计）
            if (button == 0 && net.minecraft.client.gui.screens.Screen.hasControlDown()
                    && this.lastSummary != null && mouseY >= 16 && mouseY <= 34) {
                Minecraft.getInstance().keyboardHandler.setClipboard(this.lastSummary);
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§a[OpenDreamCore] §f摘要已复制: " + this.lastSummary), false);
                return true;
            }
            if (this.picking && (button == 0 || button == 1)) {
                if (button == 0) {
                    String hex = this.pickLiveHex != null
                            ? this.pickLiveHex : ClientController.sampleWorldHex(mouseX, mouseY);
                    this.picking = false;
                    if (hex != null) {
                        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                            // Ctrl = 取到元素文本色（多选/组 = 批量逐一套用）
                            java.util.List<String> targets = new java.util.ArrayList<>();
                            String grp2 = ClientController.get().worldGroupOf(this.elementId);
                            if (grp2 != null && ClientController.get().worldGroupMembers(grp2).size() > 1) {
                                targets.addAll(ClientController.get().worldGroupMembers(grp2));
                            } else if (ClientController.get().worldEditMulti.size() >= 2) {
                                targets.addAll(ClientController.get().worldEditMulti);
                            }
                            if (targets.size() >= 2) {
                                ClientController.get().setWorldElementColorBatch(targets, hex);
                            } else {
                                ClientController.get().setWorldElementColor(this.elementId, hex);
                            }
                        } else {
                            WorldBackgroundEditor.get().setWorldPanelBackground(hex);
                        }
                    }
                } else {
                    this.picking = false; // 右键取消吸色
                }
                return true;
            }
            // 点按钮/输入格以外区域：命中世界元素 = 重选对齐目标（保持打开）；空白 = 关屏（该点击透传给世界）
            boolean hitWidget = false;
            for (var child : this.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.isHoveredOrFocused()) {
                    hitWidget = true;
                    break;
                }
            }
            if (!hitWidget) {
                if (ClientController.get().pickWorldElementAt(mouseX, mouseY)) {
                    this.elementId = WorldEditor.get().worldEditSelected;
                    this.flashAt = System.currentTimeMillis();
                    return true;
                }
                this.onClose();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        /** 执行一个对齐/分布/镜像/编组模式（按钮与数字键共用）；应用后保持打开便于链式对齐，Esc 关闭。 */
        private void runMode(String mode) {
            // 大成员数保护：>20 元素需 3 秒内再次触发确认
            int n = memberCount();
            if (n > 20 && System.currentTimeMillis() - this.confirmAt > 3000) {
                this.confirmAt = System.currentTimeMillis();
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f该操作将影响 " + n
                                + " 个元素（>20），3 秒内再次触发确认执行"), false);
                return;
            }
            this.confirmAt = 0;
            this.flashAt = System.currentTimeMillis(); // 摘要闪烁反馈
            this.opCount++; // 会话内操作计数
            this.opStats.merge(opCategory(mode), 1, Integer::sum);
            this.modeStats.merge(mode, 1, Integer::sum);
            if (this.crossPanel) {
                this.crossOpCount++; // 跨面操作计数
            }
            if (WorldEditor.get().worldEditSelected != null) {
                this.touchedElements.add(WorldEditor.get().worldEditSelected);
            }
            if (this.crossPanel && ("left".equals(mode) || "right".equals(mode) || "hcenter".equals(mode)
                    || "top".equals(mode) || "bottom".equals(mode) || "vcenter".equals(mode))) {
                // 跨面板对齐：对齐到上次聚焦面板的选中元素
                WorldEditor.get().alignWorldCross(mode);
            } else if (this.crossPanel && mode.startsWith("dist_")) {
                // 跨面板分布：分布到上次聚焦面板的可见范围
                WorldEditor.get().distributeWorldCross(mode.equals("dist_x") ? "x" : "y");
            } else if (this.crossPanel && mode.startsWith("mirror_")) {
                // 跨面板镜像：绕上次聚焦面板的可见范围中心轴
                WorldEditor.get().mirrorWorldCross(mode.equals("mirror_x") ? "x" : "y");
            } else if (this.crossPanel && mode.startsWith("size_")) {
                // 跨面板统一尺寸：统一为上次聚焦面板选中元素的值
                ClientController.get().unifyWorldSizeCross(mode.equals("size_w") ? "w" : "h");
            } else if (this.crossPanel && "yaw".equals(mode)) {
                // 跨面板统一旋转：yaw 对齐到参考面板选中元素
                ClientController.get().alignWorldYawCross();
            } else if (this.scopeAll && ("left".equals(mode) || "right".equals(mode) || "hcenter".equals(mode)
                    || "top".equals(mode) || "bottom".equals(mode) || "vcenter".equals(mode))) {
                // 范围=全部：六个对齐模式应用到全部可见元素（整体包围盒对齐）
                WorldEditor.get().alignWorldAll(mode);
            } else if (this.scopeAll && "yaw".equals(mode)) {
                // 范围=全部：统一旋转应用到全部可见元素（基准 = 首个未锁定元素）
                ClientController.get().alignWorldYawAll();
            } else if (mode.startsWith("dist_")) {
                WorldEditor.get().distributeWorldGroup(elementId, mode.equals("dist_x") ? "x" : "y");
            } else if (mode.startsWith("size_")) {
                ClientController.get().unifyWorldSize(elementId, mode.equals("size_w") ? "w" : "h");
            } else if (mode.startsWith("mirror_")) {
                WorldEditor.get().mirrorWorldSelection(elementId, mode.equals("mirror_x") ? "x" : "y");
            } else if ("group".equals(mode)) {
                ClientController.get().groupWorldSelection(elementId);
            } else if ("ungroup".equals(mode)) {
                ClientController.get().ungroupWorldSelection(elementId);
            } else if ("yaw".equals(mode)) {
                WorldEditor.get().alignWorldYaw();
            } else {
                WorldEditor.get().alignWorldElement(elementId, mode);
            }
        }

        /** 当前操作影响的成员数（组/多选/单选）。 */
        private int memberCount() {
            var cc = ClientController.get();
            String grp = cc.worldGroupOf(this.elementId);
            if (grp != null && cc.worldGroupMembers(grp).size() > 1) {
                return cc.worldGroupMembers(grp).size();
            }
            if (cc.worldEditMulti.size() >= 2) {
                return cc.worldEditMulti.size();
            }
            return 1;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && this.picking) {
                this.picking = false;
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && this.helpOpen) {
                this.helpOpen = false; // 先关速查，再关屏
                return true;
            }
            // Tab：hex 聚焦时先退出输入（回到全控件焦点环）；否则走原版焦点环（全部控件循环）
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                if (this.hexBox != null && this.hexBox.isFocused()) {
                    this.hexBox.setFocused(false);
                    this.setFocused(null);
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (this.hexBox != null && this.hexBox.isFocused()
                    && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
                if (this.renamePending) {
                    WorldBackgroundEditor.get().renameWorldBackgroundPreset(this.hexBox.getValue().trim());
                    this.renamePending = false;
                    this.hexBox.setValue("");
                    this.hexBox.setFocused(false);
                    this.setFocused(null);
                    this.hexBox.setFilter(s -> s.matches("[#0-9a-fA-F]*"));
                } else {
                    applyHex();
                }
                return true;
            }
            // 数字/字母键快捷触发模式（hex 输入聚焦时让位给输入）
            if (this.hexBox == null || !this.hexBox.isFocused()) {
                // B：暂存当前背景为 A；N：A ⇄ 当前 对比切换
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_B) {
                    if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+B：快照 A 转预设保存
                        if (this.bgSnapshotA != null) {
                            WorldBackgroundEditor.get().saveBackgroundJsonPreset(this.bgSnapshotA);
                        } else {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§e[OpenDreamCore] §f无快照（先 B 暂存）"), false);
                        }
                        return true;
                    }
                    String cur = WorldBackgroundEditor.get().worldBackgroundJson();
                    if (cur != null) {
                        this.bgSnapshotA = cur;
                        this.bgSnapshotSideA = false; // 当前显示 = 原值（B 侧）
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§e[OpenDreamCore] §f已暂存背景 A（N 键对比切换）"), false);
                    } else {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§e[OpenDreamCore] §f面板无背景配置可暂存"), false);
                    }
                    return true;
                }
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_N) {
                    if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+N：清空快照，退出对比模式
                        this.bgSnapshotA = null;
                        this.bgSnapshotSideA = false;
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§e[OpenDreamCore] §f已清空 A/B 快照（退出对比）"), false);
                        return true;
                    }
                    if (this.bgSnapshotA != null) {
                    String cur = WorldBackgroundEditor.get().worldBackgroundJson();
                    java.util.Map<String, Object> snap = ClientController.parseBgJsonObject(this.bgSnapshotA);
                    if (!snap.isEmpty()) {
                        java.util.Map<String, Object> curMap = cur == null
                                ? new java.util.LinkedHashMap<>() : ClientController.parseBgJsonObject(cur);
                        int diff = 0;
                        java.util.List<String> diffKeys = new java.util.ArrayList<>();
                        java.util.Set<String> keys = new java.util.LinkedHashSet<>(snap.keySet());
                        keys.addAll(curMap.keySet());
                        for (String k : keys) {
                            if (!java.util.Objects.equals(snap.get(k), curMap.get(k))) {
                                diff++;
                                diffKeys.add(k);
                            }
                        }
                        Map<String, Object> options = ClientController.get().worldPage.options();
                        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
                        if (options.get("world") instanceof Map<?, ?> w) {
                            w.forEach((k, v) -> world.put(String.valueOf(k), v));
                        }
                        ClientController.get().pushWorldBackgroundUndo("对比切换", "bg:compare"); // N 切换可撤消
                        world.put("background", snap);
                        options.put("world", world);
                        this.bgSnapshotA = cur; // 原当前值存入 A，下次 N 切回
                        this.bgSnapshotSideA = !this.bgSnapshotSideA;
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§b[OpenDreamCore] §f已切换背景（差异 "
                                        + diff + " 键: " + String.join(",", diffKeys)
                                        + "；N 再切回）"), false);
                        return true;
                    }
                }
                }
                // Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y：链式对齐中直通世界撤消/重做
                if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+C：复制元素格式（格式刷：props+actions，不含定位键）
                        WorldEditor.get().copyWorldElementFormat();
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+V：粘贴元素格式（多选/组批量；仅同类型）
                        WorldEditor.get().pasteWorldElementFormat();
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_E
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+E：复制元素完整 YAML 块
                        WorldEditor.get().copyWorldElementYaml();
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_G
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+G：从 YAML 剪贴板粘贴为新元素（id 冲突自动加后缀）
                        WorldEditor.get().pasteWorldElementYaml();
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+A：跨面板锚点对齐（当前面板锚点 → 参考面板锚点）
                        WorldEditor.get().alignWorldAnchorCross();
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_T
                            && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                        // Ctrl+Shift+T：保存当前选中集为命名模板
                        WorldEditAlignScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                                "模板名（1~32 字符 · 保存当前选中集）", "",
                                v -> WorldEditor.get().saveWorldTemplate(v)));
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
                        // Ctrl+C：复制 YAML 片段；Alt+Ctrl+C：复制 JSON 片段（插件端配置用）
                        String yaml;
                        if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0) {
                            yaml = WorldBackgroundEditor.get().worldBackgroundJson();
                            if (yaml != null) {
                                Minecraft.getInstance().keyboardHandler.setClipboard(yaml);
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§a[OpenDreamCore] §f背景 JSON 已复制到剪贴板"), false);
                            } else {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§e[OpenDreamCore] §f面板无背景配置"), false);
                            }
                        } else {
                            yaml = WorldBackgroundEditor.get().worldBackgroundYaml();
                            if (yaml != null) {
                                Minecraft.getInstance().keyboardHandler.setClipboard(yaml);
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§a[OpenDreamCore] §f背景配置已复制到剪贴板（粘贴到页面 world 段）"), false);
                            } else {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§e[OpenDreamCore] §f面板无背景配置"), false);
                            }
                        }
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
                        // Ctrl+V：从剪贴板粘贴背景配置（hex 聚焦时让位给文本粘贴）
                        if (this.hexBox == null || !this.hexBox.isFocused()) {
                            WorldBackgroundEditor.get().pasteWorldBackgroundFromClipboard();
                            return true;
                        }
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
                        // Alt+Ctrl+S：保存背景预设到文件
                        if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0) {
                            WorldBackgroundEditor.get().saveWorldBackgroundPreset();
                            return true;
                        }
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_L) {
                        // Alt+Ctrl+L：载入/循环预设；Alt+Shift+Ctrl+L：删除当前预设
                        if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0) {
                            if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                                WorldBackgroundEditor.get().deleteWorldBackgroundPreset();
                            } else {
                                WorldBackgroundEditor.get().loadWorldBackgroundPreset();
                            }
                            return true;
                        }
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
                        // Alt+Ctrl+R：重命名当前预设（hex 格复用输入，回车提交）
                        if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0) {
                            this.renamePending = true;
                            if (this.hexBox != null) {
                                this.hexBox.setValue("");
                                this.hexBox.setFilter(s -> true); // 预设名允许任意字符
                                this.hexBox.setFocused(true);
                                this.setFocused(this.hexBox);
                                this.hexBox.setHint(Component.literal("预设名"));
                            }
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§e[OpenDreamCore] §f输入预设名后回车提交"), false);
                            return true;
                        }
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
                        if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                            WorldEditor.get().redoWorldEdit();
                        } else {
                            WorldEditor.get().undoWorldEdit();
                        }
                        this.flashAt = System.currentTimeMillis(); // 撤/重做也闪摘要
                        return true;
                    }
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
                        WorldEditor.get().redoWorldEdit();
                        this.flashAt = System.currentTimeMillis();
                        return true;
                    }
                }
                int idx = -1;
                if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
                    idx = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_0) {
                    idx = 9;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Q) {
                    idx = 10;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
                    idx = 11;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_E) {
                    idx = 12;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
                    idx = 13;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_T) {
                    idx = 14; // 统一旋转（Turn）
                }
                if (idx >= 0 && idx < MODES.length) {
                    runMode(MODES[idx][0]);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        /** hex 输入应用（#RGB / #RRGGBB / #AARRGGBB；不合法则清空重输；hexTargetKey 非空时写回对应背景键）。 */
        private void applyHex() {
            String text = this.hexBox.getValue().trim();
            if (!text.startsWith("#")) {
                text = "#" + text;
            }
            if (text.matches("#[0-9a-fA-F]{3}")) { // #RGB 缩写展开
                text = "#" + text.substring(1, 2).repeat(2) + text.substring(2, 3).repeat(2)
                        + text.substring(3, 4).repeat(2);
            }
            if (text.matches("#[0-9a-fA-F]{6,8}")) {
                if (this.hexTargetKey != null) {
                    WorldBackgroundEditor.get().setWorldBackgroundKeyValue(this.hexTargetKey, text);
                    this.hexTargetKey = null;
                    this.hexBox.setFilter(s -> s.matches("[#0-9a-fA-F]*"));
                } else {
                    WorldBackgroundEditor.get().setWorldPanelBackground(text);
                }
                this.hexBox.setValue(""); // 保持焦点：连续输入多个 hex（Esc/点击别处退出）
            } else {
                this.hexBox.setValue("#");
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            ClientController.get().setWorldMirrorPreview("x", false);
            ClientController.get().setWorldMirrorPreview("y", false);
            ClientController.get().setWorldDistributeGhost(null, this.elementId);
            ClientController.get().setWorldAlignBoundsPreview(null);
            ClientController.get().setWorldCrossPreview(false);
            String pending = ClientController.get().worldPendingSummary(); // 关闭时未保存提示
            String report = null;
            java.util.List<String> detailLines = new java.util.ArrayList<>();
            if (this.opCount > 0) { // 会话统计报告
                StringBuilder sb = new StringBuilder("会话统计: ");
                this.opStats.forEach((k, v) -> sb.append(k).append(' ').append(v).append(" 次 · "));
                int bgOps = ClientController.get().worldBgOpCount() - this.sessionBgBase;
                if (bgOps > 0) {
                    sb.append("背景/面板 ").append(bgOps).append(" 次 · ");
                }
                if (this.panelSwitchCount > 0) {
                    sb.append("面板 ").append(this.panelSwitchCount).append(" 次 · ");
                }
                if (this.tabSwitchCount > 0) {
                    sb.append("页签 ").append(this.tabSwitchCount).append(" 次 · ");
                }
                if (this.crossOpCount > 0) {
                    sb.append("跨面 ").append(this.crossOpCount).append(" 次 · ");
                }
                sb.append("共 ").append(this.opCount + Math.max(0, bgOps)).append(" 次 · 涉及 ")
                        .append(this.touchedElements.size()).append(" 个元素 · 撤消栈 ")
                        .append(WorldEditor.get().worldUndoStack.size()).append(" 步");
                report = sb.toString();
                // 明细行 1：模式分布（逐模式展开）
                if (!this.modeStats.isEmpty()) {
                    StringBuilder md = new StringBuilder("明细 · 模式: ");
                    boolean first = true;
                    for (java.util.Map.Entry<String, Integer> e : this.modeStats.entrySet()) {
                        if (!first) {
                            md.append(" · ");
                        }
                        md.append(MODES_LABELS.getOrDefault(e.getKey(), e.getKey()))
                                .append(' ').append(e.getValue());
                        first = false;
                    }
                    detailLines.add(md.toString());
                }
                // 明细行 2：涉及元素（超 12 个截断显示）
                if (!this.touchedElements.isEmpty()) {
                    StringBuilder el = new StringBuilder("明细 · 元素: ");
                    int shown = 0;
                    for (String id : this.touchedElements) {
                        if (shown >= 12) {
                            el.append(" …等 ").append(this.touchedElements.size()).append(" 个");
                            break;
                        }
                        if (shown > 0) {
                            el.append(", ");
                        }
                        el.append(id);
                        shown++;
                    }
                    detailLines.add(el.toString());
                }
            }
            super.onClose();
            if (report != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§b[OpenDreamCore] §f" + report), false);
                for (String line : detailLines) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§7[OpenDreamCore] §f" + line), false);
                }
                if (!detailLines.isEmpty()) {
                    // 完整明细（含全部涉及元素）自动复制到剪贴板
                    StringBuilder full = new StringBuilder(report);
                    for (String line : detailLines) {
                        full.append('\n').append(line);
                    }
                    Minecraft.getInstance().keyboardHandler.setClipboard(full.toString());
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§7[OpenDreamCore] §f完整明细已复制到剪贴板"), false);
                }
            }
            if (pending != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§e[OpenDreamCore] §f" + pending), false);
            }
        }

        /** 面板背景色块：点击即应用（null = 移除背景；运行时生效）；Shift+点击 = 收藏/移除收藏。 */
        private final class BgSwatch extends net.minecraft.client.gui.components.AbstractWidget {
            private final String color;
            /** true = 自定义收藏格（Shift+点击 = 移除收藏；主行 Shift+点击 = 收藏）。 */
            private final boolean custom;

            BgSwatch(int x, int y, int w, int h, String color) {
                this(x, y, w, h, color, false);
            }

            BgSwatch(int x, int y, int w, int h, String color, boolean custom) {
                super(x, y, w, h, Component.literal(color == null ? "无" : color));
                this.color = color;
                this.custom = custom;
            }

            @Override
            protected void renderWidget(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY,
                                        float partialTick) {
                // 当前背景色白圈高亮（忽略 alpha 比较；无背景时"无"格亮）
                String cur = WorldBackgroundEditor.get().worldPanelBackgroundColor();
                boolean active = this.color == null
                        ? cur == null
                        : cur != null && cur.equals(this.color.toUpperCase(java.util.Locale.ROOT));
                if (active) {
                    g.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.getWidth() + 1,
                            this.getY() + this.getHeight() + 1, 0xFFFFFFFF);
                }
                if (this.color == null) {
                    g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                            this.getY() + this.getHeight(), 0xFF263238);
                    g.drawString(Minecraft.getInstance().font, "无",
                            this.getX() + (this.getWidth() - 8) / 2, this.getY() + 3, 0xFF90A4AE);
                } else {
                    int rgb = 0xFF000000 | Integer.parseInt(this.color.substring(1), 16);
                    g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                            this.getY() + this.getHeight(), rgb);
                }
                if (this.isHovered()) {
                    g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                            this.getY() + this.getHeight(), 0x55FFFFFF);
                }
                g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                        this.getY() + 1, 0xFFB0BEC5);
                g.fill(this.getX(), this.getY() + this.getHeight() - 1,
                        this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF78909C);
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
                this.defaultButtonNarrationText(out);
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                    if (this.custom) {
                        WorldBackgroundEditor.get().removeWorldPaletteColor(this.color);
                    } else {
                        WorldBackgroundEditor.get().pinWorldPaletteColor(this.color);
                    }
                    return;
                }
                WorldBackgroundEditor.get().setWorldPanelBackground(this.color);
            }
        }

        /** 背景样式色条：实时读取 background map 对应键，悬停 tooltip 显 hex；点击随机/循环，Shift+点击 = hex 编辑。 */
        private final class ColorStrip extends net.minecraft.client.gui.components.AbstractWidget {
            private final String key;
            private final String label;
            /** 单击时间戳（双击 = 与主色互换）。 */
            private long lastStripClickAt;

            ColorStrip(int x, int y, int w, int h, String key, String label) {
                super(x, y, w, h, Component.literal(label));
                this.key = key;
                this.label = label;
            }

            @Override
            protected void renderWidget(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY,
                                        float partialTick) {
                String hex = currentHex();
                int c = hex == null ? 0xFF263238 : parseHexColor(hex);
                if (c == 0) {
                    c = 0xFF263238;
                }
                g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                        this.getY() + this.getHeight(), c);
                g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                        this.getY() + 1, 0xFF90A4AE);
                // hex 编辑目标：对应色条金框闪烁
                if (hexTargetKey != null && hexTargetKey.equals(this.key)
                        && ((System.currentTimeMillis() / 200) & 1) == 0) {
                    g.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.getWidth() + 1,
                            this.getY(), 0xFFFFB300);
                    g.fill(this.getX() - 1, this.getY() + this.getHeight(),
                            this.getX() + this.getWidth() + 1, this.getY() + this.getHeight() + 1,
                            0xFFFFB300);
                    g.fill(this.getX() - 1, this.getY() - 1, this.getX(),
                            this.getY() + this.getHeight() + 1, 0xFFFFB300);
                    g.fill(this.getX() + this.getWidth(), this.getY() - 1,
                            this.getX() + this.getWidth() + 1, this.getY() + this.getHeight() + 1,
                            0xFFFFB300);
                }
                this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(this.label + (hex == null ? ": 未设置" : ": " + hex)
                                + "（左键随机/循环，双击与主色互换，Shift+左键编辑，右键复制，Alt+左键提亮，Ctrl+Alt+左键压暗）")));
            }

            /** 当前键 hex（未设置 = null）。 */
            private String currentHex() {
                Object worldObj = ClientController.get().worldPage == null ? null
                        : ClientController.get().worldPage.options().get("world");
                Object bg = worldObj instanceof Map<?, ?> ww ? ww.get("background") : null;
                if (bg instanceof Map<?, ?> bm) {
                    Object v = bm.get(this.key);
                    if (v != null) {
                        return String.valueOf(v);
                    }
                } else if (bg != null && "color".equals(this.key)) {
                    return String.valueOf(bg);
                }
                return null;
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 1) {
                    String hex = currentHex();
                    if (hex != null) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(hex);
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a[OpenDreamCore] §f已复制 "
                                        + this.label + ": " + hex), false);
                    } else {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§e[OpenDreamCore] §f" + this.label + " 未设置"), false);
                    }
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
                this.defaultButtonNarrationText(out);
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                long now = System.currentTimeMillis();
                if (now - this.lastStripClickAt < 350) {
                    // 双击（无修饰键）= 该键与主色互换
                    this.lastStripClickAt = 0;
                    if (!"color".equals(this.key)) {
                        WorldBackgroundEditor.get().swapWorldBackgroundKeyWithColor(this.key);
                    }
                    return;
                }
                this.lastStripClickAt = now;
                if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                    // Alt+左键 = 单键提亮；Ctrl+Alt+左键 = 压暗
                    WorldBackgroundEditor.get().nudgeWorldBackgroundKey(this.key,
                            !net.minecraft.client.gui.screens.Screen.hasControlDown());
                    return;
                }
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && hexBox != null) {
                    // Shift+点击：hex 输入格预填该键当前值，回车写回该键
                    hexTargetKey = this.key;
                    Object worldObj = ClientController.get().worldPage == null ? null
                            : ClientController.get().worldPage.options().get("world");
                    Object bg = worldObj instanceof Map<?, ?> ww ? ww.get("background") : null;
                    String hex = null;
                    if (bg instanceof Map<?, ?> bm) {
                        Object v = bm.get(this.key);
                        if (v != null) {
                            hex = String.valueOf(v);
                        }
                    } else if (bg != null && "color".equals(this.key)) {
                        hex = String.valueOf(bg);
                    }
                    hexBox.setValue(hex == null ? "#" : hex);
                    hexBox.setFilter(s -> true);
                    hexBox.setFocused(true);
                    WorldEditAlignScreen.this.setFocused(hexBox);
                    hexBox.setHint(Component.literal("#hex · " + this.label));
                    return;
                }
                if ("color".equals(this.key) || "gradient".equals(this.key)) {
                    WorldBackgroundEditor.get().randomWorldBackgroundKey(this.key);
                } else if ("border".equals(this.key)) {
                    ClientController.get().cycleWorldPanelBorderColor();
                } else {
                    ClientController.get().cycleWorldPanelBorderGlow();
                }
            }
        }
}
