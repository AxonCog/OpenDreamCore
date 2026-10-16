package com.opendreamcore.model.format.bbmodel;

import com.opendreamcore.model.format.IModelFormatCodec;
import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.CodecUtil;
import com.opendreamcore.model.format.JsonParser;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.ir.ModelIR;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockbench（bbmodel）解码：elements/outliner → 骨骼树 + BoxMesh；animations.animators → AnimationIR。
 *
 * 实样校准（format_version 4.10）：
 *  - 元素级 box_uv 标志；逐面 UV 在 faces{nORTH/east/south/west/up/down:[x1,y1,x2,y2]}；
 *    box UV 在元素 uv:[x1,y1]（按 MC 盒式布局展开）；
 *  - outliner 节点：dict=骨骼组（name/uuid/origin/rotation/children），字符串=元素 uuid 引用；
 *  - 动画：animators[uuid]={name,type:'bone',keyframes:[{channel,data_points:[{x,y,z}],time,interpolation}]}，
 *    data_points 值为字符串（可能是 molang，存 expr，求值层回退）；
 *  - 贴图内嵌 base64（data:image/png;base64,...）→ 解码进 DecodeResult.embeddedTextures。
 */
public final class BbModelCodec implements IModelFormatCodec {

    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    @Override
    public String id() {
        return "bbmodel";
    }

    @Override
    public String[] extensions() {
        return new String[]{".bbmodel"};
    }

    @Override
    public boolean sniff(String text) {
        return text.contains("\"meta\"") && text.contains("\"outliner\"");
    }

    @Override
    public DecodeResult decode(String text) {
        Object root = JsonParser.parse(text);
        Map<String, Object> m = CodecUtil.map(root);
        if (m == null) {
            throw new IllegalArgumentException("bbmodel 根必须是对象");
        }
        return decodeRoot(m);
    }

    DecodeResult decodeRoot(Map<String, Object> m) {
        ModelIR.BoneIR root = new ModelIR.BoneIR("root");
        ModelIR model = new ModelIR(root);
        model.meta.sourceFormat = id();
        Map<String, Object> meta = CodecUtil.map(m.get("meta"));
        boolean defaultBoxUv = meta != null && Boolean.TRUE.equals(meta.get("box_uv"));
        Map<String, Object> res = CodecUtil.map(m.get("resolution"));
        if (res != null) {
            model.meta.textureWidth = CodecUtil.num(res.get("width"));
            model.meta.textureHeight = CodecUtil.num(res.get("height"));
        }
        // 贴图（含内嵌 base64 → PNG 字节解码）
        DecodeResult result = new DecodeResult(model);
        List<Object> textures = CodecUtil.list(m.get("textures"));
        if (textures != null) {
            for (Object t : textures) {
                readTexture(CodecUtil.map(t), model, result);
            }
        }
        // 元素 uuid → BoxMesh
        Map<String, ModelIR.BoxMesh> meshesByUuid = new LinkedHashMap<String, ModelIR.BoxMesh>();
        List<Object> elements = CodecUtil.list(m.get("elements"));
        if (elements != null) {
            for (Object e : elements) {
                readElement(CodecUtil.map(e), defaultBoxUv, model, meshesByUuid);
            }
        }
        // outliner → 骨骼树（两遍：先建组，再挂元素与子组）
        buildOutliner(CodecUtil.list(m.get("outliner")), model, meshesByUuid);
        // 动画
        List<Object> anims = CodecUtil.list(m.get("animations"));
        if (anims != null) {
            for (Object a : anims) {
                AnimationIR ir = readAnimation(CodecUtil.map(a));
                if (ir != null) {
                    result.animations.add(ir);
                }
            }
        }
        return result;
    }
    // ---------------------------------------------------------------- 贴图

