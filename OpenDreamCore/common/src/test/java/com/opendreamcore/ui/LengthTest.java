package com.opendreamcore.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 长度解析与画布像素口径：abs(px) 不乘缩放、无单位/表达式按设计单位、手滑带行列号报错。
 */
class LengthTest {

    private static final double EPS = 1e-6;

    @Test
    void pixelMarkersDoNotScaleInToPixels() {
        Viewport vp = Viewport.of(1920, 1080, 960, 540); // s=0.5
        assertEquals(100.0, Length.ofPixels(100).toPixels(vp), EPS);
        assertEquals(50.0, Length.ofExpressionResult(100).toPixels(vp), EPS);
    }

    @Test
    void parseDistinguishesPixelAndDesignUnits() {
        Length px = Length.parse("100px", -1, -1, "x");
        assertTrue(px.abs());
        assertEquals(100f, px.v());
        assertFalse(px.isExpression());

        Length plain = Length.parse("100", -1, -1, "x");
        assertFalse(plain.abs());
        assertEquals(100f, plain.v());

        // 老包数字写法：无单位（设计坐标系里数值=屏幕像素当 s=1）
        Length num = Length.parse(100, -1, -1, "x");
        assertFalse(num.abs());
        assertEquals(100f, num.v());
    }

    @Test
    void expressionValuesFollowDesignUnits() {
        assertTrue(Length.parse("parent.width", -1, -1, "x").isExpression());
        assertTrue(Length.parse("vars.coin", -1, -1, "x").isExpression());
        // 数字开头的算术表达式也是表达式（"12 px" 是手滑，但 "2 * entity.health_ratio" 不是）
        assertTrue(Length.parse("2 * entity.health_ratio", -1, -1, "x").isExpression());
        assertTrue(Length.parse("1.5 * parent.width", -1, -1, "x").isExpression());
        // 表达式结果按设计单位折算，与无单位同路
        Viewport vp = Viewport.of(1920, 1080, 960, 540);
        assertEquals(50.0, Length.ofExpressionResult(100).toPixels(vp), EPS);
    }

    @Test
    void handSlipsThrowWithYamlLocation() {
        // "12 px"（数字夹空格）是手滑不是表达式：必须带行列号炸出来
        Length.ParseException e = assertThrows(Length.ParseException.class,
                () -> Length.parse("12 px", 3, 4, "x"));
        assertEquals(3, e.line());
        assertEquals(4, e.column());
        assertTrue(e.getMessage().contains("x"));
        // 大写的 PX 也是手滑，照报
        assertThrows(Length.ParseException.class,
                () -> Length.parse("12 PX", -1, -1, "x"));
    }

    @Test
    void pixelSizesReachDoubleDesignUnitsUnderHalfScale() {
        // 一画布像素 = 1/s 个设计单位；缩放后恰好还原成原像素数
        Viewport vp = Viewport.of(1920, 1080, 960, 540);
        assertEquals(200.0, vp.layoutLength(Length.ofPixels(100)), EPS);
        assertEquals(100.0, vp.layoutLength(Length.ofExpressionResult(100)), EPS);
    }
}