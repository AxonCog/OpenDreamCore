package com.opendreamcore.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 渲染类型构造入口的访问器。
 *
 * <p>这一代把渲染类型造出来的那个方法是包内可见，包外调不到；它同时又是静态方法，所以访问器
 * 也声明成静态，调用点直接走类名。返回类型在这一代是公开的渲染类型本身，因此这里可以如实声明，
 * 调用点也受编译期检查——不需要像 1.21.8 那样退回反射。
 */
@Mixin(RenderType.class)
public abstract class WorldRenderTypeMixin {

    /** 造一条绑定给定装配描述的渲染类型。 */
    @Invoker("create")
    public static RenderType odc$create(String name, RenderSetup setup) {
        throw new AssertionError("由 Mixin 实现");
    }
}
