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

    /** 把 HeadTag 规则 IR 转成锚定实体的世界页面（去掉匹配键后就是普通页面）。 */
    public static Page headTagToPage(String id, Map<String, Object> ir) {
        Map<String, Object> page = new LinkedHashMap<>(ir);
        page.remove("entity");
        page.remove("name");
        page.remove("distance");
        page.put("display", "world");
        return PageSchema.build("ht_" + id, page);
    }
}
