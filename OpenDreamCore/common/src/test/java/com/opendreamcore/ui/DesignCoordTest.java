package com.opendreamcore.ui;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 设计坐标三路证明：顶层贴边 px + anchor 入向 + 子元素相对 px。
 * design 1920x1080 → 屏幕 960x540（s=0.5）：px 位置/尺寸换算成设计单位翻倍，
 * 渲染出来后仍是原来的画布像素数（贴真屏幕边、锚点入向、相对父都不偏移）。
 */
class DesignCoordTest {

    private static final double EPS = 1e-6;

    private static Page page(String yaml) {
        Map<String, Object> ir = new YamlParser().parse(yaml);
        return PageSchema.build("测试", ir);
    }

    /**
     * 实机口径（ClientController.layoutPage 同款）：design 页用设计尺寸布局 + 真屏幕算出的视口，
     * 布完再 projectTree 投影。测试直接走 2 参入口会拿屏幕宽当设计宽布（anchor/窗口表达式全错位）。
     */
    private static List<RenderNode> designLayout(Page p, double screenW, double screenH) {
        Viewport vp = LayoutEngine.viewportFor(p, screenW, screenH);
        @SuppressWarnings("unchecked")
        Map<String, Object> design = (Map<String, Object>) p.options().get("design");
        double dw = ((Number) design.get("width")).doubleValue();
        double dh = ((Number) design.get("height")).doubleValue();
        return LayoutEngine.layout(p, dw, dh, null, vp);
    }

    @Test
    void topLevelPixelPinsAgainstTrueScreenEdge() {
        Page p = page("""
                design:
                  width: 1920
                  height: 1080
                a:
                  type: layout
                  x: "20px"
                  y: "30px"
                  width: "100px"
                  height: "40px"
                """);
        List<RenderNode> nodes = designLayout(p, 960, 540);
        RenderNode a = nodes.get(0);
        // px 位置走 pxToDesign（贴真屏幕边）：20px → 40 设计单位（s=0.5）
        assertEquals(40.0, a.x(), EPS);
        assertEquals(60.0, a.y(), EPS);
        // px 尺寸免缩放：换算设计单位翻倍（200），投影后仍是 100 画布像素
        assertEquals(200.0, a.width(), EPS);
        assertEquals(80.0, a.height(), EPS);
    }

    @Test
    void anchorMovesInwardWithPixelUnit() {
        Page p = page("""
                design:
                  width: 1920
                  height: 1080
                a:
                  type: layout
                  anchor: bottom_right
                  x: "10px"
                  y: "20px"
                  width: 100
                  height: 50
                """);
        List<RenderNode> nodes = designLayout(p, 960, 540);
        RenderNode a = nodes.get(0);
        // 贴右下锚点：元素锚点（左上基准）放在距画布右下角入向 distance 处
        // x=10px → 20 设计单位，absX = 1920 - 20；y=20px → 40 设计单位，absY = 1080 - 40
        assertEquals(1920.0 - 20.0, a.x(), EPS);
        assertEquals(1080.0 - 40.0, a.y(), EPS);
        assertEquals(100.0, a.width(), EPS);
        assertEquals(50.0, a.height(), EPS);
    }

    @Test
    void childPixelIsRelativeToParent() {
        Page p = page("""
                design:
                  width: 1920
                  height: 1080
                parent:
                  type: layout
                  x: 100
                  y: 50
                  width: 500
                  height: 300
                  children:
                    child:
                      type: layout
                      x: "10px"
                      y: "5px"
                      width: "20px"
                      height: "10px"
                """);
        List<RenderNode> nodes = designLayout(p, 960, 540);
        RenderNode root = nodes.get(0);
        RenderNode child = root.children().get(0);
        assertNotNull(child);
        // 子元素 px 相对父容器偏移：设计坐标 = 父起点 + 翻倍后的设计单位
        assertEquals(100.0 + 20.0, child.x(), EPS);
        assertEquals(50.0 + 10.0, child.y(), EPS);
        assertEquals(40.0, child.width(), EPS);
        assertEquals(20.0, child.height(), EPS);
    }
}