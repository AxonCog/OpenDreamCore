package com.opendreamcore.script;

import java.util.ArrayList;
import java.util.List;

/**
 * 富文本解析：把 legacy 格式串（§x / &x 颜色码 + 1.16 RGB 两种写法）转成片段列表。
 * 服务端聊天通道下发的消息、客户端捕获的原版聊天都转成这个格式渲染，
 * chat_display 按片段绘制（一行内多色）。
 *
 * 支持：
 * - 颜色码 §0-9a-f / &0-9a-f（大小写均可）
 * - 格式码 §k(乱码) §l(粗体) §m(删除线) §n(下划线) §o(斜体) §r(重置)
 * - RGB：§x§R§R§G§G§B§B（1.16 传统写法，每个 hex 前一个 §/&）与 &#RRGGBB / &x#RRGGBB
 * 格式码解析后保留在 Segment 里（当前渲染只取颜色；粗体等留给未来字体渲染）。
 */
public final class RichText {

    /** 富文本片段。color 为 0xRRGGBB；bold/italic 等格式位。 */
    public record Segment(String text, int color,
                          boolean bold, boolean italic, boolean underline,
                          boolean strikethrough, boolean obfuscated) {
    }

    /** 16 色映射（§0-9a-f）。 */
    private static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private RichText() {
    }

    /** 解析 legacy 格式串 → 片段列表（无颜色码时返回单个片段）。 */
    public static List<Segment> parse(String legacy) {
        List<Segment> segments = new ArrayList<>();
        if (legacy == null || legacy.isEmpty()) {
            return segments;
        }
        int color = 0xFFFFFF;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean obfuscated = false;
        StringBuilder text = new StringBuilder();

        int i = 0;
        int len = legacy.length();
        while (i < len) {
            char c = legacy.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < len) {
                char code = legacy.charAt(i + 1);
                int idx = hexIndex(code);
                if (code == '#') {
                    // &#RRGGBB（Bungee/Spigot 风格）
                    if (i + 7 <= len) {
                        String rgb = legacy.substring(i + 2, i + 8);
                        Integer parsed = parseHex(rgb);
                        if (parsed != null) {
                            flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                            color = parsed;
                            i += 8;
                            continue;
                        }
                    }
                } else if (code == 'x' || code == 'X') {
                    // 紧凑写法：&xRRGGBB（6 位直接跟）
                    if (i + 8 <= len) {
                        String rgb = legacy.substring(i + 2, i + 8);
                        Integer parsed = parseHex(rgb);
                        if (parsed != null) {
                            flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                            color = parsed;
                            i += 8;
                            continue;
                        }
                    }
                    // 1.16 传统写法：§x§R§R§G§G§B§B（每字符一个前缀）
                    if (i + 14 <= len) {
                        char[] hex = new char[6];
                        boolean ok = true;
                        for (int h = 0; h < 6; h++) {
                            char prefix = legacy.charAt(i + 2 + h * 2);
                            char digit = legacy.charAt(i + 3 + h * 2);
                            if ((prefix != '§' && prefix != '&') || hexIndex(digit) < 0) {
                                ok = false;
                                break;
                            }
                            hex[h] = digit;
                        }
                        if (ok) {
                            Integer parsed = parseHex(new String(hex));
                            if (parsed != null) {
                                flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                                color = parsed;
                                i += 14;
                                continue;
                            }
                        }
                    }
                } else if (idx >= 0) {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    color = COLORS[idx];
                    i += 2;
                    continue;
                } else if (code == 'k' || code == 'K') {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    obfuscated = true;
                    i += 2;
                    continue;
                } else if (code == 'l' || code == 'L') {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    bold = true;
                    i += 2;
                    continue;
                } else if (code == 'm' || code == 'M') {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    strike = true;
                    i += 2;
                    continue;
                } else if (code == 'n' || code == 'N') {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    underline = true;
                    i += 2;
                    continue;
                } else if (code == 'o' || code == 'O') {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    italic = true;
                    i += 2;
                    continue;
                } else if (code == 'r' || code == 'R') {
                    flush(segments, text, color, bold, italic, underline, strike, obfuscated);
                    color = 0xFFFFFF;
                    bold = italic = underline = strike = obfuscated = false;
                    i += 2;
                    continue;
                }
            }
            text.append(c);
            i++;
        }
        flush(segments, text, color, bold, italic, underline, strike, obfuscated);
        return segments;
    }

    private static void flush(List<Segment> segments, StringBuilder text, int color,
                              boolean bold, boolean italic, boolean underline,
                              boolean strike, boolean obfuscated) {
        if (text.length() > 0) {
            segments.add(new Segment(text.toString(), color, bold, italic, underline, strike, obfuscated));
            text.setLength(0);
        }
    }

    /** 去掉全部颜色/格式码（纯文本：宽度测量/搜索用）。 */
    public static String strip(String legacy) {
        if (legacy == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(legacy.length());
        int i = 0;
        int len = legacy.length();
        while (i < len) {
            char c = legacy.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < len) {
                char code = legacy.charAt(i + 1);
                if (code == '#') {
                    if (i + 8 <= len) { // &#RRGGBB（8 字符含 & 与 #）
                        i += 8;
                        continue;
                    }
                } else if (code == 'x' || code == 'X') {
                    if (i + 8 <= len) { // &xRRGGBB（紧凑 6 位）
                        i += 8;
                        continue;
                    }
                } else if (hexIndex(code) >= 0 || "klmnor".indexOf(Character.toLowerCase(code)) >= 0) {
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int hexIndex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static Integer parseHex(String rgb) {
        if (rgb.length() != 6) {
            return null;
        }
        int value = 0;
        for (int i = 0; i < 6; i++) {
            int digit = hexIndex(rgb.charAt(i));
            if (digit < 0) {
                return null;
            }
            value = (value << 4) | digit;
        }
        return value;
    }
}
