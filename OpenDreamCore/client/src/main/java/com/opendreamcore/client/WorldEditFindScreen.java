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

    final class WorldEditFindScreen extends net.minecraft.client.gui.screens.Screen {
        private final java.util.List<net.minecraft.client.gui.components.Button> resultButtons =
                new java.util.ArrayList<>();
        private net.minecraft.client.gui.components.EditBox box;

        WorldEditFindScreen() {
            super(Component.literal("元素查找（输入 id 子串过滤）"));
        }

        @Override
        protected void init() {
            int bw = 380;
            this.box = new net.minecraft.client.gui.components.EditBox(this.font,
                    this.width / 2 - bw / 2, 26, bw, 20, Component.literal("id"));
            this.box.setMaxLength(64);
            this.box.setCanLoseFocus(false);
            this.box.setResponder(s -> rebuildResults());
            this.addRenderableWidget(this.box);
            this.setInitialFocus(this.box);
            // 显示全部隐藏元素（与 J 键等效；隐藏元素经此恢复可见）
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("显示全部隐藏 (J)"), btn -> {
                        ClientController.get().showAllWorldElements();
                        rebuildResults();
                    }).bounds(this.width / 2 + 20, 26, 120, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("显示全部会话级/持久隐藏的元素")))
                    .build());
            rebuildResults();
        }

        private void rebuildResults() {
            for (net.minecraft.client.gui.components.Button b : this.resultButtons) {
                this.removeWidget(b);
            }
            this.resultButtons.clear();
            java.util.List<String[]> matches = ClientController.get().findWorldElements(this.box.getValue());
            String pid = ClientController.get().worldPage == null ? "world"
                    : ClientController.get().worldPage.id() == null ? "world"
                    : ClientController.get().worldPage.id();
            int bw = 380;
            int y0 = 56;
            for (int i = 0; i < matches.size(); i++) {
                String[] m = matches.get(i);
                final String id = m[0];
                int depth = m.length > 3 && m[3] != null
                        ? Integer.parseInt(m[3]) : 0;
                String indent = depth > 0 ? "  ".repeat(Math.min(depth, 8)) : "";
                boolean hid = !ClientController.get().worldElementVisible(pid, id);
                String label = (hid ? "🕶 " : "") + indent + m[0] + " · " + m[1] + " · (" + m[2] + ")"
                        + (hid ? " · 隐藏" : "");
                int y = y0 + i * 22;
                if (y > this.height - 52) {
                    break;
                }
                net.minecraft.client.gui.components.Button btn =
                        net.minecraft.client.gui.components.Button.builder(Component.literal(label),
                                b -> {
                                    ClientController.get().focusWorldElement(id);
                                    this.onClose();
                                }).bounds(this.width / 2 - bw / 2, y, bw, 20).build();
                this.addRenderableWidget(btn);
                this.resultButtons.add(btn);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) { // ESC 关闭
                this.onClose();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter 定位第一个
                java.util.List<String[]> matches = ClientController.get().findWorldElements(this.box.getValue());
                if (!matches.isEmpty()) {
                    ClientController.get().focusWorldElement(matches.get(0)[0]);
                    this.onClose();
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    8, 0xFFE0E0E0);
            String hint = "id 子串过滤 · type:text 按类型 · 嵌套缩进 · 🕶=隐藏元素 · Enter 定位第一个 · ESC 关闭";
            g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2,
                    this.height - 16, 0xFF90A4AE);
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
}
