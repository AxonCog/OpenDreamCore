package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 状态补丁编解码（整数/小数/布尔/字符串/null 类型保持）。
 */
class StatePatchTest {

    @Test
    void roundTripAllTypes() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("coin", 100L);
        values.put("rate", 0.5);
        values.put("alive", true);
        values.put("name", "商店");
        values.put("nothing", null);
        StatePatch original = new StatePatch("abc123", values);

        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        StatePatch decoded = StatePatch.decode(new OdcByteArrayBuf(buf.toByteArray()));

        assertEquals("abc123", decoded.sessionId());
        assertEquals(5, decoded.values().size());
        assertEquals(100L, decoded.values().get("coin"));
        assertEquals(0.5, decoded.values().get("rate"));
        assertEquals(true, decoded.values().get("alive"));
        assertEquals("商店", decoded.values().get("name"));
        assertNull(decoded.values().get("nothing"));
    }
}
