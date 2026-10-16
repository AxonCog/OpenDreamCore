package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.page.Page;
import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.NamespaceRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Legacy clear-all aliases use the current page's Var storage. */
class LegacyVariableClearTest {

    @Test
    void clearAliasesClearCurrentPageVariablesAndHandleNoPage() {
        Page page = new Page("legacy-clear", "Legacy clear", null, null,
                new LinkedHashMap<>(), null, null, null);
        AtomicReference<Page> currentPage = new AtomicReference<>(page);
        NamespaceRegistry.register("Var", "清空", args -> {
            Page current = currentPage.get();
            if (current == null) {
                return 0.0;
            }
            int count = current.variables().size();
            current.variables().clear();
            return (double) count;
        });
        LegacyMethods.ensureRegistered();

        String[] aliases = {"clear_variables", "clear_variable_all", "清空变量", "变量清除", "清除变量"};
        for (String alias : aliases) {
            page.variables().put("coins", 12);
            page.variables().put("title", "shop");
            assertEquals(2.0, MethodRegistry.require(alias).invoke(new Object[0]), alias);
            assertTrue(page.variables().isEmpty(), alias + " clears current page variables");
        }

        currentPage.set(null);
        for (String alias : aliases) {
            assertDoesNotThrow(() -> MethodRegistry.require(alias).invoke(new Object[0]), alias);
            assertEquals(0.0, MethodRegistry.require(alias).invoke(new Object[0]), alias);
        }
    }
}
