package com.opendreamcore;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.opendreamcore.client.render.MouseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

/**
 * 隐形指针屏（1.16.5）：共享层的 feedMouse 只认 in-game——开着任何 screen
 * 就喂 -9999 假坐标，而且这代 overlay 事件在有界面时根本不触发，菜单页的
 * 按钮永远点不着。跟 1.12.2 同款解法：自己开一屏当指针宿主，屏本身全透，
 * render 里喂真鼠标 + 直接调 ClientHooks1165.renderPage() 把页面画出来。
 */
public final class LegacyOdcScreen1165 extends Screen {

    private static LegacyOdcScreen1165 active;

    private final String pageId;

    private LegacyOdcScreen1165(String pageId) {
        super(new StringTextComponent("OpenDreamCore"));
        this.pageId = pageId;
    }

    /** ScreenBridge.Host 的 open：同页重复开是空操作，切页才换屏。 */
    public static boolean requestOpen(String pageId) {
        if (pageId == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (active != null && pageId.equals(active.pageId)
                && mc.screen == active) {
            return true;
        }
        LegacyOdcScreen1165 screen = new LegacyOdcScreen1165(pageId);
        active = screen;
        mc.forceSetScreen(screen);
        return mc.screen == screen;
    }

    /** 收屏（没开就空转）。 */
    public static void requestClose() {
        LegacyOdcScreen1165 s = active;
        if (s == null) {
            return;
        }
        active = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == s) {
            mc.forceSetScreen(null);
        }
    }

    public static boolean isOpen() {
        return active != null && Minecraft.getInstance().screen == active;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        // 不画暗幕不画控件——页面归 renderPage 那套路画（和 overlay 同一个入口）。
        // 这代 Screen 自带 guiScaled 真鼠标入参，直接喂；按键 GLFW 问窗口要。
        long win = Minecraft.getInstance().getWindow().getWindow();
        MouseState.set(mouseX, mouseY,
                org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, 0)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS);
        ClientHooks1165.renderPage();
    }

    @Override
    public void onClose() {
        if (active == this) {
            active = null;
        }
        com.opendreamcore.client.spi.KeyboardBridge.clearFocus();
        // ESC/X 关屏：告诉 PageDirector 一声，它补发 page_close 并清活跃页
        com.opendreamcore.client.render.PageDirector.onScreenDismissed(this.pageId);
        super.onClose();
    }

    /** 按键：退格/回车喂给键盘桥（焦点输入框在才消费），ESC 走原版关屏。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (com.opendreamcore.client.spi.KeyboardBridge.hasFocus()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                com.opendreamcore.client.spi.KeyboardBridge.backspace();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                com.opendreamcore.client.spi.KeyboardBridge.enter();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 可打印字符统一进键盘桥（焦点输入框在才消费）。 */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (com.opendreamcore.client.spi.KeyboardBridge.hasFocus()
                && codePoint >= ' ' && codePoint != 0x7F) {
            com.opendreamcore.client.spi.KeyboardBridge.type(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
