package com.opendreamcore.model.format.geo;

import com.opendreamcore.model.format.IModelFormatCodec;
import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.CodecUtil;
import com.opendreamcore.model.format.JsonParser;

import com.opendreamcore.model.ir.ModelIR;

import java.util.List;
import java.util.Map;

/**
 * Bedrock geometry（geo.json）解码：支持 1.12.0（minecraft:geometry 数组）与 1.8.0（geometry 单对象）。
 *
 * 字段语义（bedrock 官方）：bones[].pivot 旋转中心、rotation 角度制、
 * cubes[].origin/size/uv（box uv）或 uv{nORTH/east/south/west/up/down}（per-face）、inflate/grow。
 */
public final class GeoJsonCodec implements IModelFormatCodec {

    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    @Override
    public String id() {
        return "geo.json";
    }

    @Override
    public String[] extensions() {
        return new String[]{".geo.json", ".geometry.json"};
    }

    @Override
    public boolean sniff(String text) {
        return text.contains("minecraft:geometry") || text.contains("\"geometry\"");
    }

    @Override
    public DecodeResult decode(String text) {
        Object root = JsonParser.parse(text);
        Map<String, Object> rootMap = CodecUtil.map(root);
        if (rootMap == null) {
            throw new IllegalArgumentException("geo.json 根必须是对象");
        }
        Object geos = rootMap.get("minecraft:geometry");
        if (geos == null) {
            geos = rootMap.get("geometry");
        }
        DecodeResult result = decodeGeo(geos);
        result.model.meta.sourceFormat = id();
        return result;
    }

    private DecodeResult decodeGeo(Object geos) {
        Map<String, Object> geo = firstGeo(geos);
        if (geo == null) {
            throw new IllegalArgumentException("缺少 minecraft:geometry/geometry 定义");
        }
        Map<String, Object> desc = CodecUtil.map(geo.get("description"));
        ModelIR.BoneIR rootBone = new ModelIR.BoneIR("root");
        ModelIR model = new ModelIR(rootBone);
        if (desc != null) {
            model.meta.textureWidth = CodecUtil.num(desc.get("texture_width"));
            model.meta.textureHeight = CodecUtil.num(desc.get("texture_height"));
        }
        List<Object> bones = CodecUtil.list(geo.get("bones"));
        if (bones == null) {
            return new DecodeResult(model);
        }
        java.util.Map<String, ModelIR.BoneIR> byName = new java.util.LinkedHashMap<String, ModelIR.BoneIR>();
        java.util.Map<String, String> parentOf = new java.util.LinkedHashMap<String, String>();
        buildBones(bones, byName, parentOf);
        attachTree(model, byName, parentOf);
        attachCubes(bones, byName, model);
        return new DecodeResult(model);
    }

    /** 第一遍：建全部骨骼（父引用可能在子之前）。 */
    private void buildBones(List<Object> bones, java.util.Map<String, ModelIR.BoneIR> byName,
                            java.util.Map<String, String> parentOf) {
        for (Object b : bones) {
            Map<String, Object> bm = CodecUtil.map(b);
            if (bm == null) {
                continue;
            }
            String name = CodecUtil.str(bm.get("name"));
            if (name == null) {
                continue;
            }
            ModelIR.BoneIR bone = new ModelIR.BoneIR(name);
            double[] pivot = CodecUtil.vec3(bm.get("pivot"));
            bone.pivot[0] = pivot[0];
            bone.pivot[1] = pivot[1];
            bone.pivot[2] = pivot[2];
            bone.bindRotation = CodecUtil.vec3(bm.get("rotation"));
            byName.put(name, bone);
            String parent = CodecUtil.str(bm.get("parent"));
            if (parent != null) {
                parentOf.put(name, parent);
            }
        }
    }

