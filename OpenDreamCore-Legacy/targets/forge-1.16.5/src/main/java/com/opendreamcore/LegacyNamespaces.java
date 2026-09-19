package com.opendreamcore;

import com.opendreamcore.script.NamespaceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * 客户端命名空间注册：把 1.16.5 平台实现挂进 common 的方法桥。
 * LegacyMethods 的 1742 个名字面里凡委派到这些命名空间的调用全部由此落地。
 */
public final class LegacyNamespaces {
    private LegacyNamespaces() {
    }

    public static void install() {
        Minecraft mc = Minecraft.getInstance();

        java.util.Map<String, com.opendreamcore.script.NamespaceRegistry.Handler> impl =
                new java.util.LinkedHashMap<>();
        impl.put("getName", a -> mc.player != null ? mc.player.getGameProfile().getName() : "");
        impl.put("getHealth", a -> mc.player != null ? mc.player.getHealth() : 0);
        impl.put("getMaxHealth", a -> mc.player != null ? mc.player.getMaxHealth() : 0);
        impl.put("getX", a -> mc.player != null ? mc.player.getX() : 0);
        impl.put("getY", a -> mc.player != null ? mc.player.getY() : 0);
        impl.put("getZ", a -> mc.player != null ? mc.player.getZ() : 0);
        impl.put("getYaw", a -> mc.player != null ? mc.player.yRot : 0);
        impl.put("getPitch", a -> mc.player != null ? mc.player.xRot : 0);
        impl.put("getExp", a -> mc.player != null ? mc.player.totalExperience : 0);
        impl.put("getLevel", a -> mc.player != null ? mc.player.experienceLevel : 0);
        impl.put("getHunger", a -> mc.player != null ? mc.player.getFoodData().getFoodLevel() : 0);

        for (java.util.Map.Entry<String, com.opendreamcore.script.NamespaceRegistry.Handler> e : impl.entrySet()) {
            NamespaceRegistry.registerOrReplace("Player", e.getKey(), e.getValue());
        }
        // 中文别名 → 同实现
        String[][] aliases = {
                {"获取名字", "getName"}, {"获取血量", "getHealth"}, {"获取最大血量", "getMaxHealth"},
                {"获取X", "getX"}, {"获取Y", "getY"}, {"获取Z", "getZ"},
                {"获取视角", "getYaw"}, {"获取俯仰", "getPitch"},
                {"获取经验", "getExp"}, {"获取等级", "getLevel"},
                {"获取饥饿", "getHunger"},
        };
        for (String[] pair : aliases) {
            NamespaceRegistry.registerOrReplace("Player", pair[0], impl.get(pair[1]));
        }
        // Display 基础两项
        // Screen/Var：1.16.5 最小 Shim 不依赖 PageVariables（client 现代层），改为内存 Map 占位
        java.util.Map<String, Object> _varStore = new java.util.concurrent.ConcurrentHashMap<>();
        NamespaceRegistry.registerOrReplace("Screen", "设置变量",
                a -> { _varStore.put(str(a, 0), a.length > 1 ? a[1] : null); return null; });
        NamespaceRegistry.registerOrReplace("Screen", "获取变量",
                a -> _varStore.get(str(a, 0)));
        NamespaceRegistry.registerOrReplace("Var", "设置变量",
                a -> { _varStore.put(str(a, 0), a.length > 1 ? a[1] : null); return null; });
        NamespaceRegistry.registerOrReplace("Var", "获取变量",
                a -> _varStore.get(str(a, 0)));
                NamespaceRegistry.registerOrReplace("Display", "getWidth",
                a -> mc.getWindow().getGuiScaledWidth());
        NamespaceRegistry.registerOrReplace("Display", "getHeight",
                a -> mc.getWindow().getGuiScaledHeight());
        // 1.16.5 官方映射无 getDebugFPS 入口；FPS 显示降级为 0（后续接 RenderSystem 计时）
        NamespaceRegistry.registerOrReplace("Display", "获取FPS", a -> 0);

        // Chat/Title：消息与标题显示
        NamespaceRegistry.registerOrReplace("Chat", "发送消息", a ->
        {
            if (mc.player != null && a != null && a.length > 0 && a[0] != null) {
                mc.player.sendMessage(
                        new net.minecraft.util.text.StringTextComponent(String.valueOf(a[0])),
                        mc.player.getUUID());
            }
            return null;
        });
        NamespaceRegistry.registerOrReplace("Title", "showTitle", a ->
        {
            if (mc.player == null) {
                return null;
            }
            net.minecraft.client.gui.IngameGui hud = mc.gui;
            String title = a != null && a.length > 0 && a[0] != null ? String.valueOf(a[0]) : "";
                        hud.setTitles(new net.minecraft.util.text.StringTextComponent(title),
                    null, 10, 70, 10);
            return null;
        });

        // Sound：本地播放（id 如 minecraft:block.note_block.pling）
        NamespaceRegistry.registerOrReplace("Sound", "play", a ->
        {
            if (mc.player == null || a == null || a.length < 1 || a[0] == null) {
                return null;
            }
            float vol = a.length > 1 && a[1] instanceof Number
                    ? ((Number) a[1]).floatValue() : 1.0F;
            ResourceLocation rl = new ResourceLocation(String.valueOf(a[0]));
            // 本地直接出声：SimpleSound 默认 MASTER 分类，任意 id 均可播
            if (mc.player != null) {
                mc.player.playSound(
                        new net.minecraft.util.SoundEvent(rl).setRegistryName(rl), vol, 1.0F);
            } else {
                mc.getSoundManager().play(
                        new net.minecraft.client.audio.SimpleSound(
                                rl, net.minecraft.util.SoundCategory.MASTER, vol, 1.0F,
                                false, 0, net.minecraft.client.audio.ISound.AttenuationType.NONE,
                                0.0, 0.0, 0.0, true));
            }
            return null;
        });
        // Music：音乐类别走 SoundHandler，forMusic 工厂自带 MUSIC 分类
        NamespaceRegistry.registerOrReplace("Music", "play", a ->
        {
            if (a == null || a.length < 1 || a[0] == null) {
                return null;
            }
            float vol = a.length > 1 && a[1] instanceof Number
                    ? ((Number) a[1]).floatValue() : 1.0F;
            ResourceLocation rl = new ResourceLocation(String.valueOf(a[0]));
            net.minecraft.util.SoundEvent ev =
                    new net.minecraft.util.SoundEvent(rl).setRegistryName(rl);
            mc.getSoundManager().play(net.minecraft.client.audio.SimpleSound.forMusic(ev));
            return null;
        });
        NamespaceRegistry.registerOrReplace("Music", "stop", a ->
        {
            mc.getMusicManager().stopPlaying();
            return null;
        });
    }

    private static String str(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }
}
