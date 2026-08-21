package com.opendreamcore.client;

/**
 * 世界编辑器·
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

    final class WorldEditPropsScreen extends net.minecraft.client.gui.screens.Screen {
        private final String elementId;
        private final List<String[]> props;
        private final java.util.List<net.minecraft.client.gui.components.Button> rowButtons =
                new java.util.ArrayList<>();
        private net.minecraft.client.gui.components.EditBox filterBox;

        WorldEditPropsScreen(String elementId, Element element) {
            super(Component.literal("编辑属性 · " + elementId));
            this.elementId = elementId;
            this.props = ClientController.elementPropPaths(element);
        }

        @Override
        protected void init() {
            ClientController.get().setWorldEditHighlight(java.util.List.of(this.elementId));
            this.filterBox = new net.minecraft.client.gui.components.EditBox(this.font,
                    this.width / 2 - 130, 26, 260, 20, Component.literal("过滤"));
            this.filterBox.setMaxLength(32);
            this.filterBox.setResponder(s -> rebuildRows());
            this.addRenderableWidget(this.filterBox);
            this.setInitialFocus(this.filterBox);
            rebuildRows();
        }

        /** 按路径子串过滤重建行（含 复制 按钮）。 */
        private void rebuildRows() {
            for (net.minecraft.client.gui.components.Button b : this.rowButtons) {
                this.removeWidget(b);
            }
            this.rowButtons.clear();
            String q = this.filterBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
            java.util.List<String[]> shown = new java.util.ArrayList<>();
            for (String[] prop : this.props) {
                if (q.isEmpty() || prop[0].toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    shown.add(prop);
                }
            }
            int cols = 2, bw = 260, bh = 22, gap = 8;
            int rows = (shown.size() + cols - 1) / cols;
            int totalW = cols * bw + (cols - 1) * gap;
            int x0 = this.width / 2 - totalW / 2;
            int y0 = Math.max(20, this.height / 2 - rows * (bh + gap) / 2 - 20) + 32;
            for (int i = 0; i < shown.size() && i < 60; i++) {
                int row = i / cols, col = i % cols;
                String[] prop = shown.get(i);
                String path = prop[0];
                String value = ClientController.shortText(prop[1]);
                int bx = x0 + col * (bw + gap);
                int by = y0 + row * (bh + gap);
                net.minecraft.client.gui.components.Button edit = net.minecraft.client.gui.components.Button.builder(
                        Component.literal(path + " = " + value), btn -> {
                            Minecraft.getInstance().setScreen(new WorldEditPropQuickScreen(
                                    "编辑 " + path, elementId, path, prop[1]));
                        }).bounds(bx, by, bw - 60, bh).build();
                this.addRenderableWidget(edit);
                this.rowButtons.add(edit);
                net.minecraft.client.gui.components.Button copy = net.minecraft.client.gui.components.Button.builder(
                        Component.literal("复制"), btn ->
                                WorldEditor.get().copyWorldPropValue(path, prop[1]))
                        .bounds(bx + bw - 54, by, 54, bh).build();
                this.addRenderableWidget(copy);
                this.rowButtons.add(copy);
            }
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    6, 0xFFE0E0E0);
            String hint = "顶部输入过滤路径子串（如 color）";
            g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2,
                    this.height - 16, 0xFF90A4AE);
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            ClientController.get().clearWorldEditHighlight();
            super.onClose();
        }
}
