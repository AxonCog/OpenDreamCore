package com.opendreamcore.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 通用脚本方法命名空间（零 MC 依赖，客户端/服务端共用）：
 * Math 数学 / Str 字符串 / Array 数组 / Time 时间 / UUID。
 * 各平台在启动时调用 registerAll() 即可。
 */
public final class CommonMethods {

    private CommonMethods() {
    }

    public static void registerAll() {
        registerMath();
        registerStr();
        registerArray();
        registerTime();
        registerUuid();
        registerEvent();
    }

    // ========== Event（脚本事件总线：跨脚本/跨页面发布订阅） ==========

    private static void registerEvent() {
        NamespaceRegistry.register("Event", args -> {
            // Event.订阅(名称, lambda) → 订阅 id（取消用）
            if (args.length < 2 || args[0] == null || !(args[1] instanceof DreamLangExecutor.Callable c)) {
                return -1.0;
            }
            return (double) EventBus.subscribe(String.valueOf(args[0]), c);
        }, "订阅", "subscribe", "on");
        NamespaceRegistry.register("Event", args -> {
            // Event.取消订阅(id)
            if (args.length < 1) {
                return false;
            }
            Object v = args[0];
            long id = v instanceof Number n ? n.longValue() : -1;
            return EventBus.unsubscribe(id);
        }, "取消订阅", "unsubscribe", "off");
        NamespaceRegistry.register("Event", args -> {
            // Event.发布(名称, ...参数) → 触发全部订阅（参数透传），返回最后一个处理器结果
            if (args.length < 1 || args[0] == null) {
                return null;
            }
            Object[] payload = new Object[args.length - 1];
            System.arraycopy(args, 1, payload, 0, args.length - 1);
            return EventBus.publish(String.valueOf(args[0]), payload);
        }, "发布", "publish", "emit");
        NamespaceRegistry.register("Event", args -> {
            // Event.清空(名称?) — 无参清全部
            if (args.length < 1 || args[0] == null) {
                EventBus.clearAll();
                return true;
            }
            return EventBus.clear(String.valueOf(args[0]));
        }, "清空", "clear");
    }

    // ========== Math ==========

