package com.opendreamcore.client;

import com.opendreamcore.client.elements.BadgeIconDraws;
import com.opendreamcore.client.elements.ButtonDraws;
import com.opendreamcore.client.elements.CardDraws;
import com.opendreamcore.client.elements.ChartDraws;
import com.opendreamcore.client.elements.ElementTextUtil;
import com.opendreamcore.client.elements.InputDraws;
import com.opendreamcore.client.elements.MediaItemDraws;
import com.opendreamcore.client.elements.TextElements;
import com.opendreamcore.client.elements.WorldMiscDraws;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.ui.RenderNode;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * OpenDreamCore 页面屏幕：布局树交给 UiRenderer 绘制，交互事件发给服务端裁决。
 */
public final class OdcScreen extends Screen implements UiRenderer.State {

    private final Page page;
    /** 布局树。layoutPage 有缓存会返回共享的不可变列表（List.copyOf），此处必须持私有可变副本，refresh 换引用而非就地改。 */
    private List<RenderNode> nodes;
    private final UiSession session;

    // 交互本地状态（服务端裁决前先本地响应）
    private final Map<String, String> inputText = new LinkedHashMap<>();
    private final Map<String, Double> sliderValue = new LinkedHashMap<>();
    private final Map<String, Boolean> toggleValue = new LinkedHashMap<>();
    private final Map<String, String> dropdownValue = new LinkedHashMap<>();
    private final Map<String, Boolean> dropdownOpen = new LinkedHashMap<>();
    private final Map<String, Double> scrollY = new LinkedHashMap<>();
    private final Map<String, String> areaText = new LinkedHashMap<>();
    private final Map<String, String> suggestionText = new LinkedHashMap<>();
    private final Map<String, Boolean> suggestionOpen = new LinkedHashMap<>();
    /** chat_input 发送历史（会话内，去重相邻重复，上限 50；↑/↓ 浏览）。 */
    private final java.util.List<String> chatHistory = new java.util.ArrayList<>();
    /** 历史浏览下标（-1 = 未在浏览）。 */
    private int chatHistoryIndex = -1;
    /** 输入建议键盘光标（过滤后列表下标；↑/↓ 移动，Enter 选中）。 */
    private final Map<String, Integer> suggestionCursor = new LinkedHashMap<>();
    private final Map<String, Boolean> flipped = new LinkedHashMap<>();
    private final Map<String, long[]> flipAnim = new LinkedHashMap<>(); // id → {startMs, durationMs}
    private final Map<String, Integer> dropdownCursor = new LinkedHashMap<>();
    private final Map<String, Ripple> ripples = new LinkedHashMap<>();
    private String focusedId;

    /** 涟漪点击波纹（元素 ripple 属性触发）。 */
    private record Ripple(double x, double y, long start, int duration, int color, double maxRadius) {
    }

    /** 嵌入页命中：宿主 embed 节点 + 嵌入页 + 嵌入页内节点。 */
    private record EmbedHit(RenderNode embed, com.opendreamcore.page.Page page, RenderNode hit) {
    }

    // tick/resize 生命周期节流
    private long lastTickAt;
    private int lastWidth;
    private int lastHeight;
    private String hoverId;
    private String draggingId;

    // 页面拖拽（options.draggable: true）
    private double offsetX;
    private double offsetY;
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private double dragOriginX;
    private double dragOriginY;

    // 编辑模式（/odc edit on）：显示元素边框，拖动改位置
    private boolean editMode;
    private String selectedId;
    private String editingProp; // 属性面板正在编辑的属性（x/y/z/opacity）
    private String editBuffer;

    // 编辑模式拖拽手势：按住元素 → 相对位移拖动（松手结束；与 HUD/世界编辑器同一语义）
    private boolean editDragArmed;
    private double editDragPressX;
    private double editDragPressY;
    private double editDragOriginX;
    private double editDragOriginY;
    /** 上次方向键微调时间（800ms 内连续微调合并为一次 undo）。 */
    private long lastNudgeAt;
    /** 预览模式（编辑中按 V）：隐藏编辑浮层、放行元素交互与脚本，再按 V 返回编辑。 */
    private boolean previewMode;
        /** 元素名称标签显示（H 键切换；关=只显示选中/悬停的标签）。 */
        private boolean showLabels = false;
    /** 元素级运行时拖拽（dragMode: x/y/both）：玩家可直接拖动带此属性的元素。 */
    private String runtimeDragId;
    private String runtimeDragMode;
    private double runtimeDragPressX;
    private double runtimeDragPressY;
    private double runtimeDragOriginX;
    private double runtimeDragOriginY;

    /** 专业编辑器面板系统。 */
    private EditorPanels editorPanels;

    /** 属性面板几何。 */
    private static final int PANEL_W = 250;
    private static final int PANEL_H = 204;
    private static final int PANEL_ROW_H = 14;

    public OdcScreen(Page page, List<RenderNode> nodes, UiSession session) {
        super(Component.literal(page.title() == null ? "OpenDreamCore" : page.title()));
        this.page = page;
        this.nodes = new ArrayList<>(nodes);
        this.session = session;
        LegacyClientHost.notePageOpened(); // 旧版 取界面存活时间 计时基准
    }

    public Page page() {
        return page;
    }

    public UiSession session() {
        return session;
    }

    /** 服务端状态补丁后：换布局树，保留交互状态（输入框/滑块/开关）。 */
    public void refresh(List<RenderNode> newNodes) {
        if (newNodes == this.nodes) {
            return; // 同一引用（布局缓存命中），无需操作
        }
        // newNodes 可能是 layoutCache 共享的不可变列表，绝不能 clear/addAll 就地改——整体换私有副本
        this.nodes = new ArrayList<>(newNodes);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        String closingId = session != null ? session.sessionId() : null;
        ClientController.get().close();
        if (closingId != null) {
            com.opendreamcore.client.UiRenderer.clearRevealScope(closingId);
        } else if (page != null && page.id() != null) {
            com.opendreamcore.client.UiRenderer.clearRevealScope(page.id());
        }
    }

    // ---------- UiRenderer.State ----------

    @Override
    public String inputText(String id) {
        return inputText.getOrDefault(id, "");
    }

    @Override
    public Double sliderValue(String id) {
        return sliderValue.get(id);
    }

    @Override
    public Boolean toggleValue(String id) {
        return toggleValue.get(id);
    }

    @Override
    public boolean dropdownOpen(String id) {
        return Boolean.TRUE.equals(dropdownOpen.get(id));
    }

    @Override
    public String dropdownValue(String id) {
        return dropdownValue.get(id);
    }

    @Override
    public double scrollY(String id) {
        return scrollY.getOrDefault(id, 0.0);
    }

    @Override
    public String areaText(String id) {
        return areaText.getOrDefault(id, "");
    }

    @Override
    public String suggestionText(String id) {
        return suggestionText.getOrDefault(id, "");
    }

    @Override
    public boolean suggestionOpen(String id) {
        return Boolean.TRUE.equals(suggestionOpen.get(id));
    }

    @Override
    public double flipProgress(String id) {
        long[] anim = flipAnim.get(id);
        if (anim != null) {
            double p = (System.currentTimeMillis() - anim[0]) / (double) Math.max(1, anim[1]);
            return Math.max(0, Math.min(1, p));
        }
        return Boolean.TRUE.equals(flipped.get(id)) ? 1 : 0;
    }

    @Override
    public boolean focused(String id) {
        return focusedId != null && focusedId.equals(id);
    }

    @Override
    public int dropdownCursor(String id) {
        return dropdownCursor.getOrDefault(id, -1);
    }

    @Override
    public int suggestionCursor(String id) {
        return suggestionCursor.getOrDefault(id, -1);
    }