    /** 第二遍：挂树。有 parent 的挂父；父缺失挂 root 并告警；无 parent 的根级挂 root。 */
    private void attachTree(ModelIR model, java.util.Map<String, ModelIR.BoneIR> byName,
                            java.util.Map<String, String> parentOf) {
        for (Map.Entry<String, String> e : parentOf.entrySet()) {
            ModelIR.BoneIR child = byName.get(e.getKey());
            if (child == null) {
                continue;
            }
            ModelIR.BoneIR parent = byName.get(e.getValue());
            if (parent == null) {
                model.meta.warn("骨骼 " + e.getKey() + " 的父骨骼 " + e.getValue() + " 不存在，已挂到根");
                model.root.children.add(child);
            } else {
                parent.children.add(child);
            }
        }
        for (Map.Entry<String, ModelIR.BoneIR> e : byName.entrySet()) {
            if (!parentOf.containsKey(e.getKey())) {
                model.root.children.add(e.getValue());
            }
        }
    }

    /** 第三遍：cubes → BoxMesh 挂骨骼。 */
    private void attachCubes(List<Object> bones, java.util.Map<String, ModelIR.BoneIR> byName, ModelIR model) {
        for (Object b : bones) {
            Map<String, Object> bm = CodecUtil.map(b);
            if (bm == null) {
                continue;
            }
            String name = CodecUtil.str(bm.get("name"));
            ModelIR.BoneIR bone = name == null ? null : byName.get(name);
            if (bone == null) {
                continue;
            }
            List<Object> cubes = CodecUtil.list(bm.get("cubes"));
            if (cubes == null) {
                continue;
            }
            for (Object c : cubes) {
                ModelIR.BoxMesh mesh = cube(CodecUtil.map(c), model);
                if (mesh != null) {
                    bone.meshes.add(mesh);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstGeo(Object geos) {
        if (geos instanceof Map) {
            return (Map<String, Object>) geos;
        }
        List<Object> list = CodecUtil.list(geos);
        if (list != null && !list.isEmpty()) {
            return CodecUtil.map(list.get(0));
        }
        return null;
    }

    private ModelIR.BoxMesh cube(Map<String, Object> cm, ModelIR model) {
        if (cm == null) {
            return null;
        }
        ModelIR.BoxMesh mesh = new ModelIR.BoxMesh();
        double[] origin = CodecUtil.vec3(cm.get("origin"));
        double[] size = CodecUtil.vec3(cm.get("size"));
        for (int i = 0; i < 3; i++) {
            mesh.from[i] = origin[i];
            mesh.to[i] = origin[i] + size[i];
        }
        mesh.inflate = CodecUtil.num(cm.get("inflate")) + CodecUtil.num(cm.get("grow"));
        Object uv = cm.get("uv");
        if (uv instanceof List) {
            // box uv：[u1,v1,u2,v2]
            mesh.boxUv = true;
            double[] b = CodecUtil.vec2(uv);
            mesh.uv[0][0] = b[0];
            mesh.uv[0][1] = b[1];
            mesh.uv[0][2] = b[0] + size[0];
            mesh.uv[0][3] = b[1] + size[1];
            mesh.uv[1] = mesh.uv[0];
            mesh.uv[2] = mesh.uv[0];
            mesh.uv[3] = mesh.uv[0];
            mesh.uv[4] = mesh.uv[0];
            mesh.uv[5] = mesh.uv[0];
        } else if (uv instanceof Map) {
            // 逐面
            mesh.boxUv = false;
            Map<String, Object> faces = (Map<String, Object>) uv;
            for (int i = 0; i < 6; i++) {
                List<Object> f = CodecUtil.list(faces.get(FACE_NAMES[i]));
                if (f != null) {
                    double[] a = CodecUtil.vec2(f);
                    mesh.uv[i][0] = a[0];
                    mesh.uv[i][1] = a[1];
                    if (f.size() >= 4) {
                        mesh.uv[i][2] = CodecUtil.num(f.get(2));
                        mesh.uv[i][3] = CodecUtil.num(f.get(3));
                    } else {
                        mesh.uv[i][2] = a[0] + size[0];
                        mesh.uv[i][3] = a[1] + size[1];
                    }
                }
            }
        }
        // 材质：geo 无多材质概念，建单材质
        if (model.materials.isEmpty()) {
            ModelIR.Material m = new ModelIR.Material();
            model.materials.add(m);
        }
        return mesh;
    }
}
