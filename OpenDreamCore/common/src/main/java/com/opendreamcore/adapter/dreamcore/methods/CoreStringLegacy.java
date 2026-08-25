package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class CoreStringLegacy {
    private CoreStringLegacy() { }

    private static String s(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : "";
    }

    public static void install() {
        LegacyMethods.register("length", a -> (double) s(a, 0).length());
        LegacyMethods.register("isEmpty", a -> s(a, 0).isEmpty());
        LegacyMethods.register("is_empty", a -> s(a, 0).isEmpty());
        LegacyMethods.register("isBlank", a -> s(a, 0).isBlank());
        LegacyMethods.register("is_blank", a -> s(a, 0).isBlank());
        LegacyMethods.register("indexOf", a -> (double) s(a, 0).indexOf(s(a, 1)));
        LegacyMethods.register("indexof", a -> (double) s(a, 0).indexOf(s(a, 1)));
        LegacyMethods.register("lastindexof", a -> (double) s(a, 0).lastIndexOf(s(a, 1)));
        LegacyMethods.register("lastIndexOf", a -> (double) s(a, 0).lastIndexOf(s(a, 1)));
        LegacyMethods.register("contains", a -> s(a, 0).contains(s(a, 1)));
        LegacyMethods.register("startsWith", a -> s(a, 0).startsWith(s(a, 1)));
        LegacyMethods.register("startswith", a -> s(a, 0).startsWith(s(a, 1)));
        LegacyMethods.register("endsWith", a -> s(a, 0).endsWith(s(a, 1)));
        LegacyMethods.register("endswith", a -> s(a, 0).endsWith(s(a, 1)));
        LegacyMethods.register("charAt", a -> {
            int idx = (int) (int) num(a, 1);
            return idx >= 0 && idx < s(a, 0).length() ? s(a, 0).substring(idx, idx + 1) : "";
        });
        LegacyMethods.register("char_at", a -> {
            int idx = (int) (int) num(a, 1);
            return idx >= 0 && idx < s(a, 0).length() ? s(a, 0).substring(idx, idx + 1) : "";
        });
        LegacyMethods.register("code_point_at", a -> {
            int idx = (int) (int) num(a, 1);
            return (double) (idx >= 0 && idx < s(a, 0).length() ? s(a, 0).codePointAt(idx) : 0);
        });
        LegacyMethods.register("from_char_code", a -> {
            StringBuilder sb = new StringBuilder();
            for (Object x : a) if (x instanceof Number n) sb.appendCodePoint(n.intValue());
            return sb.toString();
        });
        LegacyMethods.register("substring", a -> {
            String src = s(a, 0);
            int start = Math.max(0, (int) num(a, 1));
            if (a.length > 2) {
                int end = Math.min(src.length(), (int) num(a, 2));
                return start <= end ? src.substring(start, end) : "";
            }
            return start <= src.length() ? src.substring(start) : "";
        });
        LegacyMethods.register("slice", a -> {
            String src = s(a, 0);
            int start = (int) (int) num(a, 1);
            if (start < 0) start += src.length();
            start = Math.max(0, start);
            if (a.length > 2) {
                int end = (int) (int) num(a, 2);
                if (end < 0) end += src.length();
                end = Math.min(src.length(), end);
                return start <= end ? src.substring(start, end) : "";
            }
            return start <= src.length() ? src.substring(start) : "";
        });
        LegacyMethods.register("split", a -> java.util.List.of(s(a, 0).split(java.util.regex.Pattern.quote(s(a, 1)))));
        LegacyMethods.register("join", a -> {
            Object sep = arg(a, 0);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < a.length; i++) {
                if (i > 1) sb.append(sep);
                sb.append(arg(a, i));
            }
            return sb.toString();
        });
        LegacyMethods.register("join_string", a -> {
            Object sep = arg(a, 1);
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < a.length; i++) {
                if (i > 2) sb.append(sep);
                sb.append(arg(a, i));
            }
            return sb.toString();
        });
        LegacyMethods.register("concat", a -> {
            StringBuilder sb = new StringBuilder(s(a, 0));
            for (int i = 1; i < a.length; i++) sb.append(arg(a, i));
            return sb.toString();
        });
        LegacyMethods.register("concat_string", a -> concat(a));
        LegacyMethods.register("merge_text", a -> concat(a));
        LegacyMethods.register("replace", a -> s(a, 0).replace(s(a, 1), s(a, 2)));
        LegacyMethods.register("replaceAll", a -> {
            try { return s(a, 0).replaceAll(s(a, 1), s(a, 2)); }
            catch (Exception e) { return s(a, 0); }
        });
        LegacyMethods.register("replace_all", a -> s(a, 0).replace(s(a, 1), s(a, 2)));
        LegacyMethods.register("replace_first", a -> s(a, 0).replaceFirst(
                java.util.regex.Pattern.quote(s(a, 1)), java.util.regex.Matcher.quoteReplacement(s(a, 2))));
        LegacyMethods.register("toUpperCase", a -> s(a, 0).toUpperCase());
        LegacyMethods.register("toupper", a -> s(a, 0).toUpperCase());
        LegacyMethods.register("toLowerCase", a -> s(a, 0).toLowerCase());
        LegacyMethods.register("tolower", a -> s(a, 0).toLowerCase());
        LegacyMethods.register("trim", a -> s(a, 0).trim());
        LegacyMethods.register("trim_left", a -> s(a, 0).replaceAll("^\s+", ""));
        LegacyMethods.register("trim_right", a -> s(a, 0).replaceAll("\s+$", ""));
        LegacyMethods.register("pad_left", a -> pad(s(a, 0), num(a, 1), s(a, 2), true));
        LegacyMethods.register("pad_right", a -> pad(s(a, 0), num(a, 1), s(a, 2), false));
        LegacyMethods.register("reverse", a -> new StringBuilder(s(a, 0)).reverse().toString());
        LegacyMethods.register("strip_color", a ->
                s(a, 0).replaceAll("[§&][0-9a-fk-orA-FK-OR]", ""));
        LegacyMethods.register("equals", a -> String.valueOf(arg(a, 0)).equals(String.valueOf(arg(a, 1))));
        LegacyMethods.register("equals_ignore_case", a ->
                String.valueOf(arg(a, 0)).equalsIgnoreCase(String.valueOf(arg(a, 1))));
        LegacyMethods.register("is_same_type", a ->
                arg(a, 0) != null && arg(a, 1) != null
                        && arg(a, 0).getClass() == arg(a, 1).getClass());
        LegacyMethods.register("format_time", a -> formatTime((int) num(a, 0), s(a, 1)));
        LegacyMethods.register("decimal_format", a -> {
            try {
                return new java.text.DecimalFormat(s(a, 1)).format(dbl(arg(a, 0)));
            } catch (Exception e) { return String.valueOf(arg(a, 0)); }
        });
        LegacyMethods.register("format", a -> {
            String fmt = s(a, 0);
            Object[] rest = new Object[Math.max(0, a.length - 1)];
            System.arraycopy(a, 1, rest, 0, rest.length);
            try { return String.format(fmt, rest); } catch (Exception e) { return fmt; }
        });
        LegacyMethods.register("split_with_width", a -> {
            // 简版：按字符宽度近似切分（中文=2 英文=1）
            String text = s(a, 0);
            int width = (int) (int) num(a, 1);
            java.util.List<String> lines = new java.util.ArrayList<>();
            int cur = 0;
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < text.length(); j++) {
                char ch = text.charAt(j);
                int w = ch > 127 ? 2 : 1;
                if (cur + w > width) { lines.add(line.toString()); line.setLength(0); cur = 0; }
                line.append(ch); cur += w;
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines;
        });
        LegacyMethods.register("sub_content", a -> {
            String t = s(a, 0);
            int st = (int) num(a, 1), en = (int) num(a, 2);
            return st <= en && en <= t.length() ? t.substring(st, en) : "";
        });
        LegacyMethods.register("compress_string", a -> s(a, 0));
        LegacyMethods.register("to_tellraw", a -> s(a, 0));
        LegacyMethods.register("get_string_width", a -> (double) visualWidth(s(a, 0)) * 6);
        LegacyMethods.register("get_string_height", a -> 9.0);
    }

    private static double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    private static int visualWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c > 127 ? 2 : 1;
        return w;
    }

    private static String concat(Object[] a) {
        StringBuilder sb = new StringBuilder(s(a, 0));
        for (int i = 1; i < a.length; i++) sb.append(arg(a, i));
        return sb.toString();
    }

    private static String pad(String s, double target, String fill, boolean left) {
        String f = fill == null || fill.isEmpty() ? " " : fill;
        while (s.length() < (int) target) s = left ? f + s : s + f;
        return s;
    }

    private static String formatTime(double ms, String pattern) {
        long total = (long) ms;
        long h = total / 3600000, m = total / 60000 % 60, sec = total / 1000 % 60;
        String p = pattern == null ? "HH:mm:ss" : pattern;
        return p.replace("HH", String.format("%02d", h))
                .replace("mm", String.format("%02d", m))
                .replace("ss", String.format("%02d", sec))
                .replace("SSS", String.format("%03d", total % 1000));
    }
}
