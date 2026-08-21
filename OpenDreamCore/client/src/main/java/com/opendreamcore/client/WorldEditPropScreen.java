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

    final class WorldEditPropScreen extends net.minecraft.client.gui.screens.Screen {
        private final String title;
        private final String initial;
        private final java.util.function.Consumer<String> onCommit;
        private net.minecraft.client.gui.components.EditBox box;

        WorldEditPropScreen(String title, String initial, java.util.function.Consumer<String> onCommit) {
            super(Component.literal(title));
            this.title = title;
            this.initial = initial == null ? "" : initial;
            this.onCommit = onCommit;
        }

        @Override
        protected void init() {
            int bw = 320;
            this.box = new net.minecraft.client.gui.components.EditBox(this.font,
                    this.width / 2 - bw / 2, this.height / 2 - 10, bw, 20, Component.literal("值"));
            this.box.setValue(this.initial);
            this.box.setMaxLength(1024);
            this.box.setCanLoseFocus(false);
            this.addRenderableWidget(this.box);
            this.setInitialFocus(this.box);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) { // ESC 取消
                this.onClose();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter 提交
                this.onCommit.accept(this.box.getValue());
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    this.height / 2 - 32, 0xFFE0E0E0);
            g.drawString(this.font, "Enter 提交 · ESC 取消", this.width / 2 - 52,
                    this.height / 2 + 16, 0xFF90A4AE);
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
}
