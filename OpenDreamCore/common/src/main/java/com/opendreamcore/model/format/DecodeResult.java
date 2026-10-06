package com.opendreamcore.model.format;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.ir.ModelIR;

import java.util.ArrayList;
import java.util.List;

/**
 * 格式解码统一产物：模型 IR + 内嵌动画 + 内嵌贴图（base64 解码产物）。
 */
public final class DecodeResult {

    public final ModelIR model;
    /** 动画列表（bbmodel 内嵌 / geo 无 / obj 无）。 */
    public final List<AnimationIR> animations = new ArrayList<AnimationIR>();
    /** 内嵌贴图（bbmodel base64 解码产物；name=原文件名，data=PNG 字节）。 */
    public final List<EmbeddedTexture> embeddedTextures = new ArrayList<EmbeddedTexture>();

    public DecodeResult(ModelIR model) {
        this.model = model;
    }

    public static final class EmbeddedTexture {
        public final String name;
        public final byte[] data;

        public EmbeddedTexture(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}
