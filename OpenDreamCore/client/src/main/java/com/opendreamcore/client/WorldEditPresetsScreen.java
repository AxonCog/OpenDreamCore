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

    final class WorldEditPresetsScreen extends net.minecraft.client.gui.screens.Screen {
        private int pageIdx;

        WorldEditPresetsScreen() {
            super(Component.literal("背景预设"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("＋保存当前"), btn -> {
                        WorldBackgroundEditor.get().saveWorldBackgroundPreset();
                        this.rebuild();
                    }).bounds(cx - 150, 12, 90, 18).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("◀"), btn -> {
                        this.pageIdx = Math.max(0, this.pageIdx - 1);
                        this.rebuild();
                    }).bounds(cx - 56, 12, 28, 18).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("▶"), btn -> {
                        this.pageIdx++;
                        this.rebuild();
                    }).bounds(cx - 24, 12, 28, 18).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("关闭"), btn -> this.onClose())
                    .bounds(cx + 80, 12, 70, 18).build());
            java.util.List<String[]> presets = ClientController.get().worldBgPresetList();
            int rows = 12;
            int total = presets.size();
            if (total > 0 && this.pageIdx * rows >= total) {
                this.pageIdx = Math.max(0, (total - 1) / rows);
            }
            int start = this.pageIdx * rows;
            for (int i = 0; i < rows && start + i < total; i++) {
                String[] p = presets.get(start + i);
                String label = "#" + (start + i + 1) + " " + p[0] + "  " + p[1];
                if (label.length() > 44) {
                    label = label.substring(0, 44) + "…";
                }
                final int fi = start + i;
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(label), btn -> {
                            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                                WorldBackgroundEditor.get().deleteWorldBackgroundPresetAt(fi);
                            } else {
                                WorldBackgroundEditor.get().loadWorldBackgroundPresetAt(fi);
                            }
                            this.rebuild();
                        }).bounds(cx - 150, 36 + i * 18, 300, 16)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("点击载入 · Shift+点击删除")))
                        .build());
            }
        }

        private void rebuild() {
            this.clearWidgets();
            this.init();
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 20, 0xFFFFD54F);
            int total = ClientController.get().worldBgPresetList().size();
            String info = "共 " + total + " 条预设 · 第 " + (this.pageIdx + 1) + " 页 · 点击载入 · Shift+点击删除";
            g.drawString(this.font, info, this.width / 2 - this.font.width(info) / 2,
                    this.height / 2 + 12 * 18 + 6, 0xFF90A4AE);
            super.render(g, mouseX, mouseY, partialTick);
            com.opendreamcore.client.ClientController.renderFocusRing(g, this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
}
