package com.opendreamcore.page;

import com.opendreamcore.util.J8;

/**
 * match 触发规则：页面靠 match 匹配界面触发（类型或标题）。
 * 例：hud / 菜单 / minecraft:chest / "chest:菜单" / inventory / player
 */
public final class Match {

    private final String target;
    private final int priority;
    private final String when;

    public Match(String target, int priority, String when) {
        if (target == null || J8.isBlank(target)) {
            throw new IllegalArgumentException("match 目标不能为空");
        }
        this.target = target.trim();
        this.priority = priority;
        this.when = when;
    }

    public Match(String target) {
        this(target, 0, null);
    }

    public String target() {
        return target;
    }

    public int priority() {
        return priority;
    }

    /** 可选表达式条件，进一步限定匹配。 */
    public String when() {
        return when;
    }
}
