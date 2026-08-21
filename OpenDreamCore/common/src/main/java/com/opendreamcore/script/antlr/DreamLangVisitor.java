// Generated from DreamLang.g4 by ANTLR 4.13.1
package com.opendreamcore.script.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link DreamLangParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface DreamLangVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(DreamLangParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(DreamLangParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#loopStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLoopStatement(DreamLangParser.LoopStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#awaitStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAwaitStatement(DreamLangParser.AwaitStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#forEachStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForEachStatement(DreamLangParser.ForEachStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#whileStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileStatement(DreamLangParser.WhileStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#forStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForStatement(DreamLangParser.ForStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#forInit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForInit(DreamLangParser.ForInitContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#ifStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStatement(DreamLangParser.IfStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#returnStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturnStatement(DreamLangParser.ReturnStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#breakStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreakStatement(DreamLangParser.BreakStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#continueStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinueStatement(DreamLangParser.ContinueStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#functionDefinitionStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDefinitionStatement(DreamLangParser.FunctionDefinitionStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#varDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDeclaration(DreamLangParser.VarDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#importStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImportStatement(DreamLangParser.ImportStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#exportStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportStatement(DreamLangParser.ExportStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#exportList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportList(DreamLangParser.ExportListContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#classStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassStatement(DreamLangParser.ClassStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#classBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassBody(DreamLangParser.ClassBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#classMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassMember(DreamLangParser.ClassMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#namespaceStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamespaceStatement(DreamLangParser.NamespaceStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#namespaceBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamespaceBody(DreamLangParser.NamespaceBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#namespaceMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamespaceMember(DreamLangParser.NamespaceMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#constDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstDeclaration(DreamLangParser.ConstDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#tryStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTryStatement(DreamLangParser.TryStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#catchClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCatchClause(DreamLangParser.CatchClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#throwStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitThrowStatement(DreamLangParser.ThrowStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#javaCallStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitJavaCallStatement(DreamLangParser.JavaCallStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#argumentList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgumentList(DreamLangParser.ArgumentListContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#parameterList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterList(DreamLangParser.ParameterListContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#blockStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockStatement(DreamLangParser.BlockStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code LambdaExpr}
	 * labeled alternative in {@link DreamLangParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLambdaExpr(DreamLangParser.LambdaExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ExpressionBody}
	 * labeled alternative in {@link DreamLangParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionBody(DreamLangParser.ExpressionBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#pipeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPipeExpression(DreamLangParser.PipeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#pipeTarget}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPipeTarget(DreamLangParser.PipeTargetContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#ternaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTernaryExpression(DreamLangParser.TernaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#nullCoalesceExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullCoalesceExpression(DreamLangParser.NullCoalesceExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#orExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpression(DreamLangParser.OrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#andExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpression(DreamLangParser.AndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#equalityExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqualityExpression(DreamLangParser.EqualityExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#relationalExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelationalExpression(DreamLangParser.RelationalExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#additiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpression(DreamLangParser.AdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeExpression(DreamLangParser.MultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#unaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryExpression(DreamLangParser.UnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#lambdaExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLambdaExpression(DreamLangParser.LambdaExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DreamLangParser#lambdaBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLambdaBody(DreamLangParser.LambdaBodyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PrimaryExpression}
	 * labeled alternative in {@link DreamLangParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExpression(DreamLangParser.PrimaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ParenAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParenAtom(DreamLangParser.ParenAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BlockAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockAtom(DreamLangParser.BlockAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NumberAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumberAtom(DreamLangParser.NumberAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code StringAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringAtom(DreamLangParser.StringAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BooleanAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanAtom(DreamLangParser.BooleanAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NullAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullAtom(DreamLangParser.NullAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code IdentifierAtom}
	 * labeled alternative in {@link DreamLangParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifierAtom(DreamLangParser.IdentifierAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code FunctionCallSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionCallSuffix(DreamLangParser.FunctionCallSuffixContext ctx);
	/**
	 * Visit a parse tree produced by the {@code FunctionCallWithArgsSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionCallWithArgsSuffix(DreamLangParser.FunctionCallWithArgsSuffixContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayAccessSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayAccessSuffix(DreamLangParser.ArrayAccessSuffixContext ctx);
	/**
	 * Visit a parse tree produced by the {@code DotAccessSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDotAccessSuffix(DreamLangParser.DotAccessSuffixContext ctx);
	/**
	 * Visit a parse tree produced by the {@code DotAccessNumberSuffix}
	 * labeled alternative in {@link DreamLangParser#suffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDotAccessNumberSuffix(DreamLangParser.DotAccessNumberSuffixContext ctx);
}