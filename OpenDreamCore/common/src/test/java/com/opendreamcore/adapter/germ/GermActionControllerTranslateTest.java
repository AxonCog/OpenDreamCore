package com.opendreamcore.adapter.germ;

import com.opendreamcore.config.ConfigParseException;
import com.opendreamcore.model.anim.ControllerCodec;
import com.opendreamcore.model.anim.ControllerIR;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 动作包（萌芽风格「通常动作.yml」）→ 控制器触发翻译。 */
class GermActionControllerTranslateTest {

    private static Map<String, Object> pack(String animationName) {
        Map<String, Object> action = new LinkedHashMap<String, Object>();
        action.put("name", animationName);
        action.put("speed", 1);
        action.put("duration", 800);
        action.put("action", "mm skill \"aura{auraName=翻滚无敌状态;d=6} @self\"\ndash 1.5 0 0");
        List<Object> subject = new java.util.ArrayList<Object>();
        Map<String, Object> sub = new HashMap<String, Object>();
        sub.put("tracker", "player defense entity");
        sub.put("action", "if mm condition [ \"hasaura{n=翻滚无敌状态}\" ] then {\n  event cancel\n}");
        subject.add(sub);
        action.put("subject", subject);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("前翻滚", action);
        return root;
    }

    private static Map<String, Object> pack() {
        return pack("前翻滚");
    }

    @Test
    void triggerKeyMapsToDistinctDeclaredAnimationName() {
        ControllerIR ir = GermActionControllerTranslate.translate(pack("roll_animation"));
        ControllerIR.Trigger t = ir.trigger("前翻滚");
        assertNotNull(t);
        assertEquals("roll_animation", t.anims.get(0));
        assertEquals(800L, t.durationMs);
    }

    @Test
    void actionPackRecognizedAndTranslated() throws Exception {
        Map<String, Object> root = pack();
        assertTrue(GermActionControllerTranslate.isActionPack(root), "动作包形态应被认领");

        ControllerIR ir = GermActionControllerTranslate.translate(root);
        assertEquals(1, ir.triggers.size());
        ControllerIR.Trigger t = ir.trigger("前翻滚");
        assertNotNull(t);
        assertEquals(1, t.anims.size());
        assertEquals("前翻滚", t.anims.get(0));
        assertEquals(1.0, t.speed, 1e-9);
        assertEquals(800L, t.durationMs);
        // action/subject 不翻也不炸：翻译照常完成
    }

    @Test
    void nativeAndDragonCoreNotClaimed() {
        Map<String, Object> nativeRoot = new HashMap<String, Object>();
        nativeRoot.put("default", "idle");
        nativeRoot.put("states", new HashMap<String, Object>());
        assertFalse(GermActionControllerTranslate.isActionPack(nativeRoot));
        Map<String, Object> dcRoot = new HashMap<String, Object>();
        dcRoot.put("Layer", new HashMap<String, Object>());
        assertFalse(GermActionControllerTranslate.isActionPack(dcRoot));
    }

    @Test
    void controllerCodecRoutesActionPack() throws Exception {
        String yaml = "前翻滚:\n  name: 前翻滚\n  speed: 2\n  duration: 500\n"
                + "  action: |-\n    dash 1.5 0 0\n";
        ControllerIR ir = ControllerCodec.parse(yaml);
        ControllerIR.Trigger t = ir.trigger("前翻滚");
        assertNotNull(t, "动作包 yml 应经嗅探翻译成触发");
        assertEquals(2.0, t.speed, 1e-9);
        assertEquals(500L, t.durationMs);
    }

    @Test
    void realRollPackIfPresent() throws Exception {
        Path p = Paths.get("D:/下载备份/翻滚/通常动作.yml");
        if (!Files.exists(p)) {
            return;
        }
        String yaml = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        ControllerIR ir = ControllerCodec.parse(yaml);
        assertNotNull(ir.trigger("前翻滚"));
        assertNotNull(ir.trigger("左翻滚"));
        assertNotNull(ir.trigger("右翻滚"));
        System.out.println("[翻滚包] 通常动作.yml 翻译触发 " + ir.triggers.size() + " 条");
    }
}
