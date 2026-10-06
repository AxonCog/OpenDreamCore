package com.opendreamcore.core.descriptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 一个已注册系统：元信息 + 实现 + 上次已生效的规则集（用来算差异）。 */
public final class RegisteredSystem {

    private static final Logger LOGGER = Logger.getLogger(RegisteredSystem.class.getName());

    private final SystemDescriptor descriptor;
    private final SystemImpl impl;
    private Map<String, Map<String, Object>> lastRules = Collections.emptyMap();

    public RegisteredSystem(SystemDescriptor descriptor, SystemImpl impl) {
        this.descriptor = descriptor;
        this.impl = impl;
    }

    public SystemDescriptor descriptor() {
        return descriptor;
    }

    public SystemImpl impl() {
        return impl;
    }

    public Map<String, Map<String, Object>> lastRules() {
        return lastRules;
    }

    /**
     * 应用一份完整规则集：先 revoke 消失的，再 apply 新增/变更的。
     *
     * <p>热重载语义：{@code hotReload=false} 的系统只在**首次**生效，之后 reload 不再重复 apply
     * —— 这是有意的（不支持热重载的系统必须重启才变，而不是「改了没反应也不报错」）。
     *
     * <p>单条规则的异常被吞掉并记 WARN（坏规则不影响其它规则）。
     */
    public void applyRules(Map<String, Map<String, Object>> rules) {
        Map<String, Map<String, Object>> next = normalize(rules);
        Diff diff = Diff.of(lastRules, next);
        for (Map.Entry<String, Map<String, Object>> e : diff.removed().entrySet()) {
            try {
                impl.revoke(e.getKey());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 撤销规则失败 系统=" + descriptor.id()
                        + " 规则=" + e.getKey(), t);
            }
        }
        boolean first = lastRules.isEmpty();
        if (descriptor.hotReload() || first) {
            for (Map.Entry<String, Map<String, Object>> e : diff.addedOrChanged().entrySet()) {
                try {
                    impl.apply(e.getKey(), Collections.unmodifiableMap(e.getValue()));
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 应用规则失败 系统=" + descriptor.id()
                            + " 规则=" + e.getKey(), t);
                }
            }
        }
        lastRules = next;
    }

    /**
     * 规整规则集：把「规则名：」后面没内容的 null 取值变成空表（flag 式规则是合法配置），
     * 丢掉 key 为 null 的脏条目。不规整的话下游 {@code Collections.unmodifiableMap(null)}
     * 会抛 NPE，被 catch 吞成一条 WARN —— 表现就是「改了没反应也不报错」。
     */
    private static Map<String, Map<String, Object>> normalize(Map<String, Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> cleaned =
                new LinkedHashMap<String, Map<String, Object>>(rules.size() * 2);
        for (Map.Entry<String, Map<String, Object>> e : rules.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            cleaned.put(e.getKey(), e.getValue() == null
                    ? Collections.<String, Object>emptyMap() : e.getValue());
        }
        return cleaned;
    }
}
