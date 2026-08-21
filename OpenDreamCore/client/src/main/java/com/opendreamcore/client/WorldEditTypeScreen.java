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

    final class WorldEditTypeScreen extends net.minecraft.client.gui.screens.Screen {
        private static final String[] TYPES = {"text", "rect", "item_slot", "image", "slider", "toggle",
                "checkbox", "dropdown", "progress", "tabs"};

        WorldEditTypeScreen() {
            super(Component.literal("新增世界元素"));
        }

        @Override
        protected void init() {
            int cols = 4, bw = 108, bh = 24, gap = 8;
            int rows = (TYPES.length + cols - 1) / cols;
            int totalW = cols * bw + (cols - 1) * gap;
            int x0 = this.width / 2 - totalW / 2;
            int y0 = this.height / 2 - (rows * (bh + gap)) / 2;
            for (int i = 0; i < TYPES.length; i++) {
                int row = i / cols, col = i % cols;
                String type = TYPES[i];
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(type), btn -> {
                            if ("text".equals(type)) {
                                // 文本元素：先输入初始内容
                                Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                                        "新文本内容（Enter 提交）", "新文本",
                                        content -> WorldEditor.get().createWorldElement("text", content)));
                            } else {
                                WorldEditor.get().createWorldElement(type, null);
                                this.onClose();
                            }
                        }).bounds(x0 + col * (bw + gap), y0 + row * (bh + gap), bw, bh).build());
            }
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    this.height / 2 - 40, 0xFFE0E0E0);
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
}
