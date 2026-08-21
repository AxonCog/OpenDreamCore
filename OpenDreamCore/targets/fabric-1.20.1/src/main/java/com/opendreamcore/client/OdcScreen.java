package com.opendreamcore.client;

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

/**
 * OpenDreamCore 页面屏幕：布局树交给 UiRenderer 绘制，交互事件发给服务端裁决。
 */
public final class OdcScreen extends Screen implements UiRenderer.State {

    private final Page page;
    private final List<RenderNode> nodes;
    private final UiSession session;

    // 交互本地状态（服务端裁决前先本地响应）
    private final Map<String, String> inputText = new LinkedHashMap<>();
    private final Map<String, Double> sliderValue = new LinkedHashMap<>();
    private final Map<String, Boolean> toggleValue = new LinkedHashMap<>();
    private final Map<String, String> dropdownValue = new LinkedHashMap<>();
    private final Map<String, Boolean> dropdownOpen = new LinkedHashMap<>();
    private String focusedId;
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

    public OdcScreen(Page page, List<RenderNode> nodes, UiSession session) {
        super(Component.literal(page.title() == null ? "OpenDreamCore" : page.title()));
        this.page = page;
        this.nodes = nodes;
        this.session = session;
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
        this.nodes.clear();
        this.nodes.addAll(newNodes);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** ESC/外部关闭：与页面栈同步（ClientController.close 会 setScreen）。 */
    @Override
    public void onClose() {
        ClientController.get().close();
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

    // ---------- 渲染 ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Object bg = page.options().get("background");
        if (bg == null || Boolean.parseBoolean(String.valueOf(bg))) {
            g.fill(0, 0, this.width, this.height, 0xA0000000);
        }
        // 页面可拖动：整体偏移
        g.pose().pushPose();
        g.pose().translate(offsetX, offsetY, 0);
        UiRenderer.draw(g, this.font, nodes, mouseX - (int) offsetX, mouseY - (int) offsetY, this, page.variables());
        if (editMode) {
            drawEditOverlay(g, mouseX - (int) offsetX, mouseY - (int) offsetY);
        }
        g.pose().popPose();
        trackHover(mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    /** 编辑模式：元素虚线边框 + id 标签 + 选中高亮。 */
    private void drawEditOverlay(GuiGraphics g, int mouseX, int mouseY) {
        for (RenderNode node : nodes) {
            drawEditNode(g, node);
        }
        // 顶部提示
        g.fill(0, 0, 200, 16, 0xC0000000);
        g.drawString(this.font, "编辑模式: 拖动元素 | ESC 退出", 2, 4, 0xFFFFD54F);
    }

    private void drawEditNode(GuiGraphics g, RenderNode node) {
        if (!node.visible()) {
            return;
        }
        int x1 = (int) node.x();
        int y1 = (int) node.y();
        int x2 = (int) (node.x() + Math.max(node.width(), 0));
        int y2 = (int) (node.y() + Math.max(node.height(), 0));
        int color = node.id().equals(selectedId) ? 0xFFFFFF00 : 0x80FFFFFF;
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
        g.drawString(this.font, node.id(), x1 + 2, y1 + 2, color);
        for (RenderNode child : node.children()) {
            drawEditNode(g, child);
        }
    }

    public void setEditMode(boolean on) {
        this.editMode = on;
        this.selectedId = null;
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** hover 元素的 tooltip：服务端注册表优先，其次 YAML 静态（tooltip: 文本 或 {content,color}）。 */
    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        RenderNode hit = hit(mouseX, mouseY);
        if (hit == null || hit.source() == null) {
            return;
        }
        String text = null;
        int color = 0xFFFFFFFF;
        // 服务端 tooltip（动态，可插值）
        String server = ClientController.get().tooltips().get(hit.id());
        if (server != null && !server.isEmpty()) {
            text = server;
        } else {
            Object raw = hit.source().props().get("tooltip");
            if (raw instanceof Map<?, ?> m) {
                text = UiRenderer.str(m.get("content"));
                color = UiStyle.color(m.get("color"), 0xFFFFFFFF);
            } else if (raw != null) {
                text = String.valueOf(raw);
            }
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        text = UiRenderer.interpolate(hit, text, page.variables());
        g.renderTooltip(this.font, Component.literal(text), mouseX, mouseY);
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
        if (editMode) {
            RenderNode hit = hit(mouseX, mouseY);
            selectedId = hit == null ? null : hit.id();
            return true; // 编辑模式吞掉点击，不发事件
        }
        RenderNode hit = hit(mouseX, mouseY);
        if (hit == null) {
            // 点空白：页面可拖动就从这里拖
            if (isDraggable() && button == 0) {
                startDrag(mouseX, mouseY);
                return true;
            }
            focusedId = null;
            return false;
        }
        if (!hit.enabled()) {
            return true;
        }
        switch (hit.type()) {
            case "input" -> {
                focusedId = hit.id();
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            case "slider" -> {
                draggingId = hit.id();
                updateSlider(hit, mouseX);
                send(hit, UiEvent.Trigger.PRESS, null);
            }
            case "toggle" -> {
                boolean next = !Boolean.TRUE.equals(toggleValue.get(hit.id()));
                toggleValue.put(hit.id(), next);
                send(hit, UiEvent.Trigger.CLICK, String.valueOf(next));
            }
            case "dropdown" -> {
                boolean nowOpen = Boolean.TRUE.equals(dropdownOpen.get(hit.id()));
                dropdownOpen.put(hit.id(), !nowOpen);
                // 展开时点选项：命中选项行则选中
                if (nowOpen) {
                    String picked = pickDropdownOption(hit, mouseY);
                    if (picked != null) {
                        dropdownValue.put(hit.id(), picked);
                        dropdownOpen.put(hit.id(), false);
                        send(hit, UiEvent.Trigger.INPUT, picked);
                        return true;
                    }
                }
                send(hit, UiEvent.Trigger.CLICK, null);
            }
            default -> send(hit, UiEvent.Trigger.CLICK, null);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
        if (editMode && selectedId != null) {
            // 编辑拖动：按增量更新元素位置（LayoutEngine 覆盖）
            for (RenderNode node : nodes) {
                RenderNode target = findNode(node, selectedId);
                if (target != null) {
                    String pageId = page.id() == null ? "page" : page.id();
                    ClientController.get().elementEdits().set(pageId, selectedId,
                            target.x() + dragX, target.y() + dragY);
                    ClientController.get().refreshCurrent();
                    return true;
                }
            }
        }
        if (dragging) {
            offsetX = dragOriginX + (mouseX - dragStartX);
            offsetY = dragOriginY + (mouseY - dragStartY);
            return true;
        }
        if (draggingId == null) {
            return false;
        }
        for (RenderNode node : nodes) {
            if (node.id().equals(draggingId)) {
                updateSlider(node, mouseX);
                return true;
            }
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

    private void startDrag(double mouseX, double mouseY) {
        dragging = true;
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragOriginX = offsetX;
        dragOriginY = offsetY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        RenderNode hit = hit(mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        send(hit, UiEvent.Trigger.SCROLL, String.valueOf((int) verticalAmount));
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedId == null) {
            return false;
        }
        for (RenderNode node : nodes) {
            if (node.id().equals(focusedId) && "input".equals(node.type())) {
                String text = inputText.getOrDefault(focusedId, "");
                inputText.put(focusedId, text + codePoint);
                send(node, UiEvent.Trigger.INPUT, inputText.get(focusedId));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            if (editMode) {
                editMode = false;
                selectedId = null;
                return true; // 先退出编辑模式，不关页面
            }
            Object allow = page.options().get("allowEscClose");
            if (allow != null && !Boolean.parseBoolean(String.valueOf(allow))) {
                return true; // 页面声明禁止 ESC 关闭
            }
        }
        if (focusedId != null) {
            for (RenderNode node : nodes) {
                if (node.id().equals(focusedId) && "input".equals(node.type())) {
                    if (keyCode == 259 && !inputText.getOrDefault(focusedId, "").isEmpty()) { // 退格
                        String text = inputText.get(focusedId);
                        inputText.put(focusedId, text.substring(0, text.length() - 1));
                        send(node, UiEvent.Trigger.INPUT, inputText.get(focusedId));
                        return true;
                    }
                    if (keyCode == 258) { // Tab 切焦点
                        focusedId = null;
                        return true;
                    }
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private void updateSlider(RenderNode node, double mouseX) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "slider");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        double ratio = (mouseX - node.x()) / Math.max(node.width(), 1);
        ratio = Math.max(0, Math.min(1, ratio));
        double value = min + (max - min) * ratio;
        sliderValue.put(node.id(), value);
        send(node, UiEvent.Trigger.INPUT, String.valueOf(Math.round(value * 100) / 100.0));
    }

    /** 展开的下拉框里点选项：返回选中的项（没点中返回 null）。 */
    private String pickDropdownOption(RenderNode node, double mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "dropdown");
        Object raw = spec.get("options");
        if (!(raw instanceof List<?> options)) {
            return null;
        }
        int index = (int) ((mouseY - (node.y() + node.height())) / 14);
        if (index < 0 || index >= options.size()) {
            return null;
        }
        return String.valueOf(options.get(index));
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

    private void send(RenderNode node, UiEvent.Trigger trigger, String data) {
        ClientController.get().sendEvent(session.event(node.id(), trigger, data));
    }
}
