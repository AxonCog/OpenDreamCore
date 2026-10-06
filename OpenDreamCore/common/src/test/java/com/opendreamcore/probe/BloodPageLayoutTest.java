package com.opendreamcore.probe;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 血条/头顶页布局回归（用户实机报的「只有文字、血条条不显示」的防线）。
 *
 * <p>两件事必须成立：①平铺写法（type: rect + color/width 直接挂在元素上）能布局出 rect 节点；
 * ②作者写在元素上的 x/y/width/height 会被补进 hologram（世界渲染器只认 hologram 里的几何，
 * 不补就退化成默认 1×1 格——表现就是「配了却看不见」）。
 */
public class BloodPageLayoutTest {

    /** 文档模板原文（Blood.yml）。 */
    private static final String BLOOD = String.join("\n",
            "entity: \"*\"",
            "distance: 32",
            "offsetX: 0",
            "offsetY: 0",
            "",
            "血条底:",
            "  type: rect",
            "  x: -1",
            "  y: 0",
            "  width: 2",
            "  height: 0.5",
            "  color: \"#AA000000\"",
            "",
            "血条前景:",
            "  type: rect",
            "  x: -0.88",
            "  y: 0.11",
            "  width: \"2 * health_ratio\"",
            "  height: 0.28",
            "  color: \"#FF55FF55\"",
            "",
            "血条数值:",
            "  type: text",
            "  x: 0",
            "  y: 0.55",
            "  text:",
            "    content: \"{health}/{health_max}\"");

    private static List<RenderNode> layout(String yaml) {
        Map<String, Object> ir = YamlParser.lenient().parse(yaml);
        Map<String, Object> page = new LinkedHashMap<>(ir);
        for (String k : new String[]{"entity", "实体", "name", "distance", "mode", "显示模式"}) {
            page.remove(k);
        }
        page.put("display", "world");
        for (Map.Entry<String, Object> kv : page.entrySet()) {
            if (!(kv.getValue() instanceof Map<?, ?> rawEl)) {
                continue;
            }
            Map<String, Object> el = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e2 : rawEl.entrySet()) {
                el.put(String.valueOf(e2.getKey()), e2.getValue());
            }
            boolean isElement = el.get("type") != null || el.get("类型") != null;
            if (!isElement || el.containsKey("hologram")) {
                continue;
            }
            if (!(el.containsKey("x") || el.containsKey("y")
                    || el.containsKey("width") || el.containsKey("height"))) {
                continue;
            }
            Map<String, Object> holo = new LinkedHashMap<>();
            holo.put("x", el.getOrDefault("x", 0));
            holo.put("y", el.getOrDefault("y", 0));
            holo.put("z", el.getOrDefault("z", 0));
            holo.put("width", el.getOrDefault("width", 1));
            holo.put("height", el.getOrDefault("height", 1));
            el.put("hologram", holo);
            kv.setValue(el);
        }
        Page p = PageSchema.build("ht_test", page);
        assertNotNull(p, "页面必须建得出来");
        return LayoutEngine.layout(p, 800, 600);
    }

    @Test
    public void bloodPageProducesRectAndTextNodes() {
        List<RenderNode> nodes = layout(BLOOD);
        assertEquals(3, nodes.size(), "三个元素都要布局出节点");

        RenderNode base = nodes.get(0);
        assertEquals("rect", base.type());
        assertEquals(2.0, base.width(), 1.0E-6, "血条底宽 2 格");
        assertEquals(0.5, base.height(), 1.0E-6);
        assertTrue(base.props().containsKey("color"), "平铺写法的 color 必须留在 props 上（渲染层据此取色）");

        Map<?, ?> holo = (Map<?, ?>) base.props().get("hologram");
        assertNotNull(holo, "元素上的坐标要补进 hologram");
        assertEquals(2.0, Double.parseDouble(String.valueOf(holo.get("width"))), 1.0E-6);

        RenderNode bar = nodes.get(1);
        assertEquals("rect", bar.type());
        assertEquals(0.28, bar.height(), 1.0E-6, "前景高度 0.28");
        Map<?, ?> barHolo = (Map<?, ?>) bar.props().get("hologram");
        assertNotNull(barHolo);
        assertEquals("2 * health_ratio", String.valueOf(barHolo.get("width")),
                "宽度表达式要原样留给渲染期逐帧求值（health_ratio 是实时变量）");

        RenderNode label = nodes.get(2);
        assertEquals("text", label.type());
        assertTrue(label.props().containsKey("text"), "文本内容在 text 子表里");
    }
}