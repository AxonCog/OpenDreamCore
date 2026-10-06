package com.opendreamcore.model.format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享层自带的最小 JSON 解析器（纯 Java 8，无 MC 依赖）。
 *
 * 只服务模型格式（bbmodel / geo.json）解析：值域 = object / array / string / number / boolean / null。
 * 设计取舍：不做转义性能优化（模型文件一次性解析后进 IR）；容错策略为「尽力而为 + 行号报错」。
 */
public final class JsonParser {

    private final String src;
    private int pos;
    private int line = 1;

    private JsonParser(String src) {
        this.src = src;
    }

    /** 解析入口；根必须是 object 或 array。失败抛 IllegalArgumentException（带行号）。 */
    public static Object parse(String text) {
        JsonParser p = new JsonParser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos < p.src.length()) {
            throw p.err("JSON 末尾有多余内容");
        }
        return v;
    }

    private Object value() {
        if (pos >= src.length()) {
            throw err("JSON 提前结束");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++; // {
        ws();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw err("对象键必须是字符串");
            }
            String key = string();
            ws();
            if (peek() != ':') {
                throw err("对象缺少冒号");
            }
            pos++;
            ws();
            map.put(key, value());
            ws();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return map;
            } else {
                throw err("对象内应为 , 或 }");
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        pos++; // [
        ws();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            ws();
            list.add(value());
            ws();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return list;
            } else {
                throw err("数组内应为 , 或 ]");
            }
        }
    }

    private String string() {
        pos++; // "
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw err("字符串未闭合");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = src.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw err("未知转义 \\" + e);
                }
            } else {
                if (c == '\n') {
                    line++;
                }
                sb.append(c);
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw err("无法识别的值: " + src.charAt(pos));
        }
        try {
            return Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw err("数字格式错误: " + src.substring(start, pos));
        }
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) {
            throw err("期望 " + word);
        }
        pos += word.length();
    }

    private char peek() {
        if (pos >= src.length()) {
            throw err("JSON 提前结束");
        }
        return src.charAt(pos);
    }

    private void ws() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("JSON 行 " + line + ": " + msg);
    }
}
