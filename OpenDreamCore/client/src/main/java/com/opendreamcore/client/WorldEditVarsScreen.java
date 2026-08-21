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

    final class WorldEditVarsScreen extends net.minecraft.client.gui.screens.Screen {
        private int pageIdx;

        WorldEditVarsScreen() {
            super(Component.literal("页面变量"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("＋新增"), btn -> {
                        WorldEditVarsScreen.this.setFocused(null);
                        Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                                "新增变量（key|value · 保存后写回 YAML）", "",
                                v -> {
                                    int sep = v.indexOf('|');
                                    if (sep < 0) {
                                        sep = v.indexOf('=');
                                    }
                                    if (sep <= 0) {
                                        Minecraft.getInstance().player.displayClientMessage(
                                                Component.literal("§c[OpenDreamCore] §f格式: key|value（| 或 = 分隔）"), false);
                                        return;
                                    }
                                    ClientController.get().applyWorldVar(v.substring(0, sep), v.substring(sep + 1));
                                    WorldEditVarsScreen.this.rebuild();
                                }));
                    }).bounds(cx - 150, 12, 70, 18).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("◀"), btn -> {
                        this.pageIdx = Math.max(0, this.pageIdx - 1);
                        this.rebuild();
                    }).bounds(cx - 76, 12, 28, 18).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("▶"), btn -> {
                        this.pageIdx++;
                        this.rebuild();
                    }).bounds(cx - 44, 12, 28, 18).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("关闭"), btn -> this.onClose())
                    .bounds(cx + 80, 12, 70, 18).build());
            var vars = ClientController.get().worldVars();
            java.util.List<String> keys = new java.util.ArrayList<>(vars.keySet());
            java.util.Collections.sort(keys);
            int rows = 12;
            int total = keys.size();
            if (total > 0 && this.pageIdx * rows >= total) {
                this.pageIdx = Math.max(0, (total - 1) / rows);
            }
            int start = this.pageIdx * rows;
            for (int i = 0; i < rows && start + i < total; i++) {
                String k = keys.get(start + i);
                Object val = vars.get(k);
                String label = k + " = " + (val == null ? "null" : String.valueOf(val));
                if (label.length() > 44) {
                    label = label.substring(0, 44) + "…";
                }
                final String fk = k;
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(label), btn -> {
                            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                                ClientController.get().removeWorldVar(fk);
                                WorldEditVarsScreen.this.rebuild();
                                return;
                            }
                            Object cur = ClientController.get().worldVars().get(fk);
                            WorldEditVarsScreen.this.setFocused(null);
                            Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                                    "变量 " + fk + "（Enter 提交 · 保存后写回 YAML）",
                                    cur == null ? "" : String.valueOf(cur),
                                    v -> {
                                        ClientController.get().applyWorldVar(fk, v);
                                        WorldEditVarsScreen.this.rebuild();
                                    }));
                        }).bounds(cx - 150, 36 + i * 18, 300, 16)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("点击改值 · Shift+点击删除")))
                        .build());
            }
        }

        /** 数据变更后重建行（clearWidgets + init）。 */
        private void rebuild() {
            this.clearWidgets();
            this.init();
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 20, 0xFFFFD54F);
            int total = ClientController.get().worldVars().size();
            String info = "共 " + total + " 个变量 · 第 " + (this.pageIdx + 1) + " 页 · 保存：关闭本屏后点工具栏保存";
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
