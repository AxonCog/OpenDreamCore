package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 隐形指针屏：交互页（菜单页/可点击的世界面板）的鼠标载体。开着时原版
 * 释放指针，真坐标喂给 MouseState（命中结算在渲染钩子里照常跑）；自身
 * 不画任何东西（页面由 PageDirector 的叠加层画），背景全透不挡世界。
 * ESC 关屏：发 page_close 给服务器 + 清本地活跃页，现代端 onClose 同语义。
 */
public final class LegacyOdcScreen extends GuiScreen {

    private static LegacyOdcScreen active;

    private final String pageId;

    private LegacyOdcScreen(String pageId) {
        this.pageId = pageId;
    }

    /** ScreenBridge.Host 入口：开/切都在这（幂等：同页重复开是无操作）。 */
    public static boolean requestOpen(String pageId) {
        if (pageId == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null && pageId.equals(active.pageId)
                && mc.currentScreen == active) {
            return true;
        }
        LegacyOdcScreen screen = new LegacyOdcScreen(pageId);
        active = screen;
        mc.displayGuiScreen(screen);
        return mc.currentScreen == screen;
    }

    /** 收屏抓回指针（幂等，没开就空转）。 */
    public static void requestClose() {
        LegacyOdcScreen s = active;
        if (s == null) {
            return;
        }
        active = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == s) {
            mc.displayGuiScreen(null);
        }
    }

    public static boolean isOpen() {
        return active != null && Minecraft.getMinecraft().currentScreen == active;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // 单机不暂停，跟现代端菜单一致
    }

    @Override
    public void initGui() {
        super.initGui();
        org.lwjgl.input.Mouse.setGrabbed(false); // 真指针放出来
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 全透：不画背景不画页面（PageDirector 的叠加层负责画页面），
        // 只把真鼠标坐标喂给共享层（guiScaled 系，跟命中盒同坐标系）。
        // 点击/拖拽的边沿检测都在 MouseState，这里只负责喂。
        double scale = mc.displayWidth / (double) this.width;
        com.opendreamcore.client.render.MouseState.set(
                org.lwjgl.input.Mouse.getX() / scale,
                this.height - 1 - org.lwjgl.input.Mouse.getY() / scale,
                org.lwjgl.input.Mouse.isButtonDown(0));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC
            com.opendreamcore.client.render.PageDirector.onScreenDismissed(this.pageId);
            requestClose();
            return;
        }
        if (com.opendreamcore.client.spi.KeyboardBridge.hasFocus()) {
            if (keyCode == 14) {
                com.opendreamcore.client.spi.KeyboardBridge.backspace(); // 退格
            } else if (keyCode == 28 || keyCode == 156) {
                com.opendreamcore.client.spi.KeyboardBridge.enter(); // 回车/小键盘回车
            } else if (typedChar >= ' ' && typedChar != 127) {
                com.opendreamcore.client.spi.KeyboardBridge.type(typedChar);
            }
        }
        // 其余按键不拦截：页面键鼠绑定在键位钩子里照常收
    }

    @Override
    public void onGuiClosed() {
        if (active == this) {
            active = null;
        }
        org.lwjgl.input.Mouse.setGrabbed(true); // 回游戏抓回指针
    }
}
