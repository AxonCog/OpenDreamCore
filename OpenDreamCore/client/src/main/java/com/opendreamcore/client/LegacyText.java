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
        appendStyle(sb, component.getStyle());
        if (component.getSiblings().isEmpty()) {
            // 叶子：getString() 就是自身文本
            sb.append(component.getString());
        } else {
            // 有子组件：getString() 会递归包含全部子文本，先抠掉子文本才是自身内容
            // （旧实现直接 append getString() 再递归子组件 → 每个子组件文本都重复一次）
            String self = component.getString();
            for (Component child : component.getSiblings()) {
                self = self.replace(child.getString(), "");
            }
            sb.append(self);
        }
        for (Component child : component.getSiblings()) {
            append(sb, child);
        }
    }

    private static void appendStyle(StringBuilder sb, Style style) {
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
    }
}
