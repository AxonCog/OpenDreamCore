package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.adapter.AdapterChain;
import com.opendreamcore.adapter.dreamcore.DreamCoreSystems;
import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.Scope;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DragonCoreAdapterCompatTest {

    @Test
    void bloodAndHeadTagUseTheirDeclaredSystems() {
        Map<String, Object> blood = new LinkedHashMap<>();
        blood.put("mob", Map.of("offsetY", 12, "string", Map.of()));
        Map<String, Map<String, Map<String, Object>>> bloodOut =
                AdapterChain.translate("plugins/DragonCore/blood.yml", blood);
        assertTrue(bloodOut.containsKey("Blood"));
        assertFalse(bloodOut.containsKey("HeadTag"));

        Map<String, Object> headTag = new LinkedHashMap<>();
        headTag.put("mob", Map.of("match", "Zombie", "名字_label",
                Map.of("content", "%name%")));
        Map<String, Map<String, Map<String, Object>>> headTagOut =
                AdapterChain.translate("plugins\\DragonCore\\headtag.yml", headTag);
        assertTrue(headTagOut.containsKey("HeadTag"));
        assertFalse(headTagOut.containsKey("Blood"));
    }

    @Test
    void bloodWithNameComponentsProducesBothSystems() {
        Map<String, Object> root = Map.of("mob", Map.of(
                "offsetY", 12, "health", Map.of("path", "health.png"),
                "名字_label", Map.of("content", "%name%")));
        Map<String, Map<String, Map<String, Object>>> translated =
                AdapterChain.translate("plugins\\DragonCore\\BLOOD.YML", root);
        assertEquals(DreamCoreSystems.blood(root), translated.get("Blood"));
        assertEquals(DreamCoreSystems.headTag(root), translated.get("HeadTag"));
        assertEquals(2, translated.size());
    }


    @Test
    void routingUsesPathsAndRootLocaleWithoutClaimingRuntimeOrGuiConfig() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            DragonCoreAdapter adapter = new DragonCoreAdapter();
            Map<String, Object> root = Map.of("rule", Map.of("path", "icon.png", "match", "stone"));
            for (String name : new String[]{"ITEMICON.YML", "plugins/DragonCore/ItemIcon.yml",
                    "plugins\\DragonCore\\ITEMICON.YML"}) {
                assertTrue(adapter.accepts(name, root), name);
                assertEquals(DreamCoreSystems.itemIcon(root), adapter.translate(name, root).get("ItemIcon"));
            }
            for (String name : new String[]{"config.yml", "plugins/DragonCore/CONFIG.YML",
                    "ItemTip.yml", "plugins\\DragonCore\\ITEMTIP.YML", "unknown.yml"}) {
                assertFalse(adapter.accepts(name, root), name);
                assertTrue(adapter.translate(name, root).isEmpty(), name);
            }
            assertFalse(adapter.accepts(null, root));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void bareMethodExecutesBeforeAndAfterAdapterRewrite() {
        String name = "导入裸方法回归";
        AtomicInteger calls = new AtomicInteger();
        MethodRegistry.registerOrReplace(name, args -> {
            assertEquals(0, args.length);
            return calls.incrementAndGet();
        });
        try {
            String script = "方法." + name + ";";
            DreamLang.execute(script, new Scope());
            assertEquals(1, calls.get(), "裸方法必须执行，不能只返回方法引用");
            DreamLang.execute(AdapterChain.rewriteScript(script), new Scope());
            assertEquals(2, calls.get(), "适配链之后仍执行同一裸方法");
        } finally {
            MethodRegistry.unregister(name);
        }
    }

    @Test
    void nullRootDoesNotReachSystemTranslator() {
        assertTrue(AdapterChain.translate("blood.yml", null).isEmpty());
    }
}
