package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享层规则解析契约。
 *
 * 这些断言是「同一份 FontConfig.yml 在十六个版本里语义一致」的落点：解析只有一个
 * 实现，各版本渲染层只消费这里产出的模型，所以键名覆盖与边界行为钉死在这里即可。
 */
class FontRulesTest {

    private static Map<String, String> rules(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 键名超集：任务契约列出的每个键都必须能取到值。 */
    @Test
    void keySuperset() {
        FontRules parsed = FontRules.parse(rules(
                "肝",
                "texture: fonts/han.png\n"
                        + "gif: true\n"
                        + "fps: 12\n"
                        + "width: 16\n"
                        + "height: 20\n"
                        + "ascent: 7\n"
                        + "offsetX: 1.5\n"
                        + "offsetY: -2.5\n"
                        + "fontWidth: 18\n"
                        + "u: 3\n"
                        + "v: 4\n"
                        + "permissions:\n"
                        + "  - vip\n"
                        + "  - staff\n"
                        + "permissionsContains: false\n"
                        + "alias: gan\n"
                        + "single: true\n"
                        + "color: \"#ffffff\"\n"
                        + "style: bold\n"
                        + "alignment: center\n"));
        assertEquals(1, parsed.ruleCount());
        FontRules.Rule r = parsed.rules().get(0);
        assertEquals("肝", r.id);
        assertEquals("fonts/han.png", r.texture);
        assertTrue(r.gif);
        assertEquals(12.0, r.fps, 0.0001);
        assertEquals(16, r.width);
        assertEquals(20, r.height);
        assertEquals(7, r.ascent);
        assertEquals(1.5f, r.offsetX, 0.0001f);
        assertEquals(-2.5f, r.offsetY, 0.0001f);
        assertEquals(18, r.fontWidth);
        assertEquals(3, r.u);
        assertEquals(4, r.v);
        assertEquals(2, r.permissions.size());
        assertEquals("vip", r.permissions.get(0));
        assertFalse(r.permissionsContains);
        assertEquals("gan", r.alias);
        assertTrue(r.single);
        assertEquals("#ffffff", r.color);
        assertEquals("bold", r.style);
        assertEquals("center", r.alignment);
    }

    /** 历史键名兼容：path 等同 texture，xOffset/yOffset 等同 offsetX/offsetY，字形键亦认。 */
    @Test
    void legacyKeyAliases() {
        FontRules viaPath = FontRules.parse(rules("A", "path: fonts/a.png\nxOffset: 2\nyOffset: 3\n"));
        FontRules.Rule a = viaPath.rules().get(0);
        assertEquals("fonts/a.png", a.texture);
        assertEquals(2.0f, a.offsetX, 0.0001f);
        assertEquals(3.0f, a.offsetY, 0.0001f);

        FontRules viaHan = FontRules.parse(rules("B", "字形: fonts/b.png\n"));
        assertEquals("fonts/b.png", viaHan.rules().get(0).texture);
    }

    /** gif 键写路径时等同 texture；写布尔值时是显式声明。 */
    @Test
    void gifKeyDualForm() {
        FontRules asPath = FontRules.parse(rules("A", "gif: fonts/anim.gif\n"));
        assertEquals("fonts/anim.gif", asPath.rules().get(0).texture);
        assertTrue(asPath.rules().get(0).gif);

        FontRules asFlag = FontRules.parse(rules("A", "texture: fonts/anim.png\ngif: true\n"));
        assertTrue(asFlag.rules().get(0).gif);
    }

    /** 扩展名即动图：.gif 自动带 gif 标记，配置不写也算。 */
    @Test
    void gifByExtension() {
        FontRules parsed = FontRules.parse(rules("A", "texture: fonts/anim.gif\n"));
        assertTrue(parsed.rules().get(0).gif);
    }

    /** 单字符精确替换 + 区间等分 + 正则选字三种命中路径。 */
    @Test
    void hitPaths() {
        FontRules parsed = FontRules.parse(rules(
                "肝", "texture: fonts/han.png\n",
                "数字组", "range: 0-9\ntexture: fonts/digits.png\nwidth: 10\nheight: 10\n",
                "金色汉字", "match: \"[金银铜]\"\ntexture: fonts/metal.png\n"));

        FontRules.Glyph single = parsed.glyphFor('肝');
        assertNotNull(single);
        assertEquals("fonts/han.png", single.texture);
        assertEquals(0, single.frameIndex);
        assertEquals(1, single.totalFrames);

        FontRules.Glyph digit = parsed.glyphFor('7');
        assertNotNull(digit);
        assertEquals("fonts/digits.png", digit.texture);
        assertEquals(7, digit.frameIndex); // 0-9 横向等分，第 7 帧
        assertEquals(10, digit.totalFrames);

        FontRules.Glyph metal = parsed.glyphFor('银');
        assertNotNull(metal);
        assertEquals("fonts/metal.png", metal.texture);

        assertNull(parsed.glyphFor('未'));
        assertEquals(10, parsed.rangeCodePoints()); // 0-9 共 10 个字符
        assertEquals(1, parsed.regexCount());
    }

    /** 全局字体：只有 ttf 键也算一条规则，且能被查出来。 */
    @Test
    void globalTtf() {
        FontRules parsed = FontRules.parse(rules("全局字体", "ttf: fonts/msyh.ttc\n"));
        assertEquals(1, parsed.ruleCount());
        assertEquals("fonts/msyh.ttc", parsed.defaultTtf());
        assertTrue(parsed.hasAny());
        assertFalse(parsed.rules().get(0).hasBitmap());
        assertNull(parsed.glyphFor('任')); // 字体不产生位图字形
    }

    /** 反斜杠路径统一成斜杠（Windows 配置直写路径的常见形态）。 */
    @Test
    void backslashNormalized() {
        FontRules parsed = FontRules.parse(rules("A", "texture: fonts\\sub\\a.png\n"));
        assertEquals("fonts/sub/a.png", parsed.rules().get(0).texture);
    }

    /** 本地「整包多规则」形态：值又是一层 map 时逐个展开。 */
    @Test
    void localBundleForm() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("_local_override",
                "肝:\n  texture: fonts/local.png\n全局字体:\n  ttf: fonts/local.ttf\n");
        FontRules parsed = FontRules.parse(m);
        assertEquals(2, parsed.ruleCount());
        assertEquals("fonts/local.png", parsed.glyphFor('肝').texture);
        assertEquals("fonts/local.ttf", parsed.defaultTtf());
    }

