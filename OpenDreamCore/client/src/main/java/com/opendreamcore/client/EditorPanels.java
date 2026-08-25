package com.opendreamcore.client;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.page.PageExporter;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

/**
 * 专业编辑器面板系统：调色板 + 元素树 + 属性检查器 + 对齐工具。
 * 由 OdcScreen 在编辑模式下委托渲染和输入处理。
 * 面板布局：左侧调色板 + 元素树，右侧属性检查器，顶部工具栏。
 */
public final class EditorPanels {

    // ---- 面板几何 ----
    static final int PALETTE_W = 130;
    static final int TREE_W = 160;
    static final int INSPECTOR_W = 220;
    static final int TOOLBAR_H = 18;
    static final int ROW_H = 14;
    static final int HEADER_H = 16;

    // ---- 调色板元素类型 ----
    private static final String[][] PALETTE = {
            {"布局", "layout", "rect", "container"},
            {"文本", "text", "button"},
            {"输入", "input", "area_input", "suggestion"},
            {"交互", "slider", "toggle", "dropdown", "checkbox"},
            {"展示", "image", "progress", "gauge", "arc_slider"},
            {"高级", "embed", "scroll", "foreach", "flip_card", "table"},
    };

    // ---- 状态 ----
    private boolean showPalette = true;
    private boolean showTree = true;
    private boolean showInspector = true;
    private boolean showAlign = false;
    /** 紧凑模式（HUD 编辑用）：面板半透明 + 拖拽时自动收起。 */
    private boolean compactMode;
    /** 面板整体可见（false = 全部收起只留工具栏）。 */
    private boolean panelsVisible = true;

    // 调色板选中的类型（待放置）
    private String pendingType;

    // 多选
    private final Set<String> multiSelect = new LinkedHashSet<>();

    // 分组：groupId → 元素 id 集合
    private final Map<String, Set<String>> groups = new LinkedHashMap<>();

    // 属性检查器滚动
    private int inspectorScroll;
    private String editingProp;
    private String editBuffer;

    // 对齐工具：记录对齐参考（第一个选中元素）
    private String alignRefId;

    // 树面板滚动
    private int treeScroll;

    // 拖拽放置模式
    private boolean placing;

    // ---- 元素树拖拽 ----
    /** 正在拖拽的元素 id（null = 非拖拽状态）。 */
    private String treeDragId;
    /** 拖拽按下时的屏幕 Y 坐标。 */
    private int treeDragPressY;
    /** 拖拽按下时的屏幕 X 坐标。 */
    private int treeDragPressX;
    /** 是否已超过拖拽阈值（5px）。 */
    private boolean treeDragActive;
    /** 放置目标：插入到这个元素之前。 */
    private String treeDropBeforeId;
    /** 放置目标：插入到这个元素之后。 */
    private String treeDropAfterId;
    /** 放置目标：变成这个元素的子元素。 */
    private String treeDropInsideId;

    // 导出/导入面板
    private boolean showExport;
    private String exportText;
    private boolean exportCopied;

    // ---- 回调接口 ----
    public interface Host {
        Page page();
        List<RenderNode> nodes();
        Font font();
        int width();
        int height();
        RenderNode findNode(String id);
        Element findElement(String id);
        void selectElement(String id);
        void refreshCurrent();
        void pushUndo();
        String editSnapshot();
        void restoreEdit(String json);
        void setElementProp(String elementId, String prop, Object value);
        /** dotted path 版：prop = "text.content" → 写入 props.text.content 嵌套 map。 */
        void setElementPropDeep(String elementId, String prop, Object value);
        void setElementPos(String elementId, double x, double y);
        void deleteElement(String elementId);
        /** 切换元素运行时显隐（编辑器眼区；visibleWhen=false 机制，不改 YAML）。 */
        void toggleElementHidden(String elementId);
        /** 树分支管理：移动元素在兄弟中的位置（-1 上移，+1 下移）。 */
        void moveElementInTree(String elementId, int direction);
        /** 树分支管理：重新挂载父元素（null = 提升到顶层）。 */
        void reparentElement(String elementId, String newParentId);
        void copyElement(String elementId);
        void addElement(String type, double x, double y);
        String selectedId();
    }

    private final Host host;

    public EditorPanels(Host host) {
        this.host = host;
    }

    /** 紧凑模式（HUD 编辑）：面板半透明，初始收起，拖拽自动隐藏。 */
    public void setCompactMode(boolean on) {
        this.compactMode = on;
        if (on) {
            // HUD 编辑初始收起所有面板，只留工具栏
            this.showPalette = false;
            this.showTree = false;
            this.showInspector = false;
            this.showAlign = false;
        }
    }

