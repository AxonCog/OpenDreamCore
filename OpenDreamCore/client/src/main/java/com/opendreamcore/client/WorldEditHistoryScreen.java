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

    final class WorldEditHistoryScreen extends net.minecraft.client.gui.screens.Screen {
        WorldEditHistoryScreen() {
            super(Component.literal("编辑历史"));
        }

        @Override
        protected void init() {
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("撤消一步"), btn ->
                            WorldEditor.get().undoWorldEdit())
                    .bounds(this.width / 2 - 180, 26, 120, 20).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("重做一步"), btn ->
                            WorldEditor.get().redoWorldEdit())
                    .bounds(this.width / 2 - 50, 26, 120, 20).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("关闭"), btn -> this.onClose())
                    .bounds(this.width / 2 + 80, 26, 60, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    6, 0xFFE0E0E0);
            g.drawString(this.font, "撤消栈（最新在前）", this.width / 2 - 190, 56, 0xFF80CBC4);
            g.drawString(this.font, "重做栈", this.width / 2 + 30, 56, 0xFF80CBC4);
            java.util.List<String> undo = ClientController.get().worldUndoLabels();
            java.util.List<String> redo = ClientController.get().worldRedoLabels();
            int maxRows = 16;
            for (int i = 0; i < undo.size() && i < maxRows; i++) {
                String label = (i + 1) + ". " + undo.get(i);
                g.drawString(this.font, label, this.width / 2 - 190, 70 + i * 10,
                        i == 0 ? 0xFFFFD54F : 0xFFB0BEC5);
            }
            if (undo.size() > maxRows) {
                g.drawString(this.font, "…", this.width / 2 - 190, 70 + maxRows * 10, 0xFF78909C);
            }
            for (int i = 0; i < redo.size() && i < maxRows; i++) {
                g.drawString(this.font, (i + 1) + ". " + redo.get(i), this.width / 2 + 30, 70 + i * 10,
                        i == 0 ? 0xFFA5D6A7 : 0xFF90A4AE);
            }
            if (redo.size() > maxRows) {
                g.drawString(this.font, "…", this.width / 2 + 30, 70 + maxRows * 10, 0xFF78909C);
            }
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
}