    /** 单条 YAML 写坏只丢该条，其余规则照常生效——配置里一个错字不该让整套字形失效。 */
    @Test
    void brokenRuleIsSkipped() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("坏规则", "texture: [unclosed\n"); // 流式序列没闭合，YAML 直接报错
        m.put("好", "texture: fonts/ok.png\n");
        FontRules parsed = FontRules.parse(m);
        assertEquals(1, parsed.ruleCount());
        assertEquals("fonts/ok.png", parsed.glyphFor('好').texture);
    }

    /** 正则写坏只丢该条。 */
    @Test
    void brokenRegexIsSkipped() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("坏正则", "match: \"[unclosed\"\ntexture: fonts/bad.png\n");
        m.put("好", "texture: fonts/ok.png\n");
        FontRules parsed = FontRules.parse(m);
        assertEquals(0, parsed.regexCount()); // 写坏的正则不进索引
        assertEquals(2, parsed.ruleCount());  // 但规则本身仍被记录
        assertNotNull(parsed.glyphFor('好'));
        assertEquals("fonts/ok.png", parsed.glyphFor('好').texture);
    }

    /** 尺寸缺省：远程贴图不写尺寸取原生（0 = 交给贴图），本地贴图维持 9。 */
    @Test
    void sizeDefaults() {
        FontRules local = FontRules.parse(rules("A", "texture: fonts/a.png\n"));
        assertEquals(9, local.rules().get(0).width);
        assertEquals(9, local.rules().get(0).height);
        assertEquals(9, local.rules().get(0).fontWidth); // 不写 fontWidth 等于 width

        FontRules remote = FontRules.parse(rules("A", "texture: https://x/a.gif\n"));
        assertEquals(0, remote.rules().get(0).width);
        assertEquals(0, remote.rules().get(0).height);
    }

    /** 空表 / null 输入不炸，且判定为「无能力」。 */
    @Test
    void emptyInput() {
        assertFalse(FontRules.parse(null).hasAny());
        assertFalse(FontRules.parse(rules()).hasAny());
        assertNull(FontRules.parse(rules()).defaultTtf());
        assertNull(FontRules.parse(rules()).glyphFor('A'));
    }

    /** 没有贴图也没有 ttf 的规则不占索引（避免空规则把命中路径拖慢或误判）。 */
    @Test
    void ruleWithoutContentIgnored() {
        FontRules parsed = FontRules.parse(rules("空规则", "height: 8\nascent: 8\n"));
        assertEquals(0, parsed.ruleCount());
        assertFalse(parsed.hasAny());
    }

    /** 贴图路径清单按声明顺序去重，供跨版本一致性自检逐项比对。 */
    @Test
    void textureInventory() {
        FontRules parsed = FontRules.parse(rules(
                "A", "texture: fonts/1.png\n",
                "B", "texture: fonts/1.png\n",
                "C", "texture: fonts/2.png\n"));
        assertEquals(2, parsed.textures().size());
        assertEquals("fonts/1.png", parsed.textures().get(0));
        assertEquals("fonts/2.png", parsed.textures().get(1));
    }
}
