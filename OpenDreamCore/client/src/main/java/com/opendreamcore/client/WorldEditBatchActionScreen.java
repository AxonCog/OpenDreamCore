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

    final class WorldEditBatchActionScreen extends net.minecraft.client.gui.screens.Screen {
        private static final String[] SLOTS = {"click", "hover", "input"};
        private final java.util.List<String> targets;

        WorldEditBatchActionScreen(java.util.List<String> targets) {
            super(Component.literal("批量动作绑定 · " + targets.size() + " 元素"));
            this.targets = new java.util.ArrayList<>(targets);
        }

        @Override
        protected void init() {
            ClientController.get().setWorldEditHighlight(this.targets);
            int i = 0;
            for (String key : SLOTS) {
                int y = 34 + i * 26;
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(key + "  点击绑定脚本"), btn -> Minecraft.getInstance().setScreen(
                                new WorldEditPropScreen("批量绑定 " + key + " 动作脚本（Enter 提交）", "",
                                        v -> ClientController.get().setWorldActionBatch(this.targets, key, v))))
                        .bounds(this.width / 2 - 180, y, 300, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("绑定 " + key + " 动作脚本到全部 "
                                        + this.targets.size() + " 个元素（留空提交 = 清除）")))
                        .build());
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("清除"), btn -> {
                            ClientController.get().setWorldActionBatch(this.targets, key, "");
                            Minecraft.getInstance().setScreen(new WorldEditBatchActionScreen(this.targets));
                        }).bounds(this.width / 2 + 128, y, 52, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("清除全部目标元素的 " + key + " 动作")))
                        .build());
                i++;
            }
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    8, 0xFFE0E0E0);
            String hint = "脚本应用到全部目标元素（DreamLang），Enter 提交 · ESC 取消；保存后写回页面文件";
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
