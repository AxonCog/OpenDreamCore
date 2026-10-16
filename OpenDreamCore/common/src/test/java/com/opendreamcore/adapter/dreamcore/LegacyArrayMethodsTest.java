package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyArrayMethodsTest {
    @Test
    void mappingAliasesInvokeCallable() {
        LegacyMethods.ensureRegistered();
        Scope scope = new Scope();
        Object callback = DreamLang.evaluate("(x) => x + 1", scope);
        List<Object> source = List.of(1L, 2L, 3L);

        for (String name : List.of("map", "for_each", "for_each_simple", "映射")) {
            Object result = MethodRegistry.require(name).invoke(new Object[]{source, callback});
            assertEquals("[2.0, 3.0, 4.0]", String.valueOf(result), name);
        }
    }

    @Test
    void mappingHandlesEmptyAndInvalidInputs() {
        LegacyMethods.ensureRegistered();
        Scope scope = new Scope();
        Object callback = DreamLang.evaluate("(x) => x", scope);
        List<Object> empty = new ArrayList<>();

        Object emptyResult = MethodRegistry.require("map").invoke(new Object[]{empty, callback});
        assertTrue(emptyResult instanceof List<?> && ((List<?>) emptyResult).isEmpty());
        assertTrue(((List<?>) MethodRegistry.require("映射").invoke(new Object[]{"not a list", callback})).isEmpty());

        Object invalid = MethodRegistry.require("for_each").invoke(new Object[]{List.of(1L, 2L), "not callable"});
        assertEquals("[1, 2]", String.valueOf(invalid));
        assertNotSame(empty, emptyResult);
    }
}
