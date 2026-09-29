package com.opendreamcore.legacy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;

/**
 * 替换贴图专用的渲染类型（1.16.5）。
 *
 * 为什么不蹭原版字形图集：这代的原版字体在重载时把图集一次烘死，运行时塞不进
 * 新贴图。所以贴着每张替换图各建一个文字渲染类型，直接绑自己的贴图，绕开图集。
 *
 * 同一张贴图会被一行里的多个字形、以及每一帧反复取用，这里按贴图缓存住，
 * 免得每画一个字就新造一个渲染类型对象。缓存键用贴图全名，取值走无锁并发表，
 * 多线程同时补建同一张也只会留下一个实例。
 */
final class LegacyGlyphRenderTypes {

    private static final Map<String, RenderType> TEXT = new ConcurrentHashMap<String, RenderType>();

    private LegacyGlyphRenderTypes() {
    }

    /** 取（或补建）某张贴图对应的文字渲染类型。 */
    static RenderType text(ResourceLocation texture) {
        String key = texture.toString();
        RenderType cached = TEXT.get(key);
        if (cached != null) {
            return cached;
        }
        RenderType made = RenderType.text(texture);
        RenderType prior = TEXT.putIfAbsent(key, made);
        return prior != null ? prior : made;
    }
}
