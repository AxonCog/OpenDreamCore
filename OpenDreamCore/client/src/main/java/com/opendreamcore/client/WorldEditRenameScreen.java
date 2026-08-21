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

    final class WorldEditRenameScreen extends net.minecraft.client.gui.screens.Screen {
        private final List<String> ids;
        private net.minecraft.client.gui.components.EditBox prefixBox;
        private net.minecraft.client.gui.components.EditBox startBox;

        WorldEditRenameScreen(List<String> ids) {
            super(Component.literal("批量重命名 · " + ids.size() + " 个元素"));
            this.ids = ids;
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            this.prefixBox = new net.minecraft.client.gui.components.EditBox(this.font,
                    cx - 150, this.height / 2 - 40, 200, 20, Component.literal("前缀"));
            this.prefixBox.setMaxLength(32);
            this.addRenderableWidget(this.prefixBox);
            this.startBox = new net.minecraft.client.gui.components.EditBox(this.font,
                    cx + 60, this.height / 2 - 40, 60, 20, Component.literal("起始"));
            this.startBox.setValue("1");
            this.startBox.setMaxLength(6);
            this.addRenderableWidget(this.startBox);
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("执行重命名"), btn -> {
                        int start = 1;
                        try {
                            start = Integer.parseInt(this.startBox.getValue().trim());
                        } catch (NumberFormatException ignored) {
                        }
                        ClientController.get().renameWorldElements(this.ids, this.prefixBox.getValue(), start);
                        this.onClose();
                    }).bounds(cx - 80, this.height / 2 + 10, 160, 20).build());
            this.setInitialFocus(this.prefixBox);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) { // ESC 关闭
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    8, 0xFFE0E0E0);
            String hint = "新 id = 前缀 + 序号（如 item_1、item_2…）；字母/数字/下划线/短横线 · ESC 取消";
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
