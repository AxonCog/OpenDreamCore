package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 窗口标题下发（S→C）：服务端接管客户端窗口标题（DreamCore ClientTitleManager 语义平移）。
 * SET_CONFIG 下发完整标题配置（打字机/轮播/随机，字段与 branding.TitleConfig 一致）；
 * SET_STATIC 单文本直设（运行时轻量通道）；RESET 解除覆盖、还原本地 branding。
 * 覆盖期间客户端本地 title.txt/title.json 序列静默；断线由客户端自动 RESET。
 */
public final class WindowTitlePush implements Message {

    public enum Op {
        SET_CONFIG(0), SET_STATIC(1), RESET(2);

        final int id;

        Op(int id) {
            this.id = id;
        }

        static Op byId(int id) {
            for (Op op : values()) {
                if (op.id == id) {
                    return op;
                }
            }
            throw new IllegalArgumentException("未知标题指令: " + id);
        }
    }

    /** 边界：条目数量与单条长度上限（超限拒绝，防恶意/异常包）。 */
    public static final int MAX_TITLES = 64;
    public static final int MAX_TEXT_LEN = 512;

    private final Op op;
    private final String text;
    private final List<String> titles;
    private final boolean typewriter;
    private final boolean random;
    private final int speed;
    private final int interval;
    private final int holdMs;
    private final boolean loop;

    /**
     * @param text       SET_STATIC 的单文本 / SET_CONFIG 的兜底文本（其余 op 忽略）
     * @param titles     SET_CONFIG 轮播序列（可空）
     * @param typewriter 打字机逐字显现
     * @param random     随机轮换（多句时按时间格哈希随机选句）
     * @param speed      每字符毫秒
     * @param interval   轮播间隔/每句展示时长基准毫秒
     * @param holdMs     打字完成后停留毫秒（-1 = 取 interval）
     * @param loop       播完是否循环
     */
    public WindowTitlePush(Op op, String text, List<String> titles,
                           boolean typewriter, boolean random, int speed, int interval,
                           int holdMs, boolean loop) {
        if (op == null) {
            throw new IllegalArgumentException("标题指令不能为空");
        }
        if (op == Op.SET_STATIC && (text == null || text.isEmpty())) {
            throw new IllegalArgumentException("SET_STATIC 需要非空文本");
        }
        this.op = op;
        this.text = text == null ? "" : text;
        this.titles = titles == null ? new ArrayList<>() : new ArrayList<>(titles);
        this.typewriter = typewriter;
        this.random = random;
        this.speed = speed;
        this.interval = interval;
        this.holdMs = holdMs;
        this.loop = loop;
    }

    /** SET_CONFIG 工厂：完整配置。 */
    public static WindowTitlePush config(String text, List<String> titles,
                                         boolean typewriter, boolean random, int speed, int interval,
                                         int holdMs, boolean loop) {
        return new WindowTitlePush(Op.SET_CONFIG, text, titles, typewriter, random, speed, interval, holdMs, loop);
    }

    /** SET_STATIC 工厂：单文本直设。 */
    public static WindowTitlePush statik(String text) {
        return new WindowTitlePush(Op.SET_STATIC, text, null, false, false, 0, 0, -1, false);
    }

    /** RESET 工厂：解除覆盖。 */
    public static WindowTitlePush reset() {
        return new WindowTitlePush(Op.RESET, "", null, false, false, 0, 0, -1, false);
    }

    public Op op() {
        return op;
    }

    public String text() {
        return text;
    }

    public List<String> titles() {
        return titles;
    }

    public boolean typewriter() {
        return typewriter;
    }

    public boolean random() {
        return random;
    }

    public int speed() {
        return speed;
    }

    public int interval() {
        return interval;
    }

    public int holdMs() {
        return holdMs;
    }

    public boolean loop() {
        return loop;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeByte(op.id);
        if (op == Op.RESET) {
            return;
        }
        buf.writeString(text);
        if (op == Op.SET_STATIC) {
            return;
        }
        // SET_CONFIG 剩余字段
        buf.writeVarInt(titles.size());
        for (String t : titles) {
            buf.writeString(t);
        }
        buf.writeByte(typewriter ? 1 : 0);
        buf.writeByte(random ? 1 : 0);
        buf.writeVarInt(Math.max(0, speed));
        buf.writeVarInt(Math.max(0, interval));
        buf.writeVarInt(Math.max(-1, holdMs));
        buf.writeByte(loop ? 1 : 0);
    }

    public static WindowTitlePush decode(OdcByteBuf buf) {
        Op op = Op.byId(buf.readByte());
        if (op == Op.RESET) {
            return reset();
        }
        String text = bounded(buf.readString());
        if (op == Op.SET_STATIC) {
            return statik(text);
        }
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TITLES) {
            throw new IllegalStateException("标题轮播数量非法: " + count);
        }
        List<String> titles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            titles.add(bounded(buf.readString()));
        }
        boolean typewriter = buf.readByte() != 0;
        boolean random = buf.readByte() != 0;
        int speed = buf.readVarInt();
        int interval = buf.readVarInt();
        int holdMs = buf.readVarInt();
        boolean loop = buf.readByte() != 0;
        return new WindowTitlePush(Op.SET_CONFIG, text, titles, typewriter, random, speed, interval, holdMs, loop);
    }

    private static String bounded(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_TEXT_LEN ? s.substring(0, MAX_TEXT_LEN) : s;
    }
}
