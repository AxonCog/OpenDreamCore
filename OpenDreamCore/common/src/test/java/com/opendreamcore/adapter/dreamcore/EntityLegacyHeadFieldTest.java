package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.adapter.dreamcore.methods.EntityLegacy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 线C②：头顶语境实体桩布线——方法.取实体血量() 等经
 * EntityLegacy.headField → HostExt（client 安装，读 VisualNameTags.CURRENT_ENTITY）。
 * 这里验证静态路由契约：安装后按 field 名取值，未实现的字段回退 null。
 */
class EntityLegacyHeadFieldTest {

    @AfterAll
    static void reset() {
        // 还原空语境宿主，避免污染其他测试的全局注册状态
        EntityLegacy.installHost(new EntityLegacy.HostExt() {
            @Override public Object aimedEntity() { return null; }
            @Override public String field(String f) { return ""; }
            @Override public Object nearby(String type, double range) { return java.util.List.of(); }
        });
    }

    @Test
    void headFieldRoutesThroughInstalledHost() {
        EntityLegacy.installHost(new EntityLegacy.HostExt() {
            @Override public Object aimedEntity() { return null; }
            @Override public String field(String f) { return ""; }
            @Override public Object nearby(String type, double range) { return java.util.List.of(); }
            @Override public Object headField(String f) {
                if ("health".equals(f)) {
                    return 8.0;
                }
                if ("ratio".equals(f)) {
                    return 0.5;
                }
                return null;
            }
        });
        assertEquals(8.0, EntityLegacy.headField("health"));
        assertEquals(0.5, EntityLegacy.headField("ratio"));
        assertNull(EntityLegacy.headField("height"), "宿主未实现的字段回退 null");
    }
}
