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

    final class WorldEditTabScreen extends net.minecraft.client.gui.screens.Screen {
        private final java.util.List<String> targetIds;
        private final java.util.List<net.minecraft.client.gui.components.Button> buttons =
                new java.util.ArrayList<>();

        WorldEditTabScreen(String elementId) {
            this(java.util.List.of(elementId));
        }

        WorldEditTabScreen(java.util.List<String> targetIds) {
            super(Component.literal("页签归属 · " + targetIds.size() + " 元素"));
            this.targetIds = new java.util.ArrayList<>(targetIds);
        }

        @Override
        protected void init() {
            int bw = 380;
            String pid = ClientController.get().worldPage == null ? "world"
                    : ClientController.get().worldPage.id() == null ? "world"
                    : ClientController.get().worldPage.id();
            var tabs = ClientController.get().worldTabList(pid);
            String cur = null;
            if (this.targetIds.size() == 1) {
                var el = WorldEditor.get().findWorldElement(this.targetIds.get(0));
                cur = el == null || el.props().get("tab") == null
                        ? null : String.valueOf(el.props().get("tab"));
            }
            int y0 = 56;
            java.util.List<String> entries = new java.util.ArrayList<>();
            entries.add(null); // 公共区（无 tab）
            entries.addAll(tabs);
            for (int i = 0; i < entries.size(); i++) {
                String tab = entries.get(i);
                boolean active = cur == null ? tab == null : tab != null && cur.equals(tab);
                String label = (tab == null ? "公共区（不属任何页签）" : tab)
                        + (active ? "  ✓" : "");
                int y = y0 + i * 22;
                if (y > this.height - 40) {
                    break;
                }
                net.minecraft.client.gui.components.Button btn =
                        net.minecraft.client.gui.components.Button.builder(Component.literal(label),
                                b -> {
                                    if (this.targetIds.size() == 1) {
                                        ClientController.get().setWorldElementTab(
                                                this.targetIds.get(0), tab);
                                    } else {
                                        ClientController.get().setWorldElementTabBatch(
                                                this.targetIds, tab);
                                    }
                                    this.onClose();
                                }).bounds(this.width / 2 - bw / 2, y, bw, 20).build();
                this.addRenderableWidget(btn);
                this.buttons.add(btn);
            }
            // 新建页签：输入新页签名直接设置
            int ny = y0 + entries.size() * 22;
            if (ny > this.height - 40) {
                ny = this.height - 40;
            }
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("+ 新建页签并归属…"), b -> Minecraft.getInstance().setScreen(
                            new WorldEditPropScreen("新页签名（Enter 提交）", "",
                                    v -> {
                                        if (this.targetIds.size() == 1) {
                                            ClientController.get().setWorldElementTab(
                                                    this.targetIds.get(0), v);
                                        } else {
                                            ClientController.get().setWorldElementTabBatch(
                                                    this.targetIds, v);
                                        }
                                        Minecraft.getInstance().setScreen(
                                                new WorldEditTabScreen(this.targetIds));
                                    })))
                    .bounds(this.width / 2 - bw / 2, ny, bw, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("输入新页签名并把目标元素归入其中；保存后写回页面文件")))
                    .build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    8, 0xFFE0E0E0);
            String hint = "点击设置页签归属（页面已知页签去重；公共区 = 不属任何页签）· ESC 关闭";
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
