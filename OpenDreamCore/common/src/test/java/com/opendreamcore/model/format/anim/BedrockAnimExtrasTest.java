package com.opendreamcore.model.format.anim;

import com.opendreamcore.model.anim.AnimPlayer;
import com.opendreamcore.model.anim.AnimEval;
import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 动作包扩展字段以及仓库内真实四向翻滚样本的数值回归。 */
class BedrockAnimExtrasTest {

    @Test
    void speedAndOverrideParsed() {
        String json = "{\"animations\":{\"roll\":{\"loop\":true,\"animation_length\":1.0,"
                + "\"speed\":2.0,\"override_previous_animation\":true,"
                + "\"bones\":{\"body\":{\"rotation\":{\"0.0\":[0,0,0],\"1.0\":[360,0,0]}}}}}}";
        List<AnimationIR> anims = BedrockAnimCodec.parse(json);
        assertEquals(1, anims.size());
        AnimationIR roll = anims.get(0);
        assertEquals(2.0, roll.speed, 1e-9);
        assertTrue(roll.overridePrevious);
    }

    @Test
    void speedScalesEffectiveDuration() {
        String json = "{\"animations\":{\"roll\":{\"loop\":false,\"animation_length\":1.0,\"speed\":2.0,"
                + "\"bones\":{\"body\":{\"rotation\":{\"0.0\":[0,0,0],\"1.0\":[90,0,0]}}}}}}";
        AnimationIR roll = BedrockAnimCodec.parse(json).get(0);
        AnimPlayer player = new AnimPlayer(0);
        player.play(roll, 0);
        Map<String, com.opendreamcore.model.anim.AnimEval.Pose> out = new HashMap<>();
        // 0.5 真实秒 × 2 倍速 = 动画时间 1.0s = 全程；无倍速时只走到一半
        for (int i = 0; i < 30; i++) {
            player.step(1.0 / 60, null, out);
        }
        double z = out.get("body").rot[0];
        // bedrock rotation X 轴取反约定（GeckoLib 同款）：json 里的 90 → java -90°
        assertEquals(-90.0, z, 1.0, "2 倍速下半分钟应播完整条动画（1s 时长），且 X 轴取反");
    }

    @Test
    void overrideCutsTransitionImmediately() {
        AnimationIR a = new AnimationIR("idle", 1.0, AnimationIR.LOOP_LOOP);
        AnimationIR roll = new AnimationIR("roll", 0.5, AnimationIR.LOOP_ONCE);
        roll.overridePrevious = true;
        AnimPlayer player = new AnimPlayer(0.2);
        player.play(a, 0);
        for (int i = 0; i < 10; i++) {
            player.step(1.0 / 60, null, new HashMap<>());
        }
        player.playOnce(roll, a, 0.2);
        // override：无淡入窗口，立即就是新动画的姿态输出
        assertFalse(player.fading(), "override_previous_animation 应跳过过渡");
        assertEquals("roll", player.current().name);
    }

    @Test
    void repoRollFixtureParsesFourAnimations() throws Exception {
        java.io.InputStream in = BedrockAnimExtrasTest.class.getResourceAsStream("/model/sample/roll.animation.json");
        assertNotNull(in, "仓库翻滚样本必须存在");
        String json;
        try (java.io.InputStream stream = in) {
            json = new String(readAll(stream), StandardCharsets.UTF_8);
        }
        List<AnimationIR> anims = BedrockAnimCodec.parse(json);
        assertEquals(4, anims.size(), "翻滚样本应包含四个方向");
        for (AnimationIR a : anims) {
            assertEquals(0.8, a.length, 1e-9);
            assertNotNull(a.bone("Body"));
            assertNotNull(a.bone("Body").rot);
            assertTrue(a.bone("Body").rot.keys.size() >= 6);
            assertEquals("catmullrom", a.bone("Body").rot.keys.get(0).interp);
        }
        // 四个方向的 Body 0.1s 关键帧，显式核对轴变换后的三分量。
        double[][] atPoint = {
                {-41.28592, -13.14105, 11.04931},
                {-34.44132, -12.786, 38.06042},
                {-34.44132, 12.786, -38.06042},
                {-24.48798, -11.98076, 11.58467}
        };
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        for (int i = 0; i < anims.size(); i++) {
            AnimEval.sample(anims.get(i), .1, null, out);
            assertArrayEquals(atPoint[i], out.get("Body").rot, 1e-9);
            AnimPlayer player = new AnimPlayer(0);
            player.playOnce(anims.get(i), null, 0, true);
            for (int frame = 0; frame < 9; frame++) {
                player.step(.1, null, out);
            }
            assertFalse(player.oneShotActive());
            assertNull(player.current());
            assertTrue(out.isEmpty());
        }
        AnimEval.sample(anims.get(0), .05, null, out);
        assertNotNull(out.get("Body"));
        assertEquals(-30.104578125, out.get("Body").rot[0], 1e-9,
                "真实前翻滚首段 Catmull-Rom 使用端点重复夹住，不能替换成线性 -31.74181");
        AnimEval.sample(anims.get(0), .8, null, out);
        assertNotNull(out.get("Body"));
        assertEquals(-382.19774, out.get("Body").rot[0], 1e-9,
                "末帧保留完整转圈，不折叠到 -22.19774");
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
