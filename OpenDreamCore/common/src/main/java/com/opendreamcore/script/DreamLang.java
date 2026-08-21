package com.opendreamcore.script;

import com.opendreamcore.script.antlr.DreamLangLexer;
import com.opendreamcore.script.antlr.DreamLangParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * DreamLang 入口：解析 + 执行。
 */
public final class DreamLang {

    private DreamLang() {
    }

    /** 执行脚本，返回最后一条语句的值。 */
    public static Object execute(String script, Scope scope) {
        DreamLangParser.ProgramContext tree = parse(script);
        DreamLangExecutor executor = new DreamLangExecutor(scope);
        return executor.visitProgram(tree);
    }

    /** 只求值表达式（布局公式/条件表达式用）。 */
    public static Object evaluate(String expression, Scope scope) {
        DreamLangParser.ProgramContext tree = parse(expression);
        DreamLangExecutor executor = new DreamLangExecutor(scope);
        Object result = null;
        for (DreamLangParser.StatementContext stmt : tree.statement()) {
            result = executor.visit(stmt);
        }
        return result;
    }

    public static DreamLangParser.ProgramContext parse(String script) {
        DreamLangLexer lexer = new DreamLangLexer(CharStreams.fromString(script));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DreamLangParser parser = new DreamLangParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ThrowingErrorListener());
        return parser.program();
    }
}
