package com.opendreamcore.model.anim;

import com.opendreamcore.model.format.anim.BedrockAnimCodec;
import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bedrock 动画 json 解析（真实样例形态全覆盖）+ 自动绑定回退链。 */
class BedrockAnimAutoBindTest {

    @Test
    void 解析真实bedrock样例() {
        String json = "{\n"
                + "  \"format_version\": \"1.8.0\",\n"
                + "  \"animations\": {\n"
                + "    \"animation.model.idle\": {\n"
                + "      \"loop\": true,\n"
                + "      \"animation_length\": 2.0,\n"
                + "      \"bones\": {\n"
                + "        \"body\": { \"rotation\": [0, 3, 0] },\n"
                + "        \"head\": {\n"
                + "          \"rotation\": {\n"
                + "            \"0.0\": [0, 0, 0],\n"
                + "            \"1.0\": {\"post\": [10, 0, 0], \"pre\": [0, 0, 0]},\n"
                + "            \"2.0\": [0, 0, 0]\n"
                + "          },\n"
                + "          \"position\": {\"0.0\": [0, 0.5, 0], \"2.0\": [0, 0.5, 0]}\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"
                + "    \"animation.model.walk\": {\n"
                + "      \"loop\": \"hold_on_last_frame\",\n"
                + "      \"animation_length\": 1.0,\n"
                + "      \"bones\": {\n"
                + "        \"leg0\": { \"rotation\": [\"math.sin(q.anim_time*360)*30\", 0, 0] }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";
        List<AnimationIR> anims = BedrockAnimCodec.parse(json);
        assertEquals(2, anims.size());

        AnimationIR idle = anims.get(0);
        assertEquals(AnimationIR.LOOP_LOOP, idle.loopMode);
        assertEquals(2.0, idle.length, 1e-9);
        AnimationIR.BoneAnim body = idle.bone("body");
        assertNotNull(body);
        assertNotNull(body.rot);
        assertEquals(1, body.rot.keys.size()); // 常量轨道 = 单帧
        java.util.Map<String, AnimEval.Pose> poses = new java.util.HashMap<String, AnimEval.Pose>();
        AnimEval.sample(idle, .999, null, poses);
        assertEquals(0, poses.get("head").rot[0], 1e-9, "进入 1s 边界前使用 pre=0");
        AnimEval.sample(idle, 1, null, poses);
        assertEquals(-10, poses.get("head").rot[0], 1e-9, "精确到 1s 使用 post=10，再按 Java X 轴取反");
        AnimEval.sample(idle, 1.5, null, poses);
        assertEquals(-5, poses.get("head").rot[0], 1e-9, "离开边界从 post 插值到下一帧");

        AnimationIR walk = anims.get(1);
        AnimEval.sample(walk, .25, null, poses);
        assertEquals(0, poses.get("leg0").rot[0], 1e-9, "未知 q 命名空间按宽容语义回零");
    }

    @Test
    void 坏文件与缺字段宽容() {
        assertTrue(BedrockAnimCodec.parse(null).isEmpty());
        assertTrue(BedrockAnimCodec.parse("not json {{{").isEmpty());
        assertTrue(BedrockAnimCodec.parse("{\"animations\":{}}").isEmpty());
        // 动画体缺 bones 也算有效（空动画被过滤）
        assertTrue(BedrockAnimCodec.parse("{\"animations\":{\"a\":{\"loop\":true}}}").isEmpty());
    }

    @Test
    void 自动绑定回退链与覆盖() {
        AnimationIR idle = new AnimationIR("idle", 1, AnimationIR.LOOP_LOOP);
        AnimationIR walk = new AnimationIR("walk", 1, AnimationIR.LOOP_LOOP);
        java.util.Map<String, AnimationIR> pool = new java.util.HashMap<String, AnimationIR>();
        pool.put("idle", idle);
        pool.put("walk", walk);
        AutoBind.NameResolver r = pool::get;

        // 命中状态本身
        assertSame(walk, AutoBind.pick("walk", null, r));
        // 未知状态回退 walk→idle
        assertSame(walk, AutoBind.pick("sleep", null, r));
        // 只有 idle 的池：任何状态都回退到 idle
        java.util.Map<String, AnimationIR> onlyIdle = new java.util.HashMap<String, AnimationIR>();
        onlyIdle.put("idle", idle);
        assertSame(idle, AutoBind.pick("run", null, onlyIdle::get));
        // 覆盖映射优先：run 指到 walk（完整池里覆盖生效，拿到的是 walk 实例）
        java.util.Map<String, String> overrides = new java.util.HashMap<String, String>();
        overrides.put("run", "walk");
        assertSame(walk, AutoBind.pick("run", overrides, r));
        // 覆盖指向不存在的动画（仅 idle 池）→ 覆盖解析失败落回退链拿 idle
        overrides.put("run", "nope");
        assertSame(idle, AutoBind.pick("run", overrides, onlyIdle::get));
        // 空池 → null
        assertNull(AutoBind.pick("walk", null, s -> null));
        // 字典完整性
        assertTrue(AutoBind.knownState("idle"));
        assertTrue(AutoBind.knownState("look_at_target"));
        assertTrue(AutoBind.STATES.length >= 35);
    }
}
