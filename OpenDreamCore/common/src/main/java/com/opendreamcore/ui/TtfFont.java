package com.opendreamcore.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TTF 字体封装（纯 Java，零 MC 依赖，可独立单测）：
 * 从 .ttf 文件加载（Font.createFont，TRUETYPE）
 * 字形宽度/文本宽度测量（FontMetrics）
 * 字形渲染到透明 BufferedImage（软件渲染，供客户端图集缓存与染色绘制）
 * 内部统一用 64px 渲染源保证字形质量，客户端按目标尺寸缩放绘制。
 */
public final class TtfFont {

    /** 渲染源字号（字形质量与图集尺寸的平衡点）。 */
    public static final int SOURCE_SIZE = 64;

    /**
     * 常见系统字体别名（小写文件名基名 → AWT 字体族名候选）。
     * Font.createFont 不支持 .ttc 集合（JDK 限制），微软雅黑/黑体等在 Windows
     * 上恰恰都是 ttc —— 命中别名表时改用系统已装字体，零文件解析。
     */
    private static final Map<String, List<String>> SYSTEM_ALIASES = buildSystemAliases();

    /** Java 8 兼容构造（远古线 common-j8 也编译本文件，不能用 Map.of/List.of）。 */
    private static Map<String, List<String>> buildSystemAliases() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("msyh", Arrays.asList("Microsoft YaHei", "微软雅黑"));
        m.put("msyhbd", Arrays.asList("Microsoft YaHei", "微软雅黑"));
        m.put("msyhl", Arrays.asList("Microsoft YaHei Light", "微软雅黑 Light"));
        m.put("simhei", Arrays.asList("SimHei", "黑体"));
        m.put("simsun", Arrays.asList("SimSun", "宋体"));
        m.put("simkai", Arrays.asList("KaiTi", "楷体"));
        m.put("simfang", Arrays.asList("FangSong", "仿宋"));
        m.put("deng", Arrays.asList("DengXian", "等线"));
        m.put("arial", Collections.singletonList("Arial"));
        return Collections.unmodifiableMap(m);
    }

    private final String name;
    private final Font font;
    private final FontMetrics metrics;

    /** 工厂入口：.ttf/.otf 直接解析；.ttc 或解析失败 → 系统字体别名回退。 */
    public static TtfFont fromFile(String name, File file) throws IOException, java.awt.FontFormatException {
        try {
            return new TtfFont(name, Font.createFont(Font.TRUETYPE_FONT, file));
        } catch (java.awt.FontFormatException | IOException first) {
            // ttc 容器/损坏文件：按文件名猜系统字体（msyh.ttc → 微软雅黑）
            String base = file.getName().replaceFirst("(?i)\\.(tt[cf]|otf)$", "").toLowerCase(java.util.Locale.ROOT);
            for (String family : SYSTEM_ALIASES.getOrDefault(base, Collections.singletonList(base))) {
                Font sys = new Font(family, Font.PLAIN, SOURCE_SIZE);
                if (!Font.DIALOG.equals(sys.getFamily()) && sys.canDisplay('中')) {
                    return new TtfFont(name, sys);
                }
            }
            throw first;
        }
    }

    public TtfFont(String name, File ttfFile) throws IOException, java.awt.FontFormatException {
        Font base = Font.createFont(Font.TRUETYPE_FONT, ttfFile);
        this.name = name;
        this.font = base.deriveFont(Font.PLAIN, (float) SOURCE_SIZE);
        this.metrics = metricsOf(this.font);
    }

    /** 直接用 AWT 字体构造（测试/系统字体回退用）。 */
    public TtfFont(String name, Font awtFont) {
        this.name = name;
        this.font = awtFont.deriveFont(Font.PLAIN, (float) SOURCE_SIZE);
        this.metrics = metricsOf(this.font);
    }

    private static FontMetrics metricsOf(Font f) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        try {
            return g.getFontMetrics(f);
        } finally {
            g.dispose();
        }
    }

    public String name() {
        return name;
    }

    /** 字形宽度（SOURCE_SIZE 字号下）。 */
    public int advance(char c) {
        return metrics.charWidth(c);
    }

    /** 文本总宽（SOURCE_SIZE 字号下）。 */
    public int textWidth(String text) {
        return metrics.stringWidth(text);
    }

    /** 行高（SOURCE_SIZE 字号下）。 */
    public int lineHeight() {
        return metrics.getHeight();
    }

    /** 基线到字形框顶的距离（绘制时 y 对齐用）。 */
    public int ascent() {
        return metrics.getAscent();
    }

    /**
     * 渲染字形到透明图像（SOURCE_SIZE 字号，白字）。
     * 空白/换行等无字形字符返回 null（由调用方按 advance 跳进）。
     */
    public BufferedImage renderGlyph(char c) {
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            return null;
        }
        int w = Math.max(1, advance(c));
        int h = metrics.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.setFont(font);
        g.drawString(String.valueOf(c), 0, metrics.getAscent());
        g.dispose();
        return img;
    }
}
