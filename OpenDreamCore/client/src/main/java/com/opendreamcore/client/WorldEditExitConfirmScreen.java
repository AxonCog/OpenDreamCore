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

    final class WorldEditExitConfirmScreen extends net.minecraft.client.gui.screens.Screen {
        WorldEditExitConfirmScreen() {
            super(Component.literal("未保存修改"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            int cy = this.height / 2;
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("保存并退出"), btn -> {
                        WorldEditor.get().saveWorldEdits();
                        ClientController.get().exitWorldEditMode();
                        this.onClose();
                    }).bounds(cx - 140, cy - 10, 88, 20).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("放弃并退出"), btn -> {
                        WorldEditor.get().discardWorldEdits();
                        ClientController.get().exitWorldEditMode();
                        this.onClose();
                    }).bounds(cx - 44, cy - 10, 88, 20).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("取消"), btn -> this.onClose())
                    .bounds(cx + 52, cy - 10, 60, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    this.height / 2 - 34, 0xFFFFD54F);
            String count = "有 " + ClientController.get().worldPendingEditCount()
                    + " 项未保存修改（保存后写回页面文件；放弃将丢弃草稿）";
            g.drawString(this.font, count, this.width / 2 - this.font.width(count) / 2,
                    this.height / 2 - 22, 0xFFE0E0E0);
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
}
