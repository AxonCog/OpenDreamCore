package com.opendreamcore.core.script;

/**
 * 缓存下来的调用句柄（{@link HandleCache} 的产物）。
 *
 * <p>为什么不用裸 {@code MethodHandle}：{@code void} 返回值没法用
 * {@code MethodHandle.asType} 变成 {@code Object}（{@code filterReturnValue} 也要求目标有返回值），
 * 与其在组合子（foldArguments/filterReturnValue）里绕，不如用一个两个实现的极薄接口把它挡住 ——
 * 仍然是「一次解析、之后零反射」，而且调用点没有变长参数装箱。
 */
public interface Handle {

    /** 调用；异常原样抛出（由调用方决定转成脚本错误还是继续）。 */
    Object call(Object[] args) throws Throwable;
}
