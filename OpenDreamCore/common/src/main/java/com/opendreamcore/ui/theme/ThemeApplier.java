package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 主题应用器。
 *
 * 在 PageSchema 建完元素树之后运行：此时嵌套 children 与扁平 parent 已归一为
 * 同一个 Element 模型（children 列表 + parent 链接），本类只面向统一树工作，
 * 对页面书写语法无感知。
 *
 * 级联规则：
 *   元素内联 props（含内联 style 块展开）> 高权重规则 > 低权重规则 > 组件默认值
 * 规则只填充内联缺失的键，内联值永远不被覆盖。
 * 带状态伪类的规则不参与基础合并，归入状态覆盖层随页面下发，
 * 由客户端在运行时按 hover/pressed/disabled/focus 状态取用。
 */
public final class ThemeApplier {

    private ThemeApplier() {
    }

    /** 按声明顺序依次应用多个主题；后面的主题只填充前面没填到的键。 */
    public static void apply(Page page, List<Theme> themes) {
        if (page == null) {
            return;
        }
        Map<String, Element> byId = new LinkedHashMap<>();
        for (Element root : page.elements()) {
            collect(root, byId);
        }
        List<Theme> valid = new ArrayList<>();
        if (themes != null) {
            for (Theme t : themes) {
                if (t != null && !t.rules().isEmpty()) {
                    valid.add(t);
                }
            }
        }
        // 无有效主题也要走一遍元素处理：内联 transition 声明的结构化提取不依赖主题
        for (Element element : page.elements()) {
            for (Theme theme : valid) {
                applyToSubtree(element, theme, byId);
            }
            extractInlineStyle(element);
        }
    }

    public static void apply(Page page, Theme theme) {
        apply(page, J8.list(theme));
    }

    // 收集与投影

    private static void collect(Element element, Map<String, Element> out) {
        if (element == null) {
            return;
        }
        out.putIfAbsent(element.id(), element);
        for (Element child : element.children()) {
            // 嵌套子元素的 parent 链接已在 PageSchema 建树时写入（inheritedParent）
            collect(child, out);
        }
    }

    private static void applyToSubtree(Element element, Theme theme, Map<String, Element> byId) {
        applyOne(element, theme, byId);
        for (Element child : element.children()) {
            applyToSubtree(child, theme, byId);
        }
    }
    private static void applyOne(Element element, Theme theme, Map<String, Element> byId) {
        List<StyleNode> ancestors = ancestry(element, byId);
        StyleNode self = StyleNode.compileTime(element.type(), element.id(), element.classes());

        // 候选圈定：先桶索引缩小范围，再全链校验
        List<StyleRule> baseHits = new ArrayList<>();
        Map<String, List<StyleRule>> stateHits = new LinkedHashMap<>();
        List<ElementStyle.MediaOverride> mediaHits = new ArrayList<>();
        for (StyleRule rule : theme.candidates(self)) {
            // 编译期结构匹配：伪类不参与判定，命中的带状态规则进状态覆盖层
            boolean structuralMatch = StyleMatcher.matches(rule.selector(), self, ancestors, true);
            if (!structuralMatch) {
                continue;
            }
            if (rule.media() != null) {
                // 响应式规则：编译期不定生死，带着条件存成覆盖层，布局时按窗口尺寸现判现用
                mediaHits.add(new ElementStyle.MediaOverride(rule.media(),
                        castMap(ThemeVars.resolve(rule.declarations(), theme.vars())),
                        rule.specificity(), rule.order()));
            } else if (!rule.hasState()) {
                baseHits.add(rule);
            } else {
                String key = String.join("+", new TreeSet<>(rule.selector().stateKey()));
                stateHits.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            }
        }

        // 多主题叠加语义的基础：留住前序主题已写入的产物（状态覆盖/媒体覆盖/过渡）
        ElementStyle prevStyle = element.style();

        // 基础合并：升序遍历（低权重先写，高权重覆盖），最后只提交内联缺失的键
        baseHits.sort(RULE_ORDER);
        Map<String, Object> props = element.props();
        Map<String, Object> fills = new LinkedHashMap<>();
        for (StyleRule rule : baseHits) {
            for (Map.Entry<String, Object> d : rule.declarations().entrySet()) {
                fills.put(d.getKey(), d.getValue());
            }
        }
        for (Map.Entry<String, Object> e : fills.entrySet()) {
            if (!props.containsKey(e.getKey())) {
                props.put(e.getKey(), ThemeVars.resolve(e.getValue(), theme.vars()));
            }
        }

        // 状态覆盖层：同状态内再做一次升序级联
        Map<String, Map<String, Object>> overlays = new LinkedHashMap<>();
        for (Map.Entry<String, List<StyleRule>> st : stateHits.entrySet()) {
            List<StyleRule> rules = new ArrayList<>(st.getValue());
            rules.sort(RULE_ORDER);
            Map<String, Object> merged = new LinkedHashMap<>();
            for (StyleRule rule : rules) {
                merged.putAll(rule.declarations());
            }
            overlays.put(st.getKey(), castMap(ThemeVars.resolve(merged, theme.vars())));
        }
        if (prevStyle != null) {
            for (Map.Entry<String, Map<String, Object>> e : prevStyle.stateOverlays().entrySet()) {
                Map<String, Object> mine = overlays.get(e.getKey());
                if (mine == null) {
                    overlays.put(e.getKey(), e.getValue());       // 新主题没提的状态：沿用旧主题的
                } else {
                    Map<String, Object> both = new LinkedHashMap<>(e.getValue());
                    both.putAll(mine);                            // 都有：新主题赢
                    overlays.put(e.getKey(), both);
                }
            }
        }

        // 过渡声明：内联或主题声明的 transition 属性解析成结构化列表
        List<TransitionSpec> transitions = J8.list();
        Object rawTransition = props.get("transition");
        if (rawTransition instanceof String s && !J8.isBlank(s)) {
            transitions = TransitionSpec.parseAll(s);
        }

        // 响应式覆盖层：多主题/多次应用依次追加，统一按级联序排列
        java.util.List<ElementStyle.MediaOverride> allMedia = new ArrayList<>();
        if (prevStyle != null && !prevStyle.mediaOverrides().isEmpty()) {
            allMedia.addAll(prevStyle.mediaOverrides());
        }
        allMedia.addAll(mediaHits);
        allMedia.sort(MediaResolver::compare);

        element.setStyle(new ElementStyle(overlays, transitions, allMedia));
    }

    /** 祖先投影链：index 0 = 父节点，向根递增；parent 缺失/未知即止，环检测防死循环。 */
    private static List<StyleNode> ancestry(Element element, Map<String, Element> byId) {
        List<StyleNode> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(element.id());
        String cursor = element.parent();
        while (cursor != null && visited.add(cursor)) {
            Element parent = byId.get(cursor);
            if (parent == null) {
                break;
            }
            chain.add(StyleNode.compileTime(parent.type(), parent.id(), parent.classes()));
            cursor = parent.parent();
        }
        return chain;
    }

    /** 无主题时的内联样式提取（transition 结构化），保证行为与有主题时一致。 */
    private static void extractInlineStyle(Element element) {
        Object rawTransition = element.props().get("transition");
        if (rawTransition instanceof String s && !J8.isBlank(s)) {
            element.setStyle(new ElementStyle(J8.map(), TransitionSpec.parseAll(s)));
        }
    }

    private static final java.util.Comparator<StyleRule> RULE_ORDER =
            java.util.Comparator.comparingInt(StyleRule::specificity)
                    .thenComparingInt(StyleRule::order);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
