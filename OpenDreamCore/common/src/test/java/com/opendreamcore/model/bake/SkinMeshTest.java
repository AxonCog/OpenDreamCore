package com.opendreamcore.model.bake;

import com.opendreamcore.model.anim.BoneXform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 蒙皮网格与采样：刚性退化、权重混合、矩阵一致性。 */
class SkinMeshTest {

    /** 两骨骼：根在原点，子骨 pivot 在 (16,0,0)。 */
    private static BakedRig rig() {
        BakedRig.Bone root = new BakedRig.Bone("root", -1,
                new double[]{0, 0, 0}, null);
        BakedRig.Bone arm = new BakedRig.Bone("arm", 0,
                new double[]{16, 0, 0}, null);
        // root 上一个四边形（pivot 相对坐标，8 float/顶点）
        root.parts.add(new BakedRig.Part("t.png", new float[]{
                0, 0, 0, 0, 0, 0, 0, 1,
                1, 0, 0, 1, 0, 0, 0, 1,
                1, 1, 0, 1, 1, 0, 0, 1,
                0, 1, 0, 0, 1, 0, 0, 1,
        }, new float[0]));
        return new BakedRig(new BakedRig.Bone[]{root, arm});
    }

    @Test
    void 刚性退化权重恒为一() {
        List<SkinMesh> meshes = SkinSampler.unify(rig());
        assertEquals(1, meshes.size());
        SkinMesh m = meshes.get(0);
        assertEquals("t.png", m.texture);
        // 四边形拆两三角 = 6 顶点
        assertEquals(6, m.vertices());
        assertEquals(2, m.triangles());
        for (int i = 0; i < m.vertices(); i++) {
            assertEquals(0, m.bones[i * 4]);
            assertEquals(1.0f, m.weight[i * 4], 1e-6);
            assertEquals(-1, m.bones[i * 4 + 1]);
            // 模型空间 = pivot 相对 + pivot
            assertEquals(m.xyz[i * 3], m.xyz[i * 3], 1e-6);
        }
    }

    @Test
    void 单骨骼满权重采样等于骨骼矩阵直乘() {
        List<SkinMesh> meshes = SkinSampler.unify(rig());
        BoneXform[] world = {new BoneXform(), new BoneXform()};
        world[0].identity();
        world[1].identity();
        // root 平移 (3,4,0)：刚性顶点加回 pivot 后再被矩阵平移
        world[0].t[0] = 3;
        world[0].t[1] = 4;

        double[] ox = new double[18];
        double[] on = new double[18];
        final float[][] got = new float[6][3];
        final int[] c = {0};
        SkinSampler.sample(meshes.get(0), world, ox, on, (x, y, z, u, v, nx, ny, nz) -> {
            got[c[0]][0] = x;
            got[c[0]][1] = y;
            got[c[0]][2] = z;
            c[0]++;
        });
        assertEquals(6, c[0]);
        // 原始第一顶点 (0,0,0)+pivot(0,0,0) → 矩阵平移后 (3,4,0)
        assertEquals(3.0f, got[0][0], 1e-4);
        assertEquals(4.0f, got[0][1], 1e-4);
        // (1,0,0) → (4,4,0)
        assertEquals(4.0f, got[1][0], 1e-4);
        assertEquals(4.0f, got[1][1], 1e-4);
    }

    @Test
    void 混合权重位置等于逐骨线性组合() {
        // 手工构建：一根单位骨骼 bone0（不动）+ bone1（平移 (10,0,0)），
        // 顶点 (0,0,0) 各 0.5 → 期望 (5,0,0)
        SkinMesh.Builder b = new SkinMesh.Builder();
        b.triangle("t.png",
                new float[][]{{0, 0, 0}, {1, 0, 0}, {0, 1, 0}},
                new float[][]{{0, 0}, {1, 0}, {0, 1}},
                new float[][]{{0, 0, 1}, {0, 0, 1}, {0, 0, 1}},
                new int[][]{{0, 1, -1, -1}, {0, -1, -1, -1}, {0, -1, -1, -1}},
                new float[][]{{0.5f, 0.5f, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}});
        SkinMesh m = b.build().get(0);
        assertEquals(3, m.vertices());

        BoneXform[] world = {new BoneXform(), new BoneXform()};
        world[0].identity();
        world[1].identity();
        world[1].t[0] = 10;

        double[] ox = new double[9];
        double[] on = new double[9];
        final float[][] got = new float[3][8];
        final int[] c = {0};
        SkinSampler.sample(m, world, ox, on, (x, y, z, u, v, nx, ny, nz) -> {
            got[c[0]][0] = x;
            got[c[0]][1] = y;
            got[c[0]][2] = z;
            got[c[0]][3] = u;
            got[c[0]][4] = v;
            got[c[0]][5] = nx;
            got[c[0]][6] = ny;
            got[c[0]][7] = nz;
            c[0]++;
        });
        assertEquals(3, c[0]);
        assertEquals(5.0f, got[0][0], 1e-4);   // 0.5*(0) + 0.5*(0+10)
        assertEquals(0.0f, got[0][1], 1e-4);
        assertEquals(1.0f, got[1][0], 1e-4);   // 满权重 bone0 → 顶点 (1,0,0) 不动
        assertEquals(1.0f, got[1][3], 1e-6);   // UV 原样透传
        // 法线混合后归一（两骨同向 → 仍 (0,0,1)）
        assertEquals(0.0f, got[0][5], 1e-4);
        assertEquals(1.0f, got[0][7], 1e-4);
    }
}
