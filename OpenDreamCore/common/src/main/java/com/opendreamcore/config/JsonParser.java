package com.opendreamcore.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置 JSON 解析器（换格式不碰核心的示例实现）。
 */
public final class JsonParser implements ConfigParser {

    private static final Gson GSON = new Gson();

    @Override
    public String format() {
        return "json";
    }

    @Override
    public Map<String, Object> parse(String text) throws ConfigParseException {
        try {
            JsonElement root = com.google.gson.JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                throw new ConfigParseException("配置根必须是对象", 1, 1);
            }
            return convert(root.getAsJsonObject());
        } catch (JsonParseException e) {
            throw new ConfigParseException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> convert(JsonObject obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        obj.entrySet().forEach(entry -> map.put(entry.getKey(), convert(entry.getValue())));
        return map;
    }

    private static Object convert(JsonElement el) {
        if (el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber()) {
                Number n = p.getAsNumber();
                // 三元表达式会把 long 提升成 double，这里显式分支
                if (n.doubleValue() == n.longValue()) {
                    return n.longValue();
                }
                return n.doubleValue();
            }
            return p.getAsString();
        }
        if (el.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            el.getAsJsonArray().forEach(item -> list.add(convert(item)));
            return list;
        }
        return convert(el.getAsJsonObject());
    }
}
