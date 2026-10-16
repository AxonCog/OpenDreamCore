package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.NamespaceRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 方法.* 旧脚本桥测试：纯本地方法、委派路由、零参裸调用改写。
 */
class LegacyMethodsTest {

    @Test
    void pureStringAndTimeMethods() {
        LegacyMethods.ensureRegistered();
        assertEquals("a-b-c", MethodRegistry.require("合并文本").invoke(new Object[]{"a", "-", "b", "-", "c"}));
        assertEquals("你好世MC", MethodRegistry.require("替换")
                .invoke(new Object[]{"你好,MC", ",", "世"}));
        // 替换成对参数："," → "世"，".com" → ""
        Object time = MethodRegistry.require("取当前时间格式化").invoke(new Object[0]);
        assertTrue(time instanceof String t && t.matches("\\d{2}:\\d{2}:\\d{2}"), "HH:mm:ss 格式");
    }

    @Test
    void delegationRoutesToClientNamespaces() {
        AtomicReference<Object[]> captured = new AtomicReference<>();
        NamespaceRegistry.register("Screen", "关闭页面", args -> {
            captured.set(args);
            return null;
        });
        NamespaceRegistry.register("Music", "播放", args -> "played:" + args[0]);
        LegacyMethods.ensureRegistered();

        assertNull(MethodRegistry.require("关闭界面").invoke(new Object[0]));
        assertNotNull(captured.get(), "关闭界面 → Screen.关闭页面");

        Object r = MethodRegistry.require("播放声音").invoke(new Object[]{"菜单/打开菜单.ogg"});
        assertEquals("played:菜单/打开菜单.ogg", r);
    }

    @Test
    void legacyKeyStatusDelegatesToHost() {
        LegacyMethods.ensureRegistered();
        LegacyMethods.Host original = LegacyMethods.host();
        try {
            LegacyMethods.installHost(new LegacyMethods.Host() {
                @Override public void runFunctionAsync(String name) { }
                @Override public double screenHeight() { return 0; }
                @Override public double wheelDelta() { return 0; }
                @Override public String pressedKey() { return ""; }
                @Override public boolean keyDown(String key) { return "LMENU".equals(key); }
                @Override public String slotItem(String identifier) { return ""; }
                @Override public int slotItemCount(Object item) { return 0; }
                @Override public void refreshVariables(String name) { }
                @Override public long pageAliveMs() { return 0; }
            });
            assertEquals(true, MethodRegistry.require("键位是否按下").invoke(new Object[]{"LMENU"}));
            assertEquals(false, MethodRegistry.require("键位是否按下").invoke(new Object[]{"W"}));
        } finally {
            LegacyMethods.installHost(original);
        }
    }

    @Test
    void sleepMethodsPreserveInterruptStatus() {
        LegacyMethods.ensureRegistered();

        Thread.currentThread().interrupt();
        try {
            Object pause = MethodRegistry.require("延时").invoke(new Object[]{0L});
            assertInstanceOf(com.opendreamcore.script.engine.DreamLang.PauseRequest.class, pause);
            assertEquals(0L, ((com.opendreamcore.script.engine.DreamLang.PauseRequest) pause).delayMs());
            assertTrue(Thread.currentThread().isInterrupted(), "延时返回暂停请求且不能清除线程中断信号");
        } finally {
            Thread.interrupted();
        }

        Thread.currentThread().interrupt();
        try {
            assertNull(MethodRegistry.require("thread_sleep").invoke(new Object[]{0L}));
            assertTrue(Thread.currentThread().isInterrupted(), "thread_sleep 不能吞掉线程中断信号");
        } finally {
            Thread.interrupted();
        }

        Thread.currentThread().interrupt();
        try {
            assertNull(MethodRegistry.require("线程休眠").invoke(new Object[]{0L}));
            assertTrue(Thread.currentThread().isInterrupted(), "线程休眠不能吞掉线程中断信号");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void legacyPropPathMapping() {
        assertEquals("image.src", LegacyMethods.legacyPropPath("texture"));
        assertEquals("image.hoverSrc", LegacyMethods.legacyPropPath("textureHovered"));
        assertEquals("opacity", LegacyMethods.legacyPropPath("alpha"));
        assertEquals("tooltip", LegacyMethods.legacyPropPath("tip"));
        assertEquals("width", LegacyMethods.legacyPropPath("width"), "未知键原样透传");
    }

    @Test
    void zeroArgBareCallsGetParens() {
        assertEquals("方法.关闭界面();",
                LegacyMethods.ensureZeroArgParens("方法.关闭界面;"));
        assertEquals("(方法.取屏幕高度()/700)",
                LegacyMethods.ensureZeroArgParens("(方法.取屏幕高度/700)"));
        // 已带括号的不重复加
        assertEquals("方法.延时(100);",
                LegacyMethods.ensureZeroArgParens("方法.延时(100);"));
        // 非方法名不受影响
        assertEquals("变量.x = 用户变量.y;", LegacyMethods.ensureZeroArgParens("变量.x = 用户变量.y;"));
    }

    @Test
    void parserRewritesFunctionsAndActions() {
        Map<String, Object> out = new DreamCoreParser().parse("""
                Functions:
                  open: |-
                    方法.异步执行方法('每秒刷新')
                    方法.关闭界面
                  keyPress: "(方法.取当前按下键=='E')?{方法.异步执行方法('关闭界面');}:0"
                标题:
                  type: label
                  actions:
                    click: |-
                      方法.聊天('/spawn')
                      方法.打开GUI('传送')
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> fns = (Map<String, Object>) out.get("Functions");
        assertTrue(fns.get("open").toString().contains("方法.异步执行方法('每秒刷新')"),
                "已带括号调用保持不变");
        assertTrue(fns.get("open").toString().contains("方法.关闭界面()"),
                "Functions 裸调用补括号");
        assertTrue(fns.get("keyPress").toString().contains("方法.取当前按下键()=="),
                "表达式内裸调用补括号");
        // 元素：_label 后缀推断 + actions 改写（摊平后按 id 直取）
        assertTrue(out.get("标题") instanceof Map<?, ?>, "label 后缀推断的元素保留为顶层键");
        @SuppressWarnings("unchecked")
        Map<String, Object> el = (Map<String, Object>) out.get("标题");
        assertEquals("text", el.get("type"), "_label 后缀推断为 text");
        @SuppressWarnings("unchecked")
        Map<String, Object> actions = (Map<String, Object>) el.get("actions");
        assertFalse(actions.get("click").toString().contains("方法.聊天()"),
                "带参调用不被误改");
    }
}
