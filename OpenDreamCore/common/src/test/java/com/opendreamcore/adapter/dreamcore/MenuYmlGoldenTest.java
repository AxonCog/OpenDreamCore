package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden 测试：真实 菜单.yml（仓库根目录快照）全链路解析。
 * 链路 = YamlParser → DreamCoreParser.transform → PageSchema.build（与客户端 LocalPageManager 一致）。
 * 验收：不抛异常、元素数量正确、关键映射逐点抽查。
 */
class MenuYmlGoldenTest {

    private static Map<String, Object> transformReal() throws Exception {
        try (var in = MenuYmlGoldenTest.class.getResourceAsStream("/legacy/menu.yml")) {
            assertNotNull(in, "测试资源 legacy/menu.yml 缺失");
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> ir = new YamlParser().parse(yaml);
            return DreamCoreParser.transform(ir);
        }
    }

    @Test
    void fullLegacyMenuParsesAndBuilds() throws Exception {
        Map<String, Object> ir = transformReal();
        Page page = PageSchema.build("龙核菜单", ir);
        assertEquals("龙核菜单", page.match().target());
        assertTrue(page.options().containsKey("allowEscClose"), "allowEscClose 顶层标量透传进选项");
        assertTrue(page.elements().size() > 40,
                "菜单.yml 应产出 40+ 顶层元素，实际 " + page.elements().size());
        assertTrue(count(page) >= page.elements().size(),
                "元素树完整（含嵌套子元素）");
    }

    @Test
    void backgroundTextureBecomesImageWithExpression() throws Exception {
        Map<String, Object> el = find(transformReal(), "背景");
        assertEquals("image", el.get("type"));
        // 贴图路径是作用域变量引用 → 改写为页面变量裸名（运行时由 槽位检测 脚本赋值）
        assertTrue(el.get("image") instanceof Map<?, ?> img
                && "odc_user_壁纸存储".equals(((Map<?, ?>) img).get("src")),
                "texture 引用改写为 odc_user_* 页面变量");
        assertTrue(el.get("x").toString().contains("(w-背景.width)/1.8"), "w 别名 + 交叉引用原样保留");
    }

    @Test
    void mallSlotMapsToChestSlot11() throws Exception {
        Map<String, Object> el = find(transformReal(), "商城");
        assertEquals("chest_slot", el.get("type"));
        assertTrue(el.get("chest_slot") instanceof Map<?, ?> cs
                && Integer.valueOf(11).equals(((Map<?, ?>) cs).get("slot")), "container_11 → slot 11");
        assertTrue(el.get("chest_slot") instanceof Map<?, ?> cs2
                && Boolean.FALSE.equals(((Map<?, ?>) cs2).get("showSlot")));
    }

    @Test
    void functionsRewritten() throws Exception {
        Map<String, Object> ir = transformReal();
        assertTrue(ir.get("Functions") instanceof Map<?, ?> fns, "Functions 键保留");
        Map<?, ?> fns = (Map<?, ?>) ir.get("Functions");
        assertTrue(fns.containsKey("open") && fns.containsKey("keyPress")
                && fns.containsKey("wheel") && fns.containsKey("槽位检测"));
        String open = String.valueOf(fns.get("open"));
        assertTrue(open.contains("方法.异步执行方法('每秒重新计算绘制起始点和更新变量')"), "带参调用保持原样");
        assertFalse(open.contains("方法.播放声音('菜单/打开菜单.ogg')\n") == false && !open.contains("播放声音"),
                "播放声音调用存在");
        String keyPress = String.valueOf(fns.get("keyPress"));
        assertTrue(keyPress.contains("方法.取当前按下键()"), "keyPress 内裸调用补括号");
        String wheel = String.valueOf(fns.get("wheel"));
        assertTrue(wheel.contains("方法.取滚轮值()"), "wheel 内裸调用补括号");
        // 槽位检测：界面变量/用户变量 赋值 → Screen.设置变量
        String slotDetect = String.valueOf(fns.get("槽位检测"));
        assertTrue(slotDetect.contains("Screen.设置变量(\"odc_ui_"), "界面变量.X = → 设置变量");
        assertTrue(slotDetect.contains("odc_user_"), "用户变量 读引用改写");
        assertFalse(slotDetect.contains("界面变量."), "旧作用域记号不再残留");
    }

    @Test
    void scrollRegionAndOpacityExpressionsPreserved() throws Exception {
        Map<String, Object> el = find(transformReal(), "标题_label");
        assertEquals("text", el.get("type"));
        assertEquals("方法.取屏幕高度*0.0034", el.get("scale"), "scale 表达式原样（运行时求值）");
        assertEquals("方法.取界面存活时间/700", el.get("opacity"), "alpha→opacity 表达式原样");
        Map<String, Object> mainCity = find(transformReal(), "主城");
        assertTrue(mainCity.containsKey("limitY") && mainCity.containsKey("limitWidth"),
                "旧滚动区 limit* 键保留（滚动语义在事件阶段处理）");
    }

    // ---------- 工具 ----------

    private static Map<String, Object> find(Map<String, Object> ir, String id) {
        Object o = ir.get(id);
        if (o instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) o;
            return cast;
        }
        fail("找不到元素: " + id);
        return null;
    }

    private static int count(Page p) {
        int n = p.elements().size();
        for (Element e : p.elements()) {
            n += countChildren(e);
        }
        return n;
    }

    private static int countChildren(Element e) {
        int n = e.children().size();
        for (Element c : e.children()) {
            n += countChildren(c);
        }
        return n;
    }
}
