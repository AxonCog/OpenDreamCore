package com.opendreamcore.client.render;

import com.opendreamcore.client.CompatRender;
import java.lang.reflect.Method;

/**
 * 世界里画物品图标（各版本 ItemRenderer 签名都不一样）。
 *
 * <p>1.20.1~1.21.8 是 {@code ItemRenderer.renderStatic(ItemStack, ItemDisplayContext, int, int,
 * PoseStack, MultiBufferSource, ClientLevel, int)}；1.21.11 参数顺序变了；26.1.2 连
 * {@code Minecraft.getItemRenderer()} 都没了。所以整条链走反射，解析不到就返回 false，
 * 调用方跳过这一项——不能让某个版本的签名差异把整个世界渲染编译搞挂。
 */
public final class CompatItemWorld {

    private CompatItemWorld() {
    }

    private static final java.util.Set<String> FAIL_LOG =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean renderItemWorld(net.minecraft.client.Minecraft mc,
                                          net.minecraft.world.item.ItemStack stack,
                                          Object pose, Object buffers) {
        if (mc == null || stack == null || pose == null || buffers == null) {
            return false;
        }
        try {
            Object renderer = itemRendererOf(mc);
            if (renderer == null) {
                return false;
            }
            Method m = renderStaticOf(renderer.getClass());
            if (m == null) {
                return false;
            }
            Object ctx = fixedContext();
            if (ctx == null) {
                return false;
            }
            Object level = mc.level;
            Object[] args = m.getParameterCount() == 9
                    ? new Object[]{stack, ctx, 0xF000F0, 0, pose, buffers, level, 0}
                    : new Object[]{stack, ctx, 0xF000F0, 0, pose, buffers, level};
            m.invoke(renderer, args);
            return true;
        } catch (Throwable t) {
            if (FAIL_LOG.add("itemWorld")) {
                com.opendreamcore.client.OdcDebug.log("[ODC-item] 世界物品图标渲染失败（只报一次）: " + t);
            }
            return false;
        }
    }

    /** 取 ItemRenderer 实例：26.1.2 起 Minecraft 上没有 getItemRenderer()，退回全局渲染单例。 */
    private static Object itemRendererOf(net.minecraft.client.Minecraft mc) {
        try {
            Method getter = CompatRender.resolveMethod(mc.getClass(), "getItemRenderer");
            if (getter != null) {
                Object r = getter.invoke(mc);
                if (r != null) {
                    return r;
                }
            }
        } catch (Throwable ignored) {
            // 落到下面的静态渲染单例
        }
        try {
            for (Method m : net.minecraft.client.Minecraft.class.getMethods()) {
                if ("getItemRenderer".equals(m.getName()) && m.getReturnType() != null) {
                    // 静态形态（26.1.2 这类）
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Object r = m.invoke(null);
                        if (r != null) {
                            return r;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 取不到就是取不到
        }
        return null;
    }

    /** 两代签名各试一次（1.20.1~1.21.8 是 9 参，1.21.11 少一个 int）。 */
    private static Method renderStaticOf(Class<?> rendererCls) throws ClassNotFoundException {
        Class<?> ctxCls = CompatRender.resolveClass("net.minecraft.world.item.ItemDisplayContext");
        Class<?> levelCls = CompatRender.resolveClass("net.minecraft.client.multiplayer.ClientLevel");
        Class<?> poseCls = CompatRender.resolveClass("com.mojang.blaze3d.vertex.PoseStack");
        Class<?> bufCls = CompatRender.resolveClass("com.mojang.blaze3d.vertex.MultiBufferSource");
        if (bufCls == null) {
            bufCls = CompatRender.resolveClass("com.mojang.blaze3d.buffers.MultiBufferSource");
        }
        if (ctxCls == null || levelCls == null || poseCls == null || bufCls == null) {
            return null;
        }
        Method m = CompatRender.resolveMethod(rendererCls, "renderStatic",
                net.minecraft.world.item.ItemStack.class, ctxCls,
                int.class, int.class, poseCls, bufCls, levelCls, int.class);
        if (m == null) {
            m = CompatRender.resolveMethod(rendererCls, "renderStatic",
                    net.minecraft.world.item.ItemStack.class, ctxCls,
                    int.class, int.class, poseCls, bufCls, levelCls);
        }
        return m;
    }

    /** ItemDisplayContext.FIXED（手持视角）。 */
    private static Object fixedContext() {
        try {
            Class<?> ctxCls = CompatRender.resolveClass("net.minecraft.world.item.ItemDisplayContext");
            if (ctxCls == null) {
                return null;
            }
            return ctxCls.getField("FIXED").get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}