package com.opendreamcore.branding;

import java.util.List;

/**
 * 打字机标题序列器：逐字推进状态机。
 *
 * 调用契约是"每帧喂当前毫秒"。引擎内部按固定步长补帧——
 * 哪怕掉帧、休眠、隔了十分钟才喂，都能按时间差追上正确进度，
 * 不会出现"卡在某个字上不动"的尴尬。
 *
 * 三种模式：
 *   typewriter 逐字打字 + 句尾停留 hold 后切下一句；loop=false 走完定格
 *   轮播        非 random 的多句轮换，每 interval 换一句
 *   random      每 interval 随机选一句（同格确定性：同一格内恒定）
 */
public final class TypewriterSequencer {

    private final List<String> seq;
    private final boolean typewriter;
    private final boolean random;
    private final boolean loop;
    private final int speed;
    private final int hold;
    private final int interval;

    // 状态机游标：句号 / 已显示字符数 / 上次推进时刻 / 是否处于句尾等待
    private int index;
    private int shown;
    private long lastMs;
    private boolean waiting;
    private boolean started;

    /** 非循环模式走完末句后置位；isFinished 的前瞻推演用。 */
    private boolean finished;

    // 轮播/随机模式的时间格游标
    private long slotOrigin = Long.MIN_VALUE;
    private long lastSlot;

    public TypewriterSequencer(TitleConfig cfg) {
        this.seq = cfg.sequence();
        this.typewriter = !cfg.random && cfg.typewriter;
        this.random = cfg.random && this.seq.size() > 1;
        this.loop = cfg.loop;
        this.speed = Math.max(1, cfg.effectiveSpeed());
        this.hold = cfg.effectiveHoldMs();
        this.interval = Math.max(1, cfg.interval > 0 ? cfg.interval : 2000);
    }

    /** 当前时刻应显示的窗口标题文本。 */
    public String tick(long nowMs) {
        if (seq.isEmpty()) {
            return "";
        }
        if (!typewriter) {
            return rotate(nowMs);
        }
        advance(nowMs);
        return display();
    }

    /** 非循环模式是否已走完末句。无副作用：推演完把现场原样还回去。 */
    public boolean isFinished(long nowMs) {
        if (seq.isEmpty() || random || loop || !typewriter) {
            return false;
        }
        long sLast = lastMs; int sIdx = index, sShown = shown;
        boolean sWait = waiting, sStart = started, sFin = finished;
        try {
            advance(nowMs);
            return finished;
        } finally {
            lastMs = sLast; index = sIdx; shown = sShown;
            waiting = sWait; started = sStart; finished = sFin;
        }
    }

    // 打字机

    private void advance(long nowMs) {
        String full = cur();
        int len = full.length();

        if (!started) {
            started = true;
            lastMs = nowMs;
            shown = Math.min(1, len);          // 首帧亮出第一个字符
            if (!loop && seq.size() == 1 && shown >= len) {
                finished = true;               // 单句非循环：一句定档
            }
            waiting = false;
            return;
        }
        // 打字阶段：固定步长补帧。用 lastMs += speed 累加而不是 =nowMs，
        // 这样长间隔能一次追上多个字，速度也不因调用频率漂移。
        if (!waiting) {
            while (nowMs - lastMs >= speed && shown < len) {
                lastMs += speed;
                shown++;
            }
            if (shown >= len) {
                if (!loop && index >= seq.size() - 1) {
                    finished = true;           // 末句打完即视为完成
                }
                waiting = true;                // 停留期从打完那刻起算
            }
        }
        // 停留期检查放在同一趟里：打完与切句可能发生在同一次喂入（稀疏时间点）
        if (waiting && nowMs - lastMs >= hold) {
            waiting = false;
            lastMs = nowMs;
            index++;
            if (!loop && index >= seq.size()) {
                index = seq.size() - 1;
                shown = curLen();
                finished = true;               // 走完定格
            } else {
                shown = Math.min(1, curLen()); // 切句即亮首字，不留空拍
            }
        }
    }

    private String display() {
        return cur().substring(0, Math.min(shown, curLen()));
    }

    private String cur() {
        return seq.get(Math.floorMod(index, seq.size()));
    }

    private int curLen() {
        return cur().length();
    }

    // 轮播 / 随机

    /**
     * 按 interval 换格。格号从首次 tick 起算；
     * 大间隔一次补齐跨过的格数，保证轮播节奏与时间严格对齐。
     */
    private String rotate(long nowMs) {
        if (slotOrigin == Long.MIN_VALUE) {
            slotOrigin = nowMs;
        }
        long slot = Math.max(0, (nowMs - slotOrigin)) / interval;
        if (slot != lastSlot) {
            if (random) {
                index = pickForSlot(slot);
            } else {
                // 补齐跨过的步数（取模后等效于前进 slot-lastSlot 格）
                int steps = (int) Math.min(slot - lastSlot, seq.size());
                index = Math.floorMod(index + steps, seq.size());
            }
            lastSlot = slot;
        }
        return cur();
    }

    /** 同一格恒定的伪随机挑选：格号做种子，确定性可测试。 */
    private int pickForSlot(long slot) {
        var r = new java.util.Random(slot * 31 + 97);
        return r.nextInt(seq.size());
    }
}
