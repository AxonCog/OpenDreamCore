package com.opendreamcore.model.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 控制器双语法：原生解析、龙核翻译、嗅探报错。 */
class ControllerCodecTest {

    private static final String NATIVE = "default: idle\n"
            + "states:\n"
            + "  idle:\n"
            + "    anim: idle\n"
            + "    loop: hold\n"
            + "  walk:\n"
            + "    anim: walk\n"
            + "    when: moving\n"
            + "    transition_time: 0.15\n"
            + "  attack:\n"
            + "    variants: [attack_1, attack_2]\n"
            + "    when: swinging\n"
            + "    once: true\n"
            + "    next_transition_time: 0.2\n"
            + "    priority: 10\n"
            + "layers:\n"
            + "  特效:\n"
            + "    blend_type: ADDING\n"
            + "    weight: 0.6\n"
            + "triggers:\n"
            + "  cast:\n"
            + "    layer: 特效\n"
            + "    anim: \"起手 > 循环\"\n"
            + "    transition_time: 0.15\n";

    private static final String DRAGON = "Layer:\n"
            + "  base:\n"
            + "    blendType: OVERRIDE\n"
            + "    weight: 1.0\n"
            + "  特效:\n"
            + "    blendType: ADDING\n"
            + "    weight: 0.6\n"
            + "    lockBodyAngle: true\n"
            + "Trigger:\n"
            + "  施法: 施法动作\n"
            + "  连招:\n"
            + "    layer: 特效\n"
            + "    animation: \"A > B\"\n"
            + "    transitionTime: 0.2\n";

    @Test
    void 原生写法全字段解析() throws Exception {
        ControllerIR ir = ControllerCodec.parse(NATIVE);
        assertEquals("idle", ir.defaultAnim);

        ControllerIR.State attack = ir.state("attack");
        assertEquals(2, attack.variants.size());
        assertEquals("swinging", attack.when);
        assertTrue(attack.once);
        assertEquals(10.0, attack.priority, 1e-9);
        assertEquals("hold", ir.state("idle").loop);
        assertEquals(0.15, ir.state("walk").transitionTime, 1e-9);

        assertEquals("ADDING", ir.layer("特效").blend);
        assertEquals(0.6, ir.layer("特效").weight, 1e-9);
        // 没写 base 也要有：引擎永远有地方站
        assertEquals("OVERRIDE", ir.layer("base").blend);

        ControllerIR.Trigger cast = ir.trigger("cast");
        assertEquals("特效", cast.layer);
        assertEquals(2, cast.anims.size());
        assertEquals("起手", cast.anims.get(0));
    }

    @Test
    void 龙核写法翻译归一() throws Exception {
        ControllerIR ir = ControllerCodec.parse(DRAGON);
        assertEquals("ADDING", ir.layer("特效").blend);
        assertTrue(ir.layer("特效").lockBodyAngle);
        assertEquals("base", ir.trigger("施法").layer);
        assertEquals("施法动作", ir.trigger("施法").anims.get(0));
        assertEquals(2, ir.trigger("连招").anims.size());
        assertEquals(0.2, ir.trigger("连招").transitionTime, 1e-9);
    }

    @Test
    void 龙核没写base层自动补() throws Exception {
        ControllerIR ir = ControllerCodec.parse("Layer:\n  特效:\n    blendType: ADDING\n");
        assertEquals("base", ir.layer("base").name);
        assertEquals("OVERRIDE", ir.layer("base").blend);
    }

    @Test
    void 认不出就报人话() {
        com.opendreamcore.config.ConfigParseException ex =
                assertThrows(com.opendreamcore.config.ConfigParseException.class,
                        () -> ControllerCodec.parse("随便写点东西: 1\n"));
        assertTrue(ex.getMessage().contains("states"));
        assertTrue(ex.getMessage().contains("Layer"));
    }

    @Test
    void 龙核与原生可共存于同一库() throws Exception {
        AnimationStore s = new AnimationStore();
        s.applyRemote("人形.yml", NATIVE.getBytes("UTF-8"));
        s.applyRemote("龙核实体.yml", DRAGON.getBytes("UTF-8"));

        assertEquals("idle", s.controllerIR("人形").defaultAnim);
        assertEquals("施法动作", s.controllerIR("龙核实体").trigger("施法").anims.get(0));
    }
}
