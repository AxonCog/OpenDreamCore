package com.opendreamcore.branding;

import java.util.List;

/**
 * 打字机标题序列器：逐字推进状态机。
 * 每 tick 喂当前毫秒，按 speed 推进可见字符数，句尾停留 hold 后切下一句；
 * loop=false 时走完即停。逐字推进，不依赖时间基准换算。
 */
public final class TypewriterSequencer {

    private final List<String> seq;
    private final boolean typewriter;
    private final boolean random;
    private final boolean loop;
    private final int speed;
    private final int hold;

    // 状态机游标：句号 / 已显示字符数 / 上次推进时刻 / 是否处于句尾等待
    private int index;
    private int shown;
    private long lastMs;
    private boolean waiting;
    private boolean started;

    public TypewriterSequencer(TitleConfig cfg) {
        this.seq = cfg.sequence();
        this.typewriter = !cfg.random && cfg.typewriter;
        this.random = cfg.random && this.seq.size() > 1;
        this.loop = cfg.loop;
        this.speed = Math.max(1, cfg.effectiveSpeed());
        this.hold = cfg.effectiveHoldMs();
    }

    /** 当前时刻应显示的窗口标题文本。 */
    public String tick(long nowMs) {
        if (seq.isEmpty()) {
            return "";
        }
        String full = seq.get(index % seq.size());
        if (!typewriter) {
            return full;
        }
        if (!started) {
            // 首帧立即亮出第一个字符
            started = true;
            lastMs = nowMs;
            shown = 1;
            return full.substring(0, Math.min(1, full.length()));
        }
        if (waiting) {
            if (nowMs - lastMs >= hold) {
                waiting = false;
                shown = 0;
                index++;
                if (!loop && index >= seq.size()) {
                    index = seq.size() - 1;
                    shown = seq.get(index).length();
                    return seq.get(index);
                }
                lastMs = nowMs;
            }
            return seq.get(Math.min(index, seq.size() - 1) % seq.size()).substring(0,
                    Math.min(shown, seq.get(Math.min(index, seq.size() - 1)).length()));
        }
        if (nowMs - lastMs >= speed) {
            lastMs = nowMs;
            if (shown < full.length()) {
                shown++;
            }
            if (shown >= full.length()) {
                waiting = true;
                lastMs = nowMs;
            }
        }
        return full.substring(0, Math.min(shown, full.length()));
    }

    /** 非循环模式是否已走完末句。 */
    public boolean isFinished(long nowMs) {
        return !random && !loop && index >= seq.size() - 1 && shown >= seq.get(index % seq.size()).length();
    }
}
