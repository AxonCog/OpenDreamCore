package com.opendreamcore.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符注册表：{分类.键} 语法，分类（player/entity/item/color/query/system...）各自注册解析器。
 * 客户端注册 MC 实现（当前玩家/准星实体/手持物品/窗口尺寸），服务端注册 Bukkit 实现（按接收玩家）。
 * 已知占位符替换，未知保留原样（避免误伤普通文本）。
 * 与 {{vars.xxx}} 变量插值互补：{{}} 是页面变量，{} 是占位符。
 */
public final class PlaceholderRegistry {

    /** 分类解析器：返回 null 表示该键无法解析（保留原文）。 */
    public interface Resolver {
        Object resolve(String key);
    }

    private static final Map<String, Resolver> REGISTRY = new ConcurrentHashMap<>();
    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z_]+)\\.([a-zA-Z0-9_]+)}");

    private PlaceholderRegistry() {
    }

    public static void register(String category, Resolver resolver) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("占位符分类不能为空");
        }
        REGISTRY.put(category, resolver);
    }

    public static void unregister(String category) {
        REGISTRY.remove(category);
    }

    /** 解析单键（未知分类/键 → null）。 */
    public static Object resolveOne(String category, String key) {
        Resolver resolver = REGISTRY.get(category);
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.resolve(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** 替换文本里的全部已知占位符；未知的保留原文。 */
    public static String resolve(String text) {
        if (text == null || text.isEmpty() || text.indexOf('{') < 0) {
            return text;
        }
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String category = matcher.group(1);
            String key = matcher.group(2);
            Object value = resolveOne(category, key);
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(value == null ? matcher.group() : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 是否已注册某分类。 */
    public static boolean has(String category) {
        return REGISTRY.containsKey(category);
    }
}
