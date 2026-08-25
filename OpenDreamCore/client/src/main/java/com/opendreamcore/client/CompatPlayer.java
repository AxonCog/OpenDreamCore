package com.opendreamcore.client;

/**
 * 播放 UI 提示音的版本自适应垫片。
 *
 * <p>mojmap 漂移：1.21.8 及以前是 {@code LocalPlayer.playNotifySound(SoundEvent, SoundSource, float, float)}；
 * 1.21.9+ 移除，改为 {@code playSound(SoundEvent, float, float)}。经反射择路。</p>
 */
public final class CompatPlayer {

    private CompatPlayer() {}

    /**
     * 播放提示音。source 参数仅旧版 API 使用；新版统一走 MASTER 级 playSound。
     */
    public static void playNotifySound(net.minecraft.client.player.LocalPlayer player,
                                       net.minecraft.sounds.SoundEvent event,
                                       Object source, float volume, float pitch) {
        try {
            var m = player.getClass().getMethod("playNotifySound",
                    net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class,
                    float.class, float.class);
            m.invoke(player, event, net.minecraft.sounds.SoundSource.MASTER, volume, pitch);
        } catch (NoSuchMethodException modern) {
            player.playSound(event, volume, pitch);
        } catch (Exception ignored) {
            // 声音失败不影响交互
        }
    }
}
