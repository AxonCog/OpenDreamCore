package com.opendreamcore.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视口变换测试：设计画布 ↔ 屏幕的投影/反解、IDENTITY 恒等、整数倍闸门、fit 策略。
 */
class ViewportTest {

    private static final double EPS = 1e-6;

    @Test
    void identityStaysOneToOne() {
        // 老包（无 design）口径：s=1、ox=oy=0，正反变换全部恒等，一帧不挪
        Viewport vp = Viewport.IDENTITY;
        assertTrue(vp.isIdentity());
        assertEquals(10.0, vp.pxToDesignX(10.0), EPS);
        assertEquals(7.0, vp.pxToDesignY(7.0), EPS);
        assertEquals(5.0, vp.unlayoutX(5.0), EPS);
        assertEquals(5.0, vp.unlayoutY(5.0), EPS);
        assertEquals(3.0, vp.layoutLength(Length.ofExpressionResult(3.0)), EPS);
        assertEquals(3.0, vp.layoutLength(Length.ofPixels(3.0)), EPS);
        assertEquals(42.0, vp.edgeX(42.0), EPS);
        assertEquals(42.0, vp.spanW(0, 42.0), EPS);
    }

    @Test
    void letterboxProjectsAndUnprojects() {
        // 1920x1080 设计画布 → 960x540 屏幕（s=0.5，居中）
        Viewport vp = Viewport.of(1920, 1080, 960, 540);
        assertEquals(0.5, vp.scale(), EPS);
        assertEquals(0.0, vp.offsetX(), EPS);
        assertEquals(0.0, vp.offsetY(), EPS);
        // 设计 (100, 200) → 屏幕 (50, 100)；反解回去原值
        assertEquals(50.0, vp.edgeX(100), EPS);
        assertEquals(100.0, vp.edgeY(200), EPS);
        assertEquals(100.0, vp.unlayoutX(50), EPS);
        assertEquals(200.0, vp.unlayoutY(100), EPS);
    }

    @Test
    void letterboxCenterOffsetWhenAspectDiffers() {
        // 4:3 屏幕放 16:9 画布：s 按宽/高最小（宽度受限），纵向留边
        Viewport vp = Viewport.of(1920, 1080, 640, 480);
        double s = Math.min(640.0 / 1920, 480.0 / 1080);
        assertEquals(640.0 / 1920, s, EPS);
        assertEquals(0.0, vp.offsetX(), EPS);
        double oy = (480 - 1080 * s) / 2;
        assertEquals(oy, vp.offsetY(), EPS);
        // 贴真屏幕左/右边 px 位置反解成设计坐标的 0 和 1920（不受纵向留边影响）
        assertEquals(0.0, vp.pxToDesignX(0), EPS);
        assertEquals(1920.0, vp.pxToDesignX(640), EPS);
    }

    @Test
    void anchorTopRightPinsToRightEdge() {
        // anchor_top_right：等比缩放 + 贴右上（左/下溢出裁掉）
        Viewport vp = ViewportStrategies.get("anchor_top_right")
                .build(1920, 1080, 960, 540);
        assertEquals(0.5, vp.scale(), EPS);
        assertEquals(0.0, vp.offsetX(), EPS); // 屏幕宽/设计宽恰整倍 → 无偏移
        // 设计 x=1920（最右）→ 屏幕 x=960（贴右边）
        assertEquals(960.0, vp.edgeX(1920), EPS);
    }

    @Test
    void invalidDesignFallsBackToIdentity() {
        // 参数非法/零 → IDENTITY（不拦渲染）
        assertTrue(Viewport.of(0, 1080, 960, 540).isIdentity());
        assertTrue(Viewport.of(-1, 1080, 960, 540).isIdentity());
        assertTrue(Viewport.of(1920, 1080, 0, 540).isIdentity());
        // 不存在的策略名退回 letterbox（幂等安全），只验证不抛
        assertNotNull(ViewportStrategies.get("不存在").build(1920, 1080, 960, 540));
    }

    @Test
    void integerGateRejectsNonMultipleHeight() {
        // 高度不是 180 的整数倍 → IDENTITY（整数倍闸门）
        assertTrue(Viewport.of(1920, 800, 1920, 800).isIdentity());
        // 合规基准 1080 正常启用（半缩放下非恒等）
        assertFalse(Viewport.of(1920, 1080, 960, 540).isIdentity());
        // 注意：合规基准在等尺寸屏幕上 s=1、无偏移，isIdentity() 恰为 true（老包等价），
        // 这不代表被闸门退回——用 designWidth 佐证基准已被记住
        assertEquals(1920.0, Viewport.of(1920, 1080, 1920, 1080).designWidth(), EPS);
    }

    @Test
    void layoutLengthPixels() {
        // 尺寸 px：画布像素 = v/s 个设计单位；缩放后还原成 v 像素
        Viewport vp = Viewport.of(1920, 1080, 960, 540);
        assertEquals(200.0, vp.layoutLength(Length.ofPixels(100)), EPS); // 100px → 200 设计单位（s=0.5）
        // 无单位 = 设计单位原值
        assertEquals(100.0, vp.layoutLength(Length.ofExpressionResult(100)), EPS);
    }
}