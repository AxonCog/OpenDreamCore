package com.opendreamcore.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opendreamcore.script.engine.DreamLang;

/** evaluateCached：变量指纹结果缓存——没变零计算，变了落实算（布局热路径）。 */
public class DreamLangEvalCacheTest {

    @Test
    public void sameVarsSameResult() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("health_ratio", 0.5);
        Object a = DreamLang.evaluateCached("2 * health_ratio", vars);
        Object b = DreamLang.evaluateCached("2 * health_ratio", vars);
        assertEquals(((Number) a).doubleValue(), ((Number) b).doubleValue(), 1e-9);
    }

    @Test
    public void changedVarsNewResult() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("health_ratio", 0.5);
        double half = ((Number) DreamLang.evaluateCached("2 * health_ratio", vars)).doubleValue();
        vars.put("health_ratio", 1.0);
        double full = ((Number) DreamLang.evaluateCached("2 * health_ratio", vars)).doubleValue();
        assertEquals(1.0, half, 1e-9);
        assertEquals(2.0, full, 1e-9);
    }

    @Test
    public void noVarsPureArithmetic() {
        Object r = DreamLang.evaluateCached("1 + 2 * 3", null);
        assertEquals(7.0, ((Number) r).doubleValue(), 1e-9);
    }

    @Test
    public void clearForcesRecompute() {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("x", 3);
        assertEquals(6.0, ((Number) DreamLang.evaluateCached("x * 2", vars)).doubleValue(), 1e-9);
        DreamLang.clearEvalCache();
        assertEquals(6.0, ((Number) DreamLang.evaluateCached("x * 2", vars)).doubleValue(), 1e-9);
    }
}
