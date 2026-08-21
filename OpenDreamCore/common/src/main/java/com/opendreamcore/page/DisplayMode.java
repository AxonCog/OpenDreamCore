package com.opendreamcore.page;

import java.util.ArrayList;
import java.util.List;

/**
 * 显示模式声明（不写时按 match 推断）。
 */
public enum DisplayMode {
    SCREEN("screen"),
    HUD("hud"),
    WORLD("world"),
    CONTAINER("container");

    private final String id;

    DisplayMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DisplayMode byId(String id) {
        for (DisplayMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知显示模式: " + id);
    }
}
