package com.opendreamcore.branding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * branding/title.json 配置（DreamCore ClientTitleManager 语义平移）：
 * <pre>
 * {
 *   "text": "静态标题",              // 单文本（与 titles 二选一）
 *   "titles": ["第一句", "第二句"],   // 多文本轮播
 *   "typewriter": true,              // 打字机逐字显现（默认 false）
 *   "random": false,                 // 随机轮换：按时间格哈希随机选句（默认 false）
 *   "speed": 120,                    // 打字机每字符毫秒（默认 120）
 *   "interval": 3000,                // 轮播间隔/每句展示时长基准（默认 3000）
 *   "holdMs": 3000,                  // 打字完成后停留毫秒（默认取 interval）
 *   "loop": true                     // 播完是否循环（默认 true）
 * }
 * </pre>
 * 字段全部容错：缺失取默认，非法值回退默认。
 */
public final class TitleConfig {

    public String text = "";
    public List<String> titles = new ArrayList<>();
    public boolean typewriter = false;
    public boolean random = false;
    public int speed = 120;
    public int interval = 3000;
    public int holdMs = -1;     // -1 = 未设置 → 取 interval
    public boolean loop = true;

    /** 有效停留时长（holdMs 未配置时回落 interval）。 */
    public int effectiveHoldMs() {
        return holdMs > 0 ? holdMs : Math.max(0, interval);
    }

    /** 有效速度（非正回退默认 120）。 */
    public int effectiveSpeed() {
        return speed > 0 ? speed : 120;
    }

    /** 展示序列：titles 优先；否则单元素 [text]。全空返回空列表。 */
    public List<String> sequence() {
        List<String> seq = new ArrayList<>();
        if (titles != null) {
            for (String t : titles) {
                if (t != null) {
                    seq.add(t);
                }
            }
        }
        if (seq.isEmpty() && text != null && !text.isEmpty()) {
            seq.add(text);
        }
        return seq;
    }

    /** 从 JSON 文件加载（Gson）；解析失败返回 null 由调用方回退 title.txt。 */
    public static TitleConfig load(Path json) {
        try {
            String raw = Files.readString(json, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return null;
            }
            TitleConfig cfg = new com.google.gson.Gson().fromJson(raw, TitleConfig.class);
            return cfg == null ? null : cfg;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 兼容 Gson 反序列化的无参构造。 */
    public TitleConfig() {
    }
}
