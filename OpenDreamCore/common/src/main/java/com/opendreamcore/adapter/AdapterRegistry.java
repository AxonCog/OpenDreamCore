package com.opendreamcore.adapter;

import com.opendreamcore.config.ConfigParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语法适配器注册中心。
 * 管理所有已注册的 ConfigParser，按内容自动检测格式并路由到正确的解析器。
 * 附属模组调用 {@link #register} 即可接入自定义语法，无需改动核心代码。
 */
public final class AdapterRegistry {

    private static final Map<String, ConfigParser> PARSERS = new ConcurrentHashMap<>();

    private AdapterRegistry() {
    }

    /** 注册语法解析器（format() 返回值作为 id）。 */
    public static void register(ConfigParser parser) {
        PARSERS.put(parser.format(), parser);
    }

    /** 按 format id 取解析器。 */
    public static ConfigParser get(String format) {
        return PARSERS.get(format);
    }

    /** 所有已注册的解析器。 */
    public static List<ConfigParser> all() {
        return new ArrayList<>(PARSERS.values());
    }

    /**
     * 自动检测：遍历所有解析器，让每个解析器判断文本是否属于自己的格式。
     * 实现 {@link SelfDetecting} 接口的解析器优先；否则返回第一个能解析的。
     */
    public static ConfigParser detect(String text) {
        bootstrap();
        for (ConfigParser p : PARSERS.values()) {
            if (p instanceof SelfDetecting sd && sd.detects(text)) {
                return p;
            }
        }
        return null;
    }

    /** 首次检测前确保内置解析器完成自注册（Class.forName 触发 static 块，缺失则静默跳过）。 */
    private static volatile boolean bootstrapped;

    private static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (AdapterRegistry.class) {
            if (bootstrapped) {
                return;
            }
            for (String cn : new String[]{"com.opendreamcore.adapter.dreamcore.DreamCoreParser"}) {
                try {
                    Class.forName(cn);
                } catch (ClassNotFoundException ignored) {
                    // 可选适配器不在 classpath 上：正常
                } catch (Throwable ignored) {
                    // 初始化失败不拖垮默认管线
                }
            }
            bootstrapped = true;
        }
    }

    /** 支持自动格式检测的解析器实现此接口。 */
    public interface SelfDetecting {
        boolean detects(String text);
    }
}
