package com.opendreamcore.core.script;

/** 脚本被策略拒绝、或参数/目标不合法时抛出；消息面向脚本作者（中文、可直接显示）。 */
public class ScriptDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ScriptDeniedException(String message) {
        super(message);
    }

    public ScriptDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
