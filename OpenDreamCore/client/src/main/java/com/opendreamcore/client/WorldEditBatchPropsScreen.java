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

    final class WorldEditBatchPropsScreen extends net.minecraft.client.gui.screens.Screen {
        private final List<String> ids;
        private final List<String[]> props;
        private final java.util.Set<String> diffProps = new java.util.HashSet<>();

        WorldEditBatchPropsScreen(List<String> ids, Element first) {
            super(Component.literal("批量属性 · " + ids.size() + " 个元素"));
            this.ids = ids;
            this.props = ClientController.elementPropPaths(first);
            // 差异扫描：组内任一元素该路径值不同 → 标注
            for (String[] prop : this.props) {
                String base = prop[1];
                boolean diff = false;
                for (String id : ids) {
                    var el = WorldEditor.get().findWorldElement(id);
                    if (el == null) {
                        continue;
                    }
                    String v = ClientController.elementPropValue(el, prop[0]);
                    if (!java.util.Objects.equals(v, base)) {
                        diff = true;
                        break;
                    }
                }
                if (diff) {
                    this.diffProps.add(prop[0]);
                }
            }
        }

        @Override
        protected void init() {
            ClientController.get().setWorldEditHighlight(this.ids);
            int cols = 2, bw = 260, bh = 22, gap = 8;
            int rows = (props.size() + cols - 1) / cols;
            int totalW = cols * bw + (cols - 1) * gap;
            int x0 = this.width / 2 - totalW / 2;
            int y0 = Math.max(20, this.height / 2 - rows * (bh + gap) / 2 - 20);
            for (int i = 0; i < props.size() && i < 60; i++) {
                int row = i / cols, col = i % cols;
                String[] prop = props.get(i);
                String path = prop[0];
                String value = ClientController.shortText(prop[1]);
                boolean diff = this.diffProps.contains(path);
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(path + " = " + value + (diff ? "  ⚠" : "")), btn -> Minecraft.getInstance().setScreen(
                                new WorldEditPropQuickScreen("批量编辑 " + path + "（" + ids.size() + " 个元素）",
                                        ids, path, prop[1])))
                        .bounds(x0 + col * (bw + gap), y0 + row * (bh + gap), bw, bh)
                        .tooltip(diff ? net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("组内值不一致，批量设置将统一")) : null)
                        .build());
            }
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
            g.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
                    6, 0xFFE0E0E0);
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