    private void readTexture(Map<String, Object> t, ModelIR model, DecodeResult result) {
        if (t == null) {
            return;
        }
        String name = CodecUtil.str(t.get("name"));
        String source = CodecUtil.str(t.get("source"));
        ModelIR.Material mat = new ModelIR.Material();
        mat.sourceIndex = model.materials.size();
        if (source != null && source.startsWith("data:image")) {
            String label = name == null ? String.valueOf(model.materials.size()) : name;
            mat.texture = "embedded:" + label;
            int comma = source.indexOf("base64,");
            if (comma >= 0) {
                try {
                    byte[] data = java.util.Base64.getDecoder().decode(source.substring(comma + 7));
                    result.embeddedTextures.add(new DecodeResult.EmbeddedTexture(label, data));
                } catch (IllegalArgumentException bad64) {
                    // 贴图字节损坏时保留材质命名，解码产物缺失由上层回退占位
                }
            }
        } else {
            mat.texture = name == null ? null : "texture/" + name;
        }
        model.materials.add(mat);
    }

    // ---------------------------------------------------------------- 元素

    private void readElement(Map<String, Object> e, boolean defaultBoxUv, ModelIR model,
                             Map<String, ModelIR.BoxMesh> meshesByUuid) {
        if (e == null || !"cube".equals(e.get("type"))) {
            if (e != null && !"cube".equals(e.get("type"))) {
                model.meta.warn("元素 " + e.get("name") + " 类型 " + e.get("type") + " 暂不支持，已跳过");
            }
            return;
        }
        ModelIR.BoxMesh mesh = new ModelIR.BoxMesh();
        double[] from = CodecUtil.vec3(e.get("from"));
        double[] to = CodecUtil.vec3(e.get("to"));
        System.arraycopy(from, 0, mesh.from, 0, 3);
        System.arraycopy(to, 0, mesh.to, 0, 3);
        boolean boxUv = e.containsKey("box_uv") ? Boolean.TRUE.equals(e.get("box_uv")) : defaultBoxUv;
        double[] w = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        Map<String, Object> faces = CodecUtil.map(e.get("faces"));
        boolean hasMaterial = false;
        mesh.faceMaterials = new int[6];
        for (int i = 0; i < mesh.faceMaterials.length; i++) {
            mesh.faceMaterials[i] = -1;
        }
        if (boxUv) {
            mesh.boxUv = true;
            double[] uv = CodecUtil.vec2(e.get("uv"));
            fillBoxUv(mesh, uv, w);
        } else {
            mesh.boxUv = false;
        }
        int materialCount = model.materials.isEmpty() ? 1 : model.materials.size();
        for (int i = 0; i < 6; i++) {
            // bbmodel 实样：每面是 {"uv":[x1,y1,x2,y2],"texture":0} 对象；也容忍直接数组
            Object raw = faces == null ? null : faces.get(FACES[i]);
            Map<String, Object> fm = CodecUtil.map(raw);
            int material = -1;
            if (raw != null) {
                material = fm == null ? 0 : materialIndex(fm.get("texture"), materialCount);
                if (material < 0) {
                    material = 0;
                }
            }
            mesh.faceMaterials[i] = material;
            if (!hasMaterial && material >= 0) {
                mesh.material = material;
                hasMaterial = true;
            }
            if (!boxUv) {
                List<Object> f = CodecUtil.list(raw);
                if (f == null) {
                    f = fm == null ? null : CodecUtil.list(fm.get("uv"));
                }
                if (f != null && f.size() >= 4) {
                    mesh.uv[i][0] = CodecUtil.num(f.get(0));
                    mesh.uv[i][1] = CodecUtil.num(f.get(1));
                    mesh.uv[i][2] = CodecUtil.num(f.get(2));
                    mesh.uv[i][3] = CodecUtil.num(f.get(3));
                }
            }
        }
        String uuid = CodecUtil.str(e.get("uuid"));
        if (uuid != null) {
            meshesByUuid.put(uuid, mesh);
        }
        if (model.materials.isEmpty()) {
            ModelIR.Material mat = new ModelIR.Material();
            model.materials.add(mat);
        }
    }

