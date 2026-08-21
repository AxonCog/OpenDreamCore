// Generated from DreamLang.g4 by ANTLR 4.13.1
package com.opendreamcore.script.antlr;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link DreamLangParser}.
 */
public interface DreamLangListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(DreamLangParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(DreamLangParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(DreamLangParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(DreamLangParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#loopStatement}.
	 * @param ctx the parse tree
	 */
	void enterLoopStatement(DreamLangParser.LoopStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#loopStatement}.
	 * @param ctx the parse tree
	 */
	void exitLoopStatement(DreamLangParser.LoopStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#awaitStatement}.
	 * @param ctx the parse tree
	 */
	void enterAwaitStatement(DreamLangParser.AwaitStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#awaitStatement}.
	 * @param ctx the parse tree
	 */
	void exitAwaitStatement(DreamLangParser.AwaitStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#forEachStatement}.
	 * @param ctx the parse tree
	 */
	void enterForEachStatement(DreamLangParser.ForEachStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#forEachStatement}.
	 * @param ctx the parse tree
	 */
	void exitForEachStatement(DreamLangParser.ForEachStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#whileStatement}.
	 * @param ctx the parse tree
	 */
	void enterWhileStatement(DreamLangParser.WhileStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#whileStatement}.
	 * @param ctx the parse tree
	 */
	void exitWhileStatement(DreamLangParser.WhileStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#forStatement}.
	 * @param ctx the parse tree
	 */
	void enterForStatement(DreamLangParser.ForStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#forStatement}.
	 * @param ctx the parse tree
	 */
	void exitForStatement(DreamLangParser.ForStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#forInit}.
	 * @param ctx the parse tree
	 */
	void enterForInit(DreamLangParser.ForInitContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#forInit}.
	 * @param ctx the parse tree
	 */
	void exitForInit(DreamLangParser.ForInitContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement(DreamLangParser.IfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement(DreamLangParser.IfStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#returnStatement}.
	 * @param ctx the parse tree
	 */
	void enterReturnStatement(DreamLangParser.ReturnStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#returnStatement}.
	 * @param ctx the parse tree
	 */
	void exitReturnStatement(DreamLangParser.ReturnStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#breakStatement}.
	 * @param ctx the parse tree
	 */
	void enterBreakStatement(DreamLangParser.BreakStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#breakStatement}.
	 * @param ctx the parse tree
	 */
	void exitBreakStatement(DreamLangParser.BreakStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#continueStatement}.
	 * @param ctx the parse tree
	 */
	void enterContinueStatement(DreamLangParser.ContinueStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#continueStatement}.
	 * @param ctx the parse tree
	 */
	void exitContinueStatement(DreamLangParser.ContinueStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#functionDefinitionStatement}.
	 * @param ctx the parse tree
	 */
	void enterFunctionDefinitionStatement(DreamLangParser.FunctionDefinitionStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#functionDefinitionStatement}.
	 * @param ctx the parse tree
	 */
	void exitFunctionDefinitionStatement(DreamLangParser.FunctionDefinitionStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#varDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterVarDeclaration(DreamLangParser.VarDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#varDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitVarDeclaration(DreamLangParser.VarDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#importStatement}.
	 * @param ctx the parse tree
	 */
	void enterImportStatement(DreamLangParser.ImportStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#importStatement}.
	 * @param ctx the parse tree
	 */
	void exitImportStatement(DreamLangParser.ImportStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#exportStatement}.
	 * @param ctx the parse tree
	 */
	void enterExportStatement(DreamLangParser.ExportStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#exportStatement}.
	 * @param ctx the parse tree
	 */
	void exitExportStatement(DreamLangParser.ExportStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#exportList}.
	 * @param ctx the parse tree
	 */
	void enterExportList(DreamLangParser.ExportListContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#exportList}.
	 * @param ctx the parse tree
	 */
	void exitExportList(DreamLangParser.ExportListContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#classStatement}.
	 * @param ctx the parse tree
	 */
	void enterClassStatement(DreamLangParser.ClassStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#classStatement}.
	 * @param ctx the parse tree
	 */
	void exitClassStatement(DreamLangParser.ClassStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#classBody}.
	 * @param ctx the parse tree
	 */
	void enterClassBody(DreamLangParser.ClassBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#classBody}.
	 * @param ctx the parse tree
	 */
	void exitClassBody(DreamLangParser.ClassBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#classMember}.
	 * @param ctx the parse tree
	 */
	void enterClassMember(DreamLangParser.ClassMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#classMember}.
	 * @param ctx the parse tree
	 */
	void exitClassMember(DreamLangParser.ClassMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#namespaceStatement}.
	 * @param ctx the parse tree
	 */
	void enterNamespaceStatement(DreamLangParser.NamespaceStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#namespaceStatement}.
	 * @param ctx the parse tree
	 */
	void exitNamespaceStatement(DreamLangParser.NamespaceStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#namespaceBody}.
	 * @param ctx the parse tree
	 */
	void enterNamespaceBody(DreamLangParser.NamespaceBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#namespaceBody}.
	 * @param ctx the parse tree
	 */
	void exitNamespaceBody(DreamLangParser.NamespaceBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#namespaceMember}.
	 * @param ctx the parse tree
	 */
	void enterNamespaceMember(DreamLangParser.NamespaceMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#namespaceMember}.
	 * @param ctx the parse tree
	 */
	void exitNamespaceMember(DreamLangParser.NamespaceMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#constDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterConstDeclaration(DreamLangParser.ConstDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#constDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitConstDeclaration(DreamLangParser.ConstDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#tryStatement}.
	 * @param ctx the parse tree
	 */
	void enterTryStatement(DreamLangParser.TryStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#tryStatement}.
	 * @param ctx the parse tree
	 */
	void exitTryStatement(DreamLangParser.TryStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#catchClause}.
	 * @param ctx the parse tree
	 */
	void enterCatchClause(DreamLangParser.CatchClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#catchClause}.
	 * @param ctx the parse tree
	 */
	void exitCatchClause(DreamLangParser.CatchClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#throwStatement}.
	 * @param ctx the parse tree
	 */
	void enterThrowStatement(DreamLangParser.ThrowStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#throwStatement}.
	 * @param ctx the parse tree
	 */
	void exitThrowStatement(DreamLangParser.ThrowStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#javaCallStatement}.
	 * @param ctx the parse tree
	 */
	void enterJavaCallStatement(DreamLangParser.JavaCallStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#javaCallStatement}.
	 * @param ctx the parse tree
	 */
	void exitJavaCallStatement(DreamLangParser.JavaCallStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#argumentList}.
	 * @param ctx the parse tree
	 */
	void enterArgumentList(DreamLangParser.ArgumentListContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#argumentList}.
	 * @param ctx the parse tree
	 */
	void exitArgumentList(DreamLangParser.ArgumentListContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#parameterList}.
	 * @param ctx the parse tree
	 */
	void enterParameterList(DreamLangParser.ParameterListContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#parameterList}.
	 * @param ctx the parse tree
	 */
	void exitParameterList(DreamLangParser.ParameterListContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#blockStatement}.
	 * @param ctx the parse tree
	 */
	void enterBlockStatement(DreamLangParser.BlockStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#blockStatement}.
	 * @param ctx the parse tree
	 */
	void exitBlockStatement(DreamLangParser.BlockStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code LambdaExpr}
	 * labeled alternative in {@link DreamLangParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterLambdaExpr(DreamLangParser.LambdaExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code LambdaExpr}
	 * labeled alternative in {@link DreamLangParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitLambdaExpr(DreamLangParser.LambdaExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionBody}
	 * labeled alternative in {@link DreamLangParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpressionBody(DreamLangParser.ExpressionBodyContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionBody}
	 * labeled alternative in {@link DreamLangParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpressionBody(DreamLangParser.ExpressionBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#pipeExpression}.
	 * @param ctx the parse tree
	 */
	void enterPipeExpression(DreamLangParser.PipeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#pipeExpression}.
	 * @param ctx the parse tree
	 */
	void exitPipeExpression(DreamLangParser.PipeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#pipeTarget}.
	 * @param ctx the parse tree
	 */
	void enterPipeTarget(DreamLangParser.PipeTargetContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#pipeTarget}.
	 * @param ctx the parse tree
	 */
	void exitPipeTarget(DreamLangParser.PipeTargetContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#ternaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterTernaryExpression(DreamLangParser.TernaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#ternaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitTernaryExpression(DreamLangParser.TernaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#nullCoalesceExpression}.
	 * @param ctx the parse tree
	 */
	void enterNullCoalesceExpression(DreamLangParser.NullCoalesceExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#nullCoalesceExpression}.
	 * @param ctx the parse tree
	 */
	void exitNullCoalesceExpression(DreamLangParser.NullCoalesceExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#orExpression}.
	 * @param ctx the parse tree
	 */
	void enterOrExpression(DreamLangParser.OrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#orExpression}.
	 * @param ctx the parse tree
	 */
	void exitOrExpression(DreamLangParser.OrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#andExpression}.
	 * @param ctx the parse tree
	 */
	void enterAndExpression(DreamLangParser.AndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#andExpression}.
	 * @param ctx the parse tree
	 */
	void exitAndExpression(DreamLangParser.AndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#equalityExpression}.
	 * @param ctx the parse tree
	 */
	void enterEqualityExpression(DreamLangParser.EqualityExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#equalityExpression}.
	 * @param ctx the parse tree
	 */
	void exitEqualityExpression(DreamLangParser.EqualityExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#relationalExpression}.
	 * @param ctx the parse tree
	 */
	void enterRelationalExpression(DreamLangParser.RelationalExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#relationalExpression}.
	 * @param ctx the parse tree
	 */
	void exitRelationalExpression(DreamLangParser.RelationalExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#additiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveExpression(DreamLangParser.AdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#additiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveExpression(DreamLangParser.AdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicativeExpression(DreamLangParser.MultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicativeExpression(DreamLangParser.MultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#unaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterUnaryExpression(DreamLangParser.UnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#unaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitUnaryExpression(DreamLangParser.UnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#lambdaExpression}.
	 * @param ctx the parse tree
	 */
	void enterLambdaExpression(DreamLangParser.LambdaExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#lambdaExpression}.
	 * @param ctx the parse tree
	 */
	void exitLambdaExpression(DreamLangParser.LambdaExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DreamLangParser#lambdaBody}.
	 * @param ctx the parse tree
	 */
	void enterLambdaBody(DreamLangParser.LambdaBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link DreamLangParser#lambdaBody}.
	 * @param ctx the parse tree
	 */
	void exitLambdaBody(DreamLangParser.LambdaBodyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PrimaryExpression}
	 * labeled alternative in {@link DreamLangParser#primary}.
	 * @param ctx the parse tree
	 */
	void enterPrimaryExpression(DreamLangParser.PrimaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PrimaryExpression}
	 * labeled alternative in {@link DreamLangParser#primary}.
	 * @param ctx the parse tree
	 */
	void exitPrimaryExpression(DreamLangParser.PrimaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ParenAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterParenAtom(DreamLangParser.ParenAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ParenAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitParenAtom(DreamLangParser.ParenAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BlockAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterBlockAtom(DreamLangParser.BlockAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BlockAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitBlockAtom(DreamLangParser.BlockAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NumberAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNumberAtom(DreamLangParser.NumberAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NumberAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNumberAtom(DreamLangParser.NumberAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code StringAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterStringAtom(DreamLangParser.StringAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code StringAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitStringAtom(DreamLangParser.StringAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterBooleanAtom(DreamLangParser.BooleanAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitBooleanAtom(DreamLangParser.BooleanAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NullAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNullAtom(DreamLangParser.NullAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NullAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNullAtom(DreamLangParser.NullAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IdentifierAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierAtom(DreamLangParser.IdentifierAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IdentifierAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierAtom(DreamLangParser.IdentifierAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FunctionCallSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void enterFunctionCallSuffix(DreamLangParser.FunctionCallSuffixContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FunctionCallSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void exitFunctionCallSuffix(DreamLangParser.FunctionCallSuffixContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FunctionCallWithArgsSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void enterFunctionCallWithArgsSuffix(DreamLangParser.FunctionCallWithArgsSuffixContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FunctionCallWithArgsSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void exitFunctionCallWithArgsSuffix(DreamLangParser.FunctionCallWithArgsSuffixContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayAccessSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void enterArrayAccessSuffix(DreamLangParser.ArrayAccessSuffixContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayAccessSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void exitArrayAccessSuffix(DreamLangParser.ArrayAccessSuffixContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DotAccessSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void enterDotAccessSuffix(DreamLangParser.DotAccessSuffixContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DotAccessSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void exitDotAccessSuffix(DreamLangParser.DotAccessSuffixContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DotAccessNumberSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void enterDotAccessNumberSuffix(DreamLangParser.DotAccessNumberSuffixContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DotAccessNumberSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 */
	void exitDotAccessNumberSuffix(DreamLangParser.DotAccessNumberSuffixContext ctx);
}