    private static void registerMath() {
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.abs(a);
        }, "绝对值", "abs");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            double b = num(args, 1);
            return Math.max(a, b);
        }, "最大", "max");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            double b = num(args, 1);
            return Math.min(a, b);
        }, "最小", "min");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return (double) (long) Math.floor(a);
        }, "向下取整", "floor");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return (double) (long) Math.ceil(a);
        }, "向上取整", "ceil");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return (double) Math.round(a);
        }, "四舍五入", "round");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.sqrt(a);
        }, "平方根", "sqrt");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            double b = num(args, 1);
            return Math.pow(a, b);
        }, "幂", "pow");
        NamespaceRegistry.register("Math", args -> {
            double min = num(args, 0);
            double max = num(args, 1);
            return min + Math.random() * (max - min);
        }, "随机", "random");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.sin(Math.toRadians(a));
        }, "正弦", "sin");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.cos(Math.toRadians(a));
        }, "余弦", "cos");
        NamespaceRegistry.register("Math", args -> Math.PI, "圆周率", "pi");
        NamespaceRegistry.register("Math", args -> Math.E, "自然常数", "e");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.tan(Math.toRadians(a));
        }, "正切", "tan");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, a))));
        }, "反正弦", "asin", "arcsin");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a))));
        }, "反余弦", "acos", "arccos");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.toDegrees(Math.atan(a));
        }, "反正切", "atan", "arctan");
        NamespaceRegistry.register("Math", args -> {
            double y = num(args, 0);
            double x = num(args, 1);
            return Math.toDegrees(Math.atan2(y, x));
        }, "反正切2", "atan2", "atan_2");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.exp(a);
        }, "指数", "exp");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.log(a);
        }, "对数", "ln", "log");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.log10(a);
        }, "常用对数", "log10", "log_10");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            double b = num(args, 1);
            if (b == 0) return 0.0;
            return a - b * Math.floor(a / b);
        }, "取模", "mod", "fmod");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.signum(a);
        }, "符号", "sign", "signum");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return a - Math.floor(a);
        }, "小数部分", "fract");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return a < 0 ? Math.ceil(a) : Math.floor(a);
        }, "截断", "trunc", "truncate");
        NamespaceRegistry.register("Math", args -> {
            double v = num(args, 0);
            double min = num(args, 1);
            double max = num(args, 2);
            if (min > max) { double t = min; min = max; max = t; }
            return Math.max(min, Math.min(max, v));
        }, "限制", "clamp", "clampValue");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            double b = num(args, 1);
            double t = num(args, 2);
            return a + (b - a) * t;
        }, "插值", "lerp", "mix");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            double b = num(args, 1);
            double c = num(args, 2);
            double d = num(args, 3);
            double t = num(args, 4);
            double ab = a + (b - a) * t;
            double bc = b + (c - b) * t;
            double cd = c + (d - c) * t;
            double abc = ab + (bc - ab) * t;
            double bcd = bc + (cd - bc) * t;
            return abc + (bcd - abc) * t;
        }, "贝塞尔", "bezier", "cubicBezier", "cubic_bezier");
        NamespaceRegistry.register("Math", args -> {
            if (args.length >= 6) {
                double x1 = num(args, 0); double y1 = num(args, 1); double z1 = num(args, 2);
                double x2 = num(args, 3); double y2 = num(args, 4); double z2 = num(args, 5);
                double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
                return Math.sqrt(dx * dx + dy * dy + dz * dz);
            } else if (args.length >= 4) {
                double x1 = num(args, 0); double y1 = num(args, 1);
                double x2 = num(args, 2); double y2 = num(args, 3);
                double dx = x2 - x1, dy = y2 - y1;
                return Math.sqrt(dx * dx + dy * dy);
            } else if (args.length >= 2) {
                double a = num(args, 0); double b = num(args, 1);
                return Math.hypot(a, b);
            }
            return 0.0;
        }, "距离", "distance", "hypot", "length");
        NamespaceRegistry.register("Math", args -> {
            double v = num(args, 0);
            double inMin = num(args, 1); double inMax = num(args, 2);
            double outMin = num(args, 3); double outMax = num(args, 4);
            if (inMax == inMin) return outMin;
            return outMin + (v - inMin) * (outMax - outMin) / (inMax - inMin);
        }, "映射", "map", "remap");
        NamespaceRegistry.register("Math", args -> {
            double min = num(args, 0); double max = num(args, 1);
            int lo = (int) Math.ceil(Math.min(min, max));
            int hi = (int) Math.floor(Math.max(min, max));
            if (hi < lo) return (double) lo;
            return (double) (lo + (int) (Math.random() * (hi - lo + 1)));
        }, "随机整数", "randomInt", "random_int", "dieRoll", "die_roll");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.toRadians(a);
        }, "角度转弧度", "toRadians", "to_radians", "radians");
        NamespaceRegistry.register("Math", args -> {
            double a = num(args, 0);
            return Math.toDegrees(a);
        }, "弧度转角度", "toDegrees", "to_degrees", "degrees");
        NamespaceRegistry.register("Math", args -> {
            // hermite blend: 3t^2 - 2t^3
            double t = num(args, 0);
            t = Math.max(0, Math.min(1, t));
            return t * t * (3 - 2 * t);
        }, "平滑插值", "smoothstep", "hermite", "hermiteBlend", "hermite_blend");
    }

    // ========== Str ==========

    private static void registerStr() {
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            return (double) s.length();
        }, "长度", "length");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            int from = (int) num(args, 1);
            int to = (int) num(args, 2);
            return s.substring(Math.max(0, from), Math.min(s.length(), Math.max(from, to)));
        }, "截取", "substring", "sub");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            return s.replace(args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "",
                    args.length > 2 && args[2] != null ? String.valueOf(args[2]) : "");
        }, "替换", "replace");
        NamespaceRegistry.register("Str", args -> str(args, 0).toUpperCase(Locale.ROOT), "大写", "upper", "toUpper");
        NamespaceRegistry.register("Str", args -> str(args, 0).toLowerCase(Locale.ROOT), "小写", "lower", "toLower");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String sep = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : ",";
            List<Object> out = new ArrayList<>();
            for (String part : s.split(java.util.regex.Pattern.quote(sep))) {
                out.add(part);
            }
            return out;
        }, "分割", "split");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String needle = str(args, 1);
            return s.contains(needle);
        }, "包含", "contains");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            return s.trim();
        }, "去除空格", "trim");
        NamespaceRegistry.register("Str", args -> {
            // Str.拼接(列表, 分隔符)
            Object list = args.length > 0 ? args[0] : null;
            String sep = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
            if (list instanceof List<?> l) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        sb.append(sep);
                    }
                    sb.append(l.get(i));
                }
                return sb.toString();
            }
            return list == null ? "" : String.valueOf(list);
        }, "拼接", "join");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String prefix = str(args, 1);
            return s.startsWith(prefix);
        }, "开头是", "startsWith", "starts_with");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String suffix = str(args, 1);
            return s.endsWith(suffix);
        }, "结尾是", "endsWith", "ends_with");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String needle = str(args, 1);
            return (double) s.indexOf(needle);
        }, "索引", "indexOf", "index_of");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String needle = str(args, 1);
            return (double) s.lastIndexOf(needle);
        }, "最后索引", "lastIndexOf", "last_index_of");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            int times = (int) num(args, 1);
            return s.repeat(Math.max(0, times));
        }, "重复", "repeat");
        NamespaceRegistry.register("Str", args -> {
            // Str.格式化("你好 %s，等级 %d", 名字, 等级)
            if (args.length < 1 || args[0] == null) {
                return "";
            }
            String fmt = String.valueOf(args[0]);
            Object[] params = new Object[args.length - 1];
            System.arraycopy(args, 1, params, 0, args.length - 1);
            try {
                return String.format(Locale.ROOT, fmt, params);
            } catch (Exception e) {
                return fmt;
            }
        }, "格式化", "format");
        NamespaceRegistry.register("Str", args -> {
            // 去掉 §/& 颜色码（ChatColor 风格）
            String s = str(args, 0);
            return s.replaceAll("(?i)[§&][0-9a-fk-orx]", "");
        }, "去颜色码", "stripColor", "strip_color");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            try {
                return (double) Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }, "转整数", "parseInt", "parse_int", "toInt", "to_int");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }, "转小数", "parseFloat", "parse_float", "parseDouble", "parse_double", "toDouble", "to_double");
        NamespaceRegistry.register("Str", args -> str(args, 0).isEmpty(), "为空", "isEmpty", "is_empty");
        NamespaceRegistry.register("Str", args -> str(args, 0).isBlank(), "为空白", "isBlank", "is_blank");
        NamespaceRegistry.register("Str", args -> new StringBuilder(str(args, 0)).reverse().toString(), "反转", "reverse");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            int len = (int) num(args, 1);
            String pad = args.length > 2 && args[2] != null ? String.valueOf(args[2]) : " ";
            if (pad.isEmpty()) {
                pad = " ";
            }
            if (s.length() >= len) {
                return s;
            }
            StringBuilder sb = new StringBuilder();
            while (sb.length() < len - s.length()) {
                sb.append(pad);
            }
            return sb.substring(0, len - s.length()) + s;
        }, "左填充", "padLeft", "pad_left");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            int len = (int) num(args, 1);
            String pad = args.length > 2 && args[2] != null ? String.valueOf(args[2]) : " ";
            if (pad.isEmpty()) {
                pad = " ";
            }
            if (s.length() >= len) {
                return s;
            }
            StringBuilder sb = new StringBuilder(s);
            while (sb.length() < len) {
                sb.append(pad);
            }
            return sb.substring(0, len);
        }, "右填充", "padRight", "pad_right");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            int i = (int) num(args, 1);
            return i >= 0 && i < s.length() ? String.valueOf(s.charAt(i)) : "";
        }, "字符", "charAt", "char_at");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String sub = str(args, 1);
            return s.replace(sub, "");
        }, "移除", "remove");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            int index = (int) num(args, 1);
            String sub = str(args, 2);
            if (index < 0 || index > s.length()) {
                return s;
            }
            return s.substring(0, index) + sub + s.substring(index);
        }, "插入", "insert");
        NamespaceRegistry.register("Str", args -> {
            String a = str(args, 0);
            String b = str(args, 1);
            return a.equalsIgnoreCase(b);
        }, "忽略大小写相等", "equalsIgnoreCase", "equals_ignore_case");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String old = str(args, 1);
            String replacement = str(args, 2);
            int i = s.indexOf(old);
            if (i < 0) {
                return s;
            }
            return s.substring(0, i) + replacement + s.substring(i + old.length());
        }, "替换首个", "replaceFirst", "replace_first");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            String regex = str(args, 1);
            try {
                return s.matches(regex);
            } catch (Exception e) {
                return false;
            }
        }, "匹配", "matches");
        NamespaceRegistry.register("Str", args -> {
            String s = str(args, 0);
            if (s.isEmpty()) {
                return s;
            }
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }, "首字母大写", "capitalize");
    }

    // ========== Array（列表操作；尽量返回新列表，不改入参） ==========

    private static void registerArray() {
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            if (list instanceof List<?> l) {
                return (double) l.size();
            }
            return 0.0;
        }, "大小", "size", "length");
        NamespaceRegistry.register("Array", args -> {
            List<Object> out = listOf(args, 0);
            if (args.length < 2) {
                return out;
            }
            out.add(args[1]);
            return out;
        }, "添加", "add", "append", "追加", "push");
        NamespaceRegistry.register("Array", args -> {
            List<Object> out = listOf(args, 0);
            int index = (int) num(args, 1);
            if (index >= 0 && index < out.size()) {
                out.remove(index);
            }
            return out;
        }, "移除", "remove");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            int index = (int) num(args, 1);
            if (list instanceof List<?> l) {
                if (index >= 0 && index < l.size()) {
                    return l.get(index);
                }
                if (index < 0) {
                    int i = l.size() + index; // 负索引：-1 = 最后一个
                    if (i >= 0 && i < l.size()) {
                        return l.get(i);
                    }
                }
            }
            return null;
        }, "获取", "get");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            if (list instanceof List<?> l) {
                return containsValue(l, args.length > 1 ? args[1] : null);
            }
            return false;
        }, "包含", "contains", "has");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            String sep = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : ",";
            if (list instanceof List<?> l) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        sb.append(sep);
                    }
                    sb.append(l.get(i));
                }
                return sb.toString();
            }
            return "";
        }, "拼接", "join");
        NamespaceRegistry.register("Array", args -> {
            // 弹出最后一个并返回它（入参列表不动，返回被弹出元素）
            Object list = args.length > 0 ? args[0] : null;
            if (list instanceof List<?> l && !l.isEmpty()) {
                return l.get(l.size() - 1);
            }
            return null;
        }, "弹出", "pop", "last", "最后");
        NamespaceRegistry.register("Array", args -> {
            // 首个元素
            Object list = args.length > 0 ? args[0] : null;
            if (list instanceof List<?> l && !l.isEmpty()) {
                return l.get(0);
            }
            return null;
        }, "首个", "first");
        NamespaceRegistry.register("Array", args -> {
            List<Object> out = listOf(args, 0);
            out.sort(Comparator.comparing(o -> String.valueOf(o))); // 统一按字符串排序（数字优先位）
            return out;
        }, "排序", "sort");
        NamespaceRegistry.register("Array", args -> {
            List<Object> out = listOf(args, 0);
            java.util.Collections.reverse(out);
            return out;
        }, "反转", "reverse");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            Object needle = args.length > 1 ? args[1] : null;
            if (list instanceof List<?> l) {
                for (int i = 0; i < l.size(); i++) {
                    if (valuesEqual(l.get(i), needle)) {
                        return (double) i;
                    }
                }
            }
            return -1.0;
        }, "索引", "indexOf", "index_of");
        NamespaceRegistry.register("Array", args -> {
            // 切片(list, 起始, 结束?)：负索引从尾部数
            Object list = args.length > 0 ? args[0] : null;
            int from = (int) num(args, 1);
            int to = args.length > 2 ? (int) num(args, 2) : Integer.MAX_VALUE;
            if (!(list instanceof List<?> l)) {
                return new ArrayList<>();
            }
            int size = l.size();
            int start = from < 0 ? size + from : from;
            int end = to < 0 ? size + to : to;
            start = Math.max(0, Math.min(size, start));
            end = Math.max(start, Math.min(size, end));
            return new ArrayList<>(l.subList(start, end));
        }, "切片", "slice", "subList", "sub_list");
        NamespaceRegistry.register("Array", args -> {
            List<Object> out = listOf(args, 0);
            Set<Object> seen = new LinkedHashSet<>(out);
            return new ArrayList<>(seen);
        }, "去重", "unique", "distinct");
        NamespaceRegistry.register("Array", args -> new ArrayList<>(), "清空", "clear");
        NamespaceRegistry.register("Array", args -> {
            // 合并多个列表/值
            List<Object> out = new ArrayList<>();
            for (Object a : args) {
                if (a instanceof List<?> l) {
                    out.addAll(l);
                } else if (a != null) {
                    out.add(a);
                }
            }
            return out;
        }, "合并", "concat", "merge");
        NamespaceRegistry.register("Array", args -> {
            List<Object> out = listOf(args, 0);
            int idx = (int) num(args, 1);
            Object val = args.length > 2 ? args[2] : null;
            idx = Math.max(0, Math.min(out.size(), idx));
            if (val != null) out.add(idx, val);
            return out;
        }, "插入", "insert", "insertAt", "insert_at");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            if (!(list instanceof List<?> l) || l.isEmpty()) return List.of();
            Object fn = args.length > 1 ? args[1] : null;
            if (!(fn instanceof DreamLangExecutor.Callable c)) return new ArrayList<>(l);
            List<Object> out = new ArrayList<>();
            for (Object item : l) {
                Object r = c.call(new Object[]{item});
                if (r instanceof Boolean b ? b : r != null && !"false".equalsIgnoreCase(String.valueOf(r))) out.add(item);
            }
            return out;
        }, "过滤", "filter", "where");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            if (!(list instanceof List<?> l)) return List.of();
            Object fn = args.length > 1 ? args[1] : null;
            if (!(fn instanceof DreamLangExecutor.Callable c)) return new ArrayList<>(l);
            List<Object> out = new ArrayList<>();
            for (Object item : l) out.add(c.call(new Object[]{item}));
            return out;
        }, "映射", "map", "select");
        NamespaceRegistry.register("Array", args -> {
            Object list = args.length > 0 ? args[0] : null;
            if (!(list instanceof List<?> l) || l.isEmpty()) return null;
            Object fn = args.length > 1 ? args[1] : null;
            if (!(fn instanceof DreamLangExecutor.Callable c)) return l.get(0);
            Object acc = args.length > 2 ? args[2] : l.get(0);
            int start = args.length > 2 ? 0 : 1;
            for (int i = start; i < l.size(); i++) acc = c.call(new Object[]{acc, l.get(i)});
            return acc;
        }, "归约", "reduce", "fold", "聚合");
    }

    // ========== Time（纯逻辑版；客户端另有游戏时间） ==========

    private static void registerTime() {
        NamespaceRegistry.register("Time", args -> (double) (System.currentTimeMillis() / 1000), "当前时间戳", "now", "timestamp");
        NamespaceRegistry.register("Time", args -> (double) System.currentTimeMillis(), "当前毫秒", "millis");
    }

    // ========== UUID ==========

    private static void registerUuid() {
        NamespaceRegistry.register("UUID", args -> java.util.UUID.randomUUID().toString(), "随机", "random");
    }

    // ========== 工具 ==========

    private static double num(Object[] args, int index) {
        if (args.length <= index || args[index] == null) {
            return 0;
        }
        if (args[index] instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(args[index]));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(Object[] args, int index) {
        if (args.length <= index || args[index] == null) {
            return "";
        }
        return String.valueOf(args[index]);
    }

    /** 参数转可变列表副本（null/非列表 → 空列表）。 */
    private static List<Object> listOf(Object[] args, int index) {
        Object v = args.length > index ? args[index] : null;
        if (v instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return new ArrayList<>();
    }

    /** 数值感知的包含判断：数字按数值比较（Integer 2 == Long 2），其余按 equals。 */
    private static boolean containsValue(List<?> list, Object needle) {
        for (Object item : list) {
            if (valuesEqual(item, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() == nb.doubleValue();
        }
        return a == null ? b == null : a.equals(b);
    }
}
