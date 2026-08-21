package com.opendreamcore.script;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * 语法错误直接抛异常（带行列）。
 */
final class ThrowingErrorListener extends BaseErrorListener {

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg, RecognitionException e) {
        throw new DreamLangExecutor.ScriptException(
                "脚本语法错误（第 " + line + " 行第 " + (charPositionInLine + 1) + " 列）: " + msg);
    }
}
