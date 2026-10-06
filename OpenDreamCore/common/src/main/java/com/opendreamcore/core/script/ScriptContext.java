package com.opendreamcore.core.script;

/**
 * 脚本执行上下文：脚本名 / 行号（报错定位）、策略、审计。
 *
 * <p>所有 {@code @OdcMethod} 方法都拿到它 —— 它是 core-c 与其它模块之间唯一的方法级契约。
 */
public interface ScriptContext {

    /** 脚本来源名（文件名，或 {@code <控制台>}）。 */
    String scriptName();

    /** 当前行号；未知返回 -1。 */
    int line();

    /** 生效策略。 */
    ScriptPolicy policy();

    /** 审计出口（默认空实现）。 */
    Audit audit();

    /** 审计接口：互操作调用应该在这里留痕（谁在什么时候调了哪个类的方法）。 */
    interface Audit {

        void log(String action, Object... detail);
    }

    /** 空审计。 */
    Audit NO_AUDIT = new Audit() {
        @Override
        public void log(String action, Object... detail) {
            // 默认不留痕
        }
    };

    /** 控制台 / 单测用的上下文：策略全开、审计空、无行号。 */
    ScriptContext CONSOLE = new ScriptContext() {
        @Override
        public String scriptName() {
            return "<控制台>";
        }

        @Override
        public int line() {
            return -1;
        }

        @Override
        public ScriptPolicy policy() {
            return ScriptPolicy.OPEN;
        }

        @Override
        public Audit audit() {
            return NO_AUDIT;
        }
    };
}
