package com.opendreamcore.model.molang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** molang 子集：SafeMath 语义与回退行为。 */
class MolangTest {

    private final MolangEval eval = new MolangEval();

    @Test
    void arithmetic() {
        assertEquals(7, eval.eval("1+2*3"), 1e-9);
        assertEquals(0.5, eval.eval("1/2"), 1e-9);
        assertEquals(0, eval.eval("1/0"), 1e-9, "除零回退 0");
        assertEquals(1, eval.eval("5%2"), 1e-9);
        assertEquals(-3, eval.eval("-(1+2)"), 1e-9);
    }

    @Test
    void compareAndTernary() {
        assertEquals(1, eval.eval("2>1"), 1e-9);
        assertEquals(0, eval.eval("2<=1"), 1e-9);
        assertEquals(10, eval.eval("1 ? 10 : 20"), 1e-9);
        assertEquals(20, eval.eval("0 ? 10 : 20"), 1e-9);
        assertEquals(1, eval.eval("1==1 && 2>=2"), 1e-9);
        assertEquals(1, eval.eval("0||1"), 1e-9);
    }

    @Test
    void mathFunctions() {
        assertEquals(3, eval.eval("math.abs(-3)"), 1e-9);
        assertEquals(0, eval.eval("math.sin(0)"), 1e-9);
        assertEquals(1, eval.eval("math.sin(90)"), 1e-9, "角度制");
        assertEquals(2, eval.eval("math.sqrt(4)"), 1e-9);
        assertEquals(8, eval.eval("math.pow(2,3)"), 1e-9);
        assertEquals(5, eval.eval("math.clamp(7, 0, 5)"), 1e-9);
        assertEquals(3.14, eval.eval("math.pi * 1"), 1e-2);
    }

    @Test
    void variables() {
        assertEquals(2.5, eval.eval("query.anim_time * 2", name ->
                "query.anim_time".equals(name) ? 1.25 : 0), 1e-9);
    }

    @Test
    void fallbackOnUnknown() {
        assertEquals(0, eval.eval("math.unknown_fn(1)"), 1e-9, "未知函数回退 0");
        assertEquals(0, eval.eval("1 +"), 1e-9, "残缺表达式回退 0");
    }

    @Test
    void strictModeThrows() {
        MolangEval strict = new MolangEval(true);
        assertThrows(RuntimeException.class, () -> strict.eval("1 +"));
        assertThrows(RuntimeException.class, () -> strict.eval("math.unknown_fn(1)"));
    }

    @Test
    void expressionKeyframeStrings() {
        // bbmodel data_points 可能带 molang（如 "math.sin(query.anim_time*360)"）
        assertEquals(1, eval.eval("math.sin(query.anim_time*360)", name ->
                "query.anim_time".equals(name) ? 0.25 : 0), 1e-9, "0.25×360=90° → sin=1");
    }
}
