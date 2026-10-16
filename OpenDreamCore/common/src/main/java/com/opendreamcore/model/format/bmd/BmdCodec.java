package com.opendreamcore.model.format.bmd;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.IModelFormatCodec;
import com.opendreamcore.model.format.SkinToRig;
import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.ir.ModelIR;

import java.io.ByteArrayInputStream;

/**
 * BMD codec：二进制蒙皮模型 → 统一骨架 IR。
 * 静止位姿给枢轴 + 绑定旋转；三角形按首顶点主骨骼归骨。
 */
public final class BmdCodec implements IModelFormatCodec {

    /** 帧动画名（由调用方用文件名去扩展名填入；独立动画文件的关键）。 */
    private String animName = "bmd";

    public void setAnimName(String name) {
        this.animName = name == null || name.trim().isEmpty() ? "bmd" : name;
    }

    @Override
    public String id() {
        return "bmd";
    }

    @Override
    public String[] extensions() {
        return new String[]{".bmd"};
    }

    @Override
    public boolean sniff(String text) {
        return false; // 纯二进制，文本嗅探不参与
    }

    @Override
    public DecodeResult decode(String text) {
        throw new IllegalArgumentException("BMD 是二进制格式，文本入口不适用");
    }

    /** 首字节是版本号（1/2，2=XZ 载荷）；再要求 v1 头第二字节（节点数高字节）为 0 的宽约束。 */
    @Override
    public boolean sniffBinary(byte[] data) {
        return data != null && data.length > 6
                && (data[0] == 1 || data[0] == 2)
                && data[1] == 0;
    }

    @Override
    public DecodeResult decode(byte[] data) {
        BmdParser.BmdDocument doc;
        try {
            doc = BmdParser.parse(new ByteArrayInputStream(data));
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("BMD 解析失败: " + e.getMessage(), e);
        }
        int n = doc.bones.length;
        String[] names = new String[n];
        int[] parents = new int[n];
        double[][] pivots = new double[n][];
        double[][] restRot = new double[n][];
        for (int i = 0; i < n; i++) {
            names[i] = doc.bones[i].name;
            parents[i] = doc.bones[i].parent;
            double[] rest = doc.restPose != null && i < doc.restPose.length ? doc.restPose[i] : null;
            pivots[i] = new double[3];
            if (rest != null && rest.length >= 3) {
                pivots[i][0] = rest[0];
                pivots[i][1] = rest[1];
                pivots[i][2] = rest[2];
            }
            restRot[i] = rest != null && rest.length >= 6
                    ? new double[]{rest[3], rest[4], rest[5]} : null;
        }
        ModelIR model = SkinToRig.toModelIR(names, parents, pivots, restRot, doc.meshes);
        model.meta.sourceFormat = id();
        DecodeResult result = new DecodeResult(model);
        buildFrameAnim(doc, result);
        return result;
    }

    /**
     * 多骨架组 = 帧动画（20fps，循环）：每组一帧，位姿+欧拉角全量入库。
     * 单组的模型文件不产动画（那组就是静止位姿本身）。
     */
    private void buildFrameAnim(BmdParser.BmdDocument doc, DecodeResult result) {
        int frames = doc.frameGroups.size();
        if (frames <= 1) {
            return;
        }
        AnimationIR anim = new AnimationIR(animName, (frames - 1) / 20.0, AnimationIR.LOOP_LOOP);
        for (int g = 1; g < frames; g++) {
            double t = g / 20.0;
            double[][] frame = doc.frameGroups.get(g);
            for (int i = 0; i < doc.bones.length && i < frame.length; i++) {
                double[] v = frame[i];
                if (v == null) {
                    continue;
                }
                AnimationIR.BoneAnim ba = anim.ensureBone(doc.bones[i].name);
                if (ba.pos == null) {
                    ba.pos = new AnimationIR.Track();
                }
                if (ba.rot == null) {
                    ba.rot = new AnimationIR.Track();
                }
                ba.pos.keys.add(new AnimationIR.Keyframe(t, new double[]{v[0], v[1], v[2]}));
                ba.rot.keys.add(new AnimationIR.Keyframe(t, new double[]{v[3], v[4], v[5]}));
            }
        }
        result.animations.add(anim);
    }
}
