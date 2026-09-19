package com.opendreamcore.client.controller;

import com.opendreamcore.client.MusicPlayer;

import java.util.Map;

/**
 * 背景音乐：页面 music 选项与服务端 MusicSync 指令的播放动作都走这里。
 * 页面开/关由控制器调 applyMusic/stopPageMusic，服务端指令进 handleMusicSync。
 */
public final class MusicService {

    private volatile boolean musicConfigured;

    public MusicService() {
    }

    /**
     * 页面音乐配置（music: 文件 或 {file, volume, loop}），打开页面时调用。
     */
    public void applyMusic(Map<String, Object> options) {
        musicConfigured = false;
        if (options == null) {
            return;
        }
        Object music = options.get("music");
        if (music == null) {
            return;
        }
        String file;
        double vol = 0.8;
        boolean loop = true;
        if (music instanceof Map<?, ?> m) {
            Object rawFile = m.get("file");
            file = rawFile == null ? null : String.valueOf(rawFile);
            vol = m.get("volume") instanceof Number n ? n.doubleValue() : 0.8;
            loop = !(m.get("loop") instanceof Boolean b && !b);
        } else {
            file = String.valueOf(music);
        }
        if (file != null && !file.isBlank()) {
            musicConfigured = true;
            MusicPlayer.get().play(file, vol, loop);
        }
    }

    /** 页面音乐随页面关闭停止（仅停止本页配置的音乐）。 */
    public void stopPageMusic() {
        if (musicConfigured) {
            MusicPlayer.get().stop();
            musicConfigured = false;
        }
    }

    /** 服务端背景音乐指令（music 通道）。 */
    public void handleMusicSync(com.opendreamcore.protocol.message.MusicSync sync) {
        switch (sync.action()) {
            case PLAY -> MusicPlayer.get().play(sync.file(), sync.volume(), sync.loop());
            case STOP -> MusicPlayer.get().stop();
            case VOLUME -> MusicPlayer.get().volume(sync.volume());
        }
    }
}