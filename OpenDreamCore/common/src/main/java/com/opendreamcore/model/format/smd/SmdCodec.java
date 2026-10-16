package com.opendreamcore.model.format.smd;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.IModelFormatCodec;
import com.opendreamcore.model.format.SkinToRig;
import com.opendreamcore.model.ir.ModelIR;

import java.io.StringReader;

/**
 * SMD codec：Valve SMD 文本格式 → 统一骨架 IR。
 * 骨骼树来自 nodes 段；枢轴取动画第 0 帧（SMD 本身不存 rest pivot）。
 */
public final class SmdCodec implements IModelFormatCodec {

    @Override
    public String id() {
        return "smd";
    }

    @Override
    public String[] extensions() {
        return new String[]{".smd"};
    }

    @Override
    public boolean sniff(String text) {
        if (text == null) {
            return false;
        }
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        return head.trim().startsWith("version") && head.contains("nodes");
    }

    @Override
    public DecodeResult decode(String text) {
        SmdParser.SmdDocument doc;
        try {
            doc = SmdParser.parse(new StringReader(text), "model");
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("SMD 解析失败: " + e.getMessage(), e);
        }
        int n = doc.bones.length;
        String[] names = new String[n];
        int[] parents = new int[n];
        for (int i = 0; i < n; i++) {
            names[i] = doc.bones[i].name;
            parents[i] = doc.bones[i].parent;
        }
        ModelIR model = SkinToRig.toModelIR(names, parents,
                pivotsFromAnim(doc, names), null, doc.meshes);
        model.meta.sourceFormat = id();
        DecodeResult r = new DecodeResult(model);
        if (doc.animation != null) {
            r.animations.add(doc.animation);
        }
        return r;
    }

    /** 动画位置轨道第 0 关键帧 = rest 枢轴（没有动画/轨道就零）。 */
    private static double[][] pivotsFromAnim(SmdParser.SmdDocument doc, String[] names) {
        double[][] out = new double[names.length][];
        for (int i = 0; i < names.length; i++) {
            out[i] = new double[3];
            if (doc.animation == null) {
                continue;
            }
            com.opendreamcore.model.ir.AnimationIR.BoneAnim ba = doc.animation.bones.get(names[i]);
            if (ba == null || ba.pos == null || ba.pos.keys.isEmpty()) {
                continue;
            }
            double[] v = ba.pos.keys.get(0).value;
            if (v != null && v.length >= 3) {
                out[i][0] = v[0];
                out[i][1] = v[1];
                out[i][2] = v[2];
            }
        }
        return out;
    }
}
