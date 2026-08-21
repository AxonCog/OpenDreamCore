package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;

/**
 * OpenDreamCore 对外 Java API（供附属插件调用）。
 *
 * 快速上手：
 * <pre>
 * // 打开页面
 * OpenDreamCoreAPI.gui().open(player, "menu");
 *
 * // 推送页面变量（页面 {{vars.gold}} 立即刷新）
 * OpenDreamCoreAPI.gui().setVariable(player, "gold", 999);
 *
 * // 修改元素属性
 * OpenDreamCoreAPI.element().setProp(player, "title", "text.content", "新标题");
 *
 * // 显示 Boss 条
 * OpenDreamCoreAPI.hud().showBossBar(null, "boss1", "末影龙", 60, "#E53935");
 *
 * // 注册自定义脚本方法
 * ScriptAPI.register("Shop", "购买", args -> { ... });
 *
 * // 监听 GUI 事件
 * @EventHandler void onButtonClick(OdcEvents.ButtonEvent e) { ... }
 * </pre>
 *
 * 依赖方式（build.gradle）：
 * <pre>
 * compileOnly 'com.opendreamcore:opendreamcore-plugin:0.1.0'
 * </pre>
 * 插件未加载时所有调用安全返回默认值（false/null）。
 */
public final class OpenDreamCoreAPI {

    private OpenDreamCoreAPI() {
    }

    /** 页面 API：打开/关闭/变量推送/元素属性/容器绑定。 */
    public static GUIAPI gui() {
        return GUIAPI.INSTANCE;
    }

    /** HUD/世界 UI API：HUD 三型 / Boss 条 / 名牌 / 物品提示 / 音乐 / 屏幕特效。 */
    public static HUDAPI hud() {
        return HUDAPI.INSTANCE;
    }

    /** Tooltip API：运行时注册/移除元素提示。 */
    public static TooltipAPI tooltip() {
        return TooltipAPI.INSTANCE;
    }

    /** 容器 API：读写槽位物品 / 光标管理 / 手动重同步。 */
    public static ContainerAPI container() {
        return ContainerAPI.INSTANCE;
    }

    /** 元素 API：显隐 / 属性修改（dotted path）。 */
    public static ElementAPI element() {
        return ElementAPI.INSTANCE;
    }

    /** 脚本 API：注册自定义 DreamLang 方法（附属插件扩展脚本能力）。 */
    public static ScriptAPI script() {
        return ScriptAPI.INSTANCE;
    }

    /** 插件实例（未加载返回 null）。 */
    static OpenDreamCorePlugin plugin() {
        return OpenDreamCorePlugin.get();
    }
}
