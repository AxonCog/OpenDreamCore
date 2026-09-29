package com.opendreamcore.client;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;
import com.opendreamcore.adapter.dreamcore.methods.EntityLegacy;
import com.opendreamcore.client.visual.VisualNameTags;
import com.opendreamcore.page.Page;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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

    /** 读取当前键名上下文（脚本 Key.当前按下键 桥）。 */
    static String pressedKeyValue() {
        return lastKey;
    }

    /** GLFW 键码 → 旧版键名（E / SPACE / …；ESC/RETURN 特判）：glfwGetKeyName 主线程调用。 */
    static String keyName(int keyCode, int scanCode) {
        if (keyCode == 256) {
            return "ESCAPE";
        }
        if (keyCode == 257) {
            // 旧配置（DragonCore yml）把 Enter 叫 RETURN，glfwGetKeyName 对非打印键返回 null
            return "RETURN";
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
            // 旧方法注册表 + 实体语境宿主一起装齐（头顶页面脚本也要用 方法.取实体血量()）
            LegacyMethods.ensureRegistered();
            LegacyMethods.installHost(new LegacyClientHost());
            installEntityHost();
        }
    }

    /**
     * 实体语境宿主（线C②）：方法.取实体血量/最大血量/名/高度/比例 优先读
     * VisualNameTags.CURRENT_ENTITY（头顶渲染名牌的当前实体）；指向实体族
     * （取指向实体/取指向生物X）同步从 NOOP 空转升级为十字准星实体。
     */
    private static void installEntityHost() {
        EntityLegacy.installHost(new EntityLegacy.HostExt() {
            @Override
            public Object aimedEntity() {
                Minecraft mc = Minecraft.getInstance();
                return mc == null ? null : mc.crosshairPickEntity;
            }

            @Override
            public String field(String f) {
                if (!(aimedEntity() instanceof Entity e)) {
                    return "";
                }
                String key = f == null ? "" : f;
                if (key.equals("uuid")) {
                    return e.getUUID().toString();
                }
                if (key.equals("name")) {
                    return e.getName().getString();
                }
                if (key.equals("health") && e instanceof LivingEntity l) {
                    return String.valueOf(l.getHealth());
                }
                if (key.equals("maxHealth") && e instanceof LivingEntity l) {
                    return String.valueOf(l.getMaxHealth());
                }
                return "";
            }

            @Override
            public Object nearby(String type, double range) {
                return java.util.List.of();
            }

            @Override
            public Object headField(String f) {
                Entity e = VisualNameTags.currentEntity();
                if (e == null) {
                    return null;
                }
                String key = f == null ? "" : f;
                switch (key) {
                    case "name":
                        return e.getName().getString();
                    case "height":
                        return (double) e.getBbHeight();
                    case "health":
                        return e instanceof LivingEntity l ? l.getHealth() : 0.0;
                    case "maxHealth":
                        return e instanceof LivingEntity l ? l.getMaxHealth() : 0.0;
                    case "ratio":
                        return e instanceof LivingEntity l
                                ? (l.getMaxHealth() > 0.0 ? l.getHealth() / l.getMaxHealth() : 0.0)
                                : 1.0;
                    default:
                        return null;
                }
            }
        });
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
