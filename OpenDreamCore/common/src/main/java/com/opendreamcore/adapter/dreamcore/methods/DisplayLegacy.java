package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/**
 * 发送/显示类旧方法：标题、动作栏、副标题、通知、富文本等。
 * 全部委派到客户端 Title/Tip/Message/Chat 命名空间。
 */
public final class DisplayLegacy {

    private DisplayLegacy() {
    }

    public static void install() {
        // —— 标题族 ——
        LegacyMethods.register("发送标题", a -> LegacyMethods.delegate("Title",
                "大标题", a.length > 0 ? a[0] : null, a.length > 1 ? a[1] : null));
        LegacyMethods.register("发送副标题", a -> LegacyMethods.delegate("Title",
                "小标题", a.length > 0 ? a[0] : null));
        LegacyMethods.register("发送动作栏", a -> LegacyMethods.delegate("Title",
                "动作栏", a.length > 0 ? a[0] : null));
        LegacyMethods.register("发送操作栏", a -> LegacyMethods.delegate("Title",
                "动作栏", a.length > 0 ? a[0] : null));

        // —— 消息族 ——
        LegacyMethods.register("发送消息", a -> LegacyMethods.delegate("Message",
                "系统", a.length > 0 ? a[0] : null));
        LegacyMethods.register("发送聊天", a -> LegacyMethods.delegate("Chat",
                "发送消息", a));
        LegacyMethods.register("发送聊天消息", a -> LegacyMethods.delegate("Chat",
                "发送消息", a));
        LegacyMethods.register("发送颜色消息", a -> LegacyMethods.delegate("Message",
                "系统", a.length > 0 ? a[0] : null));
        LegacyMethods.register("发送背景消息", a -> LegacyMethods.delegate("Message",
                "系统", a.length > 0 ? a[0] : null));
        LegacyMethods.register("发送通知", a -> LegacyMethods.delegate("Tip",
                "提示", a.length > 0 ? a[0] : null));
        LegacyMethods.register("发送富文本", a -> LegacyMethods.delegate("Message",
                "系统", a.length > 0 ? a[0] : null));

        // —— HUD 开关（客户端常驻条）——
        LegacyMethods.register("打开HUD", a -> LegacyMethods.delegate("Screen", "设置变量", "_odc_hud_show", true));
        LegacyMethods.register("关闭HUD", a -> LegacyMethods.delegate("Screen", "设置变量", "_odc_hud_show", false));

        // —— 界面开关 ——
        LegacyMethods.register("打开界面", a -> LegacyMethods.delegate("Screen", "打开页面",
                a.length > 0 ? a[0] : null));
        LegacyMethods.register("关闭主界面", a -> LegacyMethods.delegate("Screen", "关闭页面"));
        LegacyMethods.register("打开聊天", a -> LegacyMethods.delegate("Chat", "打开聊天"));
        LegacyMethods.register("打开聊天栏", a -> LegacyMethods.delegate("Chat", "打开聊天"));
    }
}
