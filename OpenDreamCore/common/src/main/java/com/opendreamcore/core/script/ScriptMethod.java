package com.opendreamcore.core.script;

/** 脚本方法：所有 {@code @OdcMethod} 方法统一这个形状（上下文 + 位置参数 → 返回值）。 */
public interface ScriptMethod {

    /** 执行；参数不合法时抛 {@link ScriptDeniedException}（消息直接给脚本作者看）。 */
    Object call(ScriptContext ctx, Object[] args);
}
