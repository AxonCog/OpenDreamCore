package com.opendreamcore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * GuiGraphics 贴图绘制兼容层（从 CompatRender 整体迁出）。
 * blit 探测按参数形状匹配（11 参、首参 RL=旧版 / Function=新版），
 * 形状跨 Mojmap/intermediary 稳定；方法名只在两个映射体系间校验。
 */
public final class GuiBlitCompat {

    private GuiBlitCompat() {
    }

    private static volatile Boolean modernBlit;
    private static volatile MethodHandle legacyBlit;
    private static volatile MethodHandle modernFactory;

    /**
     * 探测 GuiGraphics 的 11 参 blit 重载形态（首参 ResourceLocation=旧版 / Function=新版）。
     *
     * 之前只认方法名 "blit"，而 Fabric 运行时是 intermediary 名（1.21.1 实测是
     * method_25293，javap 取证），枚举不到同名方法 → legacyBlit 永远 null →
     * ItemIcon/图片全部 NPE 静默不画（[ODC-blit] 贴图绘制失败就是这条）。
     * 改按参数形状匹配：11 参、(int×4, float×2, int×4)、首参 RL 或 Function ——
     * 形状跨映射名稳定，Mojmap/intermediary 都命中；方法名只在两个体系间校验。
     */
    private static void detect() {
        try {
            for (Method m : GuiGraphics.class.getMethods()) {
                if (m.getParameterCount() != 11) {
                    continue;
                }
                String n = m.getName();
                if (!"blit".equals(n) && !n.startsWith("method_")) {
                    continue; // 两个映射体系之外的名字不认
                }
                Class<?>[] p = m.getParameterTypes();
                boolean shape = p[1] == int.class && p[2] == int.class && p[3] == int.class
                        && p[4] == int.class && p[5] == float.class && p[6] == float.class
                        && p[7] == int.class && p[8] == int.class && p[9] == int.class
                        && p[10] == int.class;
                if (!shape) {
                    continue;
                }
                Class<?> first = p[0];
                if (first == ResourceLocation.class) {
                    legacyBlit = MethodHandles.lookup().unreflect(m);
                    modernBlit = Boolean.FALSE;
                    com.opendreamcore.client.OdcDebug.log("[ODC-blit] 旧版形态（首参 ResourceLocation，方法名 " + n + "）");
                    return;
                }
                if (first == java.util.function.Function.class) {
                    modernBlit = Boolean.TRUE;
                    legacyBlit = MethodHandles.lookup().unreflect(m);
                    com.opendreamcore.client.OdcDebug.log("[ODC-blit] 新版形态（首参 Function，方法名 " + n + "）");
                    return;
                }
            }
        } catch (IllegalAccessException ignored) {
            // 反射受限：保持未解析状态，blit 调用走兜底吞异常
        }
        com.opendreamcore.client.OdcDebug.log("[ODC-blit] 未找到 11 参重载，贴图绘制将走兜底（会报绘制失败）");
        // 未找到 11 参重载：退回旧签名尝试（保持行为可见的失败）
        modernBlit = Boolean.FALSE;
    }

    private static Object guiTexturedFactory(ResourceLocation tex) {
        try {
            if (modernFactory == null) {
                synchronized (GuiBlitCompat.class) {
                    if (modernFactory == null) {
                        Class<?> rt = com.opendreamcore.client.CompatRender.resolveClass("net.minecraft.client.renderer.RenderType");
                        Method f = rt.getMethod("guiTextured", ResourceLocation.class);
                        modernFactory = MethodHandles.lookup().unreflect(f);
                    }
                }
            }
            return modernFactory.invoke(tex);
        } catch (Throwable t) {
            return tex; // 兜底：直接传贴图（最坏情况渲染层自行处理）
        }
    }

    /**
     * GuiGraphics.blit 的版本安全等价：
     * 旧版 blit(RL, x,y,w,h, u,v,uW,vH,texW,texH) /
     * 新版 blit(Function<RL,RenderType>, 同参数表)。
     */
    public static void blit(GuiGraphics g, ResourceLocation tex,
                            int x, int y, int w, int h,
                            float u, float v, int uw, int vh, int tw, int th) {
        if (modernBlit == null) {
            synchronized (GuiBlitCompat.class) {
                if (modernBlit == null) {
                    detect();
                }
            }
        }
        try {
            if (Boolean.TRUE.equals(modernBlit)) {
                legacyBlit.invoke(g,
                        guiTexturedFactory(tex), x, y, w, h, u, v, uw, vh, tw, th);
            } else {
                legacyBlit.invoke(g, tex, x, y, w, h, u, v, uw, vh, tw, th);
            }
        } catch (Throwable t) {
            if (BLIT_FAIL.add(tex.toString())) {
                com.opendreamcore.client.OdcDebug.log("[ODC-blit] 贴图绘制失败（只报一次）: " + tex + " -> " + t);
            }
        }
    }

    private static final java.util.Set<String> BLIT_FAIL = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * KongCore 式 GUI_TEXTURED 管线 blit（1.21.2+：blit(RenderPipeline, RL, x, y, u, v, w, h, tw, th, color)）。
     * 字体重绘/自定义贴图用这个比 Function 版 blit 可靠；旧版本没有 RenderPipelines 时回退 blit()。
     */
    public static void blitGuiTextured(GuiGraphics g, ResourceLocation tex,
                                       int x, int y, int w, int h,
                                       float u, float v, int uw, int vh, int tw, int th) {
        try {
            Class<?> pipelines = com.opendreamcore.client.CompatRender.resolveClass("net.minecraft.client.renderer.RenderPipelines");
            Object pipeline = pipelines.getField("GUI_TEXTURED").get(null);
            Class<?> pipelineCls = com.opendreamcore.client.CompatRender.resolveClass("net.minecraft.client.renderer.RenderPipeline");
            Method m = GuiGraphics.class.getMethod("blit", pipelineCls, ResourceLocation.class,
                    int.class, int.class, float.class, float.class,
                    int.class, int.class, int.class, int.class, int.class);
            m.invoke(g, pipeline, tex, x, y, u, v, uw, vh, tw, th, 0xFFFFFFFF);
        } catch (Throwable ignored) {
            blit(g, tex, x, y, w, h, u, v, uw, vh, tw, th);
        }
    }

    // GUI pose 栈方言（≥1.21.6 Matrix3x2fStack 替代 PoseStack）
    // 两分支都是直接类型调用（PoseStack 与 JOML Matrix3x2fStack 在所有目标版本 classpath 上都存在），零反射热路径。
    // 2D 栈无 Z 轴/四元数，X/Y 旋转在 GUI 平面无意义，新版路径仅应用 Z 分量。

    /** pose().pushPose() / pushMatrix()。 */
}
