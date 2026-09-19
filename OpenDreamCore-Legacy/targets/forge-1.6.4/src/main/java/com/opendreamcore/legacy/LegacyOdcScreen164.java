package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 隐形指针屏（1.6.4）：跟 1.7.10 那版一个模子——那两代的 GuiScreen 接口
 * 几乎没差（keyTyped/drawScreen 同签名），只是类名各起各的方便对号。
 * 页面归 PageDirector 叠加层画，这屏只管放指针、喂真坐标、接 ESC。
 */
public final class LegacyOdcScreen164 extends GuiScreen {

    private static LegacyOdcScreen164 active;

    private final String pageId;

    private LegacyOdcScreen164(String pageId) {
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
        LegacyOdcScreen164 screen = new LegacyOdcScreen164(pageId);
        active = screen;
        mc.displayGuiScreen(screen);
        return mc.currentScreen == screen;
    }

    /** 收屏抓回指针（没开就空转）。 */
    public static void requestClose() {
        LegacyOdcScreen164 s = active;
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
        org.lwjgl.input.Mouse.setGrabbed(false); // 真指针放出来（同 1.12.2 模板）
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 不画背景；真鼠标换算成 guiScaled 坐标喂出去（和命中盒同系）
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
        org.lwjgl.input.Mouse.setGrabbed(true); // 回游戏抓回指针
    }
}
