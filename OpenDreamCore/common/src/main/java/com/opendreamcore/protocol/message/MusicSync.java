package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 背景音乐同步（S→C）：服务端控制客户端音乐播放器（OpenDreamCore/music 或云端下发目录）。
 * PLAY 播放文件（WAV 原生 / MP3 需 mp3spi）；STOP 停止；VOLUME 调音量。
 */
public final class MusicSync implements Message {

    public enum Action {
        PLAY(0), STOP(1), VOLUME(2);

        final int id;

        Action(int id) {
            this.id = id;
        }

        static Action byId(int id) {
            for (Action action : values()) {
                if (action.id == id) {
                    return action;
                }
            }
            throw new IllegalArgumentException("未知音乐动作: " + id);
        }
    }

    private final Action action;
    private final String file;
    private final double volume;
    private final boolean loop;

    public MusicSync(Action action, String file, double volume, boolean loop) {
        this.action = action;
        this.file = file == null ? "" : file;
        this.volume = volume;
        this.loop = loop;
    }

    public Action action() {
        return action;
    }

    public String file() {
        return file;
    }

    /** 音量 0-1（PLAY/VOLUME 用）。 */
    public double volume() {
        return volume;
    }

    public boolean loop() {
        return loop;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeByte(action.id);
        buf.writeString(file);
        buf.writeString(String.valueOf(volume));
        buf.writeByte(loop ? 1 : 0);
    }

    public static MusicSync decode(OdcByteBuf buf) {
        Action action = Action.byId(buf.readByte());
        String file = buf.readString();
        double volume = Double.parseDouble(buf.readString());
        boolean loop = buf.readByte() != 0;
        return new MusicSync(action, file, volume, loop);
    }
}
