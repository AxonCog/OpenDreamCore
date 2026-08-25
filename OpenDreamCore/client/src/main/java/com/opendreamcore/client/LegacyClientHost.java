package com.opendreamcore.client;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;
import com.opendreamcore.page.Page;
import net.minecraft.client.Minecraft;

/**
 * 旧版脚本宿主（client 实现）：给 adapter.dreamcore 的 方法.* 桥提供运行时上下文。
 * 安装时机 = 本地页面加载命中旧格式时（LocalPageManager.parseAuto），幂等。
 */
final class LegacyClientHost implements LegacyMethods.Host {

    private static volatile boolean installed;

    /** 最近一次滚轮值 / 按键名（事件分发时写入，方法.* 读取）。 */
    private static volatile double lastWheel;
    private static volatile String lastKey = "";

    /** 当前页面打开时刻（OdcScreen 构造时写入；取界面存活时间 用）。 */
    private static volatile long pageOpenedAt = System.currentTimeMillis();

    /** 页面打开/重开时调用（OdcScreen 构造）。 */
    static void notePageOpened() {
        pageOpenedAt = System.currentTimeMillis();
    }

    /** 滚轮分发时写入（OdcScreen.mouseScrolled 调用）。 */
    static void setWheelDelta(double v) {
        lastWheel = v;
    }

    /** 按键分发时写入（OdcScreen.keyPressed 调用），处理完清空。 */
    static void setPressedKey(String k) {
        lastKey = k == null ? "" : k;
    }

    /** GLFW 键码 → 旧版键名（E / SPACE / …；ESC 特判）：glfwGetKeyName 主线程调用。 */
    static String keyName(int keyCode, int scanCode) {
        if (keyCode == 256) {
            return "ESCAPE";
        }
        try {
            String n = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, scanCode);
            if (n == null || n.isBlank()) {
                return "";
            }
            return n.toUpperCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private LegacyClientHost() {
    }

    /** 安装（幂等）。 */
    static void install() {
        if (!installed) {
            installed = true;
            LegacyMethods.installHost(new LegacyClientHost());
        }
    }

    @Override
    public void runFunctionAsync(String name) {
        Page page = ClientController.get().currentPage();
        if (page == null || name == null) {
            return;
        }
        String body = page.functions().get(name);
        if (body != null && !body.isBlank()) {
            // 下一拍执行：与调用方脚本解耦，模拟旧版"异步执行方法"语义
            ClientController.get().scheduleScript(body, 1, 0);
        }
    }

    @Override
    public double screenHeight() {
        var mc = Minecraft.getInstance();
        return mc == null || mc.getWindow() == null ? 1080 : mc.getWindow().getGuiScaledHeight();
    }

    @Override
    public double wheelDelta() {
        return lastWheel;
    }

    @Override
    public String pressedKey() {
        return lastKey;
    }

    @Override
    public String slotItem(String identifier) {
        // 容器会话内容属多人裁决体系；单机空槽语义，返回空串
        return "";
    }

    @Override
    public String slotItemLore(Object item) {
        // 单机无容器裁决体系，lore 空串语义与 slotItem 一致
        return "";
    }

    @Override
    public int slotItemCount(Object item) {
        return 0;
    }

    @Override
    public long pageAliveMs() {
        return System.currentTimeMillis() - pageOpenedAt;
    }

    @Override
    public void refreshVariables(String name) {
        // 占位符变量刷新：重布局即可让可刷新占位符重新解析
        ClientController.get().refreshCurrent();
    }
}
