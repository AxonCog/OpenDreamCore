package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class MiscLegacy {
    private MiscLegacy() { }

    public static void install() {
        LegacyMethods.register("print", a -> {
            System.out.println("[ODC] " + (a.length > 0 ? String.valueOf(a[0]) : ""));
            return null;
        });
        LegacyMethods.register("取剪切板", a -> {
            try {
                var cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                return (String) cb.getContents(null).getTransferData(
                        java.awt.datatransfer.DataFlavor.stringFlavor);
            } catch (Exception e) { return ""; }
        });
        LegacyMethods.register("设置剪切板", a -> {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(LegacyMethods.argStr(a, 0)), null);
            } catch (Exception ignored) { }
            return null;
        });
        LegacyMethods.register("取编码", a -> "UTF-8");
        LegacyMethods.register("取字符", a -> {
            String s = LegacyMethods.str(a, 0);
            int idx = (int) LegacyMethods.num2(a, 1);
            return s != null && idx >= 0 && idx < s.length() ? s.substring(idx, idx + 1) : "";
        });
        LegacyMethods.register("获取字符", a -> {
            String s = LegacyMethods.str(a, 0);
            int idx = (int) LegacyMethods.num2(a, 1);
            return s != null && idx >= 0 && idx < s.length() ? s.substring(idx, idx + 1) : "";
        });
    }
}
