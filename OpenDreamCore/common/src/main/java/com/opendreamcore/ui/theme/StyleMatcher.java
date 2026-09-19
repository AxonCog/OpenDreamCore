package com.opendreamcore.ui.theme;

import java.util.List;

/**
 * 选择器的链式匹配。祖先链从父节点向根排列（index 0 = 父节点），组合符语义：
 *   空白  = 后代（祖先链上任意位置）
 *   ">"   = 直接子代（必须命中紧邻的父节点）
 */
public final class StyleMatcher {

    private StyleMatcher() {
    }

    /**
     * 判断选择器是否命中。
     *
     * selector：解析后的选择器
     * self：元素自身投影
     * ancestors：祖先投影，index 0 = 父节点，向根递增；可为空列表
     * ignoreStates：true = 编译期结构匹配（伪类不参与判定，
     *                     命中的带状态规则进状态覆盖层）；false = 运行时全量匹配
     */
    public static boolean matches(StyleSelector selector,
                                  StyleNode self,
                                  List<StyleNode> ancestors,
                                  boolean ignoreStates) {
        int count = selector.compoundCount();
        // 右端复合段必须命中自身
        if (!selector.compounds().get(count - 1).matches(self, ignoreStates)) {
            return false;
        }
        return matchChain(selector, count - 2, ancestors, 0, ignoreStates);
    }

    /** 编译期结构匹配的便捷重载。 */
    public static boolean matches(StyleSelector selector,
                                  StyleNode self,
                                  List<StyleNode> ancestors) {
        return matches(selector, self, ancestors, false);
    }

    /**
     * 递归匹配左侧剩余复合段。
     *
     * compoundIdx：当前要匹配的复合段下标（向左递减）；-1 表示全部完成
     * ancestorIdx：下一个可用的祖先下标
     */
    private static boolean matchChain(StyleSelector selector,
                                      int compoundIdx,
                                      List<StyleNode> ancestors,
                                      int ancestorIdx,
                                      boolean ignoreStates) {
        if (compoundIdx < 0) {
            return true;
        }
        var compound = selector.compounds().get(compoundIdx);
        boolean childLink = selector.isChildLink(compoundIdx);

        if (childLink) {
            // 直接子代：必须命中祖先链上固定位置的那个节点（紧邻语义，不允许跳位）
            if (ancestorIdx >= ancestors.size()) {
                return false;
            }
            if (compound.matches(ancestors.get(ancestorIdx), ignoreStates)) {
                return matchChain(selector, compoundIdx - 1, ancestors, ancestorIdx + 1, ignoreStates);
            }
            return false;
        }
        for (int i = ancestorIdx; i < ancestors.size(); i++) {
            if (compound.matches(ancestors.get(i), ignoreStates)
                    && matchChain(selector, compoundIdx - 1, ancestors, i + 1, ignoreStates)) {
                return true;
            }
        }
        return false;
    }

}
