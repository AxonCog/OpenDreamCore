package com.opendreamcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音效循环存储：Sound.循环播放 创建的循环音效按名字登记，Sound.停止(名字) 精确停止。
 */
public final class SoundStore {

    private static final SoundStore INSTANCE = new SoundStore();

    public static SoundStore get() {
        return INSTANCE;
    }

    private final Map<String, SoundInstance> loops = new ConcurrentHashMap<>();

    private SoundStore() {
    }

    /** 循环音效实例（AbstractSoundInstance 子类，looping 置位）。 */
    private static final class LoopingSound extends AbstractSoundInstance {
        LoopingSound(SoundEvent event, float volume, float pitch) {
            super(event, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = true;
        }
    }

    /** 播放循环音效（登记名字）。 */
    public void playLoop(String name, SoundEvent event, float volume, float pitch) {
        stopLoop(name);
        if (event == null) {
            return;
        }
        var instance = new LoopingSound(event, volume, pitch);
        Minecraft.getInstance().getSoundManager().play(instance);
        loops.put(name, instance);
    }

    /** 停止指定循环音效（未登记的名字忽略）。 */
    public void stopLoop(String name) {
        SoundInstance instance = loops.remove(name);
        if (instance != null) {
            Minecraft.getInstance().getSoundManager().stop(instance);
        }
    }

    /** 停止全部循环音效。 */
    public void stopAllLoops() {
        for (String name : loops.keySet()) {
            stopLoop(name);
        }
        loops.clear();
    }
}
