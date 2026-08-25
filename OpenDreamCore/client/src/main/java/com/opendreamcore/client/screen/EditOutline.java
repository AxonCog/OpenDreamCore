package com.opendreamcore.client.screen;

import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 编辑模式元素轮廓/标签绘制（C4 自 OdcScreen 抽出的纯绘制函数）。
 * 不可见元素画暗紫幽灵框（visibleWhen 隐藏时编辑器仍能看到结构，定位"页面为什么空"）；
 * 选中元素亮黄描边，其余半透明白；每元素带 id 标签。递归子节点。
 */
public final class EditOutline {

    private EditOutline() {
    }

    /** 递归绘制一棵渲染树的编辑轮廓。 */
    public static void drawNode(GuiGraphics g, RenderNode node, String selectedId, Font font) {
        // 不可见元素画暗紫幽灵框
        if (!node.visible()) {
            int x1 = (int) node.x();
            int y1 = (int) node.y();
            int x2 = (int) (node.x() + Math.max(node.width(), 0));
            int y2 = (int) (node.y() + Math.max(node.height(), 0));
            int gc = node.id().equals(selectedId) ? 0xFFC678FF : 0x60C678FF;
            g.fill(x1, y1, x2, y1 + 1, gc);
            g.fill(x1, y2 - 1, x2, y2, gc);
            g.fill(x1, y1, x1 + 1, y2, gc);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0x18C678FF);
            g.drawString(font, node.id() + " (隐)", x1 + 2, y1 + 2, gc);
            for (RenderNode child : node.children()) {
                drawNode(g, child, selectedId, font);
            }
            return;
        }
        int x1 = (int) node.x();
        int y1 = (int) node.y();
        int x2 = (int) (node.x() + Math.max(node.width(), 0));
        int y2 = (int) (node.y() + Math.max(node.height(), 0));
        int color = node.id().equals(selectedId) ? 0xFFFFFF00 : 0x80FFFFFF;
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
        g.drawString(font, node.id(), x1 + 2, y1 + 2, color);
        for (RenderNode child : node.children()) {
            drawNode(g, child, selectedId, font);
        }
    }
}
