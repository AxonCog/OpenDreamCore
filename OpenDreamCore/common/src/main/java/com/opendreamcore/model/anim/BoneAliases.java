package com.opendreamcore.model.anim;

import com.opendreamcore.model.bake.BakedRig;

import java.util.Locale;
import java.util.Map;

/**
 * 骨骼名别名层：动画里的骨骼名 → 模型骨架上的骨。
 *
 * <p>各家的命名习惯不一样（left_fore_arm / leftForeArm / leftarm 说的都是同一根前臂），
 * 先把名字洗成统一形态（小写、去下划线空格连字符），再过一张人工确认过的同义词表
 * （hat 就是 head、torso 就是 body），最后到骨架里找。洗完直接命中的占绝大多数，
 * 同义词表只收真正常见的叫法，不猜——查不到就返回缺失，调用方跳过这个通道，
 * 跟 bbmodel 的语义一致：模型没这根骨，动画里那一段当没写。
 */
public final class BoneAliases {

    /** 少数不洗名也对不上的同义词。 */
    private static final Map<String, String> ALIASES = new java.util.LinkedHashMap<String, String>();

    static {
        ALIASES.put("hat", "head");
        ALIASES.put("torso", "body");
        ALIASES.put("leftitem", "rightitem");
    }

    private BoneAliases() {
    }

    /** 统一形态：小写、去下划线/空格/连字符。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '_' && c != ' ' && c != '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 规范化 + 同义词表后的最终形态。 */
    public static String canonical(String raw) {
        String n = normalize(raw);
        String alias = ALIASES.get(n);
        return alias == null ? n : alias;
    }

    /**
     * 在骨架里找动画写的骨骼名；命中返回骨下标，没有返回 -1（跳过，不报错）。
     */
    public static int resolve(BakedRig rig, String rawName) {
        if (rig == null || rawName == null) {
            return -1;
        }
        String want = canonical(rawName);
        if (want.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < rig.bones.length; i++) {
            if (rig.bones[i] != null && canonical(rig.bones[i].name).equals(want)) {
                return i;
            }
        }
        return -1;
    }
}
