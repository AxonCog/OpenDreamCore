package com.opendreamcore.client;

import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元素渲染器注册表：附属模组注册自定义元素类型，核心渲染管线自动分发。
 * 注册后 YAML 里直接用 type: "你的类型" 即可，编辑器调色板也会同步显示。
 */
public final class ElementRenderRegistry {

    /** 渲染回调：每个元素节点调用一次。 */
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphics g, Font font, RenderNode node,
                    int mouseX, int mouseY,
                    UiRenderer.State state,
                    Map<String, Object> pageVars);
    }

    private static final Map<String, Renderer> REGISTRY = new ConcurrentHashMap<>();

    private ElementRenderRegistry() {
    }

    /**
     * 注册自定义元素渲染器。
     *
     * @param type     元素类型名（YAML 里 type: "你的类型"）
     * @param renderer 渲染回调
     */
    public static void register(String type, Renderer renderer) {
        REGISTRY.put(type, renderer);
    }

    /** 取渲染器（无则 null，走内置渲染）。 */
    public static Renderer get(String type) {
        return REGISTRY.get(type);
    }

    /** 已注册的所有自定义类型名。 */
    public static Set<String> registeredTypes() {
        return REGISTRY.keySet();
    }
}
