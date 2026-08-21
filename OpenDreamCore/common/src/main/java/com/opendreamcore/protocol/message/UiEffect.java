package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 屏幕特效指令（S→C）：服务端远程触发客户端界面效果。
 * SHAKE 屏幕震动（强度, 时长ms）；FLASH 闪屏（颜色, 时长ms）；TRANSITION 过渡（颜色, 时长ms，淡入淡出）。
 */
public final class UiEffect implements Message {

    public enum Kind {
        SHAKE(0), FLASH(1), TRANSITION(2);

        final int id;

        Kind(int id) {
            this.id = id;
        }

        static Kind byId(int id) {
            for (Kind kind : values()) {
                if (kind.id == id) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("未知特效类型: " + id);
        }
    }

    private final Kind kind;
    private final double arg1;
    private final double arg2;
    private final String color;

    public UiEffect(Kind kind, double arg1, double arg2, String color) {
        if (kind == null) {
            throw new IllegalArgumentException("特效类型不能为空");
        }
        this.kind = kind;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.color = color == null ? "" : color;
    }

    public Kind kind() {
        return kind;
    }

    /** 参数 1：SHAKE 强度 / FLASH·TRANSITION 时长(ms)。 */
    public double arg1() {
        return arg1;
    }

    /** 参数 2：SHAKE 时长(ms)；其余 0。 */
    public double arg2() {
        return arg2;
    }

    /** 颜色（"#RRGGBB"，空 = 默认：闪屏白/过渡黑）。 */
    public String color() {
        return color;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeByte(kind.id);
        buf.writeString(String.valueOf(arg1));
        buf.writeString(String.valueOf(arg2));
        buf.writeString(color);
    }

    public static UiEffect decode(OdcByteBuf buf) {
        Kind kind = Kind.byId(buf.readByte());
        double arg1 = Double.parseDouble(buf.readString());
        double arg2 = Double.parseDouble(buf.readString());
        String color = buf.readString();
        return new UiEffect(kind, arg1, arg2, color);
    }
}
