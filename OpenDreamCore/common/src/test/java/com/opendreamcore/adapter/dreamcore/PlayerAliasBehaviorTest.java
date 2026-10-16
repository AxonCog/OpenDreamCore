package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.NamespaceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAliasBehaviorTest {
    @Test
    void fullGapPlayerAliasesUseTheirFields() {
        NamespaceRegistry.register("Player", "获取名字", args -> "PlayerName");
        NamespaceRegistry.register("Player", "获取坐标X", args -> 11.5);
        NamespaceRegistry.register("Player", "获取坐标Y", args -> 22.5);
        NamespaceRegistry.register("Player", "获取坐标Z", args -> 33.5);
        NamespaceRegistry.register("Player", "获取俯仰角", args -> 44.5);
        NamespaceRegistry.register("Player", "获取偏航角", args -> 55.5);
        NamespaceRegistry.register("Player", "是否在地面", args -> true);
        NamespaceRegistry.register("Player", "是否在水中", args -> false);
        NamespaceRegistry.register("Player", "是否游泳", args -> true);
        NamespaceRegistry.register("Player", "是否潜行", args -> false);
        NamespaceRegistry.register("Player", "是否着火", args -> true);
        NamespaceRegistry.register("Player", "是否疾跑", args -> true);
        NamespaceRegistry.register("Player", "延迟", args -> 7.0);
        LegacyMethods.ensureRegistered();

        assertEquals("", call("玩家UUID"));
        assertEquals(11.5, call("玩家X"));
        assertEquals(22.5, call("玩家Y"));
        assertEquals(33.5, call("玩家Z"));
        assertEquals("11.5,22.5,33.5", call("玩家位置"));
        assertEquals(44.5, call("玩家俯仰角"));
        assertEquals(55.5, call("玩家偏航角"));
        assertEquals("PlayerName", call("玩家名"));
        assertEquals(true, call("玩家在地面"));
        assertEquals(false, call("玩家在水中"));
        assertEquals(true, call("玩家游泳"));
        assertEquals(true, call("玩家疾跑"));
        assertEquals(7.0, call("玩家延迟"));
    }

    private static Object call(String name) {
        return MethodRegistry.require(name).invoke(new Object[0]);
    }
}
