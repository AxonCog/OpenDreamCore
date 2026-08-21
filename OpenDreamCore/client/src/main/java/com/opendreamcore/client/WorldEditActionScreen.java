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

    final class WorldEditActionScreen extends net.minecraft.client.gui.screens.Screen {
        private static final String[] SLOTS = {"click", "hover", "input"};
        private final String elementId;
        private final Element element;

        WorldEditActionScreen(String elementId, Element element) {
            super(Component.literal("动作绑定 · " + elementId));
            this.elementId = elementId;
            this.element = element;
        }

        @Override
        protected void init() {
            ClientController.get().setWorldEditHighlight(java.util.List.of(this.elementId));
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            for (String s : SLOTS) {
                keys.add(s);
            }
            keys.addAll(this.element.actions().keySet());
            int i = 0;
            for (String key : keys) {
                String script = this.element.actions().get(key);
                int y = 34 + i * 26;
                String label = key + (script == null || script.isBlank() ? "  (未绑定)" : "  " + ClientController.shortText(script));
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(label), btn -> Minecraft.getInstance().setScreen(
                                new WorldEditPropScreen("编辑 " + key + " 动作脚本（Enter 提交）",
                                        script == null ? "" : script,
                                        v -> ClientController.get().setWorldAction(this.elementId, key, v))))
                        .bounds(this.width / 2 - 180, y, 300, 20).build());
                if (script != null && !script.isBlank()) {
                    this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                            Component.literal("清除"), btn -> {
                                ClientController.get().setWorldAction(this.elementId, key, "");
                                Minecraft.getInstance().setScreen(
                                        new WorldEditActionScreen(this.elementId, this.element));
                            }).bounds(this.width / 2 + 128, y, 52, 20).build());
                }
                i++;
            }
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    8, 0xFFE0E0E0);
            String hint = "点击槽位编辑脚本（DreamLang），Enter 提交 · ESC 取消；动作保存后写回页面文件";
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
