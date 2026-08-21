package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局状态消息编解码。
 */
class GlobalStateTest {

    @Test
    void roundTrip() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("online", 12L);
        values.put("server_name", "我的服务器");
        values.put("maintenance", false);
        GlobalState original = new GlobalState(values);

        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        GlobalState decoded = GlobalState.decode(new OdcByteArrayBuf(buf.toByteArray()));

        assertEquals(3, decoded.values().size());
        assertEquals(12L, decoded.values().get("online"));
        assertEquals("我的服务器", decoded.values().get("server_name"));
        assertEquals(false, decoded.values().get("maintenance"));
    }
}
