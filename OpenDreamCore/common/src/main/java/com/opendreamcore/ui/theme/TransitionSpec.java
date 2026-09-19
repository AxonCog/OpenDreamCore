package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 单条过渡声明，形如 transition: "background 0.25 QUAD_OUT 0.1, opacity 0.3"：
 * 每一段 = 属性名 时长秒 [缓动] [延迟秒]。任何来源的属性变化（内联改值、
 * 服务端推送、状态切换、显隐切换）都按声明自动插值，业务侧零动画代码。
 */
public record TransitionSpec(String property, float duration, Ease ease, float delay) {

    /** 解析整条 transition 声明；单段语法错误跳过该段（不中断其余段）。 */
    public static List<TransitionSpec> parseAll(String raw) {
        List<TransitionSpec> out = new ArrayList<>();
        if (raw == null || J8.isBlank(raw)) {
            return out;
        }
        for (String piece : raw.split(",")) {
            TransitionSpec spec = parseOne(piece);
            if (spec != null) {
                out.add(spec);
            }
        }
        return out;
    }

    /** 解析单段："属性 时长 [缓动] [延迟]"。 */
    public static TransitionSpec parseOne(String piece) {
        if (piece == null) {
            return null;
        }
        String[] tokens = piece.trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        try {
            float duration = Float.parseFloat(tokens[1]);
            if (duration < 0) {
                return null;
            }
            Ease ease = tokens.length >= 3 ? Ease.byName(tokens[2]) : Ease.LINEAR;
            // 第三段若不是已知缓动名也回退 LINEAR（byName 已处理），第四段为延迟
            float delay = tokens.length >= 4 ? Float.parseFloat(tokens[3]) : 0f;
            if (delay < 0) {
                delay = 0;
            }
            return new TransitionSpec(tokens[0].trim(), duration, ease, delay);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从列表中取某属性的过渡声明；无则 null。 */
    public static TransitionSpec find(List<TransitionSpec> specs, String property) {
        if (specs == null || property == null) {
            return null;
        }
        String key = property.toLowerCase(Locale.ROOT);
        for (TransitionSpec s : specs) {
            if (s.property().toLowerCase(Locale.ROOT).equals(key)) {
                return s;
            }
        }
        return null;
    }

    public TransitionSpec {
        Objects.requireNonNull(property, "property");
        property = property.intern();
    }
}
