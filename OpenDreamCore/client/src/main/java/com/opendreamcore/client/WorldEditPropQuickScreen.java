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

    final class WorldEditPropQuickScreen extends net.minecraft.client.gui.screens.Screen {
        private static final String[] PALETTE = {
                "#FFFFFF", "#F5F5F5", "#E0E0E0", "#9E9E9E", "#616161", "#212121", "#000000", "#607D8B",
                "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
                "#795548", "#D7CCC8"};
        private final String title;
        private final List<String> targets;
        private final String path;
        private final String initial;
        private net.minecraft.client.gui.components.EditBox box;

        WorldEditPropQuickScreen(String title, String elementId, String path, String initial) {
            this(title, List.of(elementId), path, initial);
        }

        /** 批量目标版：多选元素同一属性一次编辑（提交走 applyWorldEditPropBatch）。 */
        WorldEditPropQuickScreen(String title, List<String> targets, String path, String initial) {
            super(Component.literal(title));
            this.title = title;
            this.targets = targets;
            this.path = path;
            this.initial = initial == null ? "" : initial;
        }

        /** 提交到单个目标或批量目标。 */
        private void commit(String value) {
            if (this.targets.size() == 1) {
                WorldEditor.get().applyWorldEditProp(this.targets.get(0), this.path, value);
            } else {
                WorldEditor.get().applyWorldEditPropBatch(this.targets, this.path, value);
            }
        }

        private boolean isColor() {
            if (this.path.endsWith("color") || this.path.endsWith("Color")) {
                return true;
            }
            String v = this.initial.trim();
            return v.matches("(?i)#[0-9a-f]{6}") || v.matches("(?i)#[0-9a-f]{3}");
        }

        private static List<String> quickValues(String path) {
            return switch (path) {
                case "text.align" -> List.of("left", "center", "right");
                case "hologram.scale" -> List.of("0.01", "0.02", "0.05", "0.1", "0.2", "0.5");
                case "hologram.width" -> List.of("0.5", "1", "1.5", "2", "3", "4");
                case "hologram.height" -> List.of("0.25", "0.5", "1", "1.5", "2", "3");
                case "hologram.z" -> List.of("-1", "-0.5", "0", "0.5", "1");
                case "opacity" -> List.of("0.2", "0.5", "0.8", "1");
                case "hologram.border.width" -> List.of("0", "2", "4", "8", "16");
                case "slider.step" -> List.of("1", "5", "10");
                case "hologram.radius", "rect.radius" -> List.of("0", "0.2", "0.5", "1");
                default -> List.of();
            };
        }

        @Override
        protected void init() {
            ClientController.get().setWorldEditHighlight(this.targets);
            ClientController.get().setWorldEditLabel(this.path);
            int cx = this.width / 2;
            this.box = new net.minecraft.client.gui.components.EditBox(this.font,
                    cx - 160, this.height / 2 - 52, 320, 20, Component.literal("值"));
            this.box.setValue(this.initial);
            this.box.setMaxLength(1024);
            this.box.setCanLoseFocus(false);
            this.addRenderableWidget(this.box);
            this.setInitialFocus(this.box);
            if (ClientController.get().hasWorldPropClipboard()) {
                // 粘贴值：把属性剪贴板值应用到当前路径（含批量目标）
                String srcPath = ClientController.get().getWorldPropClipboardPath();
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("粘贴值"), btn -> {
                            String v = ClientController.get().getWorldPropClipboard();
                            WorldEditPropQuickScreen.this.commit(v);
                            WorldEditPropQuickScreen.this.box.setValue(v);
                        }).bounds(cx + 166, this.height / 2 - 52, 60, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("来源: " + (srcPath == null ? "?" : srcPath))))
                        .build());
            }
            if (this.isColor()) {
                // 调色板：点击即应用（保持打开便于连续试色，并入一步撤消）
                int sw = 30, sh = 18, gap = 4, perRow = 8;
                int x0 = cx - (perRow * sw + (perRow - 1) * gap) / 2;
                int y0 = this.height / 2 - 2;
                for (int i = 0; i < PALETTE.length; i++) {
                    int row = i / perRow, col = i % perRow;
                    this.addRenderableWidget(new PaletteButton(x0 + col * (sw + gap),
                            y0 + row * (sh + gap), sw, sh, PALETTE[i]));
                }
            } else {
                List<String> q = quickValues(this.path);
                if (!q.isEmpty()) {
                    int bw = 64, gap = 6;
                    int x0 = cx - (q.size() * bw + (q.size() - 1) * gap) / 2;
                    int y0 = this.height / 2 + 6;
                    for (int i = 0; i < q.size(); i++) {
                        final String v = q.get(i);
                        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                                Component.literal(v), btn -> {
                                    WorldEditPropQuickScreen.this.commit(v);
                                    this.onClose();
                                }).bounds(x0 + i * (bw + gap), y0, bw, 20).build());
                    }
                }
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) { // ESC 取消
                this.onClose();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter 提交
                this.commit(this.box.getValue());
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    this.height / 2 - 74, 0xFFE0E0E0);
            String hint = this.isColor()
                    ? "点色板即应用（连续试色并入一步撤消）· Enter 提交 · ESC 取消"
                    : "点常用值即应用 · Enter 提交 · ESC 取消";
            g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2,
                    this.height - 18, 0xFF90A4AE);
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

        /** 调色板色块：点击即应用（保持打开便于连续试色）。 */
        private final class PaletteButton extends net.minecraft.client.gui.components.AbstractWidget {
            private final String color;

            PaletteButton(int x, int y, int w, int h, String color) {
                super(x, y, w, h, Component.literal(color));
                this.color = color;
            }

            @Override
            protected void renderWidget(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY,
                                        float partialTick) {
                int rgb = 0xFF000000 | Integer.parseInt(this.color.substring(1), 16);
                g.fill(this.getX(), this.getY(),
                        this.getX() + this.getWidth(), this.getY() + this.getHeight(), rgb);
                if (this.color.equalsIgnoreCase(WorldEditPropQuickScreen.this.box.getValue())) {
                    g.fill(this.getX(), this.getY(),
                            this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x66FFFFFF);
                }
                if (this.isHovered()) {
                    g.fill(this.getX(), this.getY(),
                            this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x33FFFFFF);
                }
                g.fill(this.getX(), this.getY(),
                        this.getX() + this.getWidth(), this.getY() + 1, 0xFFB0BEC5);
                g.fill(this.getX(), this.getY() + this.getHeight() - 1,
                        this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF78909C);
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
                this.defaultButtonNarrationText(out);
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                WorldEditPropQuickScreen.this.commit(this.color);
                WorldEditPropQuickScreen.this.box.setValue(this.color);
            }
        }
}
