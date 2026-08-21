package com.opendreamcore.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * 聊天组件 → legacy 格式串（含颜色码），供 chat_display 的 RichText 渲染。
 * 颜色输出 &#RRGGBB（Bungee 风格，RichText 直接支持）；格式码输出 §l/§o/§n/§m/§k。
 */
public final class LegacyText {

    private LegacyText() {
    }

    /** 把聊天组件转成 legacy 格式串（含全部子组件）。 */
    public static String toLegacy(Component component) {
        StringBuilder sb = new StringBuilder();
        append(sb, component);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Component component) {
        Style style = component.getStyle();
        TextColor color = style.getColor();
        if (color != null) {
            sb.append("&#").append(String.format("%06X", color.getValue()));
        }
        if (style.isBold()) {
            sb.append("§l");
        }
        if (style.isItalic()) {
            sb.append("§o");
        }
        if (style.isUnderlined()) {
            sb.append("§n");
        }
        if (style.isStrikethrough()) {
            sb.append("§m");
        }
        if (style.isObfuscated()) {
            sb.append("§k");
        }
        sb.append(component.getString());
        for (Component child : component.getSiblings()) {
            append(sb, child);
        }
    }
}
