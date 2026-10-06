package com.opendreamcore.client.render;

import com.opendreamcore.client.CompatRender;
import java.lang.reflect.Method;

/**
 * BufferBuilder 顶点属性的跨版本写法。
 *
 * <p>各代顶点的属性方法名不同（{@code setUv}/{@code uv}、{@code setColor}/{@code color}…），
 * 这里统一收口：调用方给「候选名 + 参数类型 + 实参」，本类负责挑当前版本能用的那个。
 * 名字必须校验匹配（不按参数个数猜）——历史上按个数兜底把
 * {@code setShaderTexture} 解成 {@code polygonMode}，每帧刷 GL_INVALID_ENUM。
 */
/** 顶点属性跨版本写法的收口；由 {@link com.opendreamcore.client.CompatBuffer} 驱动。 */
public final class CompatVertexAttributes {

    private CompatVertexAttributes() {
    }

    /**
     * 往顶点写一组属性；顶点为 null 或两个候选名都找不到就跳过（不抛，避免整批废掉）。
     *
     * @param vertex 刚由 BufferBuilder 创建的顶点对象
     * @param names  跨版本候选方法名（modern 名在前、legacy 名在后）；两个都试而不是按
     *              版本二选一，因为 {@code resolveMethod} 是按名字+参数类型精确匹配的，
     *              多试一次不会误命中，却能免掉「这代到底是哪个名字」的判断
     * @param types  参数类型（与 args 一一对应）
     * @param args   实参
     */
    public static void applyTo(Object vertex, String[] names, Class<?>[] types, Object[] args) {
        if (vertex == null) {
            return;
        }
        for (String name : names) {
            Method m = CompatRender.resolveMethod(vertex.getClass(), name, types);
            if (m == null) {
                continue;
            }
            try {
                m.invoke(vertex, args);
                return;
            } catch (Exception ignored) { /* 这个名字存在但没画成：试下一个 */ }
        }
    }
}