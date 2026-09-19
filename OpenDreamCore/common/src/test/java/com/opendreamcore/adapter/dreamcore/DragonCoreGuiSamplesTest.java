package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 龙核真实界面样本金测试（服务器实装配置快照，见 resources/dragoncore/gui/）。
 *
 * 链路与客户端 LocalPageManager 完全一致：YamlParser → AdapterRegistry 路由
 * → DreamCoreParser.transform → PageSchema.build。三个样本覆盖：
 *   DragonAuthMe —— Functions.open 里发包/异步执行方法/界面变量赋值的重脚本页
 *   MainGui      —— 常规主菜单
 *   区服界面     —— 精简页
 * 查的是：detects 命中、全链零异常、元素非空、关键键透传。
 */
class DragonCoreGuiSamplesTest {

    private static String load(String name) throws Exception {
        try (var in = DragonCoreGuiSamplesTest.class.getResourceAsStream("/dragoncore/gui/" + name)) {
            assertNotNull(in, "样本缺失: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 单样本全链路：原文 → 检测 → 解析 → 建页。 */
    private static Page build(String name) throws Exception {
        String yaml = load(name);
        assertTrue(DreamCoreParser.INSTANCE.detects(yaml), name + " 应被识别为龙核旧格式");
        Map<String, Object> ir = DreamCoreParser.INSTANCE.parse(yaml);
        return PageSchema.build(name.replace(".yml", ""), ir);
    }

    @Test
    void dragonAuthMeFullChain() throws Exception {
        Page page = build("DragonAuthMe.yml");
        assertFalse(page.elements().isEmpty(), "DragonAuthMe 应产出元素");
        // 顶层标量/列表透传
        Map<String, Object> ir = DreamCoreParser.INSTANCE.parse(load("DragonAuthMe.yml"));
        assertEquals(Boolean.FALSE, ir.get("allowEscClose"), "allowEscClose 透传");
        assertTrue(ir.get("hideHud") instanceof java.util.List<?> l && !l.isEmpty(),
                "hideHud 列表透传");
        // Functions.open 的脚本保持可读（发包调用不被破坏）
        assertTrue(ir.get("Functions") instanceof Map<?, ?> fns && fns.containsKey("open"),
                "Functions.open 保留");
        String open = String.valueOf(((Map<?, ?>) ir.get("Functions")).get("open"));
        assertTrue(open.contains("方法.发包('DragonAuthMe', 'prelogin')"),
                "带参调用 方法.发包 原样保留");
        assertTrue(open.contains("Screen.设置变量(\"odc_ui_"),
                "界面变量.执行操作 赋值被改写");
        assertFalse(open.contains("界面变量."), "旧作用域记号不残留");
    }

    @Test
    void mainGuiFullChain() throws Exception {
        Page page = build("MainGui.yml");
        assertTrue(page.elements().size() >= 5,
                "MainGui 应产出至少 5 个元素，实际 " + page.elements().size());
    }

    @Test
    void quFuJieMianFullChain() throws Exception {
        Page page = build("区服界面.yml");
        assertFalse(page.elements().isEmpty(), "区服界面 应产出元素");
    }

    @Test
    void itemTipTooltipPageParses() throws Exception {
        // ItemTip.yml 本质是鼠标跟随的提示页（龙核用它实现 tooltip），走同一条 GUI 链
        try (var in = DragonCoreGuiSamplesTest.class
                .getResourceAsStream("/dragoncore/systems/ItemTip.yml")) {
            assertNotNull(in, "ItemTip 样本缺失");
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(DreamCoreParser.INSTANCE.detects(yaml), "ItemTip 应识别为龙核格式");
            Map<String, Object> ir = DreamCoreParser.INSTANCE.parse(yaml);
            Page page = PageSchema.build("ItemTip", ir);
            assertFalse(page.elements().isEmpty(), "ItemTip 应产出元素（背景/左上角…）");
            // 背景元素 x 是多行三元块，含 方法.取鼠标x —— 求值期才解析，解析期必须原样保留
            Map<?, ?> bg = (Map<?, ?>) ir.get("背景");
            assertNotNull(bg, "背景元素存在");
            String x = String.valueOf(bg.get("x"));
            assertTrue(x.contains("方法.取鼠标x"), "取鼠标x 引用保留（大小写按原文）");
        }
    }
}
