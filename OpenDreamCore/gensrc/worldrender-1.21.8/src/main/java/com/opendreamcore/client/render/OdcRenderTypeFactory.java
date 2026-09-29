package com.opendreamcore.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * 1.21.8 的渲染类型构造入口。
 *
 * <p>这一代把渲染类型造出来的那个方法本身是包内可见，而它的返回类型（包内的复合渲染类型）同样
 * 是包内可见。后者才是真正的麻烦：访问器接口写在我们自己的包里，连这个返回类型都写不出来，
 * 于是拿到方法也无从声明。可行的替代有三条——给这一代单独配一份放宽可见性的声明文件、把代码放进
 * 原版渲染包、或者直接反射调用。前两条一条要按平台各配一份（本工程这一代恰好同时有平台两端），
 * 另一条会被平台自身的包检查拦下；这里取第三条，并且只查一次、缓存句柄。
 *
 * <p>调用点本身是编译期检查的：参数与返回类型都是公开类型，只有「方法不可见」这一件事靠运行时
 * 放开。构造失败一律返回 null，由调用方回退原有绘制路径——渲染路径上不允许把异常放出去。
 */
public final class OdcRenderTypeFactory {

    /** 已解析的构造句柄（null 表示这一代拿不到）。 */
    private static volatile MethodHandle createHandle;
    /** 是否已经尝试解析过（避免每次调用都走反射查找）。 */
    private static volatile boolean resolved;

    private OdcRenderTypeFactory() {
    }

    /**
     * 造一条绑定自建管线的渲染类型。
     *
     * @return 可直接绘制的渲染类型；这一代拿不到入口时返回 null
     */
    public static RenderType createWorldTextured(String name, int bufferSize,
                                                 RenderPipeline pipeline, RenderType.CompositeState state) {
        MethodHandle handle = handle();
        if (handle == null) {
            return null;
        }
        try {
            // 句柄签名即原方法签名，参数按序传入；返回类型向上转型成公开的渲染类型
            return (RenderType) handle.invoke(name, bufferSize, pipeline, state);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static MethodHandle handle() {
        if (resolved) {
            return createHandle;
        }
        synchronized (OdcRenderTypeFactory.class) {
            if (!resolved) {
                createHandle = resolve();
                resolved = true;
            }
            return createHandle;
        }
    }

    /** 查找并解开构造句柄；按参数类型精确匹配，避开同名的六参私有重载。 */
    private static MethodHandle resolve() {
        try {
            Method method = RenderType.class.getDeclaredMethod(
                    "create",
                    String.class,
                    int.class,
                    RenderPipeline.class,
                    RenderType.CompositeState.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
