package com.opendreamcore.legacy;

import com.opendreamcore.script.NamespaceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

/**
 * 客户端命名空间注册（1.12.2）：FG2.3 映射名直调。
 * 与 1.16.5 版同构——同一批方法桥名字面落到本版本 API。
 */
public final class LegacyNamespaces {
    private LegacyNamespaces() {
    }

    public static void install() {
        Minecraft mc = Minecraft.getMinecraft();

        java.util.Map<String, com.opendreamcore.script.NamespaceRegistry.Handler> impl =
                new java.util.LinkedHashMap<>();
        impl.put("getName", a -> mc.player != null ? mc.player.getGameProfile().getName() : "");
        impl.put("getHealth", a -> mc.player != null ? mc.player.getHealth() : 0);
        impl.put("getMaxHealth", a -> mc.player != null ? mc.player.getMaxHealth() : 0);
        impl.put("getX", a -> mc.player != null ? mc.player.posX : 0);
        impl.put("getY", a -> mc.player != null ? mc.player.posY : 0);
        impl.put("getZ", a -> mc.player != null ? mc.player.posZ : 0);
        impl.put("getYaw", a -> mc.player != null ? mc.player.rotationYaw : 0);
        impl.put("getPitch", a -> mc.player != null ? mc.player.rotationPitch : 0);
        impl.put("getExp", a -> mc.player != null ? mc.player.experience : 0);
        impl.put("getLevel", a -> mc.player != null ? mc.player.experienceLevel : 0);
        impl.put("getHunger", a -> mc.player != null ? mc.player.getFoodStats().getFoodLevel() : 0);
        // GUI 缩放尺寸每次现算（1.12.2 无窗口对象缓存）
        impl.put("getWidth", a -> new ScaledResolution(mc).getScaledWidth());
        impl.put("getHeight", a -> new ScaledResolution(mc).getScaledHeight());

        for (java.util.Map.Entry<String, com.opendreamcore.script.NamespaceRegistry.Handler> e : impl.entrySet()) {
            NamespaceRegistry.registerOrReplace("Player", e.getKey(), e.getValue());
        }
        String[][] playerAliases = {
                {"获取名字", "getName"}, {"获取血量", "getHealth"}, {"获取最大血量", "getMaxHealth"},
                {"获取X", "getX"}, {"获取Y", "getY"}, {"获取Z", "getZ"},
                {"获取视角", "getYaw"}, {"获取俯仰", "getPitch"},
                {"获取经验", "getExp"}, {"获取等级", "getLevel"},
                {"获取饥饿", "getHunger"},
        };
        for (String[] pair : playerAliases) {
            NamespaceRegistry.registerOrReplace("Player", pair[0], impl.get(pair[1]));
        }
        // Screen/Var：页面变量读写（client 层 PageVariables 后端）
        NamespaceRegistry.registerOrReplace("Screen", "设置变量",
                a -> { com.opendreamcore.client.PageVariables.set(null,
                        str(a, 0), a.length > 1 ? a[1] : null); return null; });
        NamespaceRegistry.registerOrReplace("Screen", "获取变量",
                a -> com.opendreamcore.client.PageVariables.get(null, str(a, 0)));
        NamespaceRegistry.registerOrReplace("Var", "设置变量",
                a -> { com.opendreamcore.client.PageVariables.set(null,
                        str(a, 0), a.length > 1 ? a[1] : null); return null; });
        NamespaceRegistry.registerOrReplace("Var", "获取变量",
                a -> com.opendreamcore.client.PageVariables.get(null, str(a, 0)));
                NamespaceRegistry.registerOrReplace("Display", "getWidth", impl.get("getWidth"));
        NamespaceRegistry.registerOrReplace("Display", "getHeight", impl.get("getHeight"));
        NamespaceRegistry.registerOrReplace("Display", "获取窗口宽", impl.get("getWidth"));
        NamespaceRegistry.registerOrReplace("Display", "获取窗口高", impl.get("getHeight"));

        // Chat/Title：1.12.2 GuiIngame + FontRenderer
        NamespaceRegistry.registerOrReplace("Chat", "发送消息", a ->
        {
            if (mc.player != null && a != null && a.length > 0 && a[0] != null) {
                mc.ingameGUI.getChatGUI().printChatMessage(
                        new net.minecraft.util.text.TextComponentString(String.valueOf(a[0])));
            }
            return null;
        });
        NamespaceRegistry.registerOrReplace("Title", "showTitle", a ->
        {
            if (mc.player == null) {
                return null;
            }
            String title = a != null && a.length > 0 && a[0] != null ? String.valueOf(a[0]) : "";
            mc.ingameGUI.displayTitle(title, null, 10, 70, 10);
            return null;
        });

        // Sound：本地播放（注册表事件；未注册 id 走 master 直播）
        NamespaceRegistry.registerOrReplace("Sound", "play", a ->
        {
            if (mc.player == null || a == null || a.length < 1 || a[0] == null) {
                return null;
            }
            float vol = a.length > 1 && a[1] instanceof Number
                    ? ((Number) a[1]).floatValue() : 1.0F;
            ResourceLocation rl = new ResourceLocation(String.valueOf(a[0]));
            net.minecraft.util.SoundEvent ev = new net.minecraft.util.SoundEvent(rl)
                    .setRegistryName(rl);
            mc.player.playSound(ev, vol, 1.0F);
            return null;
        });
        // Music：与 Sound 同路（1.12.2 无独立音乐工厂）；stop 自然结束降级
        NamespaceRegistry.registerOrReplace("Music", "play", a ->
        {
            if (mc.player == null || a == null || a.length < 1 || a[0] == null) {
                return null;
            }
            ResourceLocation rl = new ResourceLocation(String.valueOf(a[0]));
            net.minecraft.util.SoundEvent ev =
                    new net.minecraft.util.SoundEvent(rl).setRegistryName(rl);
            mc.player.playSound(ev,
                    a.length > 1 && a[1] instanceof Number ? ((Number) a[1]).floatValue() : 1.0F,
                    1.0F);
            return null;
        });
        NamespaceRegistry.registerOrReplace("Music", "stop", a -> null);
    }

    private static String str(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }
}
