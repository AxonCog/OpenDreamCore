package com.opendreamcore.model.format;

import java.util.List;
import java.util.Map;

/**
 * codec 共享工具：JSON 值域的向量/数值/表达式安全读取。
 */
public final class CodecUtil {

    private CodecUtil() {
    }

    /** 读取 3 分量（数组 [x,y,z]），缺项补 0。 */
    public static double[] vec3(Object v) {
        double[] out = new double[3];
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            for (int i = 0; i < 3 && i < l.size(); i++) {
                out[i] = num(l.get(i));
            }
        }
        return out;
    }

    /** 读取 2 分量。 */
    public static double[] vec2(Object v) {
        double[] out = new double[2];
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            for (int i = 0; i < 2 && i < l.size(); i++) {
                out[i] = num(l.get(i));
            }
        }
        return out;
    }

    public static double num(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1 : 0;
        }
        return 0;
    }

    /**
     * bbmodel data_points 的 x/y/z：字符串可能是 molang 表达式。
     * 返回 [值]（长度 1，可解析时）或 null（表达式，调用方存 expr）。
     */
    public static Double tryNum(String s) {
        if (s == null) {
            return 0d;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return 0d;
        }
        boolean plain = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')) {
                plain = false;
                break;
            }
        }
        if (!plain) {
            return null;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object v) {
        return v instanceof List ? (List<Object>) v : null;
    }

    public static String str(Object v) {
        return v instanceof String ? (String) v : null;
    }
}
