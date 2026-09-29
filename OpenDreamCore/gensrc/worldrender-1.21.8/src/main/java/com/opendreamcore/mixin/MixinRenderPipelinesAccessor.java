package com.opendreamcore.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自建渲染管线的登记入口（1.21.8，Fabric 侧唯一可行的登记时机）。
 *
 * <p>渲染管线必须登记进原版的静态管线表：那张表不只是给别处查询用的，它同时是「启动时要编译哪些
 * 着色器」的清单。自己 new 一条管线不登记，着色器就不会被编译，绘制阶段拿到的是没程序的管线，
 * 结果要么画不出东西要么直接报错。原版的登记方法在这是 private，所以用访问器接出来。
 *
 * <p>登记的时机也必须是这里。原版的静态初始化块在类初始化时把内置管线一条条填进表里，
 * 我们在同一个块的末尾追加，就能和内置管线处在完全相同的生命周期位置上——比在模组入口里补登记
 * 更稳（入口执行时着色器编译可能已经开始）。因此这里既做访问器，也挂一个末尾注入。
 */
@Mixin(RenderPipelines.class)
public abstract class MixinRenderPipelinesAccessor {

    /** 把一条管线登记进原版静态管线表（同时使其着色器纳入启动编译）。 */
    @Invoker("register")
    public static RenderPipeline opendreamcore$register(RenderPipeline pipeline) {
        throw new AssertionError("由 Mixin 实现");
    }

    /**
     * 在原版把内置管线全部登记完之后追加我们自己的。
     *
     * <p>用 TAIL 而不是 HEAD：我们只做追加，不参与原版那一长串内置管线的构建顺序；末尾注入意味着
     * 即使将来原版在块中间新增常量，我们的登记点也不会移动。
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void opendreamcore$registerOwnPipelines(CallbackInfo ci) {
        com.opendreamcore.client.render.OdcWorldRenderPipelines.registerAll();
    }
}
