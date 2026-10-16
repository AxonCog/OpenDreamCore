package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 自动绑定：实体状态 → 动画。
 *
 *  - 状态字典（bedrock/原版生态全量）：实体真实状态命中哪个状态名就播同名动画；
 *  - 回退链：state → walk → idle → null（模型只有 idle 也不会哑）；
 *  - 规则覆盖：anim_xxx: yyy 把状态 xxx 指到动画 yyy（覆盖优先于约定）；
 *  - 纯函数无状态（AnimationIR 查找由调用方传"动画名→IR"解析器）。
 */
public final class AutoBind {

    /** 状态字典（全量，含原版 1.19+ 生物动画命名集）。 */
    public static final String[] STATES = {
            "idle", "walk", "run", "sprint", "swim", "fly", "jump", "sneak", "crawl",
            "sit", "sleep", "climb", "attack", "hurt", "death", "eat", "drink", "breed",
            "charge", "dig", "emerge", "roar", "sniff", "look_at_target", "dance",
            "dash", "roll", "spin", "wave", "shoot", "bow", "crossbow_hold",
            "crossbow_attack", "cast", "summon", "idle_body", "move", "swing",
    };

    private AutoBind() {
    }

    /** 是否字典状态。 */
    public static boolean knownState(String state) {
        for (String s : STATES) {
            if (s.equals(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 挑动画。
     *
     * @param state     实体当前状态（walk/idle/...；null=未知）
     * @param overrides 规则级覆盖（状态→动画名，可 null）
     * @param resolver  动画名 → IR（可返回 null 表示该名字不存在）
     * @return 命中的动画（回退链全空返回 null）
     */
    public static AnimationIR pick(String state, Map<String, String> overrides,
                                   NameResolver resolver) {
        if (resolver == null) {
            return null;
        }
        String s = state == null ? null : state.toLowerCase(java.util.Locale.ROOT);
        // 1) 覆盖映射（state 与 state 的别名 run→sprint 等由调用方归一）
        if (s != null && overrides != null) {
            String name = overrides.get(s);
            if (name != null) {
                AnimationIR ir = resolver.resolve(name);
                if (ir != null) {
                    return ir;
                }
            }
        }
        // 2) 名字约定 + 回退链
        for (String candidate : fallbackChain(s)) {
            AnimationIR ir = resolver.resolve(candidate);
            if (ir != null) {
                return ir;
            }
        }
        return null;
    }

    /** 回退链：state → walk → idle（state 本身已知则从自身开始）。 */
    public static Iterable<String> fallbackChain(String state) {
        Set<String> chain = new LinkedHashSet<String>();
        if (state != null && knownState(state)) {
            chain.add(state);
        }
        chain.add("walk");
        chain.add("idle");
        return chain;
    }

    /** 动画名解析器（调用方接 ModelAssetStore/动画池）。 */
    public interface NameResolver {
        AnimationIR resolve(String name);
    }

    /** 便捷：动画列表 → 名字表（重名后写覆盖先写）。 */
    public static Map<String, AnimationIR> byName(Collection<AnimationIR> anims) {
        Map<String, AnimationIR> out = new HashMap<String, AnimationIR>();
        if (anims != null) {
            for (AnimationIR a : anims) {
                out.put(a.name, a);
            }
        }
        return out;
    }
}
