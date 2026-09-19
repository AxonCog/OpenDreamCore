package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 样式选择器。
 *
 * 支持：
 *   text                    tag
 *   .card                   class
 *   #submit                 id
 *   button:hover            tag + 伪类状态
 *   .card > .title          直接子代
 *   .modal .footer button   后代（任意层级）
 *   text, label             由 ThemeParser 按逗号拆成多条规则，本类不处理逗号
 *
 * 优先级（specificity）：#id=100、.class/:伪类=10、tag=1，逐段累加。
 */
public final class StyleSelector {

    /** 单个简单选择器：tag / .class / #id，可携带若干 :伪类。 */
    public record Simple(Kind kind, String value, Set<String> pseudos) {

        public enum Kind {TAG, CLASS, ID}

        int specificity() {
            int base = switch (kind) {
                case ID -> 100;
                case CLASS -> 10;
                case TAG -> 1;
            };
            return base + pseudos.size() * 10;
        }

        boolean matches(StyleNode node, boolean ignoreStates) {
            boolean ok = switch (kind) {
                case TAG -> node.type().equalsIgnoreCase(value);
                case CLASS -> node.classes().contains(value);
                case ID -> node.id().equals(value);
            };
            if (!ok) {
                return false;
            }
            if (ignoreStates) {
                // 编译期结构匹配：只验证形状，伪类留给客户端运行时判定
                return true;
            }
            for (String p : pseudos) {
                if (!node.states().contains(p)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** 复合段：同一元素上的多个 simple（如 button.card）。 */
    public record Compound(List<Simple> simples) {

        int specificity() {
            int sum = 0;
            for (Simple s : simples) {
                sum += s.specificity();
            }
            return sum;
        }

        boolean matches(StyleNode node, boolean ignoreStates) {
            for (Simple s : simples) {
                if (!s.matches(node, ignoreStates)) {
                    return false;
                }
            }
            return true;
        }

        /** 本段全部伪类（用于把带状态规则归入状态覆盖层）。 */
        Set<String> pseudos() {
            Set<String> out = new LinkedHashSet<>();
            for (Simple s : simples) {
                out.addAll(s.pseudos());
            }
            return out;
        }
    }

    private final List<Compound> compounds; // 从左到右
    private final List<Boolean> childLinks; // childLinks[i] = compounds[i] 与 i+1 之间是否为 ">"
    private final int specificity;
    private final Set<String> stateKey;     // 右端段伪类集合（空 = 无状态基础规则）
    private final String original;

    private StyleSelector(List<Compound> compounds, List<Boolean> childLinks, String original) {
        this.compounds = Collections.unmodifiableList(compounds);
        this.childLinks = Collections.unmodifiableList(childLinks);
        this.original = original;
        int spec = 0;
        for (Compound c : compounds) {
            spec += c.specificity();
        }
        this.specificity = spec;
        this.stateKey = compounds.isEmpty()
                ? J8.set()
                : Collections.unmodifiableSet(compounds.get(compounds.size() - 1).pseudos());
    }

    /** 解析；失败返回 null（调用方 warn 跳过该条，不中断整表加载）。 */
    public static StyleSelector parse(String raw) {
        if (raw == null || J8.isBlank(raw)) {
            return null;
        }
        List<Compound> compounds = new ArrayList<>();
        List<String> tokens = tokenize(raw);
        boolean expectChild = false;
        for (String token : tokens) {
            if (token.equals(">")) {
                if (compounds.isEmpty()) {
                    return null;
                }
                expectChild = true;
                continue;
            }
            Compound compound = parseCompound(token);
            if (compound == null || compound.simples().isEmpty()) {
                return null;
            }
            compounds.add(compound);
            expectChild = false;
        }
        if (expectChild || compounds.isEmpty()) {
            return null;
        }
        // 回放 token 序列重建连接符：遇到 > 标记下一相邻段为直接子代
        List<Boolean> childLinks = new ArrayList<>();
        boolean pendingChild = false;
        int seen = 0;
        for (String token : tokens) {
            if (token.equals(">")) {
                pendingChild = true;
                continue;
            }
            if (seen > 0) {
                childLinks.add(pendingChild);
            }
            pendingChild = false;
            seen++;
        }
        return new StyleSelector(compounds, childLinks, raw.trim());
    }

    /** compounds[i] 与 i+1 之间是否为直接子代连接（">"）；越界返回 false。 */
    public boolean isChildLink(int index) {
        return index >= 0 && index < childLinks.size() && childLinks.get(index);
    }

    /** 切分：">" 独立成 token，其余按空白切；双引号内不切。 */
    private static List<String> tokenize(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                cur.append(c);
                continue;
            }
            if (c == '>') {
                if (cur.length() != 0) {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                }
                out.add(">");
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (cur.length() != 0) {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() != 0) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    /**
     * 解析复合段："button.card:hover" → [TAG(button,伪类hover), CLASS(card)]。
     * 伪类始终挂到它紧邻前面的那个 simple 上。
     */
    private static Compound parseCompound(String token) {
        List<Simple> simples = new ArrayList<>();
        Set<String> pendingPseudos = new LinkedHashSet<>();
        StringBuilder name = new StringBuilder();
        Simple.Kind kind = Simple.Kind.TAG;

        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            switch (c) {
                case '.', '#' -> {
                    // 新 simple 开始：先结算悬空伪类，再提交名字缓冲
                    if (!attachPending(simples, pendingPseudos)) {
                        return null;
                    }
                    flushName(simples, name, kind);
                    kind = c == '.' ? Simple.Kind.CLASS : Simple.Kind.ID;
                }
                case ':' -> {
                    flushName(simples, name, kind);
                    int j = i + 1;
                    while (j < token.length() && isNameChar(token.charAt(j))) {
                        j++;
                    }
                    if (j == i + 1) {
                        return null; // 空伪类名
                    }
                    String pseudo = token.substring(i + 1, j).toLowerCase(Locale.ROOT);
                    if (!isValidState(pseudo)) {
                        return null; // 未知状态字符已在 isNameChar 拦截，这里防御空段
                    }
                    pendingPseudos.add(pseudo);
                    i = j - 1;
                }
                default -> {
                    if (!isNameChar(c)) {
                        return null;
                    }
                    name.append(c);
                }
            }
        }
        if (!attachPending(simples, pendingPseudos)) {
            return null;
        }
        flushName(simples, name, kind);
        if (simples.isEmpty()) {
            return null;
        }
        return new Compound(J8.listCopy(simples));
    }

    /** 把悬空伪类合并到上一个 simple；没有可挂对象时视为非法。 */
    private static boolean attachPending(List<Simple> simples, Set<String> pending) {
        if (pending.isEmpty()) {
            return true;
        }
        if (simples.isEmpty()) {
            return false;
        }
        Simple last = simples.remove(simples.size() - 1);
        Set<String> merged = new LinkedHashSet<>(last.pseudos());
        merged.addAll(pending);
        simples.add(new Simple(last.kind(), last.value(), J8.setCopy(merged)));
        pending.clear();
        return true;
    }

    private static void flushName(List<Simple> simples, StringBuilder name, Simple.Kind kind) {
        if (name.length() != 0) {
            simples.add(new Simple(kind, name.toString(), J8.set()));
            name.setLength(0);
        }
    }

    private static boolean isValidState(String s) {
        return !s.isEmpty();
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    public List<Compound> compounds() {
        return compounds;
    }

    public int compoundCount() {
        return compounds.size();
    }

    public int specificity() {
        return specificity;
    }

    /** 右端段伪类集合；空集合表示无状态基础规则。 */
    public Set<String> stateKey() {
        return stateKey;
    }

    public boolean hasState() {
        return !stateKey.isEmpty();
    }

    /** 最右复合段的桶键（供主题索引快速圈定候选）：如 "tag:text"、"class:card"。 */
    public String bucketKey() {
        if (compounds.isEmpty()) {
            return "*";
        }
        Compound last = compounds.get(compounds.size() - 1);
        for (Simple s : last.simples()) {
            return s.kind().name().toLowerCase(Locale.ROOT) + ":" + s.value.toLowerCase(Locale.ROOT);
        }
        return "*";
    }

    @Override
    public String toString() {
        return original;
    }
}
