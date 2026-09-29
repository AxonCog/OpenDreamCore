package com.opendreamcore.client.visual;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.page.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界贴图/实体标签的规则→页面适配。
 *
 * 把 WorldTexture/HeadTag 规则转成 display=world 的标准页面，
 * 交给既有的世界面板管线渲染——零新渲染器，全部复用。
 */
public final class VisualWorldPages {

    private VisualWorldPages() {
    }

    /** 把 WorldTexture 规则 IR 转成世界模式页面。 */
    public static Page worldTextureToPage(String id, Map<String, Object> ir) {
        Map<String, Object> page = new LinkedHashMap<>(ir);
        // 规则里的 texture 键转为标准 image 元素
        Object tex = page.remove("texture");
        if (tex != null) {
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("type", "image");
            if (tex instanceof String s) {
                img.put("image", Map.of("src", s));
            } else if (tex instanceof Map<?, ?> tm) {
                img.put("image", new LinkedHashMap<>((Map<String, Object>) tm));
            }
            page.put("背景", img);
        }
        page.put("display", "world");
        return PageSchema.build("vt_" + id, page);
    }

    /** 把 HeadTag 规则 IR 转成锚定实体的世界页面（匹配键剥掉留作档案，剩余部分就是普通页面）。 */
    public static Page headTagToPage(String id, Map<String, Object> ir) {
        Map<String, Object> page = new LinkedHashMap<>(ir);
        page.remove("entity");
        page.remove("实体");
        page.remove("name");
        page.remove("distance");
        page.remove("mode");
        page.remove("显示模式");
        page.put("display", "world");
        // 头顶页的世界元素：作者在元素上写 x/y/width/height 时（龙核写法，单位是格），
        // 除非显式写了 hologram，否则按其补一份世界单位 hologram。世界渲染器
        // （WorldHologram.renderRect/text/image…）取的是 hologram 里的 x/y/width/height，
        // 不补就一律退化成默认 1×1 格 —— 作者写的 2 格宽血条会变成默认小方块，
        // 表现就是"配了却看不见"。
        for (Map.Entry<String, Object> kv : page.entrySet()) {
            if (!(kv.getValue() instanceof Map<?, ?> rawEl)) {
                continue;
            }
            Map<String, Object> el = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e2 : rawEl.entrySet()) {
                el.put(String.valueOf(e2.getKey()), e2.getValue());
            }
            boolean isElement = el.get("type") != null || el.get("类型") != null;
            if (!isElement || el.containsKey("hologram")) {
                continue;
            }
            boolean hasCoord = el.containsKey("x") || el.containsKey("y")
                    || el.containsKey("width") || el.containsKey("height");
            if (!hasCoord) {
                continue;
            }
            Map<String, Object> holo = new LinkedHashMap<>();
            holo.put("x", el.getOrDefault("x", 0));
            holo.put("y", el.getOrDefault("y", 0));
            holo.put("z", el.getOrDefault("z", 0));
            holo.put("width", el.getOrDefault("width", 1));
            holo.put("height", el.getOrDefault("height", 1));
            el.put("hologram", holo);
            kv.setValue(el);
        }
        return PageSchema.build("ht_" + id, page);
    }
}
