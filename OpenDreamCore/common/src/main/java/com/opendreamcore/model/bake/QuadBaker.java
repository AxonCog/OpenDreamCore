package com.opendreamcore.model.bake;

import com.opendreamcore.model.ir.ModelIR;

import java.util.ArrayList;
import java.util.List;

/**
 * IR → 顶点数组（静态绑定位）。
 *
 * <p>静态 MVP 语义：不做骨骼变换（bbmodel/geo/obj 的 cube 坐标都是模型空间绝对值，
 * pivot 只在动画（P3）时参与）；贴图 UV 按贴图像素尺寸归一。
 */
public final class QuadBaker {

    private QuadBaker() {
    }

    /** 烘焙全部骨骼（展平后逐 mesh）；贴图取各 mesh 材质的标签，逐 mesh 产出一条 BakedModel。 */
    public static List<BakedModel> bake(ModelIR model) {
        List<BakedModel> out = new ArrayList<BakedModel>();
        List<ModelIR.BoneIR> flat = new ArrayList<ModelIR.BoneIR>();
        model.flatten(flat);
        // 同一材质的 mesh 合并进同一条 BakedModel，减少切换贴图次数
        java.util.LinkedHashMap<String, Buf> byTex = new java.util.LinkedHashMap<String, Buf>();
        for (ModelIR.BoneIR bone : flat) {
            for (ModelIR.Mesh mesh : bone.meshes) {
                String tex = textureLabel(model, mesh);
                Buf buf = byTex.get(tex);
                if (buf == null) {
                    buf = new Buf();
                    byTex.put(tex, buf);
                }
                if (mesh instanceof ModelIR.BoxMesh) {
                    bakeBox((ModelIR.BoxMesh) mesh, buf);
                } else if (mesh instanceof ModelIR.TriMesh) {
                    bakeTri((ModelIR.TriMesh) mesh, buf);
                }
            }
        }
        for (java.util.Map.Entry<String, Buf> e : byTex.entrySet()) {
            Buf b = e.getValue();
            out.add(new BakedModel(e.getKey(), b.quadArray(), b.triArray()));
        }
        return out;
    }

    private static String textureLabel(ModelIR model, ModelIR.Mesh mesh) {
        int idx;
        if (mesh instanceof ModelIR.BoxMesh) {
            idx = ((ModelIR.BoxMesh) mesh).material;
        } else if (mesh instanceof ModelIR.TriMesh) {
            idx = ((ModelIR.TriMesh) mesh).material;
        } else {
            idx = -1;
        }
        if (idx >= 0 && idx < model.materials.size()) {
            String t = model.materials.get(idx).texture;
            if (t != null) {
                return t;
            }
        }
        return "none";
    }

    // ---------------------------------------------------------------- 盒（6 面）

