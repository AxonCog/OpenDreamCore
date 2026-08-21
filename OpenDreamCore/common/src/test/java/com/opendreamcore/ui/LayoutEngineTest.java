package com.opendreamcore.ui;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 布局引擎测试：数字/表达式/嵌套/条件。
 */
class LayoutEngineTest {

    private static Page page(String yaml) {
        Map<String, Object> ir = new YamlParser().parse(yaml);
        return PageSchema.build("测试", ir);
    }

    @Test
    void numbersAndWindowExpression() {
        Page page = page("""
                a:
                  type: layout
                  x: 10
                  y: 20
                  width: "window.width / 2"
                  height: 30
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode a = nodes.get(0);
        assertEquals(10, a.x());
        assertEquals(20, a.y());
        assertEquals(400, a.width());
        assertEquals(30, a.height());
    }

    @Test
    void childrenRelativeToParent() {
        Page page = page("""
                root:
                  type: layout
                  x: 50
                  y: 60
                  width: 300
                  children:
                    header:
                      type: text
                      x: 0
                      y: 0
                      width: "parent.width"
                      height: 20
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode root = nodes.get(0);
        assertEquals(50, root.x());
        assertEquals(60, root.y());
        RenderNode header = root.children().get(0);
        assertEquals(50, header.x());
        assertEquals(60, header.y());
        assertEquals(300, header.width());
    }

    @Test
    void variableExpression() {
        Page page = page("""
                coin: 100
                bar:
                  type: progress
                  x: 0
                  y: 0
                  width: "vars.coin * 2"
                  height: 10
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        assertEquals(200, nodes.get(0).width());
    }

    @Test
    void visibleWhenHidesNode() {
        Page page = page("""
                coin: 50
                a:
                  type: button
                  visibleWhen: "vars.coin >= 100"
                b:
                  type: button
                  visibleWhen: "vars.coin < 100"
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        assertFalse(nodes.get(0).visible());
        assertTrue(nodes.get(1).visible());
    }

    @Test
    void hitTestPrefersDeepestChild() {
        Page page = page("""
                root:
                  type: layout
                  x: 0
                  y: 0
                  width: 100
                  height: 100
                  children:
                    ok:
                      type: button
                      x: 10
                      y: 10
                      width: 40
                      height: 20
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode hit = nodes.get(0).hitTest(25, 20);
        assertNotNull(hit);
        assertEquals("ok", hit.id());
        RenderNode root = nodes.get(0).hitTest(80, 80);
        assertEquals("root", root.id());
        assertNull(nodes.get(0).hitTest(200, 200));
    }

    @Test
    void positionOverrides() {
        Page page = page("""
                a:
                  type: button
                  x: 10
                  y: 20
                b:
                  type: button
                  x: 100
                  y: 100
                """);
        Map<String, double[]> overrides = new java.util.HashMap<>();
        overrides.put("a", new double[]{300, 400});
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600, overrides);
        assertEquals(300, nodes.get(0).x());
        assertEquals(400, nodes.get(0).y());
        assertEquals(100, nodes.get(1).x()); // 未覆盖的保持原样
    }

    @Test
    void gridLayout() {
        Page page = page("""
                grid:
                  type: grid
                  x: 0
                  y: 0
                  width: 200
                  cols: 2
                  spacing: 10
                  children:
                    a: {type: button, height: 20}
                    b: {type: button, height: 20}
                    c: {type: button, height: 20}
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode grid = nodes.get(0);
        assertEquals(3, grid.children().size());
        // 格子宽 = (200 - 10) / 2 = 95
        assertEquals(0, grid.children().get(0).x());
        assertEquals(0, grid.children().get(0).y());
        assertEquals(105, grid.children().get(1).x()); // 95 + 10
        assertEquals(0, grid.children().get(1).y());
        assertEquals(30, grid.children().get(2).y());  // 第二行 y = 20 + 10
        assertEquals(0, grid.children().get(2).x());
    }

    @Test
    void stackLayout() {
        Page page = page("""
                stack:
                  type: h_stack
                  x: 10
                  y: 10
                  spacing: 5
                  children:
                    a: {type: button, width: 40, height: 20}
                    b: {type: button, width: 60, height: 20}
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode stack = nodes.get(0);
        assertEquals(2, stack.children().size());
        assertEquals(10, stack.children().get(0).x());
        assertEquals(55, stack.children().get(1).x()); // 10 + 40 + 5
    }

    @Test
    void zSorting() {
        Page page = page("""
                a: {type: button, x: 0, y: 0, width: 10, height: 10, z: 1}
                b: {type: button, x: 0, y: 0, width: 10, height: 10, z: 5}
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        assertEquals("a", nodes.get(0).id()); // z 升序
        assertEquals("b", nodes.get(1).id());
        assertEquals(5, nodes.get(1).z());
    }

    @Test
    void foreachExpandsList() {
        Page page = page("""
                items: [苹果, 香蕉, 橘子]
                list_area:
                  type: foreach
                  x: 0
                  y: 0
                  width: 200
                  foreach:
                    list: vars.items
                    as: item
                  spacing: 4
                  children:
                    row:
                      type: text
                      height: 14
                      text:
                        content: "- {{item}}"
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode area = nodes.get(0);
        assertEquals(3, area.children().size());
        // 模板 y 偏移：0、14+4=18、36
        assertEquals(0, area.children().get(0).y());
        assertEquals(18, area.children().get(1).y());
        assertEquals(36, area.children().get(2).y());
        // {{item}} 预替换
        Object text0 = area.children().get(0).props().get("text");
        assertTrue(String.valueOf(text0).contains("苹果"));
        assertTrue(String.valueOf(text0).contains("- "));
        Object text2 = area.children().get(2).props().get("text");
        assertTrue(String.valueOf(text2).contains("橘子"));
    }

    @Test
    void containerExpandsChestSlots() {
        Page page = page("""
                inv:
                  type: container
                  x: 10
                  y: 20
                  container:
                    rows: 2
                    cols: 3
                    slotStart: 0
                    spacing: 2
                    cellSize: 18
                  actions:
                    click: |-
                      Chat.发送消息(vars.slot)
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode inv = nodes.get(0);
        assertEquals(6, inv.children().size(), "2x3 生成 6 个槽位");
        // 槽位 id 与坐标
        assertEquals("inv_0", inv.children().get(0).id());
        assertEquals("inv_3", inv.children().get(3).id());
        assertEquals("chest_slot", inv.children().get(0).type());
        assertEquals(10, inv.children().get(0).x());
        assertEquals(20, inv.children().get(0).y());
        assertEquals(10 + 20, inv.children().get(1).x()); // 18 + 2
        assertEquals(20 + 20, inv.children().get(3).y()); // 第二行
        assertEquals(18, inv.children().get(0).width());
        // 槽位号写进 props（点击事件用）
        Object slot0 = inv.children().get(0).props().get("slot");
        assertTrue(slot0 instanceof Number n && n.intValue() == 0);
        Object slot4 = inv.children().get(4).props().get("slot");
        assertTrue(slot4 instanceof Number n && n.intValue() == 4);
        // 容器级 actions 继承到槽位（点击脚本统一处理）
        assertTrue(inv.children().get(0).source() != null
                && inv.children().get(0).source().actions().containsKey("click"));
    }

    @Test
    void bindOverridesPropsFromVariables() {
        Page page = page("""
                player_name: 张三
                show: true
                label:
                  type: text
                  bind:
                    text.content: "vars.player_name"
                    text.color: "'#FF0000'"
                    visible: "vars.show"
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode label = nodes.get(0);
        assertTrue(label.visible(), "bind visible 生效");
        Object text = label.props().get("text");
        assertTrue(text instanceof Map<?, ?>);
        assertEquals("张三", ((Map<?, ?>) text).get("content"));
        assertEquals("#FF0000", ((Map<?, ?>) text).get("color"));
        // bind 键本身不残留
        assertFalse(label.props().containsKey("bind"));
    }

    @Test
    void bindReevaluatesAfterVariableChange() {
        Page page = page("""
                coin: 100
                balance:
                  type: text
                  bind:
                    text.content: "'金币: ' + vars.coin"
                """);
        assertEquals("金币: 100", bindText(page));
        // 变量变化 → 重新 layout → 绑定自动更新
        page.variables().put("coin", 250L);
        assertEquals("金币: 250", bindText(page));
    }

    private static String bindText(Page page) {
        Object text = LayoutEngine.layout(page, 800, 600).get(0).props().get("text");
        return String.valueOf(((Map<?, ?>) text).get("content"));
    }

    @Test
    void disabledNodePassesThroughForInteraction() {
        Page page = page("""
                root:
                  type: layout
                  x: 0
                  y: 0
                  width: 100
                  height: 100
                  children:
                    back:
                      type: button
                      x: 0
                      y: 0
                      width: 100
                      height: 100
                    front:
                      type: button
                      x: 0
                      y: 0
                      width: 100
                      height: 100
                      enabledWhen: "false"
                """);
        RenderNode root = LayoutEngine.layout(page, 800, 600).get(0);
        // hover/tooltip 命中最深（禁用元素也能显示 tooltip）
        assertEquals("front", root.hitTest(50, 50).id());
        // 交互命中跳过禁用元素 → 落到下层可用元素
        assertEquals("back", root.hitTestInteractive(50, 50).id());
    }

    @Test
    void adaptiveSizesFromChildren() {
        Page page = page("""
                box:
                  type: adaptive
                  x: 100
                  y: 50
                  adaptive: {spacing: 4}
                  children:
                    a:
                      type: button
                      width: 200
                      height: 24
                    b:
                      type: button
                      width: 180
                      height: 30
                """);
        RenderNode box = LayoutEngine.layout(page, 800, 600).get(0);
        assertEquals(2, box.children().size());
        // 自适应尺寸：宽 = 最宽子元素 200；高 = 24 + 4 + 30 = 58
        assertEquals(200, box.width());
        assertEquals(58, box.height());
        // 子元素纵向排布
        assertEquals(100, box.children().get(0).x());
        assertEquals(50, box.children().get(0).y());
        assertEquals(50 + 24 + 4, box.children().get(1).y());
    }

    @Test
    void adaptiveKeepsExplicitSize() {
        Page page = page("""
                box:
                  type: adaptive
                  x: 0
                  y: 0
                  width: 300
                  height: 100
                  children:
                    a: {type: button, width: 20, height: 20}
                """);
        RenderNode box = LayoutEngine.layout(page, 800, 600).get(0);
        assertEquals(300, box.width(), "显式尺寸不被自适应覆盖");
        assertEquals(100, box.height());
    }

    @Test
    void numericPropsSupportExpressionsAndRotation() {
        Page page = page("""
                coin: 50
                badge:
                  type: button
                  x: 0
                  y: 0
                  opacity: "vars.coin / 100"
                  scale: 2
                  rotation: "vars.coin / 10"
                plain:
                  type: button
                  x: 0
                  y: 0
                """);
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        RenderNode badge = nodes.get(0);
        assertEquals(0.5, badge.opacity(), 1e-9, "opacity 表达式求值");
        assertEquals(2.0, badge.scale(), 1e-9, "scale 数字原样");
        assertEquals(5.0, badge.rotation(), 1e-9, "rotation 表达式求值");
        assertEquals(1.0, nodes.get(1).opacity(), 1e-9, "未写 opacity 默认 1");
        assertEquals(0.0, nodes.get(1).rotation(), 1e-9, "未写 rotation 默认 0");
    }

    @Test
    void rotationStoredInPropsAfterNormalization() {
        Page page = page("""
                a:
                  type: button
                  rotation: "90"
                """);
        RenderNode a = LayoutEngine.layout(page, 800, 600).get(0);
        assertEquals(90.0, a.rotation(), 1e-9);
        // props 里也归一化为数字（渲染端直接读取）
        assertTrue(a.props().get("rotation") instanceof Number);
    }
}
