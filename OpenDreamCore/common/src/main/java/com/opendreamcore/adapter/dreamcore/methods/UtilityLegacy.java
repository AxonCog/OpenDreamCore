package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/**
 * 工具类旧方法：数学、文本、剪切板、执行命令等零环境依赖批次。
 */
public final class UtilityLegacy {

    private UtilityLegacy() {
    }

    public static void install() {
        LegacyMethods.register("取绝对值", a -> Math.abs(LegacyMethods.num(a, 0)));
        LegacyMethods.register("向上取整", a -> Math.ceil(LegacyMethods.num(a, 0)));
        LegacyMethods.register("向下取整", a -> Math.floor(LegacyMethods.num(a, 0)));
        LegacyMethods.register("取余", a -> {
            double x = LegacyMethods.num(a, 0);
            double y = LegacyMethods.num(a, 1);
            return y == 0 ? 0 : x % y;
        });
        LegacyMethods.register("取长度", a -> {
            String s = LegacyMethods.str(a, 0);
            return s == null ? 0 : s.length();
        });
        LegacyMethods.register("取文本长度", a -> {
            String s = LegacyMethods.str(a, 0);
            return s == null ? 0 : s.length();
        });
        LegacyMethods.register("截取", a -> {
            String s = LegacyMethods.str(a, 0);
            if (s == null) return "";
            int from = (int) LegacyMethods.num(a, 1);
            int to = (int) LegacyMethods.num(a, 2);
            return s.substring(Math.max(0, Math.min(from, s.length())),
                    Math.max(0, Math.min(to, s.length())));
        });
        LegacyMethods.register("替换全部", a -> {
            String s = LegacyMethods.str(a, 0);
            if (s == null) return "";
            for (int i = 1; i + 1 < a.length; i += 2) {
                s = s.replace(LegacyMethods.str(a, i), LegacyMethods.str(a, i + 1));
            }
            return s;
        });
        LegacyMethods.register("发送命令", a -> {
            LegacyMethods.delegate("Network", "发送命令", a.length > 0 ? a[0] : null);
            return null;
        });
        LegacyMethods.register("执行命令", a -> {
            LegacyMethods.delegate("Network", "发送命令", a.length > 0 ? a[0] : null);
            return null;
        });
        LegacyMethods.register("打开网页", a -> {
            LegacyMethods.delegate("Network", "打开网页", a.length > 0 ? a[0] : null);
            return null;
        });
    }
}
