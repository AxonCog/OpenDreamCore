package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.HashSet;
import java.util.Set;

/**
 * 选择器眼中的元素：类型/id/class/状态 四元组。
 * 编译期 states 为空集（运行时状态客户端才知道），客户端重匹配时填真实状态。
 */
public record StyleNode(String type, String id, Set<String> classes, Set<String> states) {

    public StyleNode {
        classes = classes == null ? J8.set() : J8.setCopy(classes);
        states = states == null ? J8.set() : J8.setCopy(states);
    }

    /** 编译期投影（无运行时状态）。 */
    public static StyleNode compileTime(String type, String id, Iterable<String> classes) {
        Set<String> cls = new HashSet<>();
        if (classes != null) {
            for (String c : classes) {
                cls.add(c);
            }
        }
        return new StyleNode(type, id, cls, J8.set());
    }
}
