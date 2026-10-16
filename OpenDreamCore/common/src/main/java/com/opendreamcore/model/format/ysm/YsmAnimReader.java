package com.opendreamcore.model.format.ysm;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.ir.AnimationIR;

import java.io.IOException;
import java.util.List;

/**
 * 动画段读取：平铺流逐条解（头部/骨骼通道/尾部），供 {@link YsmCodec} 主扫描循环调用。
 */
final class YsmAnimReader {

    private YsmAnimReader() {
    }

    static void readAnimations(byte[] data, int fmt, DecodeResult out) {
        for (int p = 0; p < data.length - 24; p++) {
            int ln = data[p] & 0xff;
            if (ln < 3 || ln > 64 || p + 1 + ln > data.length || !YsmScan.isPrintable(data, p + 1, ln)) {
                continue;
            }
            YsmCodec.Bin bin = new YsmCodec.Bin(data);
            bin.p = p;
            try {
                AnimationIR anim = readAnim(bin, fmt);
                if (anim.bones.isEmpty() || bin.p > data.length) {
                    continue;
                }
                out.animations.add(anim);
                p = bin.p - 1;
            } catch (RuntimeException ignore) {
                // 不是动画开头，继续扫
            }
        }
    }

    private static AnimationIR readAnim(YsmCodec.Bin bin, int fmt) {
        AnimationIR anim = readAnimHeader(bin);
        readAnimBones(bin, anim);
        readAnimTail(bin, fmt);
        return anim;
    }

    /** 头部：名/时长/循环/保留位/混合权重段。 */
    private static AnimationIR readAnimHeader(YsmCodec.Bin bin) {
        String name = bin.str();
        if (name.isEmpty() || !YsmScan.isPrintable(name)) {
            throw new IllegalStateException("动画名不可读");
        }
        float len = bin.f32();
        if (!(len >= 0f && len < 72000f)) {
            throw new IllegalStateException("时长异常 " + len);
        }
        long loop = bin.varint();
        if (loop > 3) {
            throw new IllegalStateException("循环标记异常 " + loop);
        }
        String loopMode = loop == 1 ? AnimationIR.LOOP_LOOP
                : (loop == 2 ? AnimationIR.LOOP_HOLD : AnimationIR.LOOP_ONCE);
        AnimationIR anim = new AnimationIR(name, len / 20.0, loopMode);
        if (bin.varint() != 0 || bin.varint() != 0) {
            throw new IllegalStateException("动画头保留字段非零");
        }
        long bw = bin.varint();
        for (long i = 0; i < bw; i++) {
            int t = bin.u8();
            if (t == 1) {
                bin.f32();
            } else {
                bin.str();
            }
        }
        if (bin.varint() != 0) {
            throw new IllegalStateException("混合权重段后异常");
        }
        return anim;
    }

    /** 骨骼通道循环。 */
    private static void readAnimBones(YsmCodec.Bin bin, AnimationIR anim) {
        long nb = bin.varint();
        if (nb > 128) {
            throw new IllegalStateException("骨骼数异常 " + nb);
        }
        for (long i = 0; i < nb; i++) {
            String bone = bin.str();
            AnimationIR.BoneAnim ba = new AnimationIR.BoneAnim();
            ba.rot = readChannel(bin);
            ba.pos = readChannel(bin);
            ba.scale = readChannel(bin);
            if (!ba.rot.keys.isEmpty() || !ba.pos.keys.isEmpty() || !ba.scale.keys.isEmpty()) {
                anim.bones.put(bone, ba);
            }
        }
    }

    /** 尾部：timeline + fmt>9 的 sound_effects。 */
    private static void readAnimTail(YsmCodec.Bin bin, int fmt) {
        long tl = bin.varint();
        for (long i = 0; i < tl; i++) {
            long k = bin.varint();
            for (long j = 0; j < k; j++) {
                bin.str();
            }
            bin.f32();
        }
        if (fmt > 9) {
            long se = bin.varint();
            for (long i = 0; i < se; i++) {
                bin.str();
                bin.f32();
            }
        }
    }

    /** 通道 = 关键帧列表；每帧时间 + 插值 + 三分量（数值或 molang 串）。 */
    private static AnimationIR.Track readChannel(YsmCodec.Bin bin) {
        AnimationIR.Track track = new AnimationIR.Track();
        long n = bin.varint();
        if (n == 0) {
            return track;
        }
        if (n > 4096) {
            throw new IllegalStateException("关键帧数异常 " + n);
        }
        for (long i = 0; i < n; i++) {
            float t = bin.f32();
            if (!(t >= -1e6f && t <= 1e6f)) {
                throw new IllegalStateException("关键帧时间异常 " + t);
            }
            long lerp = bin.varint();
            if (lerp > 3) {
                throw new IllegalStateException("插值标记异常 " + lerp);
            }
            double[] val = new double[3];
            String molang = null;
            for (int c = 0; c < 3; c++) {
                int flag = bin.u8();
                if (flag == 2) {
                    molang = bin.str();
                    val[c] = 0;
                } else if (flag == 1) {
                    val[c] = bin.f32();
                } else {
                    val[c] = bin.f32();
                }
            }
            bin.u8();
            AnimationIR.Keyframe kf = new AnimationIR.Keyframe(t / 20.0, val);
            if (molang != null) {
                kf.expr[0] = molang;
            }
            if (lerp == 1) {
                kf.interp = "smooth";
            }
            track.keys.add(kf);
        }
        track.sort();
        return track;
    }
}
