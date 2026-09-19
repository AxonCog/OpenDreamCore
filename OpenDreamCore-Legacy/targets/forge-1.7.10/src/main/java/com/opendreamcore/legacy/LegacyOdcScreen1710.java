package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 隐形指针屏（1.7.10）：交互页的鼠标载体，抄 1.12.2 那套。
 * 原版在这代开着界面就释放指针，真坐标由 drawScreen 喂给 MouseState；
 * 屏本身不画东西（页面归 PageDirector 叠加层画），背景全透。
 * ESC 关屏走 PageDirector.onScreenDismissed，服务器那头补发 page_close。
 */
public final class LegacyOdcScreen1710 extends GuiScreen {

    private static LegacyOdcScreen1710 active;

    private final String pageId;

    private LegacyOdcScreen1710(String pageId) {
        this.pageId = pageId;
    }

    /** ScreenBridge.Host 的 open：同页重复开是空操作，切页才换屏。 */
    public static boolean requestOpen(String pageId) {
        if (pageId == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null && pageId.equals(active.pageId)
                && mc.currentScreen == active) {
            return true;
        }
        LegacyOdcScreen1710 screen = new LegacyOdcScreen1710(pageId);
        active = screen;
        mc.displayGuiScreen(screen);
        return mc.currentScreen == screen;
    }

    /** 收屏抓回指针（没开就空转）。 */
    public static void requestClose() {
        LegacyOdcScreen1710 s = active;
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
        return false; // 单机别暂停，跟现代端菜单页一个行为
    }

    @Override
    public void initGui() {
        super.initGui();
        org.lwjgl.input.Mouse.setGrabbed(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 不画背景——页面由叠加层负责；这里只把真鼠标换算成 guiScaled 坐标喂出去
        double scale = mc.displayWidth / (double) Math.max(1, this.width);
        com.opendreamcore.client.render.MouseState.set(
                org.lwjgl.input.Mouse.getX() / scale,
                this.height - 1 - org.lwjgl.input.Mouse.getY() / scale,
                org.lwjgl.input.Mouse.isButtonDown(0));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC：页面本地关掉
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
        // 别的键不吞：键位绑定在 KeyHandler 里照常收
    }

    @Override
    public void onGuiClosed() {
        if (active == this) {
            active = null;
        }
        org.lwjgl.input.Mouse.setGrabbed(true);
    }
}