    private static void bakeBox(ModelIR.BoxMesh box, Buf buf) {
        float x1 = (float) box.from[0];
        float y1 = (float) box.from[1];
        float z1 = (float) box.from[2];
        float x2 = (float) box.to[0];
        float y2 = (float) box.to[1];
        float z2 = (float) box.to[2];
        // 面序与 IR 约定一致：0 north(-Z) 1 east(+X) 2 south(+Z) 3 west(-X) 4 up(+Y) 5 down(-Y)
        quad(buf, box, 0, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, 0, 0, -1);
        quad(buf, box, 1, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
        quad(buf, box, 2, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2, 0, 0, 1);
        quad(buf, box, 3, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
        quad(buf, box, 4, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, 0, 1, 0);
        quad(buf, box, 5, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, 0, -1, 0);
    }

    /** 写一个面：4 角按 (u1,v1)(u2,v1)(u2,v2)(u1,v2) 取 UV。 */
    private static void quad(Buf buf, ModelIR.BoxMesh box, int face,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz) {
        double[] uv = box.uv[face];
        buf.quad(
                ax, ay, az, (float) uv[0], (float) uv[1], nx, ny, nz,
                bx, by, bz, (float) uv[2], (float) uv[1], nx, ny, nz,
                cx, cy, cz, (float) uv[2], (float) uv[3], nx, ny, nz,
                dx, dy, dz, (float) uv[0], (float) uv[3], nx, ny, nz);
    }

    // ---------------------------------------------------------------- 三角（obj）

    private static void bakeTri(ModelIR.TriMesh mesh, Buf buf) {
        for (int i = 0; i + 2 < mesh.indices.length; i += 3) {
            int ia = mesh.indices[i];
            int ib = mesh.indices[i + 1];
            int ic = mesh.indices[i + 2];
            float[] faceN = faceNormal(mesh, ia, ib, ic);
            tri(buf, mesh, ia, faceN);
            tri(buf, mesh, ib, faceN);
            tri(buf, mesh, ic, faceN);
        }
    }

    private static void tri(Buf buf, ModelIR.TriMesh mesh, int v, float[] fn) {
        boolean hasN = mesh.normals != null && mesh.normals.length >= (v + 1) * 3;
        boolean hasUv = mesh.uvs != null && mesh.uvs.length >= (v + 1) * 2;
        float nx = hasN ? mesh.normals[v * 3] : fn[0];
        float ny = hasN ? mesh.normals[v * 3 + 1] : fn[1];
        float nz = hasN ? mesh.normals[v * 3 + 2] : fn[2];
        float u = hasUv ? mesh.uvs[v * 2] : 0f;
        float vv = hasUv ? mesh.uvs[v * 2 + 1] : 0f;
        buf.triVertex(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2], u, vv, nx, ny, nz);
    }

    private static float[] faceNormal(ModelIR.TriMesh mesh, int ia, int ib, int ic) {
        float ax = mesh.positions[ia * 3];
        float ay = mesh.positions[ia * 3 + 1];
        float az = mesh.positions[ia * 3 + 2];
        float ux = mesh.positions[ib * 3] - ax;
        float uy = mesh.positions[ib * 3 + 1] - ay;
        float uz = mesh.positions[ib * 3 + 2] - az;
        float vx = mesh.positions[ic * 3] - ax;
        float vy = mesh.positions[ic * 3 + 1] - ay;
        float vz = mesh.positions[ic * 3 + 2] - az;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9f) {
            return new float[]{0f, 0f, 1f};
        }
        return new float[]{nx / len, ny / len, nz / len};
    }

    // ---------------------------------------------------------------- 缓冲

    private static final class Buf {
        private float[] quads = new float[0];
        private int quadLen = 0;
        private float[] tris = new float[0];
        private int triLen = 0;

        float[] quadArray() {
            return quadLen == quads.length ? quads : java.util.Arrays.copyOf(quads, quadLen);
        }

        float[] triArray() {
            return triLen == tris.length ? tris : java.util.Arrays.copyOf(tris, triLen);
        }

        void quad(float... v) {
            if (v.length != 32) {
                return;
            }
            if (quadLen + 32 > quads.length) {
                quads = grow(quads, quadLen + 32);
            }
            System.arraycopy(v, 0, quads, quadLen, 32);
            quadLen += 32;
        }

        void triVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            if (triLen + 24 > tris.length) {
                tris = grow(tris, triLen + 24);
            }
            put(tris, triLen, x, y, z, u, v, nx, ny, nz);
            triLen += 8;
        }

        float[] quads() {
            return java.util.Arrays.copyOf(quads, quadLen);
        }

        float[] tris() {
            return java.util.Arrays.copyOf(tris, triLen);
        }

        private static float[] grow(float[] src, int need) {
            int cap = Math.max(need, Math.max(64, src.length * 2));
            float[] out = new float[cap];
            System.arraycopy(src, 0, out, 0, src.length);
            return out;
        }

        private static void put(float[] dst, int off,
                                float a, float b, float c, float d, float e, float f, float g, float h) {
            dst[off] = a;
            dst[off + 1] = b;
            dst[off + 2] = c;
            dst[off + 3] = d;
            dst[off + 4] = e;
            dst[off + 5] = f;
            dst[off + 6] = g;
            dst[off + 7] = h;
        }
    }
}
