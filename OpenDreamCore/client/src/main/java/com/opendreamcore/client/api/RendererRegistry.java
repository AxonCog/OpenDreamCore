package com.opendreamcore.client.api;

import com.opendreamcore.client.ElementRenderRegistry;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;
import java.util.Set;

/**
 * 渲染器注册表（对外门面）：附属模组想加自己的元素类型，注册到这里就行。
 *
 * 底下就是 ElementRenderRegistry 那张表，这里单独开口子是为了给附属一层稳定
 * 的 API——内部实现类哪天挪包也不影响你。注册完 YAML 里直接写 type: "你的类型"，
 * 核心绘制管线遇到它会先回调你的渲染器，画完再递归子节点。
 *
 * 用法：
 * <pre>{@code
 * RendererRegistry.register("my_gauge", (g, font, node, mx, my, state, vars) -> {
 *     // node.props() 取属性，盒子从 node 拿
 * });
 * }</pre>
 */
public final class RendererRegistry {

    private RendererRegistry() { }

    /** 渲染回调：每个匹配到该类型的元素节点调用一次。 */
    public interface Renderer {
        void render(GuiGraphics g, Font font, RenderNode node,
                    int mouseX, int mouseY, UiRenderer.State state,
                    Map<String, Object> pageVars);
    }

    /** 注册自定义元素渲染器（同名覆盖）。 */
    public static void register(String type, Renderer renderer) {
        if (type == null || type.isEmpty() || renderer == null) {
            return;
        }
        ElementRenderRegistry.register(type, new ElementRenderRegistry.Renderer() {
            @Override
            public void render(GuiGraphics g, Font font, RenderNode node,
                               int mouseX, int mouseY, UiRenderer.State state,
                               Map<String, Object> pageVars) {
                renderer.render(g, font, node, mouseX, mouseY, state, pageVars);
            }
        });
    }

    /** 摘掉某个类型，返回是否真删了。 */
    public static boolean unregister(String type) {
        return ElementRenderRegistry.unregister(type);
    }

    /** 已注册的自定义类型名。 */
    public static Set<String> registeredTypes() {
        return ElementRenderRegistry.registeredTypes();
    }
}
