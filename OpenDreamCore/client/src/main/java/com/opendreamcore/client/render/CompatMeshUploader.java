package com.opendreamcore.client.render;

import com.opendreamcore.client.CompatRender;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;

/**
 * 网格上传：把 build 出来的 mesh 交给原版 BufferUploader 画出去。
 *
 * <p>为什么单独成类：类名与位置在 >=1.21.8 移过/改过，且 Fabric 生产环境类名也是
 * intermediary 名，所以要「原样名 + 映射后名」两路都试；而方法签名这几代也各有形态
 * （单参 mesh / mesh+PoseStack / mesh+MatrixStack），每次新增版本都要在这里加一行，
 * 不该拖在 CompatBuffer 的绘制主流程里。
 *
 * <p>成功即返回 true（调用方负责 close mesh）；全试失败返回 false，让调用方走兜底路径。
 */
public final class CompatMeshUploader {

    private CompatMeshUploader() {
    }

    /** 试遍所有候选类名与签名把 mesh 画出去；成功 true。 */
    public static boolean draw(Object mesh) {
        if (mesh == null) {
            return false;
        }
        for (String cn : candidates()) {
            Class<?> uploader;
            try {
                uploader = Class.forName(cn);
            } catch (ClassNotFoundException | LinkageError ignored) {
                continue;
            }
            if (drawWith(uploader, "drawWithShader", mesh) || drawWith(uploader, "draw", mesh)) {
                return true;
            }
        }
        return false;
    }

    /** 候选类名：原样名 + 映射后名。 */
    private static LinkedHashSet<String> candidates() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String cn : new String[]{
                "com.mojang.blaze3d.vertex.BufferUploader",
                "com.mojang.blaze3d.systems.BufferUploader"}) {
            names.add(cn);
            names.add(CompatRender.mapClassName(cn));
        }
        return names;
    }

    /** 一个方法名 × 三种签名形态；任一形态画成即 true。 */
    private static boolean drawWith(Class<?> uploader, String methodName, Object mesh) {
        // 单参 mesh（1.20.4 及以前）
        Method m = CompatRender.resolveMethod(uploader, methodName, mesh.getClass());
        if (m != null) {
            try {
                m.invoke(null, mesh);
                return true;
            } catch (Exception ignored) { /* 此签名不匹配：换下一个 */ }
        }
        // 双参 mesh + PoseStack（1.21.1+ 的 drawWithShader 就是它）
        Class<?> pose = CompatRender.poseStackClass();
        if (pose != null) {
            m = CompatRender.resolveMethod(uploader, methodName, mesh.getClass(), pose);
            if (m != null) {
                Object ps = CompatRender.currentPoseStack();
                if (ps != null) {
                    try {
                        m.invoke(null, mesh, ps);
                        return true;
                    } catch (Exception ignored) { /* PoseStack 形态没画成：试 MatrixStack */ }
                }
            }
        }
        // 双参 mesh + MatrixStack
        Class<?> mstack = CompatRender.matrixStackClass();
        if (mstack != null) {
            m = CompatRender.resolveMethod(uploader, methodName, mesh.getClass(), mstack);
            if (m != null) {
                Object ms = CompatRender.currentMatrixStack();
                if (ms != null) {
                    try {
                        m.invoke(null, mesh, ms);
                        return true;
                    } catch (Exception ignored) { /* 最后一种签名 */ }
                }
            }
        }
        return false;
    }
}