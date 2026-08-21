package com.opendreamcore.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务端 GUI 编译器测试：扁平 → 嵌套、自动 id、条件剔除、lines 排布、变量/函数/选项、resolve。
 */
class GuiCompilerTest {

    private static Map<String, Object> parse(String yaml) {
        return new YamlParser().parse(yaml);
    }

    /** 测试上下文：条件按表达式里含 "keep" 判定；resolve 替换 {name}。 */
    private static GuiCompiler.Context ctx() {
        return new GuiCompiler.Context() {
            @Override
            public boolean condition(String expr) {
                return expr.contains("keep");
            }

            @Override
            public String resolve(String text) {
                return text.replace("{name}", "张三");
            }
        };
    }

    @Test
    void flatCompilesToNested() {
        Map<String, Object> out = GuiCompiler.compile(parse("""
                title: 商店
                variables:
                  coin: 100
                options:
                  allowEscClose: true
                functions:
                  open: |-
                    Sound.播放音效("minecraft:block.chest.open")
                elements:
                  - id: header
                    type: text
                    text: {content: "标题"}
                  - type: button
                    button: {label: "购买"}
                """), ctx());

        assertEquals("商店", out.get("title"));
        assertEquals(100, out.get("coin"), "variables 平铺");
        assertEquals(true, out.get("allowEscClose"), "options 平铺");
        assertTrue(out.containsKey("Functions"), "functions → Functions");
        // 元素：命名 id 保留，匿名自动编号
        assertTrue(out.containsKey("header"));
        assertTrue(out.containsKey("el_2"));
        assertEquals("text", ((Map<?, ?>) out.get("header")).get("type"));
    }

    @Test
    void conditionDropsElement() {
        Map<String, Object> out = GuiCompiler.compile(parse("""
                elements:
                  - id: a
                    type: text
                    condition: "keep"
                  - id: b
                    type: text
                    condition: "drop"
                  - id: c
                    type: text
                """), ctx());
        assertTrue(out.containsKey("a"), "condition 为真保留");
        assertFalse(out.containsKey("b"), "condition 为假剔除");
        assertTrue(out.containsKey("c"), "无 condition 保留");
        // condition 键不残留
        assertFalse(((Map<?, ?>) out.get("a")).containsKey("condition"));
    }

    @Test
    void linesAutoStack() {
        Map<String, Object> out = GuiCompiler.compile(parse("""
                lineSpacing: 4
                lines:
                  - type: button
                    height: 20
                  - type: button
                    height: 30
                """), ctx());
        assertEquals(2, out.size());
        List<String> ids = out.keySet().stream().sorted().toList();
        assertEquals("el_1", ids.get(0));
        assertEquals(0.0, ((Map<?, ?>) out.get("el_1")).get("y"));
        assertEquals(24.0, ((Map<?, ?>) out.get("el_2")).get("y"), "y 自动叠加 height+spacing");
        assertEquals("window.width", ((Map<?, ?>) out.get("el_1")).get("width"), "width 默认窗口宽");
    }

    @Test
    void resolveAppliesToStringsExceptActions() {
        Map<String, Object> out = GuiCompiler.compile(parse("""
                elements:
                  - type: text
                    text: {content: "你好 {name}"}
                    actions:
                      click: |-
                        Chat.发送消息("{name}")
                """), ctx());
        Map<?, ?> el = (Map<?, ?>) out.get("el_1");
        assertEquals("你好 张三", ((Map<?, ?>) el.get("text")).get("content"), "字符串属性替换");
        String script = (String) ((Map<?, ?>) el.get("actions")).get("click");
        assertTrue(script.contains("{name}"), "actions 脚本不替换");
    }

    @Test
    void flatChildrenList() {
        Map<String, Object> out = GuiCompiler.compile(parse("""
                elements:
                  - type: layout
                    children:
                      - type: text
                        text: {content: "子"}
                """), ctx());
        Map<?, ?> children = (Map<?, ?>) ((Map<?, ?>) out.get("el_1")).get("children");
        assertTrue(children.containsKey("el_1"), "子元素自动 id");
    }

    @Test
    void isFlatDetection() {
        assertTrue(GuiCompiler.isFlat(parse("elements: []")));
        assertTrue(GuiCompiler.isFlat(parse("lines: []")));
        assertFalse(GuiCompiler.isFlat(parse("""
                a:
                  type: text
                """)));
        assertFalse(GuiCompiler.isFlat(null));
    }

    @Test
    void flatWorldPageKeepsDisplayAndWorldOptions() {
        // 服务端扁平语法 world 页面：display/world 顶层键直通，hologram 元素保留（世界面板射线交互）
        Map<String, Object> out = GuiCompiler.compile(parse("""
                title: 世界公告板
                display: world
                options:
                  world:
                    offsetX: 0
                    offsetY: 1.7
                    offsetZ: 3
                    interact: true
                elements:
                  - id: title_el
                    type: text
                    hologram: {x: 0, y: 0, z: 0, scale: 0.03, width: 3, height: 0.3}
                    text: {content: "服务器公告", color: "#FFD700"}
                    actions:
                      click: |-
                        Chat.发送消息("点了公告牌")
                """), ctx());
        assertEquals("world", out.get("display"), "display 直通");
        assertTrue(out.get("world") instanceof Map<?, ?> w
                && Boolean.TRUE.equals(((Map<?, ?>) out.get("world")).get("interact")),
                "options.world 平铺为顶层 world 配置");
        Map<?, ?> el = (Map<?, ?>) out.get("title_el");
        assertTrue(el.get("hologram") instanceof Map<?, ?> h
                && 0.03 == ((Number) ((Map<?, ?>) h).get("scale")).doubleValue(),
                "hologram 属性保留（世界面板定位/命中区域）");
        // 整页经 PageSchema 构建后 displayMode = WORLD
        com.opendreamcore.page.Page page = PageSchema.build("world_board", out);
        assertEquals(com.opendreamcore.page.DisplayMode.WORLD, page.displayMode());
        assertTrue(page.options().get("world") instanceof Map<?, ?>);
    }
}
