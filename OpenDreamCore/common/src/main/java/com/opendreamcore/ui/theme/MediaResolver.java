package com.opendreamcore.ui.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每次布局重算时跑一遍：把当前窗口尺寸下命中的媒体覆盖层
 * 按级联顺序（specificity、声明序）合并进元素属性表。
 * 敢写进 @块 就是敢覆盖：这里优先级压过普通规则和内联样式。
 * state_patch 的写入发生得更晚，不受影响。
 */
public final class MediaResolver {

    private MediaResolver() {
    }

    /**
     * 把命中的媒体覆盖合并进 target（就地修改）。
     *
     * overrides：元素携带的全部媒体覆盖层
     * windowWidth：当前窗口宽（像素）
     * windowHeight：当前窗口高（像素）
     * target：待渲染的属性表
     */
    public static void mergeActive(List<ElementStyle.MediaOverride> overrides,
                                   double windowWidth, double windowHeight,
                                   Map<String, Object> target) {
        if (overrides == null || overrides.isEmpty() || target == null) {
            return;
        }
        // 覆盖层列表本身已按 (specificity, order) 升序存放，顺序 putAll 即"后者胜"
        for (ElementStyle.MediaOverride mo : overrides) {
            if (mo.media().matches(windowWidth, windowHeight)) {
                target.putAll(mo.props());
            }
        }
    }

    /** 排序用比较器：specificity 升序 → order 升序。 */
    public static int compare(ElementStyle.MediaOverride a, ElementStyle.MediaOverride b) {
        int bySpec = Integer.compare(a.specificity(), b.specificity());
        return bySpec != 0 ? bySpec : Integer.compare(a.order(), b.order());
    }

    /** 防御性排序工具（ThemeApplier 构建覆盖层时使用）。 */
    public static List<ElementStyle.MediaOverride> sorted(List<ElementStyle.MediaOverride> in) {
        List<ElementStyle.MediaOverride> out = new ArrayList<>(in);
        out.sort(MediaResolver::compare);
        return out;
    }
}
