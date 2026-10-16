package com.opendreamcore.model.anim;

import com.opendreamcore.model.format.anim.BedrockAnimCodec;
import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 离线复现右闪避 0.5s 的逐帧姿态（弹簧+淡入全管线），验证观感曲线。 */
public class DodgePlaybackRepro {

    @Test
    public void playback() throws Exception {
        Path p = Path.of("D:/梦想核心测试服务端/1.21.1/plugins/OpenDreamCore/Animation/闪避.json");
        if (!Files.exists(p)) {
            return; // 无样本环境跳过
        }
        List<AnimationIR> irs = BedrockAnimCodec.parse(Files.readString(p));
        AnimationIR dodge = null;
        for (AnimationIR ir : irs) {
            if (ir.name.contains("右闪避")) {
                dodge = ir;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(dodge, "右闪避未解析出");
        AnimPlayer player = new AnimPlayer(0.35);
        player.playOnce(dodge, null, 0.15);
        java.util.Map<String, AnimEval.Pose> out = new java.util.HashMap<>();
        StringBuilder sb = new StringBuilder("t(s) | body.yRot | head.yRot | body.y(1/16)\n");
        for (int i = 0; i <= 30; i++) {
            double t = i * 0.025; // 40fps 采样
            out.clear();
            player.step(0.025, null, out);
            AnimEval.Pose body = out.get("body");
            AnimEval.Pose head = out.get("head");
            sb.append(String.format("%.3f | %s | %s | %s%n", t,
                    body == null ? "-" : String.format("%.1f", body.rot[1]),
                    head == null ? "-" : String.format("%.1f", head.rot[1]),
                    body == null ? "-" : String.format("%.2f", body.pos[1])));
        }
        System.out.println(sb);
    }
}