    /** 面板整体显隐切换（Tab 键或工具栏按钮）。 */
    public void togglePanels() {
        panelsVisible = !panelsVisible;
        if (panelsVisible) {
            // 展开时恢复上次状态
            showPalette = true;
        } else {
            showPalette = false;
            showTree = false;
            showInspector = false;
            showAlign = false;
        }
    }

    /** 拖拽开始时自动收起面板（紧凑模式）。 */
    public void onDragStart() {
        if (compactMode) {
            panelsVisible = false;
            showPalette = false;
            showTree = false;
            showInspector = false;
        }
    }

    /** 面板背景色（紧凑模式下半透明）。 */
    private int panelBg() {
        return compactMode ? 0x90101418 : 0xE0101418;
    }

    // ---- 渲染入口 ----

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        drawToolbar(g);
        if (!panelsVisible) return;
        if (showPalette) drawPalette(g, mouseX, mouseY);
        if (showTree) drawTree(g, mouseX, mouseY);
        if (showInspector) drawInspector(g, mouseX, mouseY);
        if (showAlign) drawAlignPanel(g, mouseX, mouseY);
        if (showExport) drawExportPanel(g, mouseX, mouseY);
    }

    // ---- 工具栏 ----

    private void drawToolbar(GuiGraphics g) {
        int y = 0;
        g.fill(0, y, host.width(), y + TOOLBAR_H, 0xE0101418);
        int x = 4;
        x += drawToggleButton(g, x, y, "P", "调色板", showPalette);
        x += 4;
        x += drawToggleButton(g, x, y, "T", "元素树", showTree);
        x += 4;
        x += drawToggleButton(g, x, y, "I", "检查器", showInspector);
        x += 4;
        x += drawToggleButton(g, x, y, "A", "对齐", showAlign);
        x += 8;
        x += drawTextButton(g, x, y, "E", "导出YAML");
        x += 4;
        x += drawTextButton(g, x, y, "M", "多选(" + multiSelect.size() + ")");
        x += 4;
        if (pendingType != null) {
            g.drawString(host.font(), "-> " + pendingType + " (点击放置)", x, y + 5, 0xFF4FC3F7);
        }
        // 右侧提示
        String hint = "Del删|Ctrl+C复制|Ctrl+Z撤销|[ ]Z层级|G分组|Ctrl+E导出";
        int hw = host.font().width(hint);
        g.drawString(host.font(), hint, host.width() - hw - 4, y + 5, 0xFF6B7280);
    }

    private int drawToggleButton(GuiGraphics g, int x, int y, String key, String label, boolean active) {
        String text = (active ? "§a" : "§7") + key + ":" + label;
        int w = host.font().width(text) + 8;
        g.fill(x, y + 1, x + w, y + TOOLBAR_H - 1, active ? 0xFF1E3A2E : 0xFF1A1F2E);
        g.drawString(host.font(), text, x + 4, y + 5, 0xFFFFFFFF);
        return w;
    }

    private int drawTextButton(GuiGraphics g, int x, int y, String key, String label) {
        String text = "§e" + key + ":" + label;
        int w = host.font().width(text) + 8;
        g.fill(x, y + 1, x + w, y + TOOLBAR_H - 1, 0xFF1A1F2E);
        g.drawString(host.font(), text, x + 4, y + 5, 0xFFFFFFFF);
        return w;
    }

    /** 附属模组注册的自定义调色板类型（追加在内置类型后面）。 */
    private static final java.util.List<String[]> CUSTOM_PALETTE = new java.util.ArrayList<>();

    /** 注册自定义调色板条目（附属模组调用；分组名 + 类型名）。 */
    public static void registerPalette(String group, String type) {
        for (String[] g : CUSTOM_PALETTE) {
            if (g[0].equals(group)) {
                String[] expanded = new String[g.length + 1];
                System.arraycopy(g, 0, expanded, 0, g.length);
                expanded[g.length] = type;
                CUSTOM_PALETTE.set(CUSTOM_PALETTE.indexOf(g), expanded);
                return;
            }
        }
        CUSTOM_PALETTE.add(new String[]{group, type});
    }

    // ---- 调色板 ----

    private void drawPalette(GuiGraphics g, int mouseX, int mouseY) {
        int x = 0;
        int y = TOOLBAR_H;
        int h = host.height() - TOOLBAR_H;
        g.fill(x, y, x + PALETTE_W, y + h, panelBg());
        g.fill(x + PALETTE_W - 1, y, x + PALETTE_W, y + h, 0xFF2A3040);
        g.drawString(host.font(), "§e元素调色板", x + 6, y + 4, 0xFFFFFFFF);
        int rowY = y + HEADER_H + 4;
        for (String[] group : PALETTE) {
            rowY = drawPaletteGroup(g, group, x, rowY, mouseX, mouseY);
        }
        for (String[] group : CUSTOM_PALETTE) {
            rowY = drawPaletteGroup(g, group, x, rowY, mouseX, mouseY);
        }
    }

    private int drawPaletteGroup(GuiGraphics g, String[] group, int x, int rowY, int mouseX, int mouseY) {
        g.drawString(host.font(), "§7" + group[0], x + 4, rowY, 0xFF9AA3B2);
        rowY += ROW_H - 2;
        for (int i = 1; i < group.length; i++) {
            String type = group[i];
            boolean selected = type.equals(pendingType);
            boolean hover = mouseX >= x + 2 && mouseX < x + PALETTE_W - 2
                    && mouseY >= rowY && mouseY < rowY + ROW_H - 1;
            int bg = selected ? 0xFF1E3A5E : (hover ? 0xFF1A2030 : 0x00000000);
            if (bg != 0) g.fill(x + 2, rowY, x + PALETTE_W - 2, rowY + ROW_H - 1, bg);
            g.drawString(host.font(), (selected ? "§b▶ " : "  ") + type, x + 6, rowY + 2,
                    selected ? 0xFF4FC3F7 : 0xFFC0C8D0);
            rowY += ROW_H - 1;
        }
        rowY += 2;
        return rowY;
    }

    // ---- 元素树 ----

    private void drawTree(GuiGraphics g, int mouseX, int mouseY) {
        int x = showPalette ? PALETTE_W : 0;
        int y = TOOLBAR_H;
        int h = host.height() - TOOLBAR_H;
        int w = TREE_W;
        g.fill(x, y, x + w, y + h, panelBg());
        g.fill(x + w - 1, y, x + w, y + h, 0xFF2A3040);
        g.drawString(host.font(), "§e元素树", x + 6, y + 4, 0xFFFFFFFF);
        int rowY = y + HEADER_H + 4;
        for (RenderNode node : host.nodes()) {
            rowY = drawTreeNode(g, node, x, rowY, 0, mouseX, mouseY);
        }
    }

    private int drawTreeNode(GuiGraphics g, RenderNode node, int x, int rowY, int depth, int mx, int my) {
        if (rowY >= host.height() - TOOLBAR_H) return rowY;
        String id = node.id();
        boolean selected = id.equals(host.selectedId()) || multiSelect.contains(id);
        boolean hidden = ClientController.get().elementEdits()
                .isHidden(host.page().id() == null ? "page" : host.page().id(), id);
        boolean hover = mx >= x && mx < x + TREE_W && my >= rowY && my < rowY + ROW_H - 1;
        int bg = selected ? 0xFF2A4030 : (hover ? 0xFF1A2030 : 0x00000000);
        if (bg != 0) g.fill(x, rowY, x + TREE_W - 2, rowY + ROW_H - 1, bg);
        String prefix = depth > 0 ? "  ".repeat(depth) + "└ " : "";
        g.drawString(host.font(), prefix + "§r" + node.type() + ": " + id, x + 4 + depth * 6, rowY + 2,
                hidden ? 0xFF8B6BD9 : (selected ? 0xFF4FFF7F : 0xFFC0C8D0));
        // 显隐开关（行右缘眼区；隐=紫 显=绿）
        g.drawString(host.font(), hidden ? "§5隐" : "§a显", x + TREE_W - 18, rowY + 2,
                hidden ? 0xFFC678FF : 0xFF4FFF7F);
        // 拖拽放置指示器
        if (treeDragActive) {
            if (id.equals(treeDropBeforeId)) {
                g.fill(x, rowY - 1, x + TREE_W - 2, rowY + 1, 0xFF4FC3F7);
            } else if (id.equals(treeDropAfterId)) {
                g.fill(x, rowY + ROW_H - 2, x + TREE_W - 2, rowY + ROW_H, 0xFF4FC3F7);
            } else if (id.equals(treeDropInsideId)) {
                g.fill(x + 1, rowY + 1, x + TREE_W - 3, rowY + ROW_H - 2, 0x304FC3F7);
            }
        }
        rowY += ROW_H - 1;
        for (RenderNode child : node.children()) {
            rowY = drawTreeNode(g, child, x, rowY, depth + 1, mx, my);
        }
        return rowY;
    }

    // ---- 属性检查器 ----

    /** 属性行描述（渲染和点击共用同一数据源，保证显示/交互同步）。 */
    record InspectorRow(String section, String key, String path, String value, boolean editable) {}

    /** 构建当前选中元素的属性行列表（类型 spec 展开 + 几何 + 视觉 + 其他 + 动作）。 */
    private List<InspectorRow> buildInspectorRows() {
        List<InspectorRow> rows = new ArrayList<>();
        String sel = host.selectedId();
        if (sel == null) return rows;
        RenderNode node = host.findNode(sel);
        Element el = host.findElement(sel);
        if (node == null) return rows;

        rows.add(new InspectorRow("基本", "id", "id", sel, false));
        rows.add(new InspectorRow("基本", "type", "type", node.type(), false));
        rows.add(new InspectorRow("几何", "x", "x", fmt(node.x()), true));
        rows.add(new InspectorRow("几何", "y", "y", fmt(node.y()), true));
        rows.add(new InspectorRow("几何", "width", "width", fmt(node.width()), true));
        rows.add(new InspectorRow("几何", "height", "height", fmt(node.height()), true));
        rows.add(new InspectorRow("视觉", "opacity", "opacity", propVal(el, "opacity", "1"), true));
        rows.add(new InspectorRow("视觉", "scale", "scale", propVal(el, "scale", "1"), true));
        rows.add(new InspectorRow("视觉", "rotation", "rotation", propVal(el, "rotation", "0"), true));
        rows.add(new InspectorRow("视觉", "z", "z", propVal(el, "z", "0"), true));

        if (el == null) return rows;

        // 类型 spec 展开（text → content/color/align...）
        String specKey = node.type();
        if (el.props().get(specKey) instanceof Map<?, ?> spec) {
            for (Map.Entry<?, ?> entry : spec.entrySet()) {
                String k = String.valueOf(entry.getKey());
                String v = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                rows.add(new InspectorRow(specKey, k, specKey + "." + k, v, true));
            }
        }

        // 其他（非 spec 子 Map、非几何）
        for (Map.Entry<String, Object> entry : el.props().entrySet()) {
            String k = entry.getKey();
            if (isGeometryProp(k) || k.equals(specKey)) continue;
            if (entry.getValue() instanceof Map) continue;
            rows.add(new InspectorRow("其他", k, k, String.valueOf(entry.getValue()), true));
        }

        for (Map.Entry<String, String> entry : el.actions().entrySet()) {
            rows.add(new InspectorRow("动作", entry.getKey(), entry.getKey(), entry.getValue(), false));
        }
        return rows;
    }

    private void drawInspector(GuiGraphics g, int mouseX, int mouseY) {
        int w = INSPECTOR_W;
        int x = host.width() - w;
        int y = TOOLBAR_H;
        int h = host.height() - TOOLBAR_H;
        g.fill(x, y, x + w, y + h, panelBg());
        g.fill(x, y, x + 1, y + h, 0xFF2A3040);
        String sel = host.selectedId();
        g.drawString(host.font(), "§e属性检查器", x + 6, y + 4, 0xFFFFFFFF);
        if (sel == null) {
            g.drawString(host.font(), "§7点击元素选择", x + 6, y + HEADER_H + 8, 0xFF6B7280);
            return;
        }
        List<InspectorRow> propRows = buildInspectorRows();
        int rowY = y + HEADER_H + 4;
        String lastSection = null;
        for (InspectorRow row : propRows) {
            if (!row.section().equals(lastSection)) {
                rowY = drawInspectorSection(g, x, rowY, "§7── " + row.section() + " ──");
                lastSection = row.section();
            }
            String dv = row.value();
            if (dv.length() > 20) dv = dv.substring(0, 17 + 3);
            rowY = drawInspectorRow(g, x, rowY, row.key(), dv, row.editable());
            if (rowY >= host.height() - TOOLBAR_H - ROW_H * 3) break;
        }

        // 底部操作
        rowY = host.height() - TOOLBAR_H - ROW_H * 3;
        g.drawString(host.font(), "§7Del=删除 Ctrl+C=复制", x + 4, rowY, 0xFF6B7280);
        g.drawString(host.font(), "§7Ctrl+Z=撤销 Ctrl+Y=重做", x + 4, rowY + ROW_H - 2, 0xFF6B7280);
        g.drawString(host.font(), "§7[ ]=Z层级 G=分组", x + 4, rowY + ROW_H * 2 - 4, 0xFF6B7280);
    }

    private int drawInspectorSectionHeader(int rowY, String section) {
        return rowY + ROW_H;
    }

    private int drawInspectorSection(GuiGraphics g, int x, int rowY, String label) {
        g.drawString(host.font(), label, x + 4, rowY + 2, 0xFF6B7280);
        return rowY + ROW_H;
    }

    private int drawInspectorRow(GuiGraphics g, int x, int rowY, String label, String value, boolean editable) {
        int w = INSPECTOR_W;
        g.drawString(host.font(), "§7" + label, x + 4, rowY + 2, 0xFF9AA3B2);
        int vx = x + 70;
        int vw = w - 76;
        boolean editing = label.equals(editingProp);
        if (editable) {
            g.fill(vx, rowY + 1, vx + vw, rowY + ROW_H - 1, 0xFF20242C);
            g.fill(vx, rowY + 1, vx + vw, rowY + 2, editing ? 0xFF7A8BFF : 0xFF3A4254);
        }
        String display = editing ? editBuffer : value;
        if (display != null) {
            int maxLen = vw - 6;
            String truncated = host.font().plainSubstrByWidth(display, maxLen);
            g.drawString(host.font(), truncated, vx + 3, rowY + 2, 0xFFFFFFFF);
        }
        return rowY + ROW_H;
    }

    // ---- 对齐工具 ----

    private void drawAlignPanel(GuiGraphics g, int mouseX, int mouseY) {
        int pw = 120;
        int ph = ROW_H * 6 + HEADER_H + 8;
        int px = host.width() / 2 - pw / 2;
        int py = TOOLBAR_H + 4;
        g.fill(px, py, px + pw, py + ph, 0xF0101418);
        g.fill(px, py, px + pw, py + 1, 0xFF505868);
        g.drawString(host.font(), "§e对齐工具", px + 6, py + 4, 0xFFFFFFFF);
        int rowY = py + HEADER_H + 4;
        String[] aligns = {"← 左对齐", "→ 右对齐", "↕ 水平居中", "↑ 顶对齐", "↓ 底对齐", "↔ 垂直居中"};
        for (String label : aligns) {
            boolean hover = mouseX >= px && mouseX < px + pw && mouseY >= rowY && mouseY < rowY + ROW_H - 1;
            if (hover) g.fill(px + 2, rowY, px + pw - 2, rowY + ROW_H - 1, 0xFF1A2030);
            g.drawString(host.font(), label, px + 8, rowY + 2, 0xFFC0C8D0);
            rowY += ROW_H - 1;
        }
    }

    // ---- 导出面板 ----

    private void drawExportPanel(GuiGraphics g, int mouseX, int mouseY) {
        int pw = 400;
        int ph = 300;
        int px = host.width() / 2 - pw / 2;
        int py = host.height() / 2 - ph / 2;
        g.fill(px, py, px + pw, py + ph, 0xF0101418);
        g.fill(px, py, px + pw, py + 1, 0xFF505868);
        g.fill(px, py, px + 1, py + ph, 0xFF505868);
        g.drawString(host.font(), "§e导出 YAML", px + 6, py + 4, 0xFFFFFFFF);
        if (exportText == null) {
            exportText = PageExporter.toYaml(host.page());
        }
        // 简单文本显示（前 40 行）
        String[] lines = exportText.split("\n");
        int maxLines = (ph - 30) / 11;
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i];
            if (line.length() > 60) line = line.substring(0, 57) + "...";
            g.drawString(host.font(), line, px + 6, py + 18 + i * 11, 0xFFC0C8D0);
        }
        if (lines.length > maxLines) {
            g.drawString(host.font(), "... (" + (lines.length - maxLines) + " more lines)", px + 6,
                    py + 18 + maxLines * 11, 0xFF6B7280);
        }
        // 复制按钮
        int bx = px + pw - 80;
        int by = py + ph - 16;
        g.fill(bx, by, bx + 70, by + 12, 0xFF1A2030);
        g.drawString(host.font(), exportCopied ? "§a已复制!" : "§e复制到剪贴板", bx + 4, by + 2, 0xFFFFFFFF);
        // 关闭按钮
        int cx = px + pw - 16;
        g.fill(cx, py + 2, cx + 12, py + 14, 0xFF3A2030);
        g.drawString(host.font(), "X", cx + 3, py + 4, 0xFFFF4444);
    }

    // ---- 输入处理 ----

    /** 树拖拽：鼠标按下记录起点（不激活，等位移超阈值）。 */
    private boolean treePressOnRow;
    private String treePressId;

    public void mouseDragged(double mouseX, double mouseY) {
        if (treePressOnRow && !treeDragActive) {
            double dx = mouseX - treeDragPressX;
            double dy = mouseY - treeDragPressY;
            if (dx * dx + dy * dy > 25) {
                treeDragActive = true;
                treeDragId = treePressId;
            }
        }
        if (treeDragActive) {
            updateDropTarget(mouseX, mouseY);
        }
    }

    /** 树拖拽释放：执行排序或分支变更。 */
    public void mouseReleased() {
        if (treeDragActive && treeDragId != null) {
            executeDrop();
        }
        treePressOnRow = false;
        treeDragActive = false;
        treeDragId = null;
        treeDropBeforeId = null;
        treeDropAfterId = null;
        treeDropInsideId = null;
    }

    /** 遍历可见树行，找鼠标下的放置目标。 */
    private void updateDropTarget(double mx, double my) {
        int treeX = showPalette ? PALETTE_W : 0;
        if (mx < treeX || mx > treeX + TREE_W) return;
        int rowY = TOOLBAR_H + HEADER_H + 4;
        treeDropBeforeId = null;
        treeDropAfterId = null;
        treeDropInsideId = null;
        for (RenderNode node : host.nodes()) {
            rowY = scanDropTarget(node, mx, my, treeX, rowY, 0);
        }
    }

    private int scanDropTarget(RenderNode node, double mx, double my, int treeX, int rowY, int depth) {
        if (my >= rowY && my < rowY + ROW_H - 1 && mx >= treeX && mx < treeX + TREE_W) {
            String id = node.id();
            if (id.equals(treeDragId)) return rowY; // 不能放到自己身上
            // 上 1/3 = 插入前面，下 1/3 = 插入后面，中 1/3 = 变子元素
            int rel = (int) (my - rowY);
            if (rel < ROW_H / 3) {
                treeDropBeforeId = id;
            } else if (rel > ROW_H * 2 / 3) {
                treeDropAfterId = id;
            } else {
                treeDropInsideId = id;
            }
            return rowY;
        }
        rowY += ROW_H - 1;
        for (RenderNode child : node.children()) {
            rowY = scanDropTarget(child, mx, my, treeX, rowY, depth + 1);
        }
        return rowY;
    }

    /** 执行放置：调 Host 方法重排/重挂。 */
    private void executeDrop() {
        if (treeDropBeforeId != null) {
            host.moveElementInTree(treeDragId, 0); // 移到 treeDropBeforeId 之前
        } else if (treeDropAfterId != null) {
            host.moveElementInTree(treeDragId, 1); // 移到 treeDropAfterId 之后
        } else if (treeDropInsideId != null) {
            host.reparentElement(treeDragId, treeDropInsideId);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 工具栏
        if (my < TOOLBAR_H) {
            return handleToolbarClick(mx, my);
        }

        // 导出面板（模态）
        if (showExport) {
            return handleExportClick(mx, my);
        }

        // 对齐面板
        if (showAlign && hitAlignPanel(mx, my)) {
            return true;
        }

        // 调色板
        if (showPalette && mx < PALETTE_W) {
            return handlePaletteClick(mx, my);
        }

        // 元素树
        if (showTree) {
            int treeX = showPalette ? PALETTE_W : 0;
            if (mx >= treeX && mx < treeX + TREE_W) {
                return handleTreeClick(mx, my, treeX);
            }
        }

        // 属性检查器
        if (showInspector) {
            int ix = host.width() - INSPECTOR_W;
            if (mx >= ix) {
                return handleInspectorClick(mx, my);
            }
        }

        // 调色板放置模式
        if (pendingType != null) {
            host.addElement(pendingType, mouseX, mouseY);
            pendingType = null;
            return true;
        }

        return false; // 不拦截，让 OdcScreen 处理
    }

    private boolean handleToolbarClick(int mx, int my) {
        int x = 4;
        // 按钮顺序与 drawToolbar 一致
        if (hitBtn(mx, my, x, "P")) { showPalette = !showPalette; return true; }
        x += btnW("P:调色板") + 4;
        if (hitBtn(mx, my, x, "T")) { showTree = !showTree; return true; }
        x += btnW("T:元素树") + 4;
        if (hitBtn(mx, my, x, "I")) { showInspector = !showInspector; return true; }
        x += btnW("I:检查器") + 4;
        if (hitBtn(mx, my, x, "A")) { showAlign = !showAlign; return true; }
        x += btnW("A:对齐") + 8;
        if (hitBtn(mx, my, x, "E")) { showExport = !showExport; exportText = null; exportCopied = false; return true; }
        x += btnW("E:导出YAML") + 4;
        if (hitBtn(mx, my, x, "M")) { toggleMultiSelectMode(); return true; }
        return false;
    }

    private boolean hitBtn(int mx, int my, int x, String key) {
        return mx >= x && mx < x + 100 && my >= 0 && my < TOOLBAR_H;
    }

    private int btnW(String text) {
        return host.font().width("§e" + text) + 8;
    }

    private boolean handlePaletteClick(int mx, int my) {
        int y = TOOLBAR_H + HEADER_H + 4;
        for (String[] group : PALETTE) {
            y += ROW_H - 2; // 组名
            for (int i = 1; i < group.length; i++) {
                if (mx < PALETTE_W - 2 && my >= y && my < y + ROW_H - 1) {
                    pendingType = group[i];
                    return true;
                }
                y += ROW_H - 1;
            }
            y += 2;
        }
        return true; // 吞掉调色板区域的点击
    }

    private boolean handleTreeClick(int mx, int my, int treeX) {
        // 记录拖拽起点（树行按下 = 可能是拖拽）
        int probeRowY = TOOLBAR_H + HEADER_H + 4;
        for (RenderNode node : host.nodes()) {
            int[] probe = findTreeNode(node, mx, my, treeX, probeRowY, 0);
            if (probe != null) {
                treePressOnRow = true;
                treePressId = node.id();
                treeDragPressX = mx;
                treeDragPressY = my;
                break;
            }
            probeRowY = nextTreeNodeRow(node, probeRowY);
        }
        int rowY = TOOLBAR_H + HEADER_H + 4;
        for (RenderNode node : host.nodes()) {
            int[] result = findTreeNode(node, mx, my, treeX, rowY, 0);
            if (result != null) {
                String id = node.id();
                if (result[0] == 1) {
                    // 行右缘眼区：切换元素运行时显隐（不改变选中）
                    if (mx >= treeX + TREE_W - 20) {
                        host.toggleElementHidden(id);
                        return true;
                    }
                    // 选中
                    if (Screen.hasShiftDown() && host.selectedId() != null) {
                        multiSelect.add(host.selectedId());
                        multiSelect.add(id);
                    } else if (!Screen.hasControlDown()) {
                        multiSelect.clear();
                    }
                    host.selectElement(id);
                    return true;
                }
                return true;
            }
            rowY = nextTreeNodeRow(node, rowY);
        }
        return true; // 吞掉树区域的点击
    }

    private int[] findTreeNode(RenderNode node, int mx, int my, int treeX, int rowY, int depth) {
        if (my >= rowY && my < rowY + ROW_H - 1 && mx >= treeX && mx < treeX + TREE_W) {
            return new int[]{1};
        }
        rowY += ROW_H - 1;
        for (RenderNode child : node.children()) {
            int[] r = findTreeNode(child, mx, my, treeX, rowY, depth + 1);
            if (r != null) return r;
            rowY = nextTreeNodeRow(child, rowY);
        }
        return null;
    }

    private int nextTreeNodeRow(RenderNode node, int rowY) {
        rowY += ROW_H - 1;
        for (RenderNode child : node.children()) {
            rowY = nextTreeNodeRow(child, rowY);
        }
        return rowY;
    }

    private boolean handleInspectorClick(int mx, int my) {
        int x = host.width() - INSPECTOR_W;
        int y = TOOLBAR_H + HEADER_H + 4;
        if (host.selectedId() == null) return true;
        List<InspectorRow> propRows = buildInspectorRows();
        int rowY = y + HEADER_H + 4;
        String lastSection = null;
        for (InspectorRow row : propRows) {
            if (!row.section().equals(lastSection)) {
                rowY = drawInspectorSectionHeader(rowY, row.section());
                lastSection = row.section();
            }
            if (my >= rowY && my < rowY + ROW_H && mx >= x + 70 && row.editable()) {
                beginEditProp(row.path());
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    private boolean hitAlignPanel(int mx, int my) {
        int pw = 120;
        int ph = ROW_H * 6 + HEADER_H + 8;
        int px = host.width() / 2 - pw / 2;
        int py = TOOLBAR_H + 4;
        if (mx < px || mx > px + pw || my < py || my > py + ph) return false;
        // 对齐操作
        int rowY = py + HEADER_H + 4;
        String[] aligns = {"left", "right", "center_h", "top", "bottom", "center_v"};
        for (int i = 0; i < aligns.length; i++) {
            if (my >= rowY && my < rowY + ROW_H - 1) {
                alignSelected(aligns[i]);
                return true;
            }
            rowY += ROW_H - 1;
        }
        return true;
    }

    private boolean handleExportClick(int mx, int my) {
        int pw = 400, ph = 300;
        int px = host.width() / 2 - pw / 2;
        int py = host.height() / 2 - ph / 2;
        // 关闭按钮
        int cx = px + pw - 16;
        if (mx >= cx && mx < cx + 12 && my >= py + 2 && my < py + 14) {
            showExport = false;
            return true;
        }
        // 复制按钮
        int bx = px + pw - 80;
        int by = py + ph - 16;
        if (mx >= bx && mx < bx + 70 && my >= by && my < by + 12) {
            Minecraft.getInstance().keyboardHandler.setClipboard(exportText);
            exportCopied = true;
            return true;
        }
        return true; // 模态：吞掉所有点击
    }

    // ---- 键盘 ----

    public boolean keyPressed(int keyCode, int modifiers) {
        if (showExport) {
            if (keyCode == 256) { // ESC
                showExport = false;
                return true;
            }
            return false;
        }
        // Ctrl+E = 导出
        if (keyCode == 69 && (modifiers & 2) != 0) {
            showExport = !showExport;
            exportText = null;
            exportCopied = false;
            return true;
        }
        // G = 分组
        if (keyCode == 71 && !multiSelect.isEmpty()) {
            createGroup();
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (editingProp != null && editBuffer != null) {
            editBuffer += c;
            applyLiveProp();
            return true;
        }
        return false;
    }

    public boolean keyTyped(int keyCode) {
        if (editingProp != null) {
            if (keyCode == 257 || keyCode == 335) { // Enter
                editingProp = null;
                editBuffer = null;
                return true;
            }
            if (keyCode == 259 && editBuffer != null && !editBuffer.isEmpty()) { // Backspace
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                applyLiveProp();
                return true;
            }
        }
        return false;
    }

    // ---- 编辑操作 ----

    private void beginEditProp(String prop) {
        host.pushUndo();
        editingProp = prop;
        RenderNode node = host.findNode(host.selectedId());
        editBuffer = propValueFor(node, prop);
    }

    private void applyLiveProp() {
        if (host.selectedId() == null || editingProp == null || editBuffer == null) return;
        String prop = editingProp;
        switch (prop) {
            case "x", "y" -> {
                try {
                    double v = Double.parseDouble(editBuffer.trim());
                    RenderNode node = host.findNode(host.selectedId());
                    if (node != null) {
                        double x = "x".equals(prop) ? v : node.x();
                        double y = "y".equals(prop) ? v : node.y();
                        host.setElementPos(host.selectedId(), x, y);
                    }
                } catch (NumberFormatException ignored) {}
            }
            case "width", "height" -> {
                try {
                    double v = Double.parseDouble(editBuffer.trim());
                    host.setElementProp(host.selectedId(), prop, v);
                } catch (NumberFormatException ignored) {}
            }
            default -> host.setElementPropDeep(host.selectedId(), prop, editBuffer);
        }
    }

    private void alignSelected(String mode) {
        if (multiSelect.size() < 2) return;
        host.pushUndo();
        // 找参考元素（第一个选中的）
        String refId = multiSelect.iterator().next();
        RenderNode ref = host.findNode(refId);
        if (ref == null) return;
        for (String id : multiSelect) {
            if (id.equals(refId)) continue;
            RenderNode node = host.findNode(id);
            if (node == null) continue;
            double x = node.x(), y = node.y();
            switch (mode) {
                case "left" -> x = ref.x();
                case "right" -> x = ref.x() + ref.width() - node.width();
                case "center_h" -> x = ref.x() + (ref.width() - node.width()) / 2;
                case "top" -> y = ref.y();
                case "bottom" -> y = ref.y() + ref.height() - node.height();
                case "center_v" -> y = ref.y() + (ref.height() - node.height()) / 2;
            }
            host.setElementPos(id, x, y);
        }
    }

    private void createGroup() {
        String groupId = "group_" + System.currentTimeMillis() % 10000;
        groups.put(groupId, new LinkedHashSet<>(multiSelect));
    }

    private void toggleMultiSelectMode() {
        if (multiSelect.isEmpty()) {
            String sel = host.selectedId();
            if (sel != null) multiSelect.add(sel);
        } else {
            multiSelect.clear();
        }
    }

    // ---- 工具方法 ----

    private static boolean isGeometryProp(String key) {
        return "x".equals(key) || "y".equals(key) || "width".equals(key) || "height".equals(key)
                || "type".equals(key) || "children".equals(key) || "actions".equals(key);
    }

    private String propValueFor(RenderNode node, String prop) {
        if (node == null) return "0";
        return switch (prop) {
            case "x" -> fmt(node.x());
            case "y" -> fmt(node.y());
            case "width" -> fmt(node.width());
            case "height" -> fmt(node.height());
            default -> {
                Element el = host.findElement(host.selectedId());
                Object v = el == null ? null : getNested(el.props(), prop);
                yield v == null ? "0" : String.valueOf(v);
            }
        };
    }

    /** dotted path 取值："text.content" → props.get("text").get("content")。 */
    static Object getNested(Map<String, Object> props, String path) {
        String[] parts = path.split("\\\\.");
        Object current = props.get(parts[0]);
        for (int i = 1; i < parts.length && current instanceof Map<?, ?> m; i++) {
            current = m.get(parts[i]);
        }
        return current;
    }

    private String propVal(Element el, String key, String def) {
        if (el == null) return def;
        Object v = el.props().get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ---- 公开 API ----

    public void reset() {
        multiSelect.clear();
        editingProp = null;
        editBuffer = null;
        pendingType = null;
        showExport = false;
        exportText = null;
    }

    public boolean isPlacing() {
        return pendingType != null;
    }

    public void addToMultiSelect(String id) {
        multiSelect.add(id);
    }

    public void clearMultiSelect() {
        multiSelect.clear();
    }

    public Set<String> multiSelect() {
        return multiSelect;
    }
}