    /** 返回有效纹理数组下标；缺失、非整数和越界索引均保留材质 0 兜底。 */
    private int materialIndex(Object value, int materialCount) {
        double number;
        if (value instanceof Number) {
            number = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                number = Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException invalid) {
                return -1;
            }
        } else {
            return -1;
        }
        int index = (int) number;
        return number == index && index >= 0 && index < materialCount ? index : -1;
    }

    /** MC 盒式 UV 布局：入参 [u1,v1]，盒尺寸 w=(x,y,z)。 */
    private void fillBoxUv(ModelIR.BoxMesh mesh, double[] uv, double[] w) {
        double u = uv[0];
        double v = uv[1];
        double d = w[2];
        double bw = w[0];
        double bh = w[1];
        setFace(mesh, 4, u + d, v, bw, d);            // up
        setFace(mesh, 5, u + d + bw, v, bw, d);       // down
        setFace(mesh, 1, u, v + d, d, bh);            // east
        setFace(mesh, 0, u + d, v + d, bw, bh);       // north
        setFace(mesh, 3, u + d + bw, v + d, d, bh);   // west
        setFace(mesh, 2, u + d + bw + d, v + d, bw, bh); // south
    }

    private void setFace(ModelIR.BoxMesh mesh, int i, double u, double v, double sw, double sh) {
        mesh.uv[i][0] = u;
        mesh.uv[i][1] = v;
        mesh.uv[i][2] = u + sw;
        mesh.uv[i][3] = v + sh;
    }

    // ---------------------------------------------------------------- outliner → 骨骼

    private void buildOutliner(List<Object> outliner, ModelIR model, Map<String, ModelIR.BoxMesh> meshesByUuid) {
        if (outliner == null) {
            return;
        }
        for (Object o : outliner) {
            Map<String, Object> node = CodecUtil.map(o);
            if (node == null) {
                // 叶子 = 元素 uuid 引用 → 挂根
                String uuid = o instanceof String ? (String) o : null;
                attachMesh(model.root, uuid, meshesByUuid);
                continue;
            }
            ModelIR.BoneIR bone = boneOf(node);
            model.root.children.add(bone);
            collectChildren(node, bone, meshesByUuid);
        }
    }

    private ModelIR.BoneIR boneOf(Map<String, Object> node) {
        ModelIR.BoneIR bone = new ModelIR.BoneIR(CodecUtil.str(node.get("name")));
        double[] origin = CodecUtil.vec3(node.get("origin"));
        bone.pivot[0] = origin[0];
        bone.pivot[1] = origin[1];
        bone.pivot[2] = origin[2];
        bone.bindRotation = CodecUtil.vec3(node.get("rotation"));
        return bone;
    }

    private void collectChildren(Map<String, Object> node, ModelIR.BoneIR bone,
                                 Map<String, ModelIR.BoxMesh> meshesByUuid) {
        List<Object> children = CodecUtil.list(node.get("children"));
        if (children == null) {
            return;
        }
        for (Object c : children) {
            Map<String, Object> cm = CodecUtil.map(c);
            if (cm == null) {
                String uuid = c instanceof String ? (String) c : null;
                attachMesh(bone, uuid, meshesByUuid);
            } else {
                ModelIR.BoneIR child = boneOf(cm);
                bone.children.add(child);
                collectChildren(cm, child, meshesByUuid);
            }
        }
    }

    private void attachMesh(ModelIR.BoneIR bone, String uuid, Map<String, ModelIR.BoxMesh> meshesByUuid) {
        if (uuid == null) {
            return;
        }
        ModelIR.BoxMesh mesh = meshesByUuid.get(uuid);
        if (mesh != null) {
            bone.meshes.add(mesh);
        }
    }

    // ---------------------------------------------------------------- 动画

    private AnimationIR readAnimation(Map<String, Object> a) {
        if (a == null) {
            return null;
        }
        String name = CodecUtil.str(a.get("name"));
        double length = CodecUtil.num(a.get("length"));
        // loop 字段实样为字符串 "loop"/"once"（也容忍布尔）
        Object loopRaw = a.get("loop");
        String loopStr = loopRaw instanceof Boolean
                ? (((Boolean) loopRaw) ? "loop" : "once") : CodecUtil.str(loopRaw);
        AnimationIR ir = new AnimationIR(name == null ? "anim" : name, length, loopStr);
        ir.reverse = Boolean.TRUE.equals(a.get("reverse"));
        Map<String, Object> animators = CodecUtil.map(a.get("animators"));
        if (animators == null) {
            return ir;
        }
        for (Object v : animators.values()) {
            Map<String, Object> animator = CodecUtil.map(v);
            if (animator == null || !"bone".equals(animator.get("type"))) {
                continue;
            }
            String boneName = CodecUtil.str(animator.get("name"));
            if (boneName == null) {
                continue;
            }
            AnimationIR.BoneAnim ba = ir.ensureBone(boneName);
            List<Object> keyframes = CodecUtil.list(animator.get("keyframes"));
            if (keyframes == null) {
                continue;
            }
            for (Object kf : keyframes) {
                readKeyframe(CodecUtil.map(kf), ba);
            }
        }
        sortTracks(ir);
        return ir;
    }

    private void readKeyframe(Map<String, Object> kf, AnimationIR.BoneAnim ba) {
        if (kf == null) {
            return;
        }
        String channel = CodecUtil.str(kf.get("channel"));
        double time = CodecUtil.num(kf.get("time"));
        List<Object> dps = CodecUtil.list(kf.get("data_points"));
        if (dps == null || dps.isEmpty()) {
            return;
        }
        Map<String, Object> dp = CodecUtil.map(dps.get(0));
        if (dp == null) {
            return;
        }
        double[] val = new double[3];
        String[] expr = new String[3];
        readPoint(dp, val, expr);
        AnimationIR.Track track = trackFor(ba, channel);
        if (track == null) {
            return;
        }
        AnimationIR.Keyframe key = new AnimationIR.Keyframe(time, val);
        key.interp = CodecUtil.str(kf.get("interpolation"));
        if (key.interp == null) {
            key.interp = "linear";
        }
        String easing = CodecUtil.str(kf.get("easing"));
        if (easing != null && !easing.isEmpty()) {
            key.easing = easing;
            List<Object> eargs = CodecUtil.list(kf.get("easingArgs"));
            if (eargs != null && !eargs.isEmpty()) {
                double[] arr = new double[eargs.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = CodecUtil.num(eargs.get(i));
                }
                key.easingArgs = arr;
            }
        }
        System.arraycopy(expr, 0, key.expr, 0, 3);
        track.keys.add(key);
    }

    private void readPoint(Map<String, Object> dp, double[] val, String[] expr) {
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            Object raw = dp.get(axes[i]);
            if (raw instanceof String) {
                Double n = CodecUtil.tryNum((String) raw);
                if (n != null) {
                    val[i] = n;
                } else {
                    expr[i] = (String) raw;
                }
            } else {
                val[i] = CodecUtil.num(raw);
            }
        }
    }

    private AnimationIR.Track trackFor(AnimationIR.BoneAnim ba, String channel) {
        if ("position".equals(channel)) {
            if (ba.pos == null) {
                ba.pos = new AnimationIR.Track();
            }
            return ba.pos;
        }
        if ("rotation".equals(channel)) {
            if (ba.rot == null) {
                ba.rot = new AnimationIR.Track();
            }
            return ba.rot;
        }
        if ("scale".equals(channel)) {
            if (ba.scale == null) {
                ba.scale = new AnimationIR.Track();
            }
            return ba.scale;
        }
        return null;
    }

    private void sortTracks(AnimationIR ir) {
        for (AnimationIR.BoneAnim ba : ir.bones.values()) {
            if (ba.pos != null) {
                ba.pos.sort();
            }
            if (ba.rot != null) {
                ba.rot.sort();
            }
            if (ba.scale != null) {
                ba.scale.sort();
            }
        }
    }
}
