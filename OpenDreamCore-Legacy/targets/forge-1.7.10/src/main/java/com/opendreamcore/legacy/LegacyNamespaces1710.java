package com.opendreamcore.legacy;

import com.opendreamcore.script.NamespaceRegistry;
import net.minecraft.client.Minecraft;

/** 1.7.10 命名空间：FG1.2 映射名直调（thePlayer/posX 时代字段）。 */
public final class LegacyNamespaces1710 {
    private LegacyNamespaces1710() { }

    public static void install() {
        Minecraft mc = Minecraft.getMinecraft();

        java.util.Map<String, NamespaceRegistry.Handler> impl = new java.util.LinkedHashMap<>();
        impl.put("getName", a -> mc.thePlayer != null ? mc.thePlayer.getGameProfile().getName() : "");
        impl.put("getHealth", a -> mc.thePlayer != null ? mc.thePlayer.getHealth() : 0);
        impl.put("getMaxHealth", a -> mc.thePlayer != null ? mc.thePlayer.getMaxHealth() : 0);
        impl.put("getX", a -> mc.thePlayer != null ? mc.thePlayer.posX : 0);
        impl.put("getY", a -> mc.thePlayer != null ? mc.thePlayer.posY : 0);
        impl.put("getZ", a -> mc.thePlayer != null ? mc.thePlayer.posZ : 0);
        impl.put("getYaw", a -> mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0);
        impl.put("getPitch", a -> mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0);
        impl.put("getExp", a -> mc.thePlayer != null ? mc.thePlayer.experience : 0);
        impl.put("getLevel", a -> mc.thePlayer != null ? mc.thePlayer.experienceLevel : 0);
        impl.put("getHunger", a -> mc.thePlayer != null ? mc.thePlayer.getFoodStats().getFoodLevel() : 0);

        for (java.util.Map.Entry<String, com.opendreamcore.script.NamespaceRegistry.Handler> e : impl.entrySet()) {
            NamespaceRegistry.registerOrReplace("Player", e.getKey(), e.getValue());
        }
        String[][] aliases = {
                {"获取名字", "getName"}, {"获取血量", "getHealth"}, {"获取最大血量", "getMaxHealth"},
                {"获取X", "getX"}, {"获取Y", "getY"}, {"获取Z", "getZ"},
                {"获取视角", "getYaw"}, {"获取俯仰", "getPitch"},
                {"获取经验", "getExp"}, {"获取等级", "getLevel"}, {"获取饥饿", "getHunger"},
        };
        for (String[] pair : aliases) {
            NamespaceRegistry.registerOrReplace("Player", pair[0], impl.get(pair[1]));
        }
        NamespaceRegistry.registerOrReplace("Chat", "发送消息", a ->
        {
            if (mc.thePlayer != null && a != null && a.length > 0 && a[0] != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(String.valueOf(a[0])));
            }
            return null;
        });
    }
}