    // ---------- 渲染 ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        AnimationEngine.get().tick(null, page.options(), page.variables());
        tickFlips();
        // 旧版（DreamCore）Functions.preRender：每帧预绘制脚本（存在才跑；编辑模式跳过）
        if ((!editMode || previewMode) && page.functions() != null && page.functions().containsKey("preRender")) {
            ClientController.get().runLifecycle(page, "preRender");
        }
        // 生命周期：tick 每秒一次；resize 窗口尺寸变化时重排
        // （编辑模式隔离：不执行 tick 脚本）
        long now = System.currentTimeMillis();
        if (now - lastTickAt >= 1000) {
            lastTickAt = now;
            if (!editMode || previewMode) {
                ClientController.get().runLifecycle(page, "tick");
            }
        }
        if (lastWidth != 0 && (lastWidth != this.width || lastHeight != this.height)) {
            ClientController.get().refreshCurrent();
            ClientController.get().runLifecycle(page, "resize");
        }
        lastWidth = this.width;
        lastHeight = this.height;
        // 页面背景遮罩（options.background）：缺省 = 0xA0000000 深色；颜色值 = 用该色（#AARRGGBB/#RRGGBB）；false/空 = 无遮罩
        // 容器页面：强制不透明背景（原版 HUD 已被拦截，不需要透出游戏画面）
        Object bg = page.options().get("background");
        int bgColor = 0;
        if (bg instanceof Number || bg instanceof String) {
            bgColor = UiStyle.color(bg, 0);
        }
        if (page.displayMode() == com.opendreamcore.page.DisplayMode.CONTAINER) {
            g.fill(0, 0, this.width, this.height, bgColor != 0 ? bgColor : 0xFF101318);
        } else if (bg == null || Boolean.TRUE.equals(bg)) {
            g.fill(0, 0, this.width, this.height, 0xA0000000);
        } else if (bgColor != 0) {
            g.fill(0, 0, this.width, this.height, bgColor);
        }
        // 屏幕震动：整体偏移
        double[] shake = ClientController.get().shakeOffset();
        CompatRender.posePush(g.pose());
        if (shake != null) {
            CompatRender.poseTranslate(g.pose(), shake[0], shake[1]);
        }
        // 页面可拖动：整体偏移
        CompatRender.poseTranslate(g.pose(), offsetX, offsetY);
        UiRenderer.draw(g, this.font, nodes, mouseX - (int) offsetX, mouseY - (int) offsetY, this, page.variables(),
                page.id());
        if (editMode && !previewMode) {
            drawEditOverlay(g, mouseX - (int) offsetX, mouseY - (int) offsetY);
        }
        CompatRender.posePop(g.pose());
        // 闪屏
        int flash = ClientController.get().flashColor();
        if (flash != 0) {
            g.fill(0, 0, this.width, this.height, flash);
        }
        // 过渡遮罩（淡入淡出）
        double[] transition = ClientController.get().transitionProgress();
        if (transition != null) {
            double alpha = Math.sin(transition[0] * Math.PI);
            int a = (int) (alpha * 220);
            if (a > 0) {
                g.fill(0, 0, this.width, this.height, (a << 24) | ((int) transition[1] & 0xFFFFFF));
            }
        }
        drawRipples(g);
        ClientController.get().renderWorldUi(g); // Boss 条 + 物品提示（页面打开时也显示）
        drawContainerCursor(g, mouseX, mouseY); // 容器槽位拖放：光标物品跟随鼠标
        trackHover(mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    /** 容器光标物品渲染（服务端 container_sync 携带；拾起物品跟随鼠标）。 */
    private void drawContainerCursor(GuiGraphics g, int mouseX, int mouseY) {
        var data = ClientController.get().containerStore().get(ClientController.get().currentSessionId());
        if (data == null || data.cursorItemId() == null || data.cursorItemId().isEmpty()) {
            return;
        }
        int icon = 20;
        int ix = mouseX + 6;
        int iy = mouseY - 2;
        g.fill(ix - 1, iy - 1, ix + icon + 1, iy + icon + 1, 0xCC000000);
        g.fill(ix, iy, ix + icon, iy + icon, 0xFF2A2F3A);
        String raw = data.cursorItemId() + (data.cursorCount() > 1 ? " x" + data.cursorCount() : "");
        UiRenderer.drawItemAt(g, this.font, ix + (icon - 16) / 2.0, iy + (icon - 16) / 2.0, 16, raw, null);
    }

    /** 翻牌动画推进：到点后切换面并移除动画（flipProgress 回落为 0/1）。 */
    private void tickFlips() {
        long now = System.currentTimeMillis();
        var it = flipAnim.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            long[] anim = entry.getValue();
            if (now - anim[0] >= anim[1]) {
                flipped.put(entry.getKey(), !Boolean.TRUE.equals(flipped.get(entry.getKey())));
                it.remove();
            }
        }
    }

    /** 涟漪扩散绘制：从点击点画扩散圆环（淡出），逐行扫描边缘像素。 */
    private void drawRipples(GuiGraphics g) {
        if (ripples.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        var it = ripples.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Ripple r = entry.getValue();
            double p = (now - r.start) / (double) Math.max(1, r.duration);
            if (p >= 1) {
                it.remove();
                continue;
            }
            double radius = Math.max(2, p * r.maxRadius);
            int a = (int) ((1 - p) * ((r.color >>> 24) & 0xFF));
            if (a <= 0) {
                continue;
            }
            int color = (a << 24) | (r.color & 0xFFFFFF);
            for (double dy = -radius; dy <= radius; dy += 1) {
                double dx = Math.sqrt(Math.max(0, radius * radius - dy * dy));
                int y = (int) (r.y + dy);
                g.fill((int) (r.x - dx), y, (int) (r.x - dx + 2), y + 1, color);
                g.fill((int) (r.x + dx - 1), y, (int) (r.x + dx + 1), y + 1, color);
            }
        }
    }

    /** 命中元素后：元素定义 ripple 属性时记录点击波纹。 */
    private void addRipple(RenderNode hit, double mouseX, double mouseY) {
        Object rippleRaw = hit.source() == null ? null : hit.source().props().get("ripple");
        if (!(rippleRaw instanceof Map<?, ?> ripple)) {
            return;
        }
        int color = UiStyle.color(ripple.get("color"), 0x88FFFFFF);
        int duration = ripple.get("duration") instanceof Number n ? n.intValue() : 400;
        double radius = ripple.get("radius") instanceof Number n ? n.doubleValue()
                : Math.max(8, Math.min(hit.width(), hit.height()) / 2);
        ripples.put(hit.id(), new Ripple(mouseX, mouseY, System.currentTimeMillis(),
                Math.max(50, duration), color, Math.max(4, radius)));
    }

