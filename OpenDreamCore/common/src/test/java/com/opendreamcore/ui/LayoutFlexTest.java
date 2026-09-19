package com.opendreamcore.ui;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式布局（flexbox-lite）单元测试：
 * 容器 align/justify/wrap/reverse + 子级 grow/shrink/basis/absolute。
 * 硬保证：未声明弹性属性的 stack 走兼容路径，行为与历史版本一致。
 */
class LayoutFlexTest {

    private static Page page(Map<String, Object> ir) {
        return PageSchema.build("t", ir);
    }

    private static RenderNode node(Page p, String id) {
        return find(LayoutEngine.layout(p, 800, 600), id);
    }

    private static RenderNode find(java.util.List<RenderNode> nodes, String id) {
        for (RenderNode n : nodes) {
            if (n.id().equals(id)) {
                return n;
            }
            RenderNode r = find(n.children(), id);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static Map<String, Object> box(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("rect", Map.of("color", "#333333"));
        return m;
    }


    /** 有序 children 构造：Map.of 迭代顺序不保证，测试需要确定性时一律用它。 */
    private static Map<String, Object> kids(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // 兼容路径：无弹性属性时行为不变

    @Test
    void legacyStackKeepsCursorAccumulationAndDefaults() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("x", 10);
        row.put("y", 20);
        Map<String, Object> children = new LinkedHashMap<>();
        children.put("a", box("rect")); // 未写宽 → 缺省 50
        children.put("b", box("rect"));
        row.put("children", children);

        RenderNode b = node(page(new LinkedHashMap<>(Map.of("row", row))), "b");
        // a 从 x=10 起占缺省 50，b 紧随其后（spacing 缺省 0）
        assertEquals(60, b.x());
    }

    // grow 分配剩余空间

    @Test
    void growDistributesFreeSpaceProportionally() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("x", 0);
        row.put("y", 0);
        row.put("width", 300);
        row.put("justify", "start");
        Map<String, Object> a = box("rect");
        a.put("grow", 1);
        Map<String, Object> b = box("rect");
        b.put("grow", 2);
        row.put("children", kids("a", a, "b", b));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        RenderNode na = node(p, "a");
        RenderNode nb = node(p, "b");
        assertEquals(100, na.width());  // 300 × 1/3
        assertEquals(200, nb.width());  // 300 × 2/3
        assertEquals(100, nb.x());      // 紧跟 a 之后
    }

    @Test
    void basisOverridesDeclaredWidthForDistribution() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("width", 400);
        row.put("justify", "start");
        Map<String, Object> a = box("rect");
        a.put("basis", 100);
        a.put("grow", 1);
        Map<String, Object> b = box("rect");
        b.put("width", 100); // 无 grow：保持自然尺寸
        row.put("children", kids("a", a, "b", b));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        assertEquals(300, node(p, "a").width()); // 剩余 300 全给 a
        assertEquals(300, node(p, "b").x());     // b 紧随 a（a 已扩张到 300）
    }

    // justify 主轴对齐

    @Test
    void justifyBetweenSpreadsFixedItems() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("width", 300);
        row.put("justify", "between");
        Map<String, Object> a = box("rect");
        a.put("basis", 50);
        Map<String, Object> b = box("rect");
        b.put("basis", 50);
        Map<String, Object> c = box("rect");
        c.put("basis", 50);
        row.put("children", kids("a", a, "b", b, "c", c));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        assertEquals(0, node(p, "a").x());
        assertEquals(125, node(p, "b").x()); // (300-150)/2 的空隙中点
        assertEquals(250, node(p, "c").x());
    }

    @Test
    void justifyCenterGroupsItemsInMiddle() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("width", 300);
        row.put("justify", "center");
        Map<String, Object> a = box("rect");
        a.put("basis", 100);
        Map<String, Object> b = box("rect");
        b.put("basis", 50);
        row.put("children", kids("a", a, "b", b));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        assertEquals(75, node(p, "a").x()); // (300-150)/2
        assertEquals(175, node(p, "b").x());
    }

    // align 交叉轴对齐

    @Test
    void alignCenterCentersCrossAxis() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "v_stack");
        row.put("width", 300);
        row.put("height", 200);
        row.put("align", "center");
        Map<String, Object> child = box("rect");
        child.put("width", 100);
        row.put("children", kids("child", child));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        RenderNode n = node(p, "child");
        assertEquals(100, n.x()); // (300-100)/2，交叉轴居中
    }

    // wrap 换行

    @Test
    void wrapMovesOverflowToNextLine() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("width", 120);
        row.put("wrap", true);
        Map<String, Object> a = box("rect");
        a.put("basis", 50);
        Map<String, Object> b = box("rect");
        b.put("basis", 50);
        Map<String, Object> c = box("rect");
        c.put("basis", 50); // 放不下 → 换行
        row.put("children", kids("a", a, "b", b, "c", c));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        RenderNode nc = node(p, "c");
        assertEquals(0, nc.x());       // 第二行行首
        assertTrue(nc.y() > 0, "第二行应在交叉轴上推进");
    }

    // absolute 混排

    @Test
    void absoluteChildEscapesFlowAndUsesOwnPosition() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("width", 300);
        row.put("justify", "start");
        Map<String, Object> a = box("rect");
        a.put("basis", 100);
        Map<String, Object> badge = box("rect");
        badge.put("absolute", true);
        badge.put("x", 250);
        badge.put("y", 5);
        row.put("children", kids("a", a, "badge", badge));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        RenderNode nBadge = node(p, "badge");
        assertEquals(250, nBadge.x()); // 自身 x/y 相对容器，不参与流分配
        assertEquals(5, nBadge.y());
        assertEquals(0, node(p, "a").x());
    }

    // reverse 反转

    @Test
    void reverseFlowsFromFarEnd() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "h_stack");
        row.put("width", 300);
        row.put("reverse", true);
        Map<String, Object> a = box("rect");
        a.put("basis", 50);
        Map<String, Object> b = box("rect");
        b.put("basis", 50);
        row.put("children", kids("a", a, "b", b));

        Page p = page(new LinkedHashMap<>(Map.of("row", row)));
        // 反转后声明序 [b,a]，start 对齐仍从容器起点开始：b 在前
        assertEquals(0, node(p, "b").x());
        assertEquals(50, node(p, "a").x());
    }
}
