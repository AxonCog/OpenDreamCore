package com.opendreamcore.model.anim;

import com.opendreamcore.model.bake.BakedRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 三套命名体系的骨骼名统一映射：洗名 + 同义词，命中同一根骨。 */
class BoneAliasesTest {

    private final BakedRig rig = HumanoidRig.build();

    @Test
    void 三套命名命中同一根前臂() {
        assertEquals(BoneAliases.resolve(rig, "leftforearm"),
                BoneAliases.resolve(rig, "left_fore_arm"));
        assertEquals(BoneAliases.resolve(rig, "leftforearm"),
                BoneAliases.resolve(rig, "leftForeArm"));
        assertEquals(3, BoneAliases.resolve(rig, "left_fore_arm"));
    }

    @Test
    void 龙核名直接命中() {
        assertEquals(0, BoneAliases.resolve(rig, "body"));
        assertEquals(1, BoneAliases.resolve(rig, "head"));
        assertEquals(10, BoneAliases.resolve(rig, "rightitem"));
        assertEquals(5, BoneAliases.resolve(rig, "RightForeArm"));
    }

    @Test
    void 同义词表生效() {
        assertEquals(BoneAliases.resolve(rig, "head"), BoneAliases.resolve(rig, "hat"));
        assertEquals(BoneAliases.resolve(rig, "body"), BoneAliases.resolve(rig, "torso"));
    }

    @Test
    void 查不到返回缺失不报错() {
        assertEquals(-1, BoneAliases.resolve(rig, "尾巴"));
        assertEquals(-1, BoneAliases.resolve(rig, ""));
        assertEquals(-1, BoneAliases.resolve(null, "head"));
        assertEquals(-1, BoneAliases.resolve(rig, null));
    }

    @Test
    void 内置人形骨架层级完整() {
        assertEquals(11, rig.bones.length);
        // 二段式：前臂父=臂、小腿父=腿、持物手挂在前臂下
        assertEquals(2, rig.bones[3].parent);
        assertEquals(8, rig.bones[9].parent);
        assertEquals(5, rig.bones[10].parent);
        // 层级是树：body 是根
        assertEquals(-1, rig.bones[0].parent);
    }
}