    /** flip_card 点击：翻转（本地动画），事件带目标面。 */
    private void toggleFlip(RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "flip_card");
        int duration = spec.get("duration") instanceof Number n ? n.intValue() : 300;
        flipAnim.put(node.id(), new long[]{System.currentTimeMillis(), Math.max(50, duration)});
        send(node, UiEvent.Trigger.CLICK, Boolean.toString(!Boolean.TRUE.equals(flipped.get(node.id()))));
    }

    /** toggle.radio 单选组互斥：同组其它 toggle 一并置 false（本页节点树内查找，含嵌套）。 */
    private void applyRadioGroup(RenderNode hit, boolean next) {
        if (!next || hit.source() == null) {
            return;
        }
        Object raw = hit.source().props().get("toggle");
        Object radioRaw = raw instanceof Map<?, ?> tm ? tm.get("radio") : null;
        if (radioRaw == null) {
            return;
        }
        String group = String.valueOf(radioRaw);
        if (group.isBlank() || "null".equalsIgnoreCase(group)) {
            return;
        }
        for (RenderNode root : nodes) {
            collectRadioOff(root, group, hit.id());
        }
    }

    private void collectRadioOff(RenderNode node, String group, String excludeId) {
        if (!excludeId.equals(node.id()) && node.source() != null) {
            Object raw = node.source().props().get("toggle");
            if (raw instanceof Map<?, ?> tm && group.equals(String.valueOf(tm.get("radio")))) {
                toggleValue.put(node.id(), false);
            }
        }
        for (RenderNode child : node.children()) {
            collectRadioOff(child, group, excludeId);
        }
    }

    /** 编辑模式：元素虚线边框 + id 标签 + 选中高亮 + 专业面板系统。 */
    private void drawEditOverlay(GuiGraphics g, int mouseX, int mouseY) {
        for (RenderNode node : nodes) {
            drawEditNode(g, node);
        }
        // 专业面板系统（调色板 + 元素树 + 属性检查器 + 对齐 + 导出）
        if (editorPanels == null) {
            editorPanels = new EditorPanels(new EditorHost());
        }
        editorPanels.render(g, mouseX, mouseY);
    }

    /** 属性面板：选中元素的 id/type/坐标/层级/透明度，点击数值进入编辑（x/y/z/opacity 可改）。 */
    private void drawPropPanel(GuiGraphics g) {
        int px = this.width - PANEL_W - 6;
        int py = this.height - PANEL_H - 6;
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xE0101418);
        g.fill(px, py, px + PANEL_W, py + 1, 0xFF505868);
        String title = selectedId == null ? "属性面板（点击元素选择）"
                : "属性面板" + (editingProp != null ? "（编辑 " + editingProp + "）" : "");
        g.drawString(this.font, title, px + 6, py + 4, 0xFFFFD54F);
        int rowY = py + 18;
        drawPropRow(g, px, rowY, "id", selectedId == null ? "-" : selectedId, false);
        rowY += PANEL_ROW_H;
        RenderNode node = selectedId == null ? null : findNode(selectedId);
        drawPropRow(g, px, rowY, "type", node == null ? "-" : node.type(), false);
        rowY += PANEL_ROW_H;
        String[] props = {"x", "y", "z", "opacity", "scale", "rotation", "width", "height"};
        for (String prop : props) {
            boolean editable = "width".equals(prop) || "height".equals(prop) ? false : node != null;
            boolean editing = prop.equals(editingProp);
            drawPropRow(g, px, rowY, prop, editing ? editBuffer : propValue(node, prop), editable);
            rowY += PANEL_ROW_H;
        }
        rowY += 2;
        g.drawString(this.font, "Del 删除 | Ctrl+C 复制 | [ ] 调 z | 点数值编辑", px + 6, rowY, 0xFF9AA3B2);
    }

    private void drawPropRow(GuiGraphics g, int px, int y, String label, String value, boolean editable) {
        g.drawString(this.font, label, px + 6, y + 3, 0xFF9AA3B2);
        int vx = px + 62;
        int vw = PANEL_W - 68;
        if (editable) {
            g.fill(vx, y + 1, vx + vw, y + PANEL_ROW_H - 1, 0xFF20242C);
            g.fill(vx, y + 1, vx + vw, y + 2, label.equals(editingProp) ? 0xFF7A8BFF : 0xFF3A4254);
        }
        g.drawString(this.font, value == null ? "" : value, vx + 3, y + 3, 0xFFFFFFFF);
    }

    /** 属性当前值（字符串）。 */
    private String propValue(RenderNode node, String prop) {
        if (node == null) {
            return "-";
        }
        String pageId = page.id() == null ? "page" : page.id();
        switch (prop) {
            case "x", "y" -> {
                Map<String, double[]> overrides = ClientController.get().elementEdits().forPage(pageId);
                if (overrides != null) {
                    double[] pos = overrides.get(selectedId);
                    if (pos != null) {
                        return fmt("x".equals(prop) ? pos[0] : pos[1]);
                    }
                }
                return fmt("x".equals(prop) ? node.x() : node.y());
            }
            case "width" -> {
                return fmt(node.width());
            }
            case "height" -> {
                return fmt(node.height());
            }
            default -> {
                Element element = findElement(selectedId);
                Object value = element == null ? null : element.props().get(prop);
                return value == null ? "0" : String.valueOf(value);
            }
        }
    }

    private static String fmt(double v) {
        return com.opendreamcore.client.screen.EditSpecs.fmt(v);
    }

    /** 属性面板点中哪个属性（值区域，可编辑属性）；没点中返回 null。 */
    private String panelPropAt(double mouseX, double mouseY) {
        if (selectedId == null) {
            return null;
        }
        int px = this.width - PANEL_W - 6;
        int py = this.height - PANEL_H - 6;
        if (mouseX < px || mouseX > px + PANEL_W || mouseY < py || mouseY > py + PANEL_H) {
            return null;
        }
        int rowY = py + 18 + PANEL_ROW_H + PANEL_ROW_H; // 跳过 id/type 两行
        String[] props = {"x", "y", "z", "opacity", "scale", "rotation", "width", "height"};
        for (String prop : props) {
            if ("width".equals(prop) || "height".equals(prop)) {
                rowY += PANEL_ROW_H;
                continue; // 只读
            }
            if (mouseY >= rowY && mouseY < rowY + PANEL_ROW_H && mouseX >= px + 62) {
                return prop;
            }
            rowY += PANEL_ROW_H;
        }
        return null;
    }

    private void beginEditProp(String prop) {
        pushEditUndo();
        editingProp = prop;
        RenderNode node = findNode(selectedId);
        editBuffer = propValue(node, prop);
    }

    /** 撤销/重做历史（实现见 screen/EditHistory）。 */
    private final com.opendreamcore.client.screen.EditHistory editHistory =
            new com.opendreamcore.client.screen.EditHistory();

    private void pushEditUndo() {
        editHistory.push(page.id() == null ? "page" : page.id());
    }

    private void undoEdit() {
        editHistory.undo(page.id() == null ? "page" : page.id());
    }

    private void redoEdit() {
        editHistory.redo(page.id() == null ? "page" : page.id());
    }

    /** 属性编辑实时应用（数字解析失败忽略）。 */
    private void applyLiveProp() {
        if (selectedId == null || editingProp == null || editBuffer == null) {
            return;
        }
        double value;
        try {
            value = Double.parseDouble(editBuffer.trim());
        } catch (NumberFormatException e) {
            return;
        }
        String pageId = page.id() == null ? "page" : page.id();
        if ("x".equals(editingProp) || "y".equals(editingProp)) {
            Map<String, double[]> overrides = ClientController.get().elementEdits().forPage(pageId);
            double[] pos = overrides == null ? null : overrides.get(selectedId);
            RenderNode node = findNode(selectedId);
            double x = pos == null && node != null ? node.x() : pos == null ? 0 : pos[0];
            double y = pos == null && node != null ? node.y() : pos == null ? 0 : pos[1];
            if ("x".equals(editingProp)) {
                x = value;
            } else {
                y = value;
            }
            ClientController.get().elementEdits().set(pageId, selectedId, x, y);
        } else {
            Element element = findElement(selectedId);
            if (element != null) {
                element.props().put(editingProp, value);
            }
        }
        ClientController.get().refreshCurrent();
    }

    /** 删除选中元素（本地编辑记忆持久化；服务端页面仅会话内）。 */
    private void deleteSelected() {
        if (selectedId == null) {
            return;
        }
        String pageId = page.id() == null ? "page" : page.id();
        ClientController.get().elementEdits().markDeleted(pageId, selectedId);
        selectedId = null;
        editingProp = null;
        ClientController.get().refreshCurrent();
    }

    /** 复制选中元素（新 id 加 _copy 后缀，y 下移 20；复制元素追加到页面末尾）。 */
    private void copySelected() {
        Element element = findElement(selectedId);
        if (element == null) {
            return;
        }
        Layout layout = element.layout();
        String newY = layout == null || layout.y() == null ? "20"
                : offsetY(layout.y());
        Element copy = new Element(element.id() + "_copy", element.type(),
                new Layout(layout == null ? null : layout.x(), newY,
                        layout == null ? null : layout.width(), layout == null ? null : layout.height()),
                deepCopy(element.props()), element.visibleWhen(), element.enabledWhen(),
                new LinkedHashMap<>(element.actions()), copyChildren(element), element.parent());
        String pageId = page.id() == null ? "page" : page.id();
        ClientController.get().elementEdits().addCopy(pageId, copy);
        selectedId = element.id() + "_copy";
        ClientController.get().refreshCurrent();
    }

    private static String offsetY(String y) {
        return com.opendreamcore.client.screen.EditSpecs.offsetY(y);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> props) {
        return com.opendreamcore.client.screen.EditSpecs.deepCopy(props);
    }

    /** 复制子元素（id 加 _copy 后缀避免重复）。 */
    private static List<Element> copyChildren(Element element) {
        return com.opendreamcore.client.screen.EditSpecs.copyChildren(element);
    }

    /** 调整选中元素 z 层级（[ 减 / ] 加）。 */
    private void adjustZ(int delta) {
        Element element = findElement(selectedId);
        if (element == null) {
            return;
        }
        Object z = element.props().get("z");
        int value = z instanceof Number n ? n.intValue() : 0;
        element.props().put("z", value + delta);
        ClientController.get().refreshCurrent();
    }

    private void drawEditNode(GuiGraphics g, RenderNode node) {
        com.opendreamcore.client.screen.EditOutline.drawNode(g, node, selectedId, this.font);
    }

    public void setEditMode(boolean on) {
        this.editMode = on;
        this.selectedId = null;
        this.editingProp = null;
        this.editBuffer = null;
        if (on) {
            if (editorPanels == null) {
                editorPanels = new EditorPanels(new EditorHost());
            }
            editorPanels.setCompactMode(true); // 屏幕编辑器也用紧凑模式（面板收起+半透明）
        } else if (editorPanels != null) {
            editorPanels.reset();
        }
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** EditorPanels.Host 实现：桥接 OdcScreen → 编辑器面板。 */
    private final class EditorHost implements EditorPanels.Host {
        @Override public com.opendreamcore.page.Page page() { return page; }
        @Override public List<RenderNode> nodes() { return nodes; }
        @Override public net.minecraft.client.gui.Font font() { return OdcScreen.this.font; }
        @Override public int width() { return OdcScreen.this.width; }
        @Override public int height() { return OdcScreen.this.height; }
        @Override public RenderNode findNode(String id) { return OdcScreen.this.findNode(id); }
        @Override public Element findElement(String id) { return OdcScreen.this.findElement(id); }
        @Override public void selectElement(String id) { selectedId = id; }
        @Override public void refreshCurrent() { ClientController.get().refreshCurrent(); }
        @Override public void pushUndo() { OdcScreen.this.pushEditUndo(); }
        @Override public String editSnapshot() { return ClientController.get().elementEditsSnapshot(page.id() == null ? "page" : page.id()); }
        @Override public void restoreEdit(String json) { ClientController.get().restoreElementEdits(page.id() == null ? "page" : page.id(), json); }
        @Override public void setElementProp(String elementId, String prop, Object value) {
            Element el = findElement(elementId);
            if (el != null) el.props().put(prop, value);
            ClientController.get().refreshCurrent();
        }
        @Override public void setElementPropDeep(String elementId, String prop, Object value) {
            Element el = findElement(elementId);
            if (el != null && prop.contains(".")) {
                String[] parts = prop.split("\\.", 2);
                if (el.props().get(parts[0]) instanceof java.util.Map<?, ?> rawSpec) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> spec = (java.util.Map<String, Object>) rawSpec;
                    spec.put(parts[1], value);
                }
            } else if (el != null) {
                el.props().put(prop, value);
            }
            ClientController.get().refreshCurrent();
        }
        @Override public void setElementPos(String elementId, double x, double y) {
            String pid = page.id() == null ? "page" : page.id();
            ClientController.get().elementEdits().set(pid, elementId, x, y);
            ClientController.get().refreshCurrent();
        }
        @Override public void deleteElement(String elementId) {
            String pid = page.id() == null ? "page" : page.id();
            ClientController.get().elementEdits().markDeleted(pid, elementId);
            selectedId = null;
            ClientController.get().refreshCurrent();
        }
        @Override public void toggleElementHidden(String elementId) {
            String pid = page.id() == null ? "page" : page.id();
            var store = ClientController.get().elementEdits();
            if (store.isHidden(pid, elementId)) {
                store.unmarkHidden(pid, elementId);
            } else {
                pushUndo();
                store.markHidden(pid, elementId);
            }
            ClientController.get().refreshCurrent();
        }
        @Override public void moveElementInTree(String elementId, int direction) {
            moveInPageModel(elementId, direction);
            ClientController.get().refreshCurrent();
        }
        @Override public void reparentElement(String elementId, String newParentId) {
            reparentInPageModel(elementId, newParentId);
            ClientController.get().refreshCurrent();
        }

        /** 在页面模型中移动元素（兄弟排序）。 */
        private void moveInPageModel(String elementId, int direction) {
            List<Element> elements = new ArrayList<>(page.elements());
            if (moveInList(elements, elementId, direction)) {
                replacePageElements(elements);
                return;
            }
            for (int i = 0; i < elements.size(); i++) {
                Element rebuilt = rebuildWithMoved(elements.get(i), elementId, direction);
                if (rebuilt != null) {
                    elements.set(i, rebuilt);
                    replacePageElements(elements);
                    return;
                }
            }
        }

        private boolean moveInList(List<Element> list, String elementId, int direction) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(elementId)) {
                    int target = i + direction;
                    if (target < 0 || target >= list.size()) return false;
                    Element tmp = list.get(i);
                    list.set(i, list.get(target));
                    list.set(target, tmp);
                    return true;
                }
            }
            return false;
        }

        private Element rebuildWithMoved(Element parent, String elementId, int direction) {
            List<Element> children = new ArrayList<>(parent.children());
            if (moveInList(children, elementId, direction)) {
                return rebuildElement(parent, children);
            }
            for (int i = 0; i < children.size(); i++) {
                Element rebuilt = rebuildWithMoved(children.get(i), elementId, direction);
                if (rebuilt != null) {
                    children.set(i, rebuilt);
                    return rebuildElement(parent, children);
                }
            }
            return null;
        }

        private Element rebuildElement(Element original, List<Element> newChildren) {
            return new Element(original.id(), original.type(), original.layout(),
                    original.props(), original.visibleWhen(), original.enabledWhen(),
                    original.actions(), newChildren, original.parent());
        }

        private void replacePageElements(List<Element> elements) {
            page.replaceElements(elements);
        }

        /** 重新挂载父元素。 */
        private void reparentInPageModel(String elementId, String newParentId) {
            List<Element> elements = new ArrayList<>(page.elements());
            Element moved = removeElement(elements, elementId);
            if (moved == null) return;
            if (newParentId == null) {
                elements.add(moved);
            } else {
                for (int i = 0; i < elements.size(); i++) {
                    Element rebuilt = addChildNested(elements.get(i), newParentId, moved);
                    if (rebuilt != null) {
                        elements.set(i, rebuilt);
                        break;
                    }
                }
            }
            replacePageElements(elements);
        }

        private Element removeElement(List<Element> list, String elementId) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(elementId)) {
                    return list.remove(i);
                }
            }
            for (int i = 0; i < list.size(); i++) {
                Element found = removeElement(list.get(i).children(), elementId);
                if (found != null) return found;
            }
            return null;
        }

        private Element addChildNested(Element parent, String targetId, Element child) {
            if (parent.id().equals(targetId)) {
                List<Element> kids = new ArrayList<>(parent.children());
                kids.add(child);
                return rebuildElement(parent, kids);
            }
            List<Element> children = new ArrayList<>();
            boolean found = false;
            for (Element c : parent.children()) {
                Element rebuilt = addChildNested(c, targetId, child);
                children.add(rebuilt != null ? rebuilt : c);
                if (rebuilt != null) found = true;
            }
            return found ? rebuildElement(parent, children) : null;
        }
        @Override public void copyElement(String elementId) {
            OdcScreen.this.copySelected();
        }
        @Override public void addElement(String type, double x, double y) {
            String id = type + "_" + System.currentTimeMillis() % 10000;
            int[] size = defaultSizeFor(type);
            Layout layout = new Layout(String.valueOf((int) x), String.valueOf((int) y),
                    String.valueOf(size[0]), String.valueOf(size[1]));
            Element el = new Element(id, type, layout, defaultPropsFor(type),
                    null, null, new LinkedHashMap<>(), List.of(), null);
            String pid = page.id() == null ? "page" : page.id();
            ClientController.get().elementEdits().addCopy(pid, el);
            selectedId = id;
            ClientController.get().refreshCurrent();
        }
        @Override public String selectedId() { return selectedId; }
    }

    /** 键值对便捷构造（保序）。 */
    private static LinkedHashMap<String, Object> spec(Object... kv) {
        return com.opendreamcore.client.screen.EditSpecs.spec(kv);
    }

    /** 新放置元素的默认尺寸（像素）：按类型给可见的初始大小。实现见 screen/EditSpecs。 */
    static int[] defaultSizeFor(String type) {
        return com.opendreamcore.client.screen.EditSpecs.defaultSizeFor(type);
    }

    /**
     * 新放置元素的默认样式（修复"放的组件不显示组件样式"）：
     * 按类型给一套开箱可见的默认值。实现见 screen/EditSpecs。
     */
    static Map<String, Object> defaultPropsFor(String type) {
        return com.opendreamcore.client.screen.EditSpecs.defaultPropsFor(type);
    }

    /** hover 元素的 tooltip：服务端注册表优先，其次 YAML 静态（字符串 / List 多行 / 对象样式）。
     *  对象形式 {text/content, color/textColor, background, border, width} 与世界 tooltip 同管线
     *  （§ 颜色码富文本 + 可配样式 + 按码宽折行）。 */
    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        RenderNode hit = hit(mouseX, mouseY);
        if (hit == null || hit.source() == null) {
            return;
        }
        // containerTooltip: false → 容器上下文中该元素不显示 tooltip
        if (hit.source().props().containsKey("containerTooltip")
                && !Boolean.parseBoolean(String.valueOf(hit.source().props().get("containerTooltip")))
                && ClientController.get().containerStore()
                        .get(ClientController.get().currentSessionId()) != null) {
            return;
        }
        List<String> lines = null;
        int textColor = 0xFFE0E0E0;
        int background = 0xE610151F;
        int border = 0xFF42A5F5;
        int maxW = 200;
        // 服务端 tooltip（动态，可插值；样式可配：color/background/border/width，缺省默认）
        com.opendreamcore.protocol.message.TooltipRegistry.Entry server =
                ClientController.get().tooltips().get(hit.id());
        if (server != null && server.text() != null && !server.text().isEmpty()) {
            lines = List.of(server.text());
            if (server.color() != null && !server.color().isEmpty()) {
                textColor = UiStyle.color(server.color(), textColor);
            }
            if (server.background() != null && !server.background().isEmpty()) {
                background = UiStyle.color(server.background(), background);
            }
            if (server.border() != null && !server.border().isEmpty()) {
                border = UiStyle.color(server.border(), border);
            }
            if (server.width() > 0) {
                maxW = (int) server.width();
            }
        } else {
            Object raw = hit.source().props().get("tooltip");
            if (raw instanceof List<?> list) {
                lines = new java.util.ArrayList<>();
                for (Object line : list) {
                    if (line instanceof Map<?, ?> m) {
                        String text = UiRenderer.str(m.get("content"));
                        if (text != null) {
                            lines.add(text);
                        }
                    } else if (line != null) {
                        lines.add(String.valueOf(line));
                    }
                }
            } else if (raw instanceof Map<?, ?> m) {
                Object content = m.get("content");
                if (content == null) {
                    content = m.get("text");
                }
                if (content != null) {
                    lines = List.of(String.valueOf(content));
                }
                textColor = UiStyle.color(m.get("textColor"), UiStyle.color(m.get("color"), textColor));
                background = UiStyle.color(m.get("background"), background);
                border = UiStyle.color(m.get("border"), border);
                if (m.get("width") instanceof Number n) {
                    maxW = n.intValue();
                }
            } else if (raw != null) {
                lines = List.of(String.valueOf(raw));
            }
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 合并多行 + 插值后走统一样式化气泡（§ 颜色码富文本）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String interpolated = UiRenderer.interpolate(hit, lines.get(i), page.variables());
            if (interpolated == null || interpolated.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(interpolated);
        }
        if (sb.length() == 0) {
            return;
        }
        ClientController.drawWorldTooltip(g, net.minecraft.client.Minecraft.getInstance(), sb.toString(),
                textColor, background, border, maxW);
    }

    /** 服务端 MOVE / 拖拽记忆恢复用：设置页面偏移。 */
    public void setOffset(double x, double y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    // ---------- 交互 ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editMode && !previewMode) {
            // 专业面板优先处理点击
            if (editorPanels != null && editorPanels.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            // 属性面板点击：进入属性编辑（兼容旧逻辑）
            String prop = panelPropAt(mouseX, mouseY);
            if (prop != null) {
                beginEditProp(prop);
                return true;
            }
            RenderNode hit = hit(mouseX, mouseY);
            selectedId = hit == null ? null : hit.id();
            editingProp = null;
            // 按住元素即武装拖拽：移动鼠标 → 相对起点拖动（松手结束）
            // 修复：旧实现按事件增量累加（target.x()+dragX），且按住空白处也会拖动已选中元素
            if (hit != null && button == 0) {
                editDragArmed = true;
                editDragPressX = mouseX;
                editDragPressY = mouseY;
                editDragOriginX = hit.x();
                editDragOriginY = hit.y();
            } else {
                editDragArmed = false;
            }
            return true; // 编辑模式吞掉点击，不发事件
        }
        RenderNode hit = hitInteractive(mouseX, mouseY);
        if (hit == null || "embed".equals(hit.type())) {
            // 嵌入页点击路由：命中嵌入页里的元素 → 执行其 actions（单机）/ 上报（多人）
            EmbedHit embed = embedHit(mouseX, mouseY);
            if (embed != null) {
                handleEmbedClick(embed);
                return true;
            }
            if (hit != null) {
                return true; // 点中 embed 容器本身（空白处）：吞掉
            }
            // 点空白/禁用元素（穿透）：页面可拖动就从这里拖
            if (isDraggable() && button == 0) {
                startDrag(mouseX, mouseY);
                return true;
            }
            focusedId = null;
            return false;
        }
        // 元素级运行时拖拽（dragMode: x/y/both;非控件类型）：按下不立即触发点击,
        // 松手时位移 < 5px 补发 CLICK,否则按约束轴拖动(位置写入编辑记忆,可保存)
        String dm = dragModeOf(hit);
        if (dm != null && button == 0) {
            runtimeDragId = hit.id();
            runtimeDragMode = dm;
            runtimeDragPressX = mouseX;
            runtimeDragPressY = mouseY;
            runtimeDragOriginX = hit.x();
            runtimeDragOriginY = hit.y();
            return true;
        }
        // 涟漪点击波纹（元素 ripple 属性）
        addRipple(hit, mouseX, mouseY);
        // 点击音效：元素 clickSound 优先（{sound, volume, pitch} 或字符串），否则原版按钮声
        playClickSound(hit);
        // 右键点击（普通元素）：CLICK 事件带 data "right"，脚本按 vars.event 分支（chest_slot/hot_slot 保留原生右键语义）
        boolean rclick = button == 1;
        // hit 按压反馈：元素定义 hit: {scale: 0.9, duration: 150} 时按下回弹
        Object hitRaw = hit.source() != null ? hit.source().props().get("hit") : null;
        if (hitRaw instanceof Map<?, ?> hitDef) {
            double scale = hitDef.get("scale") instanceof Number n ? n.doubleValue() : 0.9;
            int duration = hitDef.get("duration") instanceof Number n2 ? n2.intValue() : 150;
            AnimationEngine.get().press(hit.id(), page.id(), scale, duration);
        }
        switch (hit.type()) {
            case "input" -> {
                focusedId = hit.id();
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "slider" -> {
                draggingId = hit.id();
                updateSlider(hit, mouseX, mouseY);
                send(hit, UiEvent.Trigger.PRESS, null);
            }
            case "arc_slider" -> {
                draggingId = hit.id();
                updateArcSlider(hit, mouseX, mouseY);
                send(hit, UiEvent.Trigger.PRESS, null);
            }
            case "toggle" -> {
                boolean next = !Boolean.TRUE.equals(toggleValue.get(hit.id()));
                toggleValue.put(hit.id(), next);
                applyRadioGroup(hit, next); // toggle.radio 单选组：同组互斥
                send(hit, UiEvent.Trigger.CLICK, String.valueOf(next));
            }
            case "checkbox" -> {
                boolean next = !Boolean.TRUE.equals(toggleValue.get(hit.id()));
                toggleValue.put(hit.id(), next);
                send(hit, UiEvent.Trigger.CLICK, String.valueOf(next));
            }
            case "hot_slot" -> {
                // 容器上下文（当前会话绑定了真实容器）：左键拿起/放置（L）、右键半组/放一（R）、
                // Shift+左键快捷移动（Q）、中键整组拿取（A）、Ctrl+左键分发（S），与 chest_slot 同管线；
                // 非容器上下文 = 点击切换选中格
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player == null) {
                    return true;
                }
                Map<?, ?> hotSpec = UiRenderer.propsMap(hit, "hot_slot");
                int slots = 9;
                int index;
                if (hotSpec.get("slot") instanceof Number explicitSlot) {
                    index = explicitSlot.intValue(); // 独立格子元素（playerInventory 生成）
                } else {
                    index = (int) ((mouseX - hit.x()) / Math.max(hit.width() / slots, 1));
                    index = Math.max(0, Math.min(slots - 1, index));
                }
                boolean containerCtx = ClientController.get().containerStore()
                        .get(ClientController.get().currentSessionId()) != null;
                if (containerCtx) {
                    char action = 'L';
                    if (button == 1) {
                        action = 'R';
                    } else if (button == 2) {
                        action = 'A';
                    } else if (hasShiftDown()) {
                        action = 'Q';
                    } else if (Screen.hasControlDown()) {
                        action = 'S';
                    }
                    send(hit, UiEvent.Trigger.CLICK, index + ":" + action);
                } else {
                    CompatRender.invSetSelectedIndex(player.getInventory(), index);
                    send(hit, UiEvent.Trigger.CLICK, String.valueOf(index));
                }
            }
            case "chat_input" -> {
                focusedId = hit.id();
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "dropdown" -> {
                boolean nowOpen = Boolean.TRUE.equals(dropdownOpen.get(hit.id()));
                dropdownOpen.put(hit.id(), !nowOpen);
                // 展开时点选项：命中选项行则选中（同步键盘光标）
                if (nowOpen) {
                    int pickedIndex = pickDropdownIndex(hit, mouseY);
                    if (pickedIndex >= 0) {
                        Object options = UiRenderer.propsMap(hit, "dropdown").get("options");
                        String picked = String.valueOf(((List<?>) options).get(pickedIndex));
                        dropdownCursor.put(hit.id(), pickedIndex);
                        dropdownValue.put(hit.id(), picked);
                        dropdownOpen.put(hit.id(), false);
                        send(hit, UiEvent.Trigger.INPUT, picked);
                        return true;
                    }
                }
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "area_input" -> {
                focusedId = hit.id();
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "suggestion" -> {
                boolean wasOpen = Boolean.TRUE.equals(suggestionOpen.get(hit.id()));
                if (wasOpen) {
                    // 展开时点选项行：选中回填
                    String picked = pickSuggestionOption(hit, mouseY);
                    if (picked != null) {
                        suggestionText.put(hit.id(), picked);
                        suggestionOpen.put(hit.id(), false);
                        suggestionCursor.put(hit.id(), -1);
                        send(hit, UiEvent.Trigger.INPUT, picked);
                        return true;
                    }
                }
                focusedId = hit.id();
                suggestionOpen.put(hit.id(), true);
                suggestionCursor.put(hit.id(), 0);
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "flip_card" -> {
                toggleFlip(hit);
                return true;
            }
            case "chest_slot" -> {
                // 容器槽位交互：左键拿起/放置（L）、右键半组/放一（R）、Shift+左键快捷移动（Q）
                // 中键整组拿取（A）、双击交换（D）、Ctrl+左键分发光标（S）
                // 服务端权威执行（绑定真实容器），事件数据 "slot:action"
                Map<?, ?> slotSpec = UiRenderer.propsMap(hit, "chest_slot");
                int slot = slotSpec.get("slot") instanceof Number n ? n.intValue() : 0;
                char action = 'L';
                if (button == 1) {
                    action = 'R';
                } else if (button == 2) {
                    action = 'A'; // 中键整组拿取
                } else if (hasShiftDown()) {
                    action = 'Q';
                } else if (Screen.hasControlDown()) {
                    action = 'S'; // Ctrl+左键分发光标
                }
                send(hit, UiEvent.Trigger.CLICK, slot + ":" + action);
            }
            case "tabs" -> {
                Map<?, ?> spec = UiRenderer.propsMap(hit, "tabs");
                Object raw = spec.get("options");
                if (raw instanceof List<?> opts && !opts.isEmpty()) {
                    double w = hit.width() / opts.size();
                    int idx = (int) ((mouseX - hit.x()) / Math.max(w, 1));
                    idx = Math.max(0, Math.min(opts.size() - 1, idx));
                    String picked = String.valueOf(opts.get(idx));
                    String interp = UiRenderer.interpolate(hit, picked, page.variables());
                    if (interp != null && !interp.isEmpty()) picked = interp;
                    dropdownValue.put(hit.id(), picked);
                    send(hit, UiEvent.Trigger.INPUT, picked);
                }
            }
            case "video" -> {
                // seek 条点击：跳转播放位置（video.seekable: true 时渲染底部进度条）
                if (!Screen.hasShiftDown() && MediaItemDraws.handleVideoSeekClick(hit.id(), mouseX, mouseY)) {
                    return true;
                }
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "table" -> send(hit, UiEvent.Trigger.CLICK, null);
            default -> send(hit, UiEvent.Trigger.CLICK, rclick && !"chest_slot".equals(hit.type())
                    && !"hot_slot".equals(hit.type()) ? "right" : null);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editMode && !previewMode && editorPanels != null) {
            editorPanels.mouseReleased();
        }
        if (runtimeDragId != null) {
            double movedX = Math.abs(mouseX - runtimeDragPressX);
            double movedY = Math.abs(mouseY - runtimeDragPressY);
            boolean dragged = movedX > 4 || movedY > 4;
            RenderNode draggedNode = findNode(runtimeDragId);
            if (!dragged && draggedNode != null && draggedNode.source() != null) {
                // 未移动 = 视作点击(补发事件)
                send(draggedNode, UiEvent.Trigger.CLICK, null);
            }
            runtimeDragId = null;
            runtimeDragMode = null;
            return true;
        }
        if (editDragArmed) {
            editDragArmed = false;
            return true;
        }
        if (dragging) {
            dragging = false;
            ClientController.get().rememberPosition(page, offsetX, offsetY);
            return true;
        }
        if (draggingId != null) {
            draggingId = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 树拖拽（编辑模式面板）
        if (editMode && !previewMode && editorPanels != null) {
            editorPanels.mouseDragged(mouseX, mouseY);
        }
        if (runtimeDragId != null) {
            double nx = runtimeDragOriginX + (mouseX - runtimeDragPressX);
            double ny = runtimeDragOriginY + (mouseY - runtimeDragPressY);
            double cx = runtimeDragOriginX;
            double cy = runtimeDragOriginY;
            if (runtimeDragMode.contains("x") || runtimeDragMode.equals("both") || runtimeDragMode.equals("free")) {
                cx = nx;
            }
            if (runtimeDragMode.contains("y") || runtimeDragMode.equals("both") || runtimeDragMode.equals("free")) {
                cy = ny;
            }
            String pid = page.id() == null ? "page" : page.id();
            ClientController.get().elementEdits().set(pid, runtimeDragId, cx, cy);
            ClientController.get().refreshCurrent();
            return true;
        }
        if (editMode && editDragArmed && selectedId != null) {
            // 编辑拖动：起点元素位置 + 鼠标相对位移（与 HUD/世界编辑器同一手势语义）
            String pageId = page.id() == null ? "page" : page.id();
            ClientController.get().elementEdits().set(pageId, selectedId,
                    editDragOriginX + (mouseX - editDragPressX),
                    editDragOriginY + (mouseY - editDragPressY));
            ClientController.get().refreshCurrent();
            return true;
        }
        if (dragging) {
            offsetX = dragOriginX + (mouseX - dragStartX);
            offsetY = dragOriginY + (mouseY - dragStartY);
            return true;
        }
        if (draggingId == null) {
            return false;
        }
        RenderNode slider = findNode(draggingId);
        if (slider != null) {
            if ("arc_slider".equals(slider.type())) {
                updateArcSlider(slider, mouseX, mouseY);
            } else {
                updateSlider(slider, mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    private boolean isDraggable() {
        Object raw = page.options().get("draggable");
        return raw != null && Boolean.parseBoolean(String.valueOf(raw));
    }

    /** 递归找节点（按 id）。 */
    private static RenderNode findNode(RenderNode node, String id) {
        if (node.id().equals(id)) {
            return node;
        }
        for (RenderNode child : node.children()) {
            RenderNode found = findNode(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 在当前页节点树里按 id 找节点（含嵌套）。 */
    private RenderNode findNode(String id) {
        for (RenderNode root : nodes) {
            RenderNode found = findNode(root, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 当前页模型里按 id 找元素（含嵌套；编辑面板属性读写用）。 */
    private Element findElement(String id) {
        return ClientController.findElement(page, id);
    }

    /** 控件类型不参与元素级拖拽（拖拽会破坏其交互）。 */
    private static final java.util.Set<String> NON_DRAGGABLE_TYPES = java.util.Set.of(
            "slider", "arc_slider", "dropdown", "toggle", "checkbox", "input",
            "chat_input", "area_input", "suggestion", "item_slot", "chest_slot", "hot_slot");

    /** 元素级运行时拖拽模式（dragMode: x/y/both/free;off 或缺省 = 不可拖;控件类型忽略）。 */
    private String dragModeOf(RenderNode node) {
        if (node.source() == null) return null;
        Object raw = node.source().props().get("dragMode");
        if (raw == null) return null;
        String mode = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
        if (mode.isEmpty() || "off".equals(mode)) return null;
        if (NON_DRAGGABLE_TYPES.contains(node.type())) return null;
        return mode;
    }

    private void startDrag(double mouseX, double mouseY) {
        dragging = true;
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragOriginX = offsetX;
        dragOriginY = offsetY;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return mouseScrolled(mouseX, mouseY, 0, verticalAmount);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 旧版（DreamCore）Functions.wheel：写入滚轮上下文并触发（存在才跑）
        if (page.functions() != null && page.functions().containsKey("wheel")) {
            LegacyClientHost.setWheelDelta(verticalAmount);
            ClientController.get().runLifecycle(page, "wheel");
        }
        // 滚轮优先滚动命中的 scroll 容器
        RenderNode hit = hitInteractive(mouseX, mouseY);
        if (hit != null) {
            if ("area_input".equals(hit.type())) {
                scrollY.put(hit.id(), Math.max(0, scrollY.getOrDefault(hit.id(), 0.0)
                        - verticalAmount * 12));
                send(hit, UiEvent.Trigger.SCROLL, String.valueOf((int) verticalAmount));
                return true;
            }
            RenderNode scroller = findScrollParent(hit, nodes);
            if (scroller != null) {
                scrollY.put(scroller.id(), Math.max(0, scrollY.getOrDefault(scroller.id(), 0.0)
                        - verticalAmount * 12));
                send(scroller, UiEvent.Trigger.SCROLL, String.valueOf((int) verticalAmount));
                return true;
            }
        }
        if (hit == null) {
            return false;
        }
        send(hit, UiEvent.Trigger.SCROLL, String.valueOf((int) verticalAmount));
        return true;
    }

    /** 找命中节点所属的 scroll 容器（含自身）。 */
    private static RenderNode findScrollParent(RenderNode node, List<RenderNode> roots) {
        for (RenderNode root : roots) {
            RenderNode found = findScrollIn(root, node);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static RenderNode findScrollIn(RenderNode parent, RenderNode target) {
        if ("scroll".equals(parent.type()) && parent.contains(target.x(), target.y())) {
            return parent;
        }
        for (RenderNode child : parent.children()) {
            RenderNode found = findScrollIn(child, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editMode && !previewMode) {
            // 专业面板优先处理字符输入
            if (editorPanels != null && editorPanels.charTyped(codePoint)) {
                return true;
            }
            if (editingProp != null && (modifiers & 2) == 0) {
                // 属性面板数值输入（Ctrl 组合键不输入）
                editBuffer = (editBuffer == null ? "" : editBuffer) + codePoint;
                applyLiveProp();
                return true;
            }
        }
        if (focusedId == null) {
            return false;
        }
        RenderNode node = findNode(focusedId);
        if (node == null) {
            return false;
        }
        if ("input".equals(node.type())) {
            String text = inputText.getOrDefault(focusedId, "");
            inputText.put(focusedId, text + codePoint);
            send(node, UiEvent.Trigger.INPUT, inputText.get(focusedId));
            return true;
        }
        if ("area_input".equals(node.type())) {
            String text = areaText.getOrDefault(focusedId, "");
            areaText.put(focusedId, text + codePoint);
            send(node, UiEvent.Trigger.INPUT, areaText.get(focusedId));
            return true;
        }
        if ("suggestion".equals(node.type())) {
            String text = suggestionText.getOrDefault(focusedId, "");
            suggestionText.put(focusedId, text + codePoint);
            suggestionOpen.put(focusedId, true);
            suggestionCursor.put(focusedId, 0); // 文本变化 → 光标回顶
            send(node, UiEvent.Trigger.INPUT, suggestionText.get(focusedId));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 旧版（DreamCore）Functions.keyPress：写入键名上下文（E/ESCAPE…）并触发（存在才跑）
        if (page.functions() != null && page.functions().containsKey("keyPress")) {
            LegacyClientHost.setPressedKey(LegacyClientHost.keyName(keyCode, scanCode));
            ClientController.get().runLifecycle(page, "keyPress");
            LegacyClientHost.setPressedKey("");
        }
        if (keyCode == 256) { // ESC
            if (editMode) {
                if (previewMode) { // 预览中 ESC = 回编辑
                    previewMode = false;
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§b[OpenDreamCore] §f已返回编辑模式"), false);
                    return true;
                }
                if (editingProp != null) { // 先退出属性编辑
                    editingProp = null;
                    editBuffer = null;
                    return true;
                }
                editMode = false;
                selectedId = null;
                return true; // 退出编辑模式，不关页面
            }
            if (focusedId != null) {
                // 优先收起展开的下拉框/建议列表，其次释放焦点（再按一次 ESC 才关页面）
                RenderNode focused = findNode(focusedId);
                if (focused != null && "dropdown".equals(focused.type())
                        && Boolean.TRUE.equals(dropdownOpen.get(focusedId))) {
                    dropdownOpen.put(focusedId, false);
                    return true;
                }
                if (focused != null && "suggestion".equals(focused.type())
                        && Boolean.TRUE.equals(suggestionOpen.get(focusedId))) {
                    suggestionOpen.put(focusedId, false);
                    suggestionCursor.put(focusedId, -1);
                    return true;
                }
                focusedId = null;
                return true;
            }
            Object allow = page.options().get("allowEscClose");
            if (allow != null && !Boolean.parseBoolean(String.valueOf(allow))) {
                return true; // 页面声明禁止 ESC 关闭
            }
        }
        if (editMode) {
            // 预览模式：V 返回编辑；其余按键放行（输入框等元素交互）
            if (previewMode) {
                if (keyCode == 72 && editingProp == null) { // H 切换元素标签
                showLabels = !showLabels;
                return true;
            }
            if (keyCode == 86 && editingProp == null) { // V
                    previewMode = false;
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§b[OpenDreamCore] §f已返回编辑模式"), false);
                    return true;
                }
                if (editingProp == null) {
                    return super.keyPressed(keyCode, scanCode, modifiers); // 元素键盘交互
                }
            }
            // 专业面板优先处理键盘
            if (editorPanels != null && editorPanels.keyPressed(keyCode, modifiers)) {
                return true;
            }
            // V = 预览交互切换
            if (keyCode == 72 && editingProp == null) { // H 切换元素标签
                showLabels = !showLabels;
                return true;
            }
            if (keyCode == 86 && editingProp == null) {
                previewMode = true;
                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a[OpenDreamCore] §f预览模式：可点击元素/触发脚本，V 或 ESC 返回编辑"), false);
                return true;
            }
            if (keyCode == 90 && (modifiers & 2) != 0) { // Ctrl+Z undo
                if ((modifiers & 1) != 0) redoEdit(); else undoEdit();
                return true;
            }
            if (keyCode == 89 && (modifiers & 2) != 0) { // Ctrl+Y redo
                redoEdit();
                return true;
            }
            // 快捷键：Del 删除 / Ctrl+C 复制 / [ ] 调 z
            if (keyCode == 259 && editingProp == null) { // Delete
                pushEditUndo();
                deleteSelected();
                return true;
            }
            if (keyCode == 67 && (modifiers & 2) != 0) { // Ctrl+C
                copySelected();
                return true;
            }
            if (keyCode == 91) { // [
                pushEditUndo();
                adjustZ(-1);
                return true;
            }
            if (keyCode == 93) { // ]
                pushEditUndo();
                adjustZ(1);
                return true;
            }
            // 方向键微调选中元素（←→↑↓ = 1px，Shift = 10px；与世界编辑器一致，连续微调合并 undo）
            if (selectedId != null && editingProp == null
                    && (keyCode == 263 || keyCode == 262 || keyCode == 261 || keyCode == 265)) {
                double step = (modifiers & 1) != 0 ? 10 : 1;
                RenderNode target = findNode(selectedId);
                if (target != null) {
                    double dx = keyCode == 263 ? -step : keyCode == 262 ? step : 0;
                    double dy = keyCode == 261 ? step : keyCode == 265 ? -step : 0;
                    long now = System.currentTimeMillis();
                    if (now - lastNudgeAt > 800) {
                        pushEditUndo(); // 连续微调只记一次 undo
                    }
                    lastNudgeAt = now;
                    String pageId = page.id() == null ? "page" : page.id();
                    ClientController.get().elementEdits().set(pageId, selectedId,
                            target.x() + dx, target.y() + dy);
                    ClientController.get().refreshCurrent();
                    return true;
                }
            }
            if (editingProp != null) {
                if (keyCode == 257 || keyCode == 335) { // Enter 提交
                    editingProp = null;
                    editBuffer = null;
                    return true;
                }
                if (keyCode == 259 && editBuffer != null && !editBuffer.isEmpty()) { // 退格
                    editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                    applyLiveProp();
                    return true;
                }
            }
        }
        if (keyCode == 258) { // Tab：焦点循环（Shift+Tab 反向）
            focusNext((modifiers & 1) != 0);
            return true;
        }
        if (focusedId != null) {
            RenderNode focused = findNode(focusedId);
            // 下拉框键盘导航：↑/↓ 移动光标，Enter 确认
            if (focused != null && "dropdown".equals(focused.type())
                    && Boolean.TRUE.equals(dropdownOpen.get(focusedId))) {
                if (keyCode == 264) { // ↓
                    moveDropdownCursor(focused, 1);
                    return true;
                }
                if (keyCode == 265) { // ↑
                    moveDropdownCursor(focused, -1);
                    return true;
                }
                if (keyCode == 257 || keyCode == 335) { // Enter
                    confirmDropdown(focused);
                    return true;
                }
            }
            // 关闭的下拉 + Enter：展开并选中第一项
            if (focused != null && "dropdown".equals(focused.type())
                    && !Boolean.TRUE.equals(dropdownOpen.get(focusedId))
                    && (keyCode == 257 || keyCode == 335)) {
                dropdownOpen.put(focusedId, true);
                dropdownCursor.put(focusedId, 0);
                return true;
            }
            // 滑块键盘：←/→ 按 step 步进（slider.step，默认 1）
            if (focused != null && ("slider".equals(focused.type()) || "arc_slider".equals(focused.type()))) {
                double delta = 0;
                if (keyCode == 263) { // ←
                    delta = -1;
                } else if (keyCode == 262) { // →
                    delta = 1;
                }
                if (delta != 0) {
                    Map<?, ?> spec = UiRenderer.propsMap(focused, focused.type());
                    double min = UiRenderer.num(spec.get("min"), 0);
                    double max = UiRenderer.num(spec.get("max"), 100);
                    double step = UiRenderer.num(spec.get("step"), 1);
                    Double local = sliderValue.get(focusedId);
                    double value = local != null ? local : UiRenderer.num(spec.get("value"), min);
                    double next = Math.max(min, Math.min(max, value + delta * step));
                    double rounded = Math.round(next * 100) / 100.0;
                    sliderValue.put(focusedId, rounded);
                    send(focused, UiEvent.Trigger.INPUT, String.valueOf(rounded));
                    return true;
                }
            }
            // 按钮/开关/复选框 Enter：确认（等价点击；toggle/checkbox 切换值）
            if (focused != null && ("button".equals(focused.type()) || "toggle".equals(focused.type())
                    || "checkbox".equals(focused.type()))
                    && (keyCode == 257 || keyCode == 335)) {
                if ("toggle".equals(focused.type()) || "checkbox".equals(focused.type())) {
                    boolean next = !Boolean.TRUE.equals(toggleValue.get(focusedId));
                    toggleValue.put(focusedId, next);
                    if ("toggle".equals(focused.type())) {
                        applyRadioGroup(focused, next); // toggle.radio 单选组：同组互斥
                    }
                    send(focused, UiEvent.Trigger.CLICK, String.valueOf(next));
                } else {
                    send(focused, UiEvent.Trigger.CLICK, null);
                }
                return true;
            }
            if (focused != null && "chat_input".equals(focused.type())) {
                if (keyCode == 257 || keyCode == 335) { // Enter
                    sendChatInput(focused);
                    return true;
                }
                if (keyCode == 265 && !chatHistory.isEmpty()) { // ↑ 历史上翻
                    if (chatHistoryIndex < 0) {
                        chatHistoryIndex = chatHistory.size();
                    }
                    chatHistoryIndex = Math.max(0, chatHistoryIndex - 1);
                    inputText.put(focusedId, chatHistory.get(chatHistoryIndex));
                    return true;
                }
                if (keyCode == 264 && chatHistoryIndex >= 0) { // ↓ 历史下翻
                    chatHistoryIndex++;
                    if (chatHistoryIndex >= chatHistory.size()) {
                        chatHistoryIndex = -1;
                        inputText.put(focusedId, "");
                    } else {
                        inputText.put(focusedId, chatHistory.get(chatHistoryIndex));
                    }
                    return true;
                }
                if (keyCode == 259 && !inputText.getOrDefault(focusedId, "").isEmpty()) { // 退格
                    String text = inputText.get(focusedId);
                    inputText.put(focusedId, text.substring(0, text.length() - 1));
                    send(focused, UiEvent.Trigger.INPUT, inputText.get(focusedId));
                    return true;
                }
                return true;
            }
            if (focused != null && "input".equals(focused.type())) {
                if (keyCode == 259 && !inputText.getOrDefault(focusedId, "").isEmpty()) { // 退格
                    String text = inputText.get(focusedId);
                    inputText.put(focusedId, text.substring(0, text.length() - 1));
                    send(focused, UiEvent.Trigger.INPUT, inputText.get(focusedId));
                    return true;
                }
                return true;
            }
            if (focused != null && "area_input".equals(focused.type())) {
                String text = areaText.getOrDefault(focusedId, "");
                if (keyCode == 257 || keyCode == 335) { // Enter 换行
                    areaText.put(focusedId, text + "\n");
                    send(focused, UiEvent.Trigger.INPUT, areaText.get(focusedId));
                    return true;
                }
                if (keyCode == 259 && !text.isEmpty()) { // 退格
                    areaText.put(focusedId, text.substring(0, text.length() - 1));
                    send(focused, UiEvent.Trigger.INPUT, areaText.get(focusedId));
                    return true;
                }
                return true;
            }
            if (focused != null && "suggestion".equals(focused.type())) {
                String text = suggestionText.getOrDefault(focusedId, "");
                List<Object> filtered = UiRenderer.filterSuggestions(
                        UiRenderer.propsMap(focused, "suggestion"), text);
                int shown = filtered.size();
                if (keyCode == 264) { // ↓：光标下移（循环）
                    if (shown > 0 && Boolean.TRUE.equals(suggestionOpen.get(focusedId))) {
                        int cur = suggestionCursor.getOrDefault(focusedId, 0);
                        suggestionCursor.put(focusedId, (cur + 1) % shown);
                        return true;
                    }
                    if (shown > 0) {
                        suggestionCursor.put(focusedId, 0);
                        suggestionOpen.put(focusedId, true);
                        return true;
                    }
                }
                if (keyCode == 265) { // ↑：光标上移（循环）
                    if (shown > 0 && Boolean.TRUE.equals(suggestionOpen.get(focusedId))) {
                        int cur = suggestionCursor.getOrDefault(focusedId, 0);
                        suggestionCursor.put(focusedId, (cur - 1 + shown) % shown);
                        return true;
                    }
                    if (shown > 0) {
                        suggestionCursor.put(focusedId, shown - 1);
                        suggestionOpen.put(focusedId, true);
                        return true;
                    }
                }
                if (keyCode == 259 && !text.isEmpty()) { // 退格
                    suggestionText.put(focusedId, text.substring(0, text.length() - 1));
                    suggestionCursor.put(focusedId, 0);
                    send(focused, UiEvent.Trigger.INPUT, suggestionText.get(focusedId));
                    return true;
                }
                if (keyCode == 257 || keyCode == 335) { // Enter 选中光标项（无光标取第一个）
                    if (!filtered.isEmpty()) {
                        int cur = suggestionCursor.getOrDefault(focusedId, 0);
                        if (cur < 0 || cur >= filtered.size()) {
                            cur = 0;
                        }
                        String picked = UiRenderer.suggestionValue(filtered.get(cur));
                        suggestionText.put(focusedId, picked);
                        suggestionOpen.put(focusedId, false);
                        suggestionCursor.put(focusedId, -1);
                        send(focused, UiEvent.Trigger.INPUT, picked);
                    }
                    return true;
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------- 焦点路由 ----------

    /** 可聚焦组件类型。 */
    private static final java.util.Set<String> FOCUSABLE =
            java.util.Set.of("input", "area_input", "suggestion", "chat_input", "dropdown");

    /** Tab/Shift+Tab 循环焦点（页面内可聚焦元素，按文档顺序）。 */
    private void focusNext(boolean reverse) {
        List<RenderNode> focusables = new java.util.ArrayList<>();
        for (RenderNode root : nodes) {
            collectFocusable(root, focusables);
        }
        if (focusables.isEmpty()) {
            focusedId = null;
            return;
        }
        int current = -1;
        if (focusedId != null) {
            for (int i = 0; i < focusables.size(); i++) {
                if (focusables.get(i).id().equals(focusedId)) {
                    current = i;
                    break;
                }
            }
        }
        int next = reverse ? current - 1 : current + 1;
        if (next < 0) {
            next = focusables.size() - 1;
        }
        if (next >= focusables.size()) {
            next = 0;
        }
        RenderNode target = focusables.get(next);
        focusedId = target.id();
        if ("dropdown".equals(target.type())) {
            dropdownOpen.put(target.id(), true); // 聚焦下拉自动展开
            dropdownCursor.put(target.id(), 0);
        }
    }

    private static void collectFocusable(RenderNode node, List<RenderNode> out) {
        if (FOCUSABLE.contains(node.type()) && node.visible() && node.enabled()) {
            out.add(node);
        }
        for (RenderNode child : node.children()) {
            collectFocusable(child, out);
        }
    }

    /** 下拉键盘移动光标。 */
    private void moveDropdownCursor(RenderNode node, int delta) {
        Object raw = UiRenderer.propsMap(node, "dropdown").get("options");
        if (!(raw instanceof List<?> options) || options.isEmpty()) {
            return;
        }
        int cursor = dropdownCursor.getOrDefault(node.id(), 0);
        cursor = ((cursor + delta) % options.size() + options.size()) % options.size();
        dropdownCursor.put(node.id(), cursor);
    }

    /** 下拉键盘确认：选中光标项。 */
    private void confirmDropdown(RenderNode node) {
        Object raw = UiRenderer.propsMap(node, "dropdown").get("options");
        if (!(raw instanceof List<?> options) || options.isEmpty()) {
            return;
        }
        int cursor = dropdownCursor.getOrDefault(node.id(), 0);
        String picked = String.valueOf(options.get(Math.min(cursor, options.size() - 1)));
        dropdownValue.put(node.id(), picked);
        dropdownOpen.put(node.id(), false);
        send(node, UiEvent.Trigger.INPUT, picked);
    }

    /** chat_input 回车：发送聊天并清空。 */
    private void sendChatInput(RenderNode node) {
        String text = inputText.getOrDefault(focusedId, "");
        if (!text.isEmpty()) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getConnection() != null) {
                mc.getConnection().sendChat(text); // 多人：走原版聊天发送
            } else if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(text), false); // 单机：本地显示
            }
            send(node, UiEvent.Trigger.INPUT, text);
            // 发送历史（去重相邻重复，上限 50；↑/↓ 浏览）
            if (chatHistory.isEmpty() || !chatHistory.get(chatHistory.size() - 1).equals(text)) {
                chatHistory.add(text);
                if (chatHistory.size() > 50) {
                    chatHistory.remove(0);
                }
            }
        }
        inputText.put(focusedId, "");
        chatHistoryIndex = -1;
        focusedId = null;
    }

    /** 嵌入页命中：递归找包含 (mx,my) 的 embed 节点，再在嵌入页节点树里命中。 */
    private EmbedHit embedHit(double mx, double my) {
        for (RenderNode root : nodes) {
            EmbedHit found = embedHitIn(root, mx, my);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private EmbedHit embedHitIn(RenderNode node, double mx, double my) {
        if ("embed".equals(node.type()) && node.contains(mx, my)) {
            Map<?, ?> spec = UiRenderer.propsMap(node, "embed");
            String pageId = UiRenderer.str(spec.get("page"));
            com.opendreamcore.page.Page target = ClientController.get().pageById(pageId);
            if (target != null) {
                List<RenderNode> embedded = ClientController.get().embeddedNodes(target,
                        Math.max(1, (int) node.width()), Math.max(1, (int) node.height()));
                for (int i = embedded.size() - 1; i >= 0; i--) {
                    RenderNode h = embedded.get(i).hitTest(mx - node.x(), my - node.y());
                    if (h != null && h.enabled() && h.source() != null) {
                        return new EmbedHit(node, target, h);
                    }
                }
            }
        }
        for (RenderNode child : node.children()) {
            EmbedHit found = embedHitIn(child, mx, my);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 嵌入页元素点击：单机执行嵌入页 actions；多人上报宿主会话（服务端按嵌入页元素 id 路由）。 */
    private void handleEmbedClick(EmbedHit embed) {
        String script = embed.hit().source().actions().get("click");
        var controller = ClientController.get();
        if (controller.isServerMode()) {
            controller.sendEvent(session.event(embed.hit().id(), UiEvent.Trigger.CLICK, null));
            return;
        }
        if (script != null && !script.isBlank()) {
            controller.runLocalAction(embed.page(), script);
        }
    }

    /** 悬停变化才发事件，避免每帧刷屏。 */
    private void trackHover(int mouseX, int mouseY) {
        RenderNode hit = hit(mouseX, mouseY);
        String now = hit == null ? null : hit.id();
        if (now == null ? hoverId != null : !now.equals(hoverId)) {
            hoverId = now;
            if (hit != null && hit.enabled()) {
                send(hit, UiEvent.Trigger.HOVER, null);
            }
        }
    }

    /** 当前悬停元素 id（无悬停 null；脚本 Screen.获取悬停元素() 用）。 */
    public String hoverId() {
        return hoverId;
    }

    private void updateSlider(RenderNode node, double mouseX, double mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "slider");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        boolean vertical = UiRenderer.bool(spec.get("vertical"), false);
        double ratio = vertical
                ? (mouseY - node.y()) / Math.max(node.height(), 1)
                : (mouseX - node.x()) / Math.max(node.width(), 1);
        ratio = Math.max(0, Math.min(1, ratio));
        double value = min + (max - min) * ratio;
        double step = UiRenderer.num(spec.get("step"), 0);
        if (step > 0) {
            value = min + Math.round((value - min) / step) * step;
            value = Math.max(min, Math.min(max, value));
        }
        sliderValue.put(node.id(), value);
        send(node, UiEvent.Trigger.INPUT, String.valueOf(Math.round(value * 100) / 100.0));
    }

    /** 环形滑块：按鼠标相对中心的角度算值（startAngle 起、sweepAngle 扫过）。 */
    private void updateArcSlider(RenderNode node, double mouseX, double mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "arc_slider");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        double start = UiRenderer.num(spec.get("startAngle"), -90);
        double sweep = UiRenderer.num(spec.get("sweepAngle"), 360);
        double angle = Math.toDegrees(Math.atan2(mouseY - cy, mouseX - cx));
        double rel = angle - start;
        while (rel < 0) {
            rel += 360;
        }
        double ratio = sweep != 0 ? rel / sweep : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        double value = min + (max - min) * ratio;
        sliderValue.put(node.id(), value);
        send(node, UiEvent.Trigger.INPUT, String.valueOf(Math.round(value * 100) / 100.0));
    }

    /** 展开的下拉框里点选项：返回选项下标（没点中 -1）。 */
    private int pickDropdownIndex(RenderNode node, double mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "dropdown");
        Object raw = spec.get("options");
        if (!(raw instanceof List<?> options)) {
            return -1;
        }
        int index = (int) ((mouseY - (node.y() + node.height())) / 14);
        int maxVisible = (int) UiRenderer.num(spec.get("maxVisibleOptions"), options.size());
        return index < 0 || index >= options.size() || index >= maxVisible ? -1 : index;
    }

    /** 展开的建议下拉里点选项：返回选中项的值（没点中返回 null）。 */
    private String pickSuggestionOption(RenderNode node, double mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "suggestion");
        List<Object> filtered = UiRenderer.filterSuggestions(spec, suggestionText.getOrDefault(node.id(), ""));
        if (filtered.isEmpty()) {
            return null;
        }
        int max = Math.max(1, (int) UiRenderer.num(spec.get("max"), 6));
        int index = (int) ((mouseY - (node.y() + node.height())) / 14);
        if (index < 0 || index >= Math.min(filtered.size(), max)) {
            return null;
        }
        return UiRenderer.suggestionValue(filtered.get(index));
    }

    private RenderNode hit(double mouseX, double mouseY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            RenderNode hit = nodes.get(i).hitTest(mouseX, mouseY);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 交互命中：禁用元素穿透（点击/滚轮落到下层可用元素）；hover/tooltip 用 hit()。 */
    private RenderNode hitInteractive(double mouseX, double mouseY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            RenderNode hit = nodes.get(i).hitTestInteractive(mouseX, mouseY);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void send(RenderNode node, UiEvent.Trigger trigger, String data) {
        ClientController.get().handleElementEvent(session, page, node, trigger, data);
    }

    /** 点击音效：元素 clickSound 优先（字符串或 {sound, volume, pitch}），否则原版 UI 按钮声。 */
    private void playClickSound(RenderNode node) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 元素自定义 clickSound（在 props 或 hologram 里）
        if (node.source() != null) {
            Object cs = node.source().props().get("clickSound");
            if (cs == null) {
                Object holo = node.source().props().get("hologram");
                if (holo instanceof Map<?, ?> h) {
                    cs = h.get("clickSound");
                }
            }
            if (cs instanceof Map<?, ?> m) {
                String sound = String.valueOf(m.get("sound"));
                float vol = m.get("volume") instanceof Number n ? n.floatValue() : 1.0f;
                float pitch = m.get("pitch") instanceof Number n2 ? n2.floatValue() : 1.0f;
                mc.player.playNotifySound(
                        UiRenderer.soundEvent(
                                net.minecraft.resources.ResourceLocation.tryParse(sound)), net.minecraft.sounds.SoundSource.MASTER, vol, pitch);
                return;
            }
            if (cs instanceof String s && !s.isBlank()) {
                mc.player.playNotifySound(
                        UiRenderer.soundEvent(
                                net.minecraft.resources.ResourceLocation.tryParse(s)), net.minecraft.sounds.SoundSource.MASTER, 1.0f, 1.0f);
                return;
            }
        }
        // 默认：原版按钮点击声
        mc.player.playNotifySound(
                UiRenderer.soundEvent(
                        net.minecraft.resources.ResourceLocation.tryParse("minecraft:ui.button.click")),
                net.minecraft.sounds.SoundSource.MASTER, 0.3f, 1.0f);
    }
}
