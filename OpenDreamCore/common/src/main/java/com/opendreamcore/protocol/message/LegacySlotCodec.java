package com.opendreamcore.protocol.message;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DragonCore 旧版槽位调用的无状态转换器。
 * 标识符后缀决定真实槽位；mouseButton 只决定 PICKUP 的左右键，不可覆盖槽位。
 */
public final class LegacySlotCodec {
    private static final Pattern SLOT_IDENTIFIER = Pattern.compile("(?:^|_)((?:0|[1-9][0-9]*))$");

    private LegacySlotCodec() {
    }

    /** 返回 identifier 最后一个数字段；无法解析或超出 int 范围返回 -1。 */
    public static int identifierSlot(String identifier) {
        if (identifier == null) return -1;
        Matcher matcher = SLOT_IDENTIFIER.matcher(identifier.trim());
        if (!matcher.find()) return -1;
        return parseNonNegative(matcher.group(1));
    }

    /** 仅接受非负整数形式，避免 0.0、负数和布尔值成为槽位。 */
    public static int numericSlot(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long n = ((Number) value).longValue();
            return n >= 0 && n <= Integer.MAX_VALUE ? (int) n : -1;
        }
        if (value instanceof String text && text.trim().matches("(?:0|[1-9][0-9]*)")) {
            return parseNonNegative(text.trim());
        }
        return -1;
    }

    /**
     * 将旧动作名转换为服务端 slot:action 字符。
     * PICKUP 遵守旧 mouseButton：0=左键 L，1=右键 R；其他动作只接受已知服务端动作。
     */
    public static String action(Object mouseButton, Object requested) {
        String raw = requested == null ? "PICKUP" : String.valueOf(requested).trim();
        if (raw.isEmpty()) raw = "PICKUP";
        String normalized = raw.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PICKUP" -> pickupAction(mouseButton);
            case "LEFT", "CLICK", "L" -> "L";
            case "RIGHT", "R" -> "R";
            case "QUICK_MOVE", "QUICK", "SHIFT", "Q" -> "Q";
            case "SWAP", "DOUBLE_CLICK", "D" -> "D";
            case "PICKUP_ALL", "MIDDLE", "ALL", "A" -> "A";
            case "QUICK_CRAFT", "DISTRIBUTE", "CTRL", "S" -> "S";
            case "THROW" -> null;
            default -> null;
        };
    }

    private static String pickupAction(Object mouseButton) {
        int button = numericSlot(mouseButton);
        if (button == 0) return "L";
        if (button == 1) return "R";
        // 调用方缺省 mouseButton 时保持旧版左键；任意其他值拒绝，不能伪造按钮。
        return mouseButton == null ? "L" : null;
    }

    private static int parseNonNegative(String text) {
        try {
            long value = Long.parseLong(text);
            return value <= Integer.MAX_VALUE ? (int) value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
