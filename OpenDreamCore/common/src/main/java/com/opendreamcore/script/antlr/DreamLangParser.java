// Generated from DreamLang.g4 by ANTLR 4.13.1
package com.opendreamcore.script.antlr;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class DreamLangParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		IF=1, ELSE=2, LOOP=3, FOR_EACH=4, WHILE=5, FOR=6, FUNCTION=7, RETURN=8, 
		ARROW=9, BREAK=10, CONTINUE=11, AWAIT=12, IMPORT=13, EXPORT=14, FROM=15, 
		AS=16, CLASS=17, NAMESPACE=18, NEW=19, THIS=20, EXTENDS=21, TRY=22, CATCH=23, 
		FINALLY=24, THROW=25, VAR=26, CONST=27, STATIC=28, DEFAULT=29, BOOLEAN=30, 
		NULL=31, JAVA=32, IDENTIFIER=33, NUMBER=34, MC_COLOR_STRING=35, MC_SECTION_COLOR_STRING=36, 
		STRING=37, COMMA=38, SEMICOLON=39, LPAREN=40, RPAREN=41, LBRACE=42, RBRACE=43, 
		LBRACKET=44, RBRACKET=45, DOT=46, ASSIGN=47, PLUS=48, MINUS=49, MULT=50, 
		DIV=51, MOD=52, EQ=53, NEQ=54, LT=55, GT=56, LE=57, GE=58, AND=59, OR=60, 
		NOT=61, TERNARY=62, NULL_COALESCE=63, COLON=64, PIPE=65, INT=66, WHITESPACE=67, 
		COMMENT=68, LINE_COMMENT=69;
	public static final int
		RULE_program = 0, RULE_statement = 1, RULE_loopStatement = 2, RULE_awaitStatement = 3, 
		RULE_forEachStatement = 4, RULE_whileStatement = 5, RULE_forStatement = 6, 
		RULE_forInit = 7, RULE_ifStatement = 8, RULE_returnStatement = 9, RULE_breakStatement = 10, 
		RULE_continueStatement = 11, RULE_functionDefinitionStatement = 12, RULE_varDeclaration = 13, 
		RULE_importStatement = 14, RULE_exportStatement = 15, RULE_exportList = 16, 
		RULE_classStatement = 17, RULE_classBody = 18, RULE_classMember = 19, 
		RULE_namespaceStatement = 20, RULE_namespaceBody = 21, RULE_namespaceMember = 22, 
		RULE_constDeclaration = 23, RULE_tryStatement = 24, RULE_catchClause = 25, 
		RULE_throwStatement = 26, RULE_javaCallStatement = 27, RULE_argumentList = 28, 
		RULE_parameterList = 29, RULE_blockStatement = 30, RULE_expression = 31, 
		RULE_pipeExpression = 32, RULE_pipeTarget = 33, RULE_ternaryExpression = 34, 
		RULE_nullCoalesceExpression = 35, RULE_orExpression = 36, RULE_andExpression = 37, 
		RULE_equalityExpression = 38, RULE_relationalExpression = 39, RULE_additiveExpression = 40, 
		RULE_multiplicativeExpression = 41, RULE_unaryExpression = 42, RULE_lambdaExpression = 43, 
		RULE_lambdaBody = 44, RULE_primary = 45, RULE_atom = 46, RULE_suffix = 47;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "statement", "loopStatement", "awaitStatement", "forEachStatement", 
			"whileStatement", "forStatement", "forInit", "ifStatement", "returnStatement", 
			"breakStatement", "continueStatement", "functionDefinitionStatement", 
			"varDeclaration", "importStatement", "exportStatement", "exportList", 
			"classStatement", "classBody", "classMember", "namespaceStatement", "namespaceBody", 
			"namespaceMember", "constDeclaration", "tryStatement", "catchClause", 
			"throwStatement", "javaCallStatement", "argumentList", "parameterList", 
			"blockStatement", "expression", "pipeExpression", "pipeTarget", "ternaryExpression", 
			"nullCoalesceExpression", "orExpression", "andExpression", "equalityExpression", 
			"relationalExpression", "additiveExpression", "multiplicativeExpression", 
			"unaryExpression", "lambdaExpression", "lambdaBody", "primary", "atom", 
			"suffix"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, "'=>'", null, null, 
			null, null, null, "'from'", "'as'", null, null, "'new'", null, "'extends'", 
			null, null, null, null, null, null, "'static'", "'default'", null, null, 
			null, null, null, null, null, null, "','", "';'", "'('", "')'", "'{'", 
			"'}'", "'['", "']'", "'.'", "'='", "'+'", "'-'", "'*'", "'/'", "'%'", 
			"'=='", "'!='", "'<'", "'>'", "'<='", "'>='", "'&&'", "'||'", "'!'", 
			"'?'", "'??'", "':'", "'|'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "IF", "ELSE", "LOOP", "FOR_EACH", "WHILE", "FOR", "FUNCTION", "RETURN", 
			"ARROW", "BREAK", "CONTINUE", "AWAIT", "IMPORT", "EXPORT", "FROM", "AS", 
			"CLASS", "NAMESPACE", "NEW", "THIS", "EXTENDS", "TRY", "CATCH", "FINALLY", 
			"THROW", "VAR", "CONST", "STATIC", "DEFAULT", "BOOLEAN", "NULL", "JAVA", 
			"IDENTIFIER", "NUMBER", "MC_COLOR_STRING", "MC_SECTION_COLOR_STRING", 
			"STRING", "COMMA", "SEMICOLON", "LPAREN", "RPAREN", "LBRACE", "RBRACE", 
			"LBRACKET", "RBRACKET", "DOT", "ASSIGN", "PLUS", "MINUS", "MULT", "DIV", 
			"MOD", "EQ", "NEQ", "LT", "GT", "LE", "GE", "AND", "OR", "NOT", "TERNARY", 
			"NULL_COALESCE", "COLON", "PIPE", "INT", "WHITESPACE", "COMMENT", "LINE_COMMENT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "DreamLang.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public DreamLangParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgramContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(DreamLangParser.EOF, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public List<TerminalNode> SEMICOLON() { return getTokens(DreamLangParser.SEMICOLON); }
		public TerminalNode SEMICOLON(int i) {
			return getToken(DreamLangParser.SEMICOLON, i);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitProgram(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitProgram(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(102);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411730769444346L) != 0)) {
				{
				{
				setState(96);
				statement();
				setState(98);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==SEMICOLON) {
					{
					setState(97);
					match(SEMICOLON);
					}
				}

				}
				}
				setState(104);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(105);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public VarDeclarationContext varDeclaration() {
			return getRuleContext(VarDeclarationContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(DreamLangParser.SEMICOLON, 0); }
		public LoopStatementContext loopStatement() {
			return getRuleContext(LoopStatementContext.class,0);
		}
		public ForEachStatementContext forEachStatement() {
			return getRuleContext(ForEachStatementContext.class,0);
		}
		public WhileStatementContext whileStatement() {
			return getRuleContext(WhileStatementContext.class,0);
		}
		public ForStatementContext forStatement() {
			return getRuleContext(ForStatementContext.class,0);
		}
		public IfStatementContext ifStatement() {
			return getRuleContext(IfStatementContext.class,0);
		}
		public AwaitStatementContext awaitStatement() {
			return getRuleContext(AwaitStatementContext.class,0);
		}
		public ReturnStatementContext returnStatement() {
			return getRuleContext(ReturnStatementContext.class,0);
		}
		public BreakStatementContext breakStatement() {
			return getRuleContext(BreakStatementContext.class,0);
		}
		public ContinueStatementContext continueStatement() {
			return getRuleContext(ContinueStatementContext.class,0);
		}
		public FunctionDefinitionStatementContext functionDefinitionStatement() {
			return getRuleContext(FunctionDefinitionStatementContext.class,0);
		}
		public ImportStatementContext importStatement() {
			return getRuleContext(ImportStatementContext.class,0);
		}
		public ExportStatementContext exportStatement() {
			return getRuleContext(ExportStatementContext.class,0);
		}
		public ClassStatementContext classStatement() {
			return getRuleContext(ClassStatementContext.class,0);
		}
		public NamespaceStatementContext namespaceStatement() {
			return getRuleContext(NamespaceStatementContext.class,0);
		}
		public TryStatementContext tryStatement() {
			return getRuleContext(TryStatementContext.class,0);
		}
		public ThrowStatementContext throwStatement() {
			return getRuleContext(ThrowStatementContext.class,0);
		}
		public JavaCallStatementContext javaCallStatement() {
			return getRuleContext(JavaCallStatementContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		try {
			setState(141);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BOOLEAN:
			case NULL:
			case IDENTIFIER:
			case NUMBER:
			case MC_COLOR_STRING:
			case MC_SECTION_COLOR_STRING:
			case STRING:
			case LPAREN:
			case LBRACE:
			case MINUS:
			case NOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(107);
				expression();
				}
				break;
			case VAR:
			case CONST:
				enterOuterAlt(_localctx, 2);
				{
				setState(108);
				varDeclaration();
				setState(110);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
				case 1:
					{
					setState(109);
					match(SEMICOLON);
					}
					break;
				}
				}
				break;
			case LOOP:
				enterOuterAlt(_localctx, 3);
				{
				setState(112);
				loopStatement();
				}
				break;
			case FOR_EACH:
				enterOuterAlt(_localctx, 4);
				{
				setState(113);
				forEachStatement();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 5);
				{
				setState(114);
				whileStatement();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 6);
				{
				setState(115);
				forStatement();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 7);
				{
				setState(116);
				ifStatement();
				}
				break;
			case AWAIT:
				enterOuterAlt(_localctx, 8);
				{
				setState(117);
				awaitStatement();
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 9);
				{
				setState(118);
				returnStatement();
				}
				break;
			case BREAK:
				enterOuterAlt(_localctx, 10);
				{
				setState(119);
				breakStatement();
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 11);
				{
				setState(120);
				continueStatement();
				}
				break;
			case FUNCTION:
				enterOuterAlt(_localctx, 12);
				{
				setState(121);
				functionDefinitionStatement();
				}
				break;
			case IMPORT:
				enterOuterAlt(_localctx, 13);
				{
				setState(122);
				importStatement();
				setState(124);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
				case 1:
					{
					setState(123);
					match(SEMICOLON);
					}
					break;
				}
				}
				break;
			case EXPORT:
				enterOuterAlt(_localctx, 14);
				{
				setState(126);
				exportStatement();
				setState(128);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
				case 1:
					{
					setState(127);
					match(SEMICOLON);
					}
					break;
				}
				}
				break;
			case CLASS:
				enterOuterAlt(_localctx, 15);
				{
				setState(130);
				classStatement();
				}
				break;
			case NAMESPACE:
				enterOuterAlt(_localctx, 16);
				{
				setState(131);
				namespaceStatement();
				}
				break;
			case TRY:
				enterOuterAlt(_localctx, 17);
				{
				setState(132);
				tryStatement();
				}
				break;
			case THROW:
				enterOuterAlt(_localctx, 18);
				{
				setState(133);
				throwStatement();
				setState(135);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
				case 1:
					{
					setState(134);
					match(SEMICOLON);
					}
					break;
				}
				}
				break;
			case NEW:
			case JAVA:
				enterOuterAlt(_localctx, 19);
				{
				setState(137);
				javaCallStatement();
				setState(139);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
				case 1:
					{
					setState(138);
					match(SEMICOLON);
					}
					break;
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LoopStatementContext extends ParserRuleContext {
		public TerminalNode LOOP() { return getToken(DreamLangParser.LOOP, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode COMMA() { return getToken(DreamLangParser.COMMA, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public LoopStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_loopStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterLoopStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitLoopStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitLoopStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LoopStatementContext loopStatement() throws RecognitionException {
		LoopStatementContext _localctx = new LoopStatementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_loopStatement);
		try {
			setState(156);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(143);
				match(LOOP);
				setState(144);
				match(LPAREN);
				setState(145);
				expression();
				setState(146);
				match(COMMA);
				setState(147);
				blockStatement();
				setState(148);
				match(RPAREN);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(150);
				match(LOOP);
				setState(151);
				match(LPAREN);
				setState(152);
				expression();
				setState(153);
				match(RPAREN);
				setState(154);
				statement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AwaitStatementContext extends ParserRuleContext {
		public TerminalNode AWAIT() { return getToken(DreamLangParser.AWAIT, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public TerminalNode COMMA() { return getToken(DreamLangParser.COMMA, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public AwaitStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_awaitStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterAwaitStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitAwaitStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitAwaitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AwaitStatementContext awaitStatement() throws RecognitionException {
		AwaitStatementContext _localctx = new AwaitStatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_awaitStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(158);
			match(AWAIT);
			setState(159);
			match(LPAREN);
			setState(160);
			blockStatement();
			setState(161);
			match(COMMA);
			setState(162);
			expression();
			setState(163);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForEachStatementContext extends ParserRuleContext {
		public TerminalNode FOR_EACH() { return getToken(DreamLangParser.FOR_EACH, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(DreamLangParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(DreamLangParser.COMMA, i);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public ForEachStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forEachStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterForEachStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitForEachStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitForEachStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForEachStatementContext forEachStatement() throws RecognitionException {
		ForEachStatementContext _localctx = new ForEachStatementContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_forEachStatement);
		try {
			setState(190);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(165);
				match(FOR_EACH);
				setState(166);
				match(LPAREN);
				setState(167);
				expression();
				setState(168);
				match(COMMA);
				setState(169);
				expression();
				setState(170);
				match(COMMA);
				setState(171);
				expression();
				setState(172);
				match(COMMA);
				setState(173);
				expression();
				setState(174);
				match(COMMA);
				setState(175);
				blockStatement();
				setState(176);
				match(RPAREN);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(178);
				match(FOR_EACH);
				setState(179);
				match(LPAREN);
				setState(180);
				expression();
				setState(181);
				match(COMMA);
				setState(182);
				expression();
				setState(183);
				match(COMMA);
				setState(184);
				expression();
				setState(185);
				match(COMMA);
				setState(186);
				expression();
				setState(187);
				match(RPAREN);
				setState(188);
				statement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WhileStatementContext extends ParserRuleContext {
		public TerminalNode WHILE() { return getToken(DreamLangParser.WHILE, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public WhileStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whileStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterWhileStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitWhileStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitWhileStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WhileStatementContext whileStatement() throws RecognitionException {
		WhileStatementContext _localctx = new WhileStatementContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_whileStatement);
		try {
			setState(204);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,10,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(192);
				match(WHILE);
				setState(193);
				match(LPAREN);
				setState(194);
				expression();
				setState(195);
				match(RPAREN);
				setState(196);
				statement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(198);
				match(WHILE);
				setState(199);
				match(LPAREN);
				setState(200);
				expression();
				setState(201);
				match(RPAREN);
				setState(202);
				blockStatement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForStatementContext extends ParserRuleContext {
		public TerminalNode FOR() { return getToken(DreamLangParser.FOR, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public List<TerminalNode> SEMICOLON() { return getTokens(DreamLangParser.SEMICOLON); }
		public TerminalNode SEMICOLON(int i) {
			return getToken(DreamLangParser.SEMICOLON, i);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public ForInitContext forInit() {
			return getRuleContext(ForInitContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public ForStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterForStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitForStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitForStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForStatementContext forStatement() throws RecognitionException {
		ForStatementContext _localctx = new ForStatementContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_forStatement);
		int _la;
		try {
			setState(236);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,17,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(206);
				match(FOR);
				setState(207);
				match(LPAREN);
				setState(209);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726435778560L) != 0)) {
					{
					setState(208);
					forInit();
					}
				}

				setState(211);
				match(SEMICOLON);
				setState(213);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(212);
					expression();
					}
				}

				setState(215);
				match(SEMICOLON);
				setState(217);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(216);
					expression();
					}
				}

				setState(219);
				match(RPAREN);
				setState(220);
				statement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(221);
				match(FOR);
				setState(222);
				match(LPAREN);
				setState(224);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726435778560L) != 0)) {
					{
					setState(223);
					forInit();
					}
				}

				setState(226);
				match(SEMICOLON);
				setState(228);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(227);
					expression();
					}
				}

				setState(230);
				match(SEMICOLON);
				setState(232);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(231);
					expression();
					}
				}

				setState(234);
				match(RPAREN);
				setState(235);
				blockStatement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForInitContext extends ParserRuleContext {
		public VarDeclarationContext varDeclaration() {
			return getRuleContext(VarDeclarationContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ForInitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forInit; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterForInit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitForInit(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitForInit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForInitContext forInit() throws RecognitionException {
		ForInitContext _localctx = new ForInitContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_forInit);
		try {
			setState(240);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAR:
			case CONST:
				enterOuterAlt(_localctx, 1);
				{
				setState(238);
				varDeclaration();
				}
				break;
			case BOOLEAN:
			case NULL:
			case IDENTIFIER:
			case NUMBER:
			case MC_COLOR_STRING:
			case MC_SECTION_COLOR_STRING:
			case STRING:
			case LPAREN:
			case LBRACE:
			case MINUS:
			case NOT:
				enterOuterAlt(_localctx, 2);
				{
				setState(239);
				expression();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IfStatementContext extends ParserRuleContext {
		public TerminalNode IF() { return getToken(DreamLangParser.IF, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(DreamLangParser.ELSE, 0); }
		public IfStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterIfStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitIfStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitIfStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IfStatementContext ifStatement() throws RecognitionException {
		IfStatementContext _localctx = new IfStatementContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_ifStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(242);
			match(IF);
			setState(243);
			match(LPAREN);
			setState(244);
			expression();
			setState(245);
			match(RPAREN);
			setState(246);
			statement();
			setState(249);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				{
				setState(247);
				match(ELSE);
				setState(248);
				statement();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReturnStatementContext extends ParserRuleContext {
		public TerminalNode RETURN() { return getToken(DreamLangParser.RETURN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterReturnStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitReturnStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitReturnStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReturnStatementContext returnStatement() throws RecognitionException {
		ReturnStatementContext _localctx = new ReturnStatementContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_returnStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(251);
			match(RETURN);
			setState(253);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
			case 1:
				{
				setState(252);
				expression();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BreakStatementContext extends ParserRuleContext {
		public TerminalNode BREAK() { return getToken(DreamLangParser.BREAK, 0); }
		public BreakStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_breakStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterBreakStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitBreakStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitBreakStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BreakStatementContext breakStatement() throws RecognitionException {
		BreakStatementContext _localctx = new BreakStatementContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_breakStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(255);
			match(BREAK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ContinueStatementContext extends ParserRuleContext {
		public TerminalNode CONTINUE() { return getToken(DreamLangParser.CONTINUE, 0); }
		public ContinueStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continueStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterContinueStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitContinueStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitContinueStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ContinueStatementContext continueStatement() throws RecognitionException {
		ContinueStatementContext _localctx = new ContinueStatementContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_continueStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(257);
			match(CONTINUE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionDefinitionStatementContext extends ParserRuleContext {
		public TerminalNode FUNCTION() { return getToken(DreamLangParser.FUNCTION, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public FunctionDefinitionStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionDefinitionStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterFunctionDefinitionStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitFunctionDefinitionStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitFunctionDefinitionStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionDefinitionStatementContext functionDefinitionStatement() throws RecognitionException {
		FunctionDefinitionStatementContext _localctx = new FunctionDefinitionStatementContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_functionDefinitionStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(259);
			match(FUNCTION);
			setState(260);
			match(IDENTIFIER);
			setState(261);
			match(LPAREN);
			setState(263);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIER) {
				{
				setState(262);
				parameterList();
				}
			}

			setState(265);
			match(RPAREN);
			setState(266);
			blockStatement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VarDeclarationContext extends ParserRuleContext {
		public TerminalNode VAR() { return getToken(DreamLangParser.VAR, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public TerminalNode ASSIGN() { return getToken(DreamLangParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode CONST() { return getToken(DreamLangParser.CONST, 0); }
		public VarDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_varDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterVarDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitVarDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitVarDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VarDeclarationContext varDeclaration() throws RecognitionException {
		VarDeclarationContext _localctx = new VarDeclarationContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_varDeclaration);
		int _la;
		try {
			setState(280);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(268);
				match(VAR);
				setState(269);
				match(IDENTIFIER);
				setState(272);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ASSIGN) {
					{
					setState(270);
					match(ASSIGN);
					setState(271);
					expression();
					}
				}

				}
				break;
			case CONST:
				enterOuterAlt(_localctx, 2);
				{
				setState(274);
				match(CONST);
				setState(275);
				match(IDENTIFIER);
				setState(278);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ASSIGN) {
					{
					setState(276);
					match(ASSIGN);
					setState(277);
					expression();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ImportStatementContext extends ParserRuleContext {
		public TerminalNode IMPORT() { return getToken(DreamLangParser.IMPORT, 0); }
		public TerminalNode STRING() { return getToken(DreamLangParser.STRING, 0); }
		public TerminalNode AS() { return getToken(DreamLangParser.AS, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public TerminalNode FROM() { return getToken(DreamLangParser.FROM, 0); }
		public TerminalNode MULT() { return getToken(DreamLangParser.MULT, 0); }
		public TerminalNode JAVA() { return getToken(DreamLangParser.JAVA, 0); }
		public ImportStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterImportStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitImportStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitImportStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportStatementContext importStatement() throws RecognitionException {
		ImportStatementContext _localctx = new ImportStatementContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_importStatement);
		int _la;
		try {
			setState(301);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,26,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(282);
				match(IMPORT);
				setState(283);
				match(STRING);
				setState(286);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(284);
					match(AS);
					setState(285);
					match(IDENTIFIER);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(288);
				match(IMPORT);
				setState(289);
				match(IDENTIFIER);
				setState(290);
				match(FROM);
				setState(291);
				match(STRING);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(292);
				match(IMPORT);
				setState(293);
				match(MULT);
				setState(294);
				match(AS);
				setState(295);
				match(IDENTIFIER);
				setState(296);
				match(FROM);
				setState(297);
				match(STRING);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(298);
				match(IMPORT);
				setState(299);
				match(JAVA);
				setState(300);
				match(STRING);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExportStatementContext extends ParserRuleContext {
		public TerminalNode EXPORT() { return getToken(DreamLangParser.EXPORT, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public TerminalNode LBRACE() { return getToken(DreamLangParser.LBRACE, 0); }
		public ExportListContext exportList() {
			return getRuleContext(ExportListContext.class,0);
		}
		public TerminalNode RBRACE() { return getToken(DreamLangParser.RBRACE, 0); }
		public TerminalNode VAR() { return getToken(DreamLangParser.VAR, 0); }
		public TerminalNode ASSIGN() { return getToken(DreamLangParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode FUNCTION() { return getToken(DreamLangParser.FUNCTION, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public TerminalNode DEFAULT() { return getToken(DreamLangParser.DEFAULT, 0); }
		public ExportStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exportStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterExportStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitExportStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitExportStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExportStatementContext exportStatement() throws RecognitionException {
		ExportStatementContext _localctx = new ExportStatementContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_exportStatement);
		int _la;
		try {
			setState(329);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,29,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(303);
				match(EXPORT);
				setState(304);
				match(IDENTIFIER);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(305);
				match(EXPORT);
				setState(306);
				match(LBRACE);
				setState(307);
				exportList();
				setState(308);
				match(RBRACE);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(310);
				match(EXPORT);
				setState(311);
				match(VAR);
				setState(312);
				match(IDENTIFIER);
				setState(315);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ASSIGN) {
					{
					setState(313);
					match(ASSIGN);
					setState(314);
					expression();
					}
				}

				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(317);
				match(EXPORT);
				setState(318);
				match(FUNCTION);
				setState(319);
				match(IDENTIFIER);
				setState(320);
				match(LPAREN);
				setState(322);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==IDENTIFIER) {
					{
					setState(321);
					parameterList();
					}
				}

				setState(324);
				match(RPAREN);
				setState(325);
				blockStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(326);
				match(EXPORT);
				setState(327);
				match(DEFAULT);
				setState(328);
				expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExportListContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(DreamLangParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(DreamLangParser.IDENTIFIER, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(DreamLangParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(DreamLangParser.COMMA, i);
		}
		public ExportListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exportList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterExportList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitExportList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitExportList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExportListContext exportList() throws RecognitionException {
		ExportListContext _localctx = new ExportListContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_exportList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(331);
			match(IDENTIFIER);
			setState(336);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(332);
				match(COMMA);
				setState(333);
				match(IDENTIFIER);
				}
				}
				setState(338);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ClassStatementContext extends ParserRuleContext {
		public TerminalNode CLASS() { return getToken(DreamLangParser.CLASS, 0); }
		public List<TerminalNode> IDENTIFIER() { return getTokens(DreamLangParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(DreamLangParser.IDENTIFIER, i);
		}
		public ClassBodyContext classBody() {
			return getRuleContext(ClassBodyContext.class,0);
		}
		public TerminalNode EXTENDS() { return getToken(DreamLangParser.EXTENDS, 0); }
		public ClassStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterClassStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitClassStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitClassStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ClassStatementContext classStatement() throws RecognitionException {
		ClassStatementContext _localctx = new ClassStatementContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_classStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(339);
			match(CLASS);
			setState(340);
			match(IDENTIFIER);
			setState(343);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(341);
				match(EXTENDS);
				setState(342);
				match(IDENTIFIER);
				}
			}

			setState(345);
			classBody();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ClassBodyContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(DreamLangParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(DreamLangParser.RBRACE, 0); }
		public List<ClassMemberContext> classMember() {
			return getRuleContexts(ClassMemberContext.class);
		}
		public ClassMemberContext classMember(int i) {
			return getRuleContext(ClassMemberContext.class,i);
		}
		public ClassBodyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classBody; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterClassBody(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitClassBody(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitClassBody(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ClassBodyContext classBody() throws RecognitionException {
		ClassBodyContext _localctx = new ClassBodyContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_classBody);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(347);
			match(LBRACE);
			setState(351);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 469762176L) != 0)) {
				{
				{
				setState(348);
				classMember();
				}
				}
				setState(353);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(354);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ClassMemberContext extends ParserRuleContext {
		public VarDeclarationContext varDeclaration() {
			return getRuleContext(VarDeclarationContext.class,0);
		}
		public FunctionDefinitionStatementContext functionDefinitionStatement() {
			return getRuleContext(FunctionDefinitionStatementContext.class,0);
		}
		public TerminalNode STATIC() { return getToken(DreamLangParser.STATIC, 0); }
		public ClassMemberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classMember; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterClassMember(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitClassMember(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitClassMember(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ClassMemberContext classMember() throws RecognitionException {
		ClassMemberContext _localctx = new ClassMemberContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_classMember);
		try {
			setState(362);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,33,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(356);
				varDeclaration();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(357);
				functionDefinitionStatement();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(358);
				match(STATIC);
				setState(359);
				functionDefinitionStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(360);
				match(STATIC);
				setState(361);
				varDeclaration();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NamespaceStatementContext extends ParserRuleContext {
		public TerminalNode NAMESPACE() { return getToken(DreamLangParser.NAMESPACE, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public NamespaceBodyContext namespaceBody() {
			return getRuleContext(NamespaceBodyContext.class,0);
		}
		public NamespaceStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namespaceStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterNamespaceStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitNamespaceStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitNamespaceStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamespaceStatementContext namespaceStatement() throws RecognitionException {
		NamespaceStatementContext _localctx = new NamespaceStatementContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_namespaceStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(364);
			match(NAMESPACE);
			setState(365);
			match(IDENTIFIER);
			setState(366);
			namespaceBody();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NamespaceBodyContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(DreamLangParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(DreamLangParser.RBRACE, 0); }
		public List<NamespaceMemberContext> namespaceMember() {
			return getRuleContexts(NamespaceMemberContext.class);
		}
		public NamespaceMemberContext namespaceMember(int i) {
			return getRuleContext(NamespaceMemberContext.class,i);
		}
		public NamespaceBodyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namespaceBody; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterNamespaceBody(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitNamespaceBody(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitNamespaceBody(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamespaceBodyContext namespaceBody() throws RecognitionException {
		NamespaceBodyContext _localctx = new NamespaceBodyContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_namespaceBody);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(368);
			match(LBRACE);
			setState(372);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 201326720L) != 0)) {
				{
				{
				setState(369);
				namespaceMember();
				}
				}
				setState(374);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(375);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NamespaceMemberContext extends ParserRuleContext {
		public VarDeclarationContext varDeclaration() {
			return getRuleContext(VarDeclarationContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(DreamLangParser.SEMICOLON, 0); }
		public ConstDeclarationContext constDeclaration() {
			return getRuleContext(ConstDeclarationContext.class,0);
		}
		public FunctionDefinitionStatementContext functionDefinitionStatement() {
			return getRuleContext(FunctionDefinitionStatementContext.class,0);
		}
		public NamespaceMemberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namespaceMember; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterNamespaceMember(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitNamespaceMember(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitNamespaceMember(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamespaceMemberContext namespaceMember() throws RecognitionException {
		NamespaceMemberContext _localctx = new NamespaceMemberContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_namespaceMember);
		int _la;
		try {
			setState(386);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(377);
				varDeclaration();
				setState(379);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==SEMICOLON) {
					{
					setState(378);
					match(SEMICOLON);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(381);
				constDeclaration();
				setState(383);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==SEMICOLON) {
					{
					setState(382);
					match(SEMICOLON);
					}
				}

				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(385);
				functionDefinitionStatement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConstDeclarationContext extends ParserRuleContext {
		public TerminalNode CONST() { return getToken(DreamLangParser.CONST, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public TerminalNode ASSIGN() { return getToken(DreamLangParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ConstDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterConstDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitConstDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitConstDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstDeclarationContext constDeclaration() throws RecognitionException {
		ConstDeclarationContext _localctx = new ConstDeclarationContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_constDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(388);
			match(CONST);
			setState(389);
			match(IDENTIFIER);
			setState(392);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(390);
				match(ASSIGN);
				setState(391);
				expression();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TryStatementContext extends ParserRuleContext {
		public TerminalNode TRY() { return getToken(DreamLangParser.TRY, 0); }
		public List<BlockStatementContext> blockStatement() {
			return getRuleContexts(BlockStatementContext.class);
		}
		public BlockStatementContext blockStatement(int i) {
			return getRuleContext(BlockStatementContext.class,i);
		}
		public CatchClauseContext catchClause() {
			return getRuleContext(CatchClauseContext.class,0);
		}
		public TerminalNode FINALLY() { return getToken(DreamLangParser.FINALLY, 0); }
		public TryStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tryStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterTryStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitTryStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitTryStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TryStatementContext tryStatement() throws RecognitionException {
		TryStatementContext _localctx = new TryStatementContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_tryStatement);
		int _la;
		try {
			setState(406);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,40,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(394);
				match(TRY);
				setState(395);
				blockStatement();
				setState(396);
				catchClause();
				setState(399);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FINALLY) {
					{
					setState(397);
					match(FINALLY);
					setState(398);
					blockStatement();
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(401);
				match(TRY);
				setState(402);
				blockStatement();
				setState(403);
				match(FINALLY);
				setState(404);
				blockStatement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CatchClauseContext extends ParserRuleContext {
		public TerminalNode CATCH() { return getToken(DreamLangParser.CATCH, 0); }
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public CatchClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_catchClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterCatchClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitCatchClause(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitCatchClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CatchClauseContext catchClause() throws RecognitionException {
		CatchClauseContext _localctx = new CatchClauseContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_catchClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(408);
			match(CATCH);
			setState(409);
			match(LPAREN);
			setState(410);
			match(IDENTIFIER);
			setState(411);
			match(RPAREN);
			setState(412);
			blockStatement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ThrowStatementContext extends ParserRuleContext {
		public TerminalNode THROW() { return getToken(DreamLangParser.THROW, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ThrowStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_throwStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterThrowStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitThrowStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitThrowStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ThrowStatementContext throwStatement() throws RecognitionException {
		ThrowStatementContext _localctx = new ThrowStatementContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_throwStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(414);
			match(THROW);
			setState(415);
			expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class JavaCallStatementContext extends ParserRuleContext {
		public TerminalNode JAVA() { return getToken(DreamLangParser.JAVA, 0); }
		public List<TerminalNode> DOT() { return getTokens(DreamLangParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(DreamLangParser.DOT, i);
		}
		public List<TerminalNode> IDENTIFIER() { return getTokens(DreamLangParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(DreamLangParser.IDENTIFIER, i);
		}
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public List<TerminalNode> STRING() { return getTokens(DreamLangParser.STRING); }
		public TerminalNode STRING(int i) {
			return getToken(DreamLangParser.STRING, i);
		}
		public TerminalNode COMMA() { return getToken(DreamLangParser.COMMA, 0); }
		public TerminalNode NEW() { return getToken(DreamLangParser.NEW, 0); }
		public JavaCallStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_javaCallStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterJavaCallStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitJavaCallStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitJavaCallStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final JavaCallStatementContext javaCallStatement() throws RecognitionException {
		JavaCallStatementContext _localctx = new JavaCallStatementContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_javaCallStatement);
		int _la;
		try {
			setState(457);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,46,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(417);
				match(JAVA);
				setState(418);
				match(DOT);
				setState(419);
				match(IDENTIFIER);
				setState(424);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==DOT) {
					{
					{
					setState(420);
					match(DOT);
					setState(421);
					match(IDENTIFIER);
					}
					}
					setState(426);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(427);
				match(LPAREN);
				setState(429);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(428);
					argumentList();
					}
				}

				setState(431);
				match(RPAREN);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(432);
				match(JAVA);
				setState(433);
				match(LPAREN);
				setState(434);
				match(STRING);
				setState(435);
				match(COMMA);
				setState(436);
				match(STRING);
				setState(438);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(437);
					argumentList();
					}
				}

				setState(440);
				match(RPAREN);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(441);
				match(NEW);
				setState(442);
				match(JAVA);
				setState(443);
				match(DOT);
				setState(444);
				match(IDENTIFIER);
				setState(449);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==DOT) {
					{
					{
					setState(445);
					match(DOT);
					setState(446);
					match(IDENTIFIER);
					}
					}
					setState(451);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(452);
				match(LPAREN);
				setState(454);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(453);
					argumentList();
					}
				}

				setState(456);
				match(RPAREN);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArgumentListContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(DreamLangParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(DreamLangParser.COMMA, i);
		}
		public ArgumentListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterArgumentList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitArgumentList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitArgumentList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentListContext argumentList() throws RecognitionException {
		ArgumentListContext _localctx = new ArgumentListContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_argumentList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(459);
			expression();
			setState(464);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(460);
				match(COMMA);
				setState(461);
				expression();
				}
				}
				setState(466);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterListContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(DreamLangParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(DreamLangParser.IDENTIFIER, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(DreamLangParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(DreamLangParser.COMMA, i);
		}
		public ParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterParameterList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitParameterList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterListContext parameterList() throws RecognitionException {
		ParameterListContext _localctx = new ParameterListContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_parameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(467);
			match(IDENTIFIER);
			setState(472);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(468);
				match(COMMA);
				setState(469);
				match(IDENTIFIER);
				}
				}
				setState(474);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockStatementContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(DreamLangParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(DreamLangParser.RBRACE, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public List<TerminalNode> SEMICOLON() { return getTokens(DreamLangParser.SEMICOLON); }
		public TerminalNode SEMICOLON(int i) {
			return getToken(DreamLangParser.SEMICOLON, i);
		}
		public BlockStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blockStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterBlockStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitBlockStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitBlockStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockStatementContext blockStatement() throws RecognitionException {
		BlockStatementContext _localctx = new BlockStatementContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_blockStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(475);
			match(LBRACE);
			setState(482);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411730769444346L) != 0)) {
				{
				{
				setState(476);
				statement();
				setState(478);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==SEMICOLON) {
					{
					setState(477);
					match(SEMICOLON);
					}
				}

				}
				}
				setState(484);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(485);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class LambdaExprContext extends ExpressionContext {
		public LambdaExpressionContext lambdaExpression() {
			return getRuleContext(LambdaExpressionContext.class,0);
		}
		public LambdaExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterLambdaExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitLambdaExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitLambdaExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionBodyContext extends ExpressionContext {
		public PipeExpressionContext pipeExpression() {
			return getRuleContext(PipeExpressionContext.class,0);
		}
		public ExpressionBodyContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterExpressionBody(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitExpressionBody(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitExpressionBody(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_expression);
		try {
			setState(489);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,51,_ctx) ) {
			case 1:
				_localctx = new LambdaExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(487);
				lambdaExpression();
				}
				break;
			case 2:
				_localctx = new ExpressionBodyContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(488);
				pipeExpression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PipeExpressionContext extends ParserRuleContext {
		public TernaryExpressionContext ternaryExpression() {
			return getRuleContext(TernaryExpressionContext.class,0);
		}
		public List<TerminalNode> PIPE() { return getTokens(DreamLangParser.PIPE); }
		public TerminalNode PIPE(int i) {
			return getToken(DreamLangParser.PIPE, i);
		}
		public List<PipeTargetContext> pipeTarget() {
			return getRuleContexts(PipeTargetContext.class);
		}
		public PipeTargetContext pipeTarget(int i) {
			return getRuleContext(PipeTargetContext.class,i);
		}
		public PipeExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pipeExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterPipeExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitPipeExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitPipeExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PipeExpressionContext pipeExpression() throws RecognitionException {
		PipeExpressionContext _localctx = new PipeExpressionContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_pipeExpression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(491);
			ternaryExpression();
			setState(496);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(492);
					match(PIPE);
					setState(493);
					pipeTarget();
					}
					} 
				}
				setState(498);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PipeTargetContext extends ParserRuleContext {
		public UnaryExpressionContext unaryExpression() {
			return getRuleContext(UnaryExpressionContext.class,0);
		}
		public LambdaExpressionContext lambdaExpression() {
			return getRuleContext(LambdaExpressionContext.class,0);
		}
		public PipeTargetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pipeTarget; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterPipeTarget(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitPipeTarget(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitPipeTarget(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PipeTargetContext pipeTarget() throws RecognitionException {
		PipeTargetContext _localctx = new PipeTargetContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_pipeTarget);
		try {
			setState(501);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,53,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(499);
				unaryExpression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(500);
				lambdaExpression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TernaryExpressionContext extends ParserRuleContext {
		public NullCoalesceExpressionContext nullCoalesceExpression() {
			return getRuleContext(NullCoalesceExpressionContext.class,0);
		}
		public TerminalNode TERNARY() { return getToken(DreamLangParser.TERNARY, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode COLON() { return getToken(DreamLangParser.COLON, 0); }
		public TernaryExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ternaryExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterTernaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitTernaryExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitTernaryExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TernaryExpressionContext ternaryExpression() throws RecognitionException {
		TernaryExpressionContext _localctx = new TernaryExpressionContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_ternaryExpression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(503);
			nullCoalesceExpression();
			setState(511);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
			case 1:
				{
				setState(504);
				match(TERNARY);
				setState(505);
				expression();
				setState(506);
				match(COLON);
				setState(507);
				expression();
				}
				break;
			case 2:
				{
				setState(509);
				match(TERNARY);
				setState(510);
				expression();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NullCoalesceExpressionContext extends ParserRuleContext {
		public List<OrExpressionContext> orExpression() {
			return getRuleContexts(OrExpressionContext.class);
		}
		public OrExpressionContext orExpression(int i) {
			return getRuleContext(OrExpressionContext.class,i);
		}
		public List<TerminalNode> NULL_COALESCE() { return getTokens(DreamLangParser.NULL_COALESCE); }
		public TerminalNode NULL_COALESCE(int i) {
			return getToken(DreamLangParser.NULL_COALESCE, i);
		}
		public NullCoalesceExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nullCoalesceExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterNullCoalesceExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitNullCoalesceExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitNullCoalesceExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NullCoalesceExpressionContext nullCoalesceExpression() throws RecognitionException {
		NullCoalesceExpressionContext _localctx = new NullCoalesceExpressionContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_nullCoalesceExpression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(513);
			orExpression();
			setState(518);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(514);
					match(NULL_COALESCE);
					setState(515);
					orExpression();
					}
					} 
				}
				setState(520);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OrExpressionContext extends ParserRuleContext {
		public List<AndExpressionContext> andExpression() {
			return getRuleContexts(AndExpressionContext.class);
		}
		public AndExpressionContext andExpression(int i) {
			return getRuleContext(AndExpressionContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(DreamLangParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(DreamLangParser.OR, i);
		}
		public OrExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterOrExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitOrExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitOrExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final OrExpressionContext orExpression() throws RecognitionException {
		OrExpressionContext _localctx = new OrExpressionContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_orExpression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(521);
			andExpression();
			setState(526);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(522);
					match(OR);
					setState(523);
					andExpression();
					}
					} 
				}
				setState(528);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AndExpressionContext extends ParserRuleContext {
		public List<EqualityExpressionContext> equalityExpression() {
			return getRuleContexts(EqualityExpressionContext.class);
		}
		public EqualityExpressionContext equalityExpression(int i) {
			return getRuleContext(EqualityExpressionContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(DreamLangParser.AND); }
		public TerminalNode AND(int i) {
			return getToken(DreamLangParser.AND, i);
		}
		public AndExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_andExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterAndExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitAndExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitAndExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AndExpressionContext andExpression() throws RecognitionException {
		AndExpressionContext _localctx = new AndExpressionContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_andExpression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(529);
			equalityExpression();
			setState(534);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,57,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(530);
					match(AND);
					setState(531);
					equalityExpression();
					}
					} 
				}
				setState(536);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,57,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EqualityExpressionContext extends ParserRuleContext {
		public List<RelationalExpressionContext> relationalExpression() {
			return getRuleContexts(RelationalExpressionContext.class);
		}
		public RelationalExpressionContext relationalExpression(int i) {
			return getRuleContext(RelationalExpressionContext.class,i);
		}
		public List<TerminalNode> EQ() { return getTokens(DreamLangParser.EQ); }
		public TerminalNode EQ(int i) {
			return getToken(DreamLangParser.EQ, i);
		}
		public List<TerminalNode> NEQ() { return getTokens(DreamLangParser.NEQ); }
		public TerminalNode NEQ(int i) {
			return getToken(DreamLangParser.NEQ, i);
		}
		public EqualityExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_equalityExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterEqualityExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitEqualityExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitEqualityExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EqualityExpressionContext equalityExpression() throws RecognitionException {
		EqualityExpressionContext _localctx = new EqualityExpressionContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_equalityExpression);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(537);
			relationalExpression();
			setState(542);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,58,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(538);
					_la = _input.LA(1);
					if ( !(_la==EQ || _la==NEQ) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(539);
					relationalExpression();
					}
					} 
				}
				setState(544);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,58,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RelationalExpressionContext extends ParserRuleContext {
		public List<AdditiveExpressionContext> additiveExpression() {
			return getRuleContexts(AdditiveExpressionContext.class);
		}
		public AdditiveExpressionContext additiveExpression(int i) {
			return getRuleContext(AdditiveExpressionContext.class,i);
		}
		public List<TerminalNode> GT() { return getTokens(DreamLangParser.GT); }
		public TerminalNode GT(int i) {
			return getToken(DreamLangParser.GT, i);
		}
		public List<TerminalNode> GE() { return getTokens(DreamLangParser.GE); }
		public TerminalNode GE(int i) {
			return getToken(DreamLangParser.GE, i);
		}
		public List<TerminalNode> LT() { return getTokens(DreamLangParser.LT); }
		public TerminalNode LT(int i) {
			return getToken(DreamLangParser.LT, i);
		}
		public List<TerminalNode> LE() { return getTokens(DreamLangParser.LE); }
		public TerminalNode LE(int i) {
			return getToken(DreamLangParser.LE, i);
		}
		public RelationalExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relationalExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterRelationalExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitRelationalExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitRelationalExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RelationalExpressionContext relationalExpression() throws RecognitionException {
		RelationalExpressionContext _localctx = new RelationalExpressionContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_relationalExpression);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(545);
			additiveExpression();
			setState(550);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,59,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(546);
					_la = _input.LA(1);
					if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 540431955284459520L) != 0)) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(547);
					additiveExpression();
					}
					} 
				}
				setState(552);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,59,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AdditiveExpressionContext extends ParserRuleContext {
		public List<MultiplicativeExpressionContext> multiplicativeExpression() {
			return getRuleContexts(MultiplicativeExpressionContext.class);
		}
		public MultiplicativeExpressionContext multiplicativeExpression(int i) {
			return getRuleContext(MultiplicativeExpressionContext.class,i);
		}
		public List<TerminalNode> PLUS() { return getTokens(DreamLangParser.PLUS); }
		public TerminalNode PLUS(int i) {
			return getToken(DreamLangParser.PLUS, i);
		}
		public List<TerminalNode> MINUS() { return getTokens(DreamLangParser.MINUS); }
		public TerminalNode MINUS(int i) {
			return getToken(DreamLangParser.MINUS, i);
		}
		public AdditiveExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_additiveExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterAdditiveExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitAdditiveExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitAdditiveExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AdditiveExpressionContext additiveExpression() throws RecognitionException {
		AdditiveExpressionContext _localctx = new AdditiveExpressionContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_additiveExpression);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(553);
			multiplicativeExpression();
			setState(558);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,60,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(554);
					_la = _input.LA(1);
					if ( !(_la==PLUS || _la==MINUS) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(555);
					multiplicativeExpression();
					}
					} 
				}
				setState(560);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,60,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MultiplicativeExpressionContext extends ParserRuleContext {
		public List<UnaryExpressionContext> unaryExpression() {
			return getRuleContexts(UnaryExpressionContext.class);
		}
		public UnaryExpressionContext unaryExpression(int i) {
			return getRuleContext(UnaryExpressionContext.class,i);
		}
		public List<TerminalNode> MULT() { return getTokens(DreamLangParser.MULT); }
		public TerminalNode MULT(int i) {
			return getToken(DreamLangParser.MULT, i);
		}
		public List<TerminalNode> DIV() { return getTokens(DreamLangParser.DIV); }
		public TerminalNode DIV(int i) {
			return getToken(DreamLangParser.DIV, i);
		}
		public List<TerminalNode> MOD() { return getTokens(DreamLangParser.MOD); }
		public TerminalNode MOD(int i) {
			return getToken(DreamLangParser.MOD, i);
		}
		public MultiplicativeExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_multiplicativeExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterMultiplicativeExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitMultiplicativeExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitMultiplicativeExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MultiplicativeExpressionContext multiplicativeExpression() throws RecognitionException {
		MultiplicativeExpressionContext _localctx = new MultiplicativeExpressionContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_multiplicativeExpression);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(561);
			unaryExpression();
			setState(566);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,61,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(562);
					_la = _input.LA(1);
					if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 7881299347898368L) != 0)) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(563);
					unaryExpression();
					}
					} 
				}
				setState(568);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,61,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnaryExpressionContext extends ParserRuleContext {
		public UnaryExpressionContext unaryExpression() {
			return getRuleContext(UnaryExpressionContext.class,0);
		}
		public TerminalNode NOT() { return getToken(DreamLangParser.NOT, 0); }
		public TerminalNode MINUS() { return getToken(DreamLangParser.MINUS, 0); }
		public PrimaryContext primary() {
			return getRuleContext(PrimaryContext.class,0);
		}
		public UnaryExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unaryExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterUnaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitUnaryExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitUnaryExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnaryExpressionContext unaryExpression() throws RecognitionException {
		UnaryExpressionContext _localctx = new UnaryExpressionContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_unaryExpression);
		int _la;
		try {
			setState(572);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MINUS:
			case NOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(569);
				_la = _input.LA(1);
				if ( !(_la==MINUS || _la==NOT) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(570);
				unaryExpression();
				}
				break;
			case BOOLEAN:
			case NULL:
			case IDENTIFIER:
			case NUMBER:
			case MC_COLOR_STRING:
			case MC_SECTION_COLOR_STRING:
			case STRING:
			case LPAREN:
			case LBRACE:
				enterOuterAlt(_localctx, 2);
				{
				setState(571);
				primary();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LambdaExpressionContext extends ParserRuleContext {
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public TerminalNode ARROW() { return getToken(DreamLangParser.ARROW, 0); }
		public LambdaBodyContext lambdaBody() {
			return getRuleContext(LambdaBodyContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public LambdaExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lambdaExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterLambdaExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitLambdaExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitLambdaExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LambdaExpressionContext lambdaExpression() throws RecognitionException {
		LambdaExpressionContext _localctx = new LambdaExpressionContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_lambdaExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(574);
			match(LPAREN);
			setState(576);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIER) {
				{
				setState(575);
				parameterList();
				}
			}

			setState(578);
			match(RPAREN);
			setState(579);
			match(ARROW);
			setState(580);
			lambdaBody();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LambdaBodyContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public LambdaBodyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lambdaBody; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterLambdaBody(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitLambdaBody(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitLambdaBody(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LambdaBodyContext lambdaBody() throws RecognitionException {
		LambdaBodyContext _localctx = new LambdaBodyContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_lambdaBody);
		try {
			setState(584);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,64,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(582);
				expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(583);
				blockStatement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PrimaryContext extends ParserRuleContext {
		public PrimaryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primary; }
	 
		public PrimaryContext() { }
		public void copyFrom(PrimaryContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PrimaryExpressionContext extends PrimaryContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public List<SuffixContext> suffix() {
			return getRuleContexts(SuffixContext.class);
		}
		public SuffixContext suffix(int i) {
			return getRuleContext(SuffixContext.class,i);
		}
		public TerminalNode ASSIGN() { return getToken(DreamLangParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public PrimaryExpressionContext(PrimaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterPrimaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitPrimaryExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitPrimaryExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PrimaryContext primary() throws RecognitionException {
		PrimaryContext _localctx = new PrimaryContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_primary);
		int _la;
		try {
			int _alt;
			_localctx = new PrimaryExpressionContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(586);
			atom();
			setState(590);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,65,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(587);
					suffix();
					}
					} 
				}
				setState(592);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,65,_ctx);
			}
			setState(595);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(593);
				match(ASSIGN);
				setState(594);
				expression();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AtomContext extends ParserRuleContext {
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	 
		public AtomContext() { }
		public void copyFrom(AtomContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumberAtomContext extends AtomContext {
		public TerminalNode NUMBER() { return getToken(DreamLangParser.NUMBER, 0); }
		public NumberAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterNumberAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitNumberAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitNumberAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BlockAtomContext extends AtomContext {
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public BlockAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterBlockAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitBlockAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitBlockAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringAtomContext extends AtomContext {
		public TerminalNode STRING() { return getToken(DreamLangParser.STRING, 0); }
		public TerminalNode MC_COLOR_STRING() { return getToken(DreamLangParser.MC_COLOR_STRING, 0); }
		public TerminalNode MC_SECTION_COLOR_STRING() { return getToken(DreamLangParser.MC_SECTION_COLOR_STRING, 0); }
		public StringAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterStringAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitStringAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitStringAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NullAtomContext extends AtomContext {
		public TerminalNode NULL() { return getToken(DreamLangParser.NULL, 0); }
		public NullAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterNullAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitNullAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitNullAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParenAtomContext extends AtomContext {
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public ParenAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterParenAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitParenAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitParenAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanAtomContext extends AtomContext {
		public TerminalNode BOOLEAN() { return getToken(DreamLangParser.BOOLEAN, 0); }
		public BooleanAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterBooleanAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitBooleanAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitBooleanAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierAtomContext extends AtomContext {
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public IdentifierAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterIdentifierAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitIdentifierAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitIdentifierAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_atom);
		try {
			setState(609);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LPAREN:
				_localctx = new ParenAtomContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(597);
				match(LPAREN);
				setState(598);
				expression();
				setState(599);
				match(RPAREN);
				}
				break;
			case LBRACE:
				_localctx = new BlockAtomContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(601);
				blockStatement();
				}
				break;
			case NUMBER:
				_localctx = new NumberAtomContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(602);
				match(NUMBER);
				}
				break;
			case STRING:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(603);
				match(STRING);
				}
				break;
			case MC_COLOR_STRING:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(604);
				match(MC_COLOR_STRING);
				}
				break;
			case MC_SECTION_COLOR_STRING:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(605);
				match(MC_SECTION_COLOR_STRING);
				}
				break;
			case BOOLEAN:
				_localctx = new BooleanAtomContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(606);
				match(BOOLEAN);
				}
				break;
			case NULL:
				_localctx = new NullAtomContext(_localctx);
				enterOuterAlt(_localctx, 8);
				{
				setState(607);
				match(NULL);
				}
				break;
			case IDENTIFIER:
				_localctx = new IdentifierAtomContext(_localctx);
				enterOuterAlt(_localctx, 9);
				{
				setState(608);
				match(IDENTIFIER);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SuffixContext extends ParserRuleContext {
		public SuffixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_suffix; }
	 
		public SuffixContext() { }
		public void copyFrom(SuffixContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayAccessSuffixContext extends SuffixContext {
		public TerminalNode LBRACKET() { return getToken(DreamLangParser.LBRACKET, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RBRACKET() { return getToken(DreamLangParser.RBRACKET, 0); }
		public ArrayAccessSuffixContext(SuffixContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterArrayAccessSuffix(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitArrayAccessSuffix(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitArrayAccessSuffix(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DotAccessSuffixContext extends SuffixContext {
		public TerminalNode DOT() { return getToken(DreamLangParser.DOT, 0); }
		public TerminalNode IDENTIFIER() { return getToken(DreamLangParser.IDENTIFIER, 0); }
		public DotAccessSuffixContext(SuffixContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterDotAccessSuffix(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitDotAccessSuffix(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitDotAccessSuffix(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DotAccessNumberSuffixContext extends SuffixContext {
		public TerminalNode DOT() { return getToken(DreamLangParser.DOT, 0); }
		public TerminalNode NUMBER() { return getToken(DreamLangParser.NUMBER, 0); }
		public DotAccessNumberSuffixContext(SuffixContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterDotAccessNumberSuffix(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitDotAccessNumberSuffix(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitDotAccessNumberSuffix(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FunctionCallSuffixContext extends SuffixContext {
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public FunctionCallSuffixContext(SuffixContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterFunctionCallSuffix(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitFunctionCallSuffix(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitFunctionCallSuffix(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FunctionCallWithArgsSuffixContext extends SuffixContext {
		public TerminalNode LPAREN() { return getToken(DreamLangParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(DreamLangParser.RPAREN, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(DreamLangParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(DreamLangParser.COMMA, i);
		}
		public FunctionCallWithArgsSuffixContext(SuffixContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).enterFunctionCallWithArgsSuffix(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DreamLangListener ) ((DreamLangListener)listener).exitFunctionCallWithArgsSuffix(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DreamLangVisitor ) return ((DreamLangVisitor<? extends T>)visitor).visitFunctionCallWithArgsSuffix(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SuffixContext suffix() throws RecognitionException {
		SuffixContext _localctx = new SuffixContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_suffix);
		int _la;
		try {
			setState(633);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,70,_ctx) ) {
			case 1:
				_localctx = new FunctionCallSuffixContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(611);
				match(LPAREN);
				setState(612);
				match(RPAREN);
				}
				break;
			case 2:
				_localctx = new FunctionCallWithArgsSuffixContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(613);
				match(LPAREN);
				setState(622);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2306411726234451968L) != 0)) {
					{
					setState(614);
					expression();
					setState(619);
					_errHandler.sync(this);
					_la = _input.LA(1);
					while (_la==COMMA) {
						{
						{
						setState(615);
						match(COMMA);
						setState(616);
						expression();
						}
						}
						setState(621);
						_errHandler.sync(this);
						_la = _input.LA(1);
					}
					}
				}

				setState(624);
				match(RPAREN);
				}
				break;
			case 3:
				_localctx = new ArrayAccessSuffixContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(625);
				match(LBRACKET);
				setState(626);
				expression();
				setState(627);
				match(RBRACKET);
				}
				break;
			case 4:
				_localctx = new DotAccessSuffixContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(629);
				match(DOT);
				setState(630);
				match(IDENTIFIER);
				}
				break;
			case 5:
				_localctx = new DotAccessNumberSuffixContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(631);
				match(DOT);
				setState(632);
				match(NUMBER);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001E\u027c\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
		"-\u0007-\u0002.\u0007.\u0002/\u0007/\u0001\u0000\u0001\u0000\u0003\u0000"+
		"c\b\u0000\u0005\u0000e\b\u0000\n\u0000\f\u0000h\t\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001o\b\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003"+
		"\u0001}\b\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u0081\b\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u0088"+
		"\b\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u008c\b\u0001\u0003\u0001"+
		"\u008e\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0003\u0002\u009d\b\u0002\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0003\u0004\u00bf\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0003\u0005\u00cd\b\u0005\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0003\u0006\u00d2\b\u0006\u0001\u0006\u0001\u0006\u0003\u0006"+
		"\u00d6\b\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00da\b\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00e1"+
		"\b\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00e5\b\u0006\u0001\u0006"+
		"\u0001\u0006\u0003\u0006\u00e9\b\u0006\u0001\u0006\u0001\u0006\u0003\u0006"+
		"\u00ed\b\u0006\u0001\u0007\u0001\u0007\u0003\u0007\u00f1\b\u0007\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0003\b\u00fa\b\b\u0001"+
		"\t\u0001\t\u0003\t\u00fe\b\t\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001"+
		"\f\u0001\f\u0001\f\u0001\f\u0003\f\u0108\b\f\u0001\f\u0001\f\u0001\f\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0003\r\u0111\b\r\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0003\r\u0117\b\r\u0003\r\u0119\b\r\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0003\u000e\u011f\b\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u012e\b\u000e"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0003\u000f\u013c\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0003\u000f\u0143\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0003\u000f\u014a\b\u000f\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0005\u0010\u014f\b\u0010\n\u0010\f\u0010\u0152\t\u0010\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u0158\b\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0005\u0012\u015e\b\u0012\n"+
		"\u0012\f\u0012\u0161\t\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0003\u0013\u016b"+
		"\b\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015\u0001"+
		"\u0015\u0005\u0015\u0173\b\u0015\n\u0015\f\u0015\u0176\t\u0015\u0001\u0015"+
		"\u0001\u0015\u0001\u0016\u0001\u0016\u0003\u0016\u017c\b\u0016\u0001\u0016"+
		"\u0001\u0016\u0003\u0016\u0180\b\u0016\u0001\u0016\u0003\u0016\u0183\b"+
		"\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u0189"+
		"\b\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0003"+
		"\u0018\u0190\b\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001"+
		"\u0018\u0003\u0018\u0197\b\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001"+
		"\u0019\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001"+
		"\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0005\u001b\u01a7"+
		"\b\u001b\n\u001b\f\u001b\u01aa\t\u001b\u0001\u001b\u0001\u001b\u0003\u001b"+
		"\u01ae\b\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0003\u001b\u01b7\b\u001b\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0005\u001b"+
		"\u01c0\b\u001b\n\u001b\f\u001b\u01c3\t\u001b\u0001\u001b\u0001\u001b\u0003"+
		"\u001b\u01c7\b\u001b\u0001\u001b\u0003\u001b\u01ca\b\u001b\u0001\u001c"+
		"\u0001\u001c\u0001\u001c\u0005\u001c\u01cf\b\u001c\n\u001c\f\u001c\u01d2"+
		"\t\u001c\u0001\u001d\u0001\u001d\u0001\u001d\u0005\u001d\u01d7\b\u001d"+
		"\n\u001d\f\u001d\u01da\t\u001d\u0001\u001e\u0001\u001e\u0001\u001e\u0003"+
		"\u001e\u01df\b\u001e\u0005\u001e\u01e1\b\u001e\n\u001e\f\u001e\u01e4\t"+
		"\u001e\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f\u0003\u001f\u01ea"+
		"\b\u001f\u0001 \u0001 \u0001 \u0005 \u01ef\b \n \f \u01f2\t \u0001!\u0001"+
		"!\u0003!\u01f6\b!\u0001\"\u0001\"\u0001\"\u0001\"\u0001\"\u0001\"\u0001"+
		"\"\u0001\"\u0003\"\u0200\b\"\u0001#\u0001#\u0001#\u0005#\u0205\b#\n#\f"+
		"#\u0208\t#\u0001$\u0001$\u0001$\u0005$\u020d\b$\n$\f$\u0210\t$\u0001%"+
		"\u0001%\u0001%\u0005%\u0215\b%\n%\f%\u0218\t%\u0001&\u0001&\u0001&\u0005"+
		"&\u021d\b&\n&\f&\u0220\t&\u0001\'\u0001\'\u0001\'\u0005\'\u0225\b\'\n"+
		"\'\f\'\u0228\t\'\u0001(\u0001(\u0001(\u0005(\u022d\b(\n(\f(\u0230\t(\u0001"+
		")\u0001)\u0001)\u0005)\u0235\b)\n)\f)\u0238\t)\u0001*\u0001*\u0001*\u0003"+
		"*\u023d\b*\u0001+\u0001+\u0003+\u0241\b+\u0001+\u0001+\u0001+\u0001+\u0001"+
		",\u0001,\u0003,\u0249\b,\u0001-\u0001-\u0005-\u024d\b-\n-\f-\u0250\t-"+
		"\u0001-\u0001-\u0003-\u0254\b-\u0001.\u0001.\u0001.\u0001.\u0001.\u0001"+
		".\u0001.\u0001.\u0001.\u0001.\u0001.\u0001.\u0003.\u0262\b.\u0001/\u0001"+
		"/\u0001/\u0001/\u0001/\u0001/\u0005/\u026a\b/\n/\f/\u026d\t/\u0003/\u026f"+
		"\b/\u0001/\u0001/\u0001/\u0001/\u0001/\u0001/\u0001/\u0001/\u0001/\u0003"+
		"/\u027a\b/\u0001/\u0000\u00000\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010"+
		"\u0012\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPR"+
		"TVXZ\\^\u0000\u0005\u0001\u000056\u0001\u00007:\u0001\u000001\u0001\u0000"+
		"24\u0002\u000011==\u02b7\u0000f\u0001\u0000\u0000\u0000\u0002\u008d\u0001"+
		"\u0000\u0000\u0000\u0004\u009c\u0001\u0000\u0000\u0000\u0006\u009e\u0001"+
		"\u0000\u0000\u0000\b\u00be\u0001\u0000\u0000\u0000\n\u00cc\u0001\u0000"+
		"\u0000\u0000\f\u00ec\u0001\u0000\u0000\u0000\u000e\u00f0\u0001\u0000\u0000"+
		"\u0000\u0010\u00f2\u0001\u0000\u0000\u0000\u0012\u00fb\u0001\u0000\u0000"+
		"\u0000\u0014\u00ff\u0001\u0000\u0000\u0000\u0016\u0101\u0001\u0000\u0000"+
		"\u0000\u0018\u0103\u0001\u0000\u0000\u0000\u001a\u0118\u0001\u0000\u0000"+
		"\u0000\u001c\u012d\u0001\u0000\u0000\u0000\u001e\u0149\u0001\u0000\u0000"+
		"\u0000 \u014b\u0001\u0000\u0000\u0000\"\u0153\u0001\u0000\u0000\u0000"+
		"$\u015b\u0001\u0000\u0000\u0000&\u016a\u0001\u0000\u0000\u0000(\u016c"+
		"\u0001\u0000\u0000\u0000*\u0170\u0001\u0000\u0000\u0000,\u0182\u0001\u0000"+
		"\u0000\u0000.\u0184\u0001\u0000\u0000\u00000\u0196\u0001\u0000\u0000\u0000"+
		"2\u0198\u0001\u0000\u0000\u00004\u019e\u0001\u0000\u0000\u00006\u01c9"+
		"\u0001\u0000\u0000\u00008\u01cb\u0001\u0000\u0000\u0000:\u01d3\u0001\u0000"+
		"\u0000\u0000<\u01db\u0001\u0000\u0000\u0000>\u01e9\u0001\u0000\u0000\u0000"+
		"@\u01eb\u0001\u0000\u0000\u0000B\u01f5\u0001\u0000\u0000\u0000D\u01f7"+
		"\u0001\u0000\u0000\u0000F\u0201\u0001\u0000\u0000\u0000H\u0209\u0001\u0000"+
		"\u0000\u0000J\u0211\u0001\u0000\u0000\u0000L\u0219\u0001\u0000\u0000\u0000"+
		"N\u0221\u0001\u0000\u0000\u0000P\u0229\u0001\u0000\u0000\u0000R\u0231"+
		"\u0001\u0000\u0000\u0000T\u023c\u0001\u0000\u0000\u0000V\u023e\u0001\u0000"+
		"\u0000\u0000X\u0248\u0001\u0000\u0000\u0000Z\u024a\u0001\u0000\u0000\u0000"+
		"\\\u0261\u0001\u0000\u0000\u0000^\u0279\u0001\u0000\u0000\u0000`b\u0003"+
		"\u0002\u0001\u0000ac\u0005\'\u0000\u0000ba\u0001\u0000\u0000\u0000bc\u0001"+
		"\u0000\u0000\u0000ce\u0001\u0000\u0000\u0000d`\u0001\u0000\u0000\u0000"+
		"eh\u0001\u0000\u0000\u0000fd\u0001\u0000\u0000\u0000fg\u0001\u0000\u0000"+
		"\u0000gi\u0001\u0000\u0000\u0000hf\u0001\u0000\u0000\u0000ij\u0005\u0000"+
		"\u0000\u0001j\u0001\u0001\u0000\u0000\u0000k\u008e\u0003>\u001f\u0000"+
		"ln\u0003\u001a\r\u0000mo\u0005\'\u0000\u0000nm\u0001\u0000\u0000\u0000"+
		"no\u0001\u0000\u0000\u0000o\u008e\u0001\u0000\u0000\u0000p\u008e\u0003"+
		"\u0004\u0002\u0000q\u008e\u0003\b\u0004\u0000r\u008e\u0003\n\u0005\u0000"+
		"s\u008e\u0003\f\u0006\u0000t\u008e\u0003\u0010\b\u0000u\u008e\u0003\u0006"+
		"\u0003\u0000v\u008e\u0003\u0012\t\u0000w\u008e\u0003\u0014\n\u0000x\u008e"+
		"\u0003\u0016\u000b\u0000y\u008e\u0003\u0018\f\u0000z|\u0003\u001c\u000e"+
		"\u0000{}\u0005\'\u0000\u0000|{\u0001\u0000\u0000\u0000|}\u0001\u0000\u0000"+
		"\u0000}\u008e\u0001\u0000\u0000\u0000~\u0080\u0003\u001e\u000f\u0000\u007f"+
		"\u0081\u0005\'\u0000\u0000\u0080\u007f\u0001\u0000\u0000\u0000\u0080\u0081"+
		"\u0001\u0000\u0000\u0000\u0081\u008e\u0001\u0000\u0000\u0000\u0082\u008e"+
		"\u0003\"\u0011\u0000\u0083\u008e\u0003(\u0014\u0000\u0084\u008e\u0003"+
		"0\u0018\u0000\u0085\u0087\u00034\u001a\u0000\u0086\u0088\u0005\'\u0000"+
		"\u0000\u0087\u0086\u0001\u0000\u0000\u0000\u0087\u0088\u0001\u0000\u0000"+
		"\u0000\u0088\u008e\u0001\u0000\u0000\u0000\u0089\u008b\u00036\u001b\u0000"+
		"\u008a\u008c\u0005\'\u0000\u0000\u008b\u008a\u0001\u0000\u0000\u0000\u008b"+
		"\u008c\u0001\u0000\u0000\u0000\u008c\u008e\u0001\u0000\u0000\u0000\u008d"+
		"k\u0001\u0000\u0000\u0000\u008dl\u0001\u0000\u0000\u0000\u008dp\u0001"+
		"\u0000\u0000\u0000\u008dq\u0001\u0000\u0000\u0000\u008dr\u0001\u0000\u0000"+
		"\u0000\u008ds\u0001\u0000\u0000\u0000\u008dt\u0001\u0000\u0000\u0000\u008d"+
		"u\u0001\u0000\u0000\u0000\u008dv\u0001\u0000\u0000\u0000\u008dw\u0001"+
		"\u0000\u0000\u0000\u008dx\u0001\u0000\u0000\u0000\u008dy\u0001\u0000\u0000"+
		"\u0000\u008dz\u0001\u0000\u0000\u0000\u008d~\u0001\u0000\u0000\u0000\u008d"+
		"\u0082\u0001\u0000\u0000\u0000\u008d\u0083\u0001\u0000\u0000\u0000\u008d"+
		"\u0084\u0001\u0000\u0000\u0000\u008d\u0085\u0001\u0000\u0000\u0000\u008d"+
		"\u0089\u0001\u0000\u0000\u0000\u008e\u0003\u0001\u0000\u0000\u0000\u008f"+
		"\u0090\u0005\u0003\u0000\u0000\u0090\u0091\u0005(\u0000\u0000\u0091\u0092"+
		"\u0003>\u001f\u0000\u0092\u0093\u0005&\u0000\u0000\u0093\u0094\u0003<"+
		"\u001e\u0000\u0094\u0095\u0005)\u0000\u0000\u0095\u009d\u0001\u0000\u0000"+
		"\u0000\u0096\u0097\u0005\u0003\u0000\u0000\u0097\u0098\u0005(\u0000\u0000"+
		"\u0098\u0099\u0003>\u001f\u0000\u0099\u009a\u0005)\u0000\u0000\u009a\u009b"+
		"\u0003\u0002\u0001\u0000\u009b\u009d\u0001\u0000\u0000\u0000\u009c\u008f"+
		"\u0001\u0000\u0000\u0000\u009c\u0096\u0001\u0000\u0000\u0000\u009d\u0005"+
		"\u0001\u0000\u0000\u0000\u009e\u009f\u0005\f\u0000\u0000\u009f\u00a0\u0005"+
		"(\u0000\u0000\u00a0\u00a1\u0003<\u001e\u0000\u00a1\u00a2\u0005&\u0000"+
		"\u0000\u00a2\u00a3\u0003>\u001f\u0000\u00a3\u00a4\u0005)\u0000\u0000\u00a4"+
		"\u0007\u0001\u0000\u0000\u0000\u00a5\u00a6\u0005\u0004\u0000\u0000\u00a6"+
		"\u00a7\u0005(\u0000\u0000\u00a7\u00a8\u0003>\u001f\u0000\u00a8\u00a9\u0005"+
		"&\u0000\u0000\u00a9\u00aa\u0003>\u001f\u0000\u00aa\u00ab\u0005&\u0000"+
		"\u0000\u00ab\u00ac\u0003>\u001f\u0000\u00ac\u00ad\u0005&\u0000\u0000\u00ad"+
		"\u00ae\u0003>\u001f\u0000\u00ae\u00af\u0005&\u0000\u0000\u00af\u00b0\u0003"+
		"<\u001e\u0000\u00b0\u00b1\u0005)\u0000\u0000\u00b1\u00bf\u0001\u0000\u0000"+
		"\u0000\u00b2\u00b3\u0005\u0004\u0000\u0000\u00b3\u00b4\u0005(\u0000\u0000"+
		"\u00b4\u00b5\u0003>\u001f\u0000\u00b5\u00b6\u0005&\u0000\u0000\u00b6\u00b7"+
		"\u0003>\u001f\u0000\u00b7\u00b8\u0005&\u0000\u0000\u00b8\u00b9\u0003>"+
		"\u001f\u0000\u00b9\u00ba\u0005&\u0000\u0000\u00ba\u00bb\u0003>\u001f\u0000"+
		"\u00bb\u00bc\u0005)\u0000\u0000\u00bc\u00bd\u0003\u0002\u0001\u0000\u00bd"+
		"\u00bf\u0001\u0000\u0000\u0000\u00be\u00a5\u0001\u0000\u0000\u0000\u00be"+
		"\u00b2\u0001\u0000\u0000\u0000\u00bf\t\u0001\u0000\u0000\u0000\u00c0\u00c1"+
		"\u0005\u0005\u0000\u0000\u00c1\u00c2\u0005(\u0000\u0000\u00c2\u00c3\u0003"+
		">\u001f\u0000\u00c3\u00c4\u0005)\u0000\u0000\u00c4\u00c5\u0003\u0002\u0001"+
		"\u0000\u00c5\u00cd\u0001\u0000\u0000\u0000\u00c6\u00c7\u0005\u0005\u0000"+
		"\u0000\u00c7\u00c8\u0005(\u0000\u0000\u00c8\u00c9\u0003>\u001f\u0000\u00c9"+
		"\u00ca\u0005)\u0000\u0000\u00ca\u00cb\u0003<\u001e\u0000\u00cb\u00cd\u0001"+
		"\u0000\u0000\u0000\u00cc\u00c0\u0001\u0000\u0000\u0000\u00cc\u00c6\u0001"+
		"\u0000\u0000\u0000\u00cd\u000b\u0001\u0000\u0000\u0000\u00ce\u00cf\u0005"+
		"\u0006\u0000\u0000\u00cf\u00d1\u0005(\u0000\u0000\u00d0\u00d2\u0003\u000e"+
		"\u0007\u0000\u00d1\u00d0\u0001\u0000\u0000\u0000\u00d1\u00d2\u0001\u0000"+
		"\u0000\u0000\u00d2\u00d3\u0001\u0000\u0000\u0000\u00d3\u00d5\u0005\'\u0000"+
		"\u0000\u00d4\u00d6\u0003>\u001f\u0000\u00d5\u00d4\u0001\u0000\u0000\u0000"+
		"\u00d5\u00d6\u0001\u0000\u0000\u0000\u00d6\u00d7\u0001\u0000\u0000\u0000"+
		"\u00d7\u00d9\u0005\'\u0000\u0000\u00d8\u00da\u0003>\u001f\u0000\u00d9"+
		"\u00d8\u0001\u0000\u0000\u0000\u00d9\u00da\u0001\u0000\u0000\u0000\u00da"+
		"\u00db\u0001\u0000\u0000\u0000\u00db\u00dc\u0005)\u0000\u0000\u00dc\u00ed"+
		"\u0003\u0002\u0001\u0000\u00dd\u00de\u0005\u0006\u0000\u0000\u00de\u00e0"+
		"\u0005(\u0000\u0000\u00df\u00e1\u0003\u000e\u0007\u0000\u00e0\u00df\u0001"+
		"\u0000\u0000\u0000\u00e0\u00e1\u0001\u0000\u0000\u0000\u00e1\u00e2\u0001"+
		"\u0000\u0000\u0000\u00e2\u00e4\u0005\'\u0000\u0000\u00e3\u00e5\u0003>"+
		"\u001f\u0000\u00e4\u00e3\u0001\u0000\u0000\u0000\u00e4\u00e5\u0001\u0000"+
		"\u0000\u0000\u00e5\u00e6\u0001\u0000\u0000\u0000\u00e6\u00e8\u0005\'\u0000"+
		"\u0000\u00e7\u00e9\u0003>\u001f\u0000\u00e8\u00e7\u0001\u0000\u0000\u0000"+
		"\u00e8\u00e9\u0001\u0000\u0000\u0000\u00e9\u00ea\u0001\u0000\u0000\u0000"+
		"\u00ea\u00eb\u0005)\u0000\u0000\u00eb\u00ed\u0003<\u001e\u0000\u00ec\u00ce"+
		"\u0001\u0000\u0000\u0000\u00ec\u00dd\u0001\u0000\u0000\u0000\u00ed\r\u0001"+
		"\u0000\u0000\u0000\u00ee\u00f1\u0003\u001a\r\u0000\u00ef\u00f1\u0003>"+
		"\u001f\u0000\u00f0\u00ee\u0001\u0000\u0000\u0000\u00f0\u00ef\u0001\u0000"+
		"\u0000\u0000\u00f1\u000f\u0001\u0000\u0000\u0000\u00f2\u00f3\u0005\u0001"+
		"\u0000\u0000\u00f3\u00f4\u0005(\u0000\u0000\u00f4\u00f5\u0003>\u001f\u0000"+
		"\u00f5\u00f6\u0005)\u0000\u0000\u00f6\u00f9\u0003\u0002\u0001\u0000\u00f7"+
		"\u00f8\u0005\u0002\u0000\u0000\u00f8\u00fa\u0003\u0002\u0001\u0000\u00f9"+
		"\u00f7\u0001\u0000\u0000\u0000\u00f9\u00fa\u0001\u0000\u0000\u0000\u00fa"+
		"\u0011\u0001\u0000\u0000\u0000\u00fb\u00fd\u0005\b\u0000\u0000\u00fc\u00fe"+
		"\u0003>\u001f\u0000\u00fd\u00fc\u0001\u0000\u0000\u0000\u00fd\u00fe\u0001"+
		"\u0000\u0000\u0000\u00fe\u0013\u0001\u0000\u0000\u0000\u00ff\u0100\u0005"+
		"\n\u0000\u0000\u0100\u0015\u0001\u0000\u0000\u0000\u0101\u0102\u0005\u000b"+
		"\u0000\u0000\u0102\u0017\u0001\u0000\u0000\u0000\u0103\u0104\u0005\u0007"+
		"\u0000\u0000\u0104\u0105\u0005!\u0000\u0000\u0105\u0107\u0005(\u0000\u0000"+
		"\u0106\u0108\u0003:\u001d\u0000\u0107\u0106\u0001\u0000\u0000\u0000\u0107"+
		"\u0108\u0001\u0000\u0000\u0000\u0108\u0109\u0001\u0000\u0000\u0000\u0109"+
		"\u010a\u0005)\u0000\u0000\u010a\u010b\u0003<\u001e\u0000\u010b\u0019\u0001"+
		"\u0000\u0000\u0000\u010c\u010d\u0005\u001a\u0000\u0000\u010d\u0110\u0005"+
		"!\u0000\u0000\u010e\u010f\u0005/\u0000\u0000\u010f\u0111\u0003>\u001f"+
		"\u0000\u0110\u010e\u0001\u0000\u0000\u0000\u0110\u0111\u0001\u0000\u0000"+
		"\u0000\u0111\u0119\u0001\u0000\u0000\u0000\u0112\u0113\u0005\u001b\u0000"+
		"\u0000\u0113\u0116\u0005!\u0000\u0000\u0114\u0115\u0005/\u0000\u0000\u0115"+
		"\u0117\u0003>\u001f\u0000\u0116\u0114\u0001\u0000\u0000\u0000\u0116\u0117"+
		"\u0001\u0000\u0000\u0000\u0117\u0119\u0001\u0000\u0000\u0000\u0118\u010c"+
		"\u0001\u0000\u0000\u0000\u0118\u0112\u0001\u0000\u0000\u0000\u0119\u001b"+
		"\u0001\u0000\u0000\u0000\u011a\u011b\u0005\r\u0000\u0000\u011b\u011e\u0005"+
		"%\u0000\u0000\u011c\u011d\u0005\u0010\u0000\u0000\u011d\u011f\u0005!\u0000"+
		"\u0000\u011e\u011c\u0001\u0000\u0000\u0000\u011e\u011f\u0001\u0000\u0000"+
		"\u0000\u011f\u012e\u0001\u0000\u0000\u0000\u0120\u0121\u0005\r\u0000\u0000"+
		"\u0121\u0122\u0005!\u0000\u0000\u0122\u0123\u0005\u000f\u0000\u0000\u0123"+
		"\u012e\u0005%\u0000\u0000\u0124\u0125\u0005\r\u0000\u0000\u0125\u0126"+
		"\u00052\u0000\u0000\u0126\u0127\u0005\u0010\u0000\u0000\u0127\u0128\u0005"+
		"!\u0000\u0000\u0128\u0129\u0005\u000f\u0000\u0000\u0129\u012e\u0005%\u0000"+
		"\u0000\u012a\u012b\u0005\r\u0000\u0000\u012b\u012c\u0005 \u0000\u0000"+
		"\u012c\u012e\u0005%\u0000\u0000\u012d\u011a\u0001\u0000\u0000\u0000\u012d"+
		"\u0120\u0001\u0000\u0000\u0000\u012d\u0124\u0001\u0000\u0000\u0000\u012d"+
		"\u012a\u0001\u0000\u0000\u0000\u012e\u001d\u0001\u0000\u0000\u0000\u012f"+
		"\u0130\u0005\u000e\u0000\u0000\u0130\u014a\u0005!\u0000\u0000\u0131\u0132"+
		"\u0005\u000e\u0000\u0000\u0132\u0133\u0005*\u0000\u0000\u0133\u0134\u0003"+
		" \u0010\u0000\u0134\u0135\u0005+\u0000\u0000\u0135\u014a\u0001\u0000\u0000"+
		"\u0000\u0136\u0137\u0005\u000e\u0000\u0000\u0137\u0138\u0005\u001a\u0000"+
		"\u0000\u0138\u013b\u0005!\u0000\u0000\u0139\u013a\u0005/\u0000\u0000\u013a"+
		"\u013c\u0003>\u001f\u0000\u013b\u0139\u0001\u0000\u0000\u0000\u013b\u013c"+
		"\u0001\u0000\u0000\u0000\u013c\u014a\u0001\u0000\u0000\u0000\u013d\u013e"+
		"\u0005\u000e\u0000\u0000\u013e\u013f\u0005\u0007\u0000\u0000\u013f\u0140"+
		"\u0005!\u0000\u0000\u0140\u0142\u0005(\u0000\u0000\u0141\u0143\u0003:"+
		"\u001d\u0000\u0142\u0141\u0001\u0000\u0000\u0000\u0142\u0143\u0001\u0000"+
		"\u0000\u0000\u0143\u0144\u0001\u0000\u0000\u0000\u0144\u0145\u0005)\u0000"+
		"\u0000\u0145\u014a\u0003<\u001e\u0000\u0146\u0147\u0005\u000e\u0000\u0000"+
		"\u0147\u0148\u0005\u001d\u0000\u0000\u0148\u014a\u0003>\u001f\u0000\u0149"+
		"\u012f\u0001\u0000\u0000\u0000\u0149\u0131\u0001\u0000\u0000\u0000\u0149"+
		"\u0136\u0001\u0000\u0000\u0000\u0149\u013d\u0001\u0000\u0000\u0000\u0149"+
		"\u0146\u0001\u0000\u0000\u0000\u014a\u001f\u0001\u0000\u0000\u0000\u014b"+
		"\u0150\u0005!\u0000\u0000\u014c\u014d\u0005&\u0000\u0000\u014d\u014f\u0005"+
		"!\u0000\u0000\u014e\u014c\u0001\u0000\u0000\u0000\u014f\u0152\u0001\u0000"+
		"\u0000\u0000\u0150\u014e\u0001\u0000\u0000\u0000\u0150\u0151\u0001\u0000"+
		"\u0000\u0000\u0151!\u0001\u0000\u0000\u0000\u0152\u0150\u0001\u0000\u0000"+
		"\u0000\u0153\u0154\u0005\u0011\u0000\u0000\u0154\u0157\u0005!\u0000\u0000"+
		"\u0155\u0156\u0005\u0015\u0000\u0000\u0156\u0158\u0005!\u0000\u0000\u0157"+
		"\u0155\u0001\u0000\u0000\u0000\u0157\u0158\u0001\u0000\u0000\u0000\u0158"+
		"\u0159\u0001\u0000\u0000\u0000\u0159\u015a\u0003$\u0012\u0000\u015a#\u0001"+
		"\u0000\u0000\u0000\u015b\u015f\u0005*\u0000\u0000\u015c\u015e\u0003&\u0013"+
		"\u0000\u015d\u015c\u0001\u0000\u0000\u0000\u015e\u0161\u0001\u0000\u0000"+
		"\u0000\u015f\u015d\u0001\u0000\u0000\u0000\u015f\u0160\u0001\u0000\u0000"+
		"\u0000\u0160\u0162\u0001\u0000\u0000\u0000\u0161\u015f\u0001\u0000\u0000"+
		"\u0000\u0162\u0163\u0005+\u0000\u0000\u0163%\u0001\u0000\u0000\u0000\u0164"+
		"\u016b\u0003\u001a\r\u0000\u0165\u016b\u0003\u0018\f\u0000\u0166\u0167"+
		"\u0005\u001c\u0000\u0000\u0167\u016b\u0003\u0018\f\u0000\u0168\u0169\u0005"+
		"\u001c\u0000\u0000\u0169\u016b\u0003\u001a\r\u0000\u016a\u0164\u0001\u0000"+
		"\u0000\u0000\u016a\u0165\u0001\u0000\u0000\u0000\u016a\u0166\u0001\u0000"+
		"\u0000\u0000\u016a\u0168\u0001\u0000\u0000\u0000\u016b\'\u0001\u0000\u0000"+
		"\u0000\u016c\u016d\u0005\u0012\u0000\u0000\u016d\u016e\u0005!\u0000\u0000"+
		"\u016e\u016f\u0003*\u0015\u0000\u016f)\u0001\u0000\u0000\u0000\u0170\u0174"+
		"\u0005*\u0000\u0000\u0171\u0173\u0003,\u0016\u0000\u0172\u0171\u0001\u0000"+
		"\u0000\u0000\u0173\u0176\u0001\u0000\u0000\u0000\u0174\u0172\u0001\u0000"+
		"\u0000\u0000\u0174\u0175\u0001\u0000\u0000\u0000\u0175\u0177\u0001\u0000"+
		"\u0000\u0000\u0176\u0174\u0001\u0000\u0000\u0000\u0177\u0178\u0005+\u0000"+
		"\u0000\u0178+\u0001\u0000\u0000\u0000\u0179\u017b\u0003\u001a\r\u0000"+
		"\u017a\u017c\u0005\'\u0000\u0000\u017b\u017a\u0001\u0000\u0000\u0000\u017b"+
		"\u017c\u0001\u0000\u0000\u0000\u017c\u0183\u0001\u0000\u0000\u0000\u017d"+
		"\u017f\u0003.\u0017\u0000\u017e\u0180\u0005\'\u0000\u0000\u017f\u017e"+
		"\u0001\u0000\u0000\u0000\u017f\u0180\u0001\u0000\u0000\u0000\u0180\u0183"+
		"\u0001\u0000\u0000\u0000\u0181\u0183\u0003\u0018\f\u0000\u0182\u0179\u0001"+
		"\u0000\u0000\u0000\u0182\u017d\u0001\u0000\u0000\u0000\u0182\u0181\u0001"+
		"\u0000\u0000\u0000\u0183-\u0001\u0000\u0000\u0000\u0184\u0185\u0005\u001b"+
		"\u0000\u0000\u0185\u0188\u0005!\u0000\u0000\u0186\u0187\u0005/\u0000\u0000"+
		"\u0187\u0189\u0003>\u001f\u0000\u0188\u0186\u0001\u0000\u0000\u0000\u0188"+
		"\u0189\u0001\u0000\u0000\u0000\u0189/\u0001\u0000\u0000\u0000\u018a\u018b"+
		"\u0005\u0016\u0000\u0000\u018b\u018c\u0003<\u001e\u0000\u018c\u018f\u0003"+
		"2\u0019\u0000\u018d\u018e\u0005\u0018\u0000\u0000\u018e\u0190\u0003<\u001e"+
		"\u0000\u018f\u018d\u0001\u0000\u0000\u0000\u018f\u0190\u0001\u0000\u0000"+
		"\u0000\u0190\u0197\u0001\u0000\u0000\u0000\u0191\u0192\u0005\u0016\u0000"+
		"\u0000\u0192\u0193\u0003<\u001e\u0000\u0193\u0194\u0005\u0018\u0000\u0000"+
		"\u0194\u0195\u0003<\u001e\u0000\u0195\u0197\u0001\u0000\u0000\u0000\u0196"+
		"\u018a\u0001\u0000\u0000\u0000\u0196\u0191\u0001\u0000\u0000\u0000\u0197"+
		"1\u0001\u0000\u0000\u0000\u0198\u0199\u0005\u0017\u0000\u0000\u0199\u019a"+
		"\u0005(\u0000\u0000\u019a\u019b\u0005!\u0000\u0000\u019b\u019c\u0005)"+
		"\u0000\u0000\u019c\u019d\u0003<\u001e\u0000\u019d3\u0001\u0000\u0000\u0000"+
		"\u019e\u019f\u0005\u0019\u0000\u0000\u019f\u01a0\u0003>\u001f\u0000\u01a0"+
		"5\u0001\u0000\u0000\u0000\u01a1\u01a2\u0005 \u0000\u0000\u01a2\u01a3\u0005"+
		".\u0000\u0000\u01a3\u01a8\u0005!\u0000\u0000\u01a4\u01a5\u0005.\u0000"+
		"\u0000\u01a5\u01a7\u0005!\u0000\u0000\u01a6\u01a4\u0001\u0000\u0000\u0000"+
		"\u01a7\u01aa\u0001\u0000\u0000\u0000\u01a8\u01a6\u0001\u0000\u0000\u0000"+
		"\u01a8\u01a9\u0001\u0000\u0000\u0000\u01a9\u01ab\u0001\u0000\u0000\u0000"+
		"\u01aa\u01a8\u0001\u0000\u0000\u0000\u01ab\u01ad\u0005(\u0000\u0000\u01ac"+
		"\u01ae\u00038\u001c\u0000\u01ad\u01ac\u0001\u0000\u0000\u0000\u01ad\u01ae"+
		"\u0001\u0000\u0000\u0000\u01ae\u01af\u0001\u0000\u0000\u0000\u01af\u01ca"+
		"\u0005)\u0000\u0000\u01b0\u01b1\u0005 \u0000\u0000\u01b1\u01b2\u0005("+
		"\u0000\u0000\u01b2\u01b3\u0005%\u0000\u0000\u01b3\u01b4\u0005&\u0000\u0000"+
		"\u01b4\u01b6\u0005%\u0000\u0000\u01b5\u01b7\u00038\u001c\u0000\u01b6\u01b5"+
		"\u0001\u0000\u0000\u0000\u01b6\u01b7\u0001\u0000\u0000\u0000\u01b7\u01b8"+
		"\u0001\u0000\u0000\u0000\u01b8\u01ca\u0005)\u0000\u0000\u01b9\u01ba\u0005"+
		"\u0013\u0000\u0000\u01ba\u01bb\u0005 \u0000\u0000\u01bb\u01bc\u0005.\u0000"+
		"\u0000\u01bc\u01c1\u0005!\u0000\u0000\u01bd\u01be\u0005.\u0000\u0000\u01be"+
		"\u01c0\u0005!\u0000\u0000\u01bf\u01bd\u0001\u0000\u0000\u0000\u01c0\u01c3"+
		"\u0001\u0000\u0000\u0000\u01c1\u01bf\u0001\u0000\u0000\u0000\u01c1\u01c2"+
		"\u0001\u0000\u0000\u0000\u01c2\u01c4\u0001\u0000\u0000\u0000\u01c3\u01c1"+
		"\u0001\u0000\u0000\u0000\u01c4\u01c6\u0005(\u0000\u0000\u01c5\u01c7\u0003"+
		"8\u001c\u0000\u01c6\u01c5\u0001\u0000\u0000\u0000\u01c6\u01c7\u0001\u0000"+
		"\u0000\u0000\u01c7\u01c8\u0001\u0000\u0000\u0000\u01c8\u01ca\u0005)\u0000"+
		"\u0000\u01c9\u01a1\u0001\u0000\u0000\u0000\u01c9\u01b0\u0001\u0000\u0000"+
		"\u0000\u01c9\u01b9\u0001\u0000\u0000\u0000\u01ca7\u0001\u0000\u0000\u0000"+
		"\u01cb\u01d0\u0003>\u001f\u0000\u01cc\u01cd\u0005&\u0000\u0000\u01cd\u01cf"+
		"\u0003>\u001f\u0000\u01ce\u01cc\u0001\u0000\u0000\u0000\u01cf\u01d2\u0001"+
		"\u0000\u0000\u0000\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d0\u01d1\u0001"+
		"\u0000\u0000\u0000\u01d19\u0001\u0000\u0000\u0000\u01d2\u01d0\u0001\u0000"+
		"\u0000\u0000\u01d3\u01d8\u0005!\u0000\u0000\u01d4\u01d5\u0005&\u0000\u0000"+
		"\u01d5\u01d7\u0005!\u0000\u0000\u01d6\u01d4\u0001\u0000\u0000\u0000\u01d7"+
		"\u01da\u0001\u0000\u0000\u0000\u01d8\u01d6\u0001\u0000\u0000\u0000\u01d8"+
		"\u01d9\u0001\u0000\u0000\u0000\u01d9;\u0001\u0000\u0000\u0000\u01da\u01d8"+
		"\u0001\u0000\u0000\u0000\u01db\u01e2\u0005*\u0000\u0000\u01dc\u01de\u0003"+
		"\u0002\u0001\u0000\u01dd\u01df\u0005\'\u0000\u0000\u01de\u01dd\u0001\u0000"+
		"\u0000\u0000\u01de\u01df\u0001\u0000\u0000\u0000\u01df\u01e1\u0001\u0000"+
		"\u0000\u0000\u01e0\u01dc\u0001\u0000\u0000\u0000\u01e1\u01e4\u0001\u0000"+
		"\u0000\u0000\u01e2\u01e0\u0001\u0000\u0000\u0000\u01e2\u01e3\u0001\u0000"+
		"\u0000\u0000\u01e3\u01e5\u0001\u0000\u0000\u0000\u01e4\u01e2\u0001\u0000"+
		"\u0000\u0000\u01e5\u01e6\u0005+\u0000\u0000\u01e6=\u0001\u0000\u0000\u0000"+
		"\u01e7\u01ea\u0003V+\u0000\u01e8\u01ea\u0003@ \u0000\u01e9\u01e7\u0001"+
		"\u0000\u0000\u0000\u01e9\u01e8\u0001\u0000\u0000\u0000\u01ea?\u0001\u0000"+
		"\u0000\u0000\u01eb\u01f0\u0003D\"\u0000\u01ec\u01ed\u0005A\u0000\u0000"+
		"\u01ed\u01ef\u0003B!\u0000\u01ee\u01ec\u0001\u0000\u0000\u0000\u01ef\u01f2"+
		"\u0001\u0000\u0000\u0000\u01f0\u01ee\u0001\u0000\u0000\u0000\u01f0\u01f1"+
		"\u0001\u0000\u0000\u0000\u01f1A\u0001\u0000\u0000\u0000\u01f2\u01f0\u0001"+
		"\u0000\u0000\u0000\u01f3\u01f6\u0003T*\u0000\u01f4\u01f6\u0003V+\u0000"+
		"\u01f5\u01f3\u0001\u0000\u0000\u0000\u01f5\u01f4\u0001\u0000\u0000\u0000"+
		"\u01f6C\u0001\u0000\u0000\u0000\u01f7\u01ff\u0003F#\u0000\u01f8\u01f9"+
		"\u0005>\u0000\u0000\u01f9\u01fa\u0003>\u001f\u0000\u01fa\u01fb\u0005@"+
		"\u0000\u0000\u01fb\u01fc\u0003>\u001f\u0000\u01fc\u0200\u0001\u0000\u0000"+
		"\u0000\u01fd\u01fe\u0005>\u0000\u0000\u01fe\u0200\u0003>\u001f\u0000\u01ff"+
		"\u01f8\u0001\u0000\u0000\u0000\u01ff\u01fd\u0001\u0000\u0000\u0000\u01ff"+
		"\u0200\u0001\u0000\u0000\u0000\u0200E\u0001\u0000\u0000\u0000\u0201\u0206"+
		"\u0003H$\u0000\u0202\u0203\u0005?\u0000\u0000\u0203\u0205\u0003H$\u0000"+
		"\u0204\u0202\u0001\u0000\u0000\u0000\u0205\u0208\u0001\u0000\u0000\u0000"+
		"\u0206\u0204\u0001\u0000\u0000\u0000\u0206\u0207\u0001\u0000\u0000\u0000"+
		"\u0207G\u0001\u0000\u0000\u0000\u0208\u0206\u0001\u0000\u0000\u0000\u0209"+
		"\u020e\u0003J%\u0000\u020a\u020b\u0005<\u0000\u0000\u020b\u020d\u0003"+
		"J%\u0000\u020c\u020a\u0001\u0000\u0000\u0000\u020d\u0210\u0001\u0000\u0000"+
		"\u0000\u020e\u020c\u0001\u0000\u0000\u0000\u020e\u020f\u0001\u0000\u0000"+
		"\u0000\u020fI\u0001\u0000\u0000\u0000\u0210\u020e\u0001\u0000\u0000\u0000"+
		"\u0211\u0216\u0003L&\u0000\u0212\u0213\u0005;\u0000\u0000\u0213\u0215"+
		"\u0003L&\u0000\u0214\u0212\u0001\u0000\u0000\u0000\u0215\u0218\u0001\u0000"+
		"\u0000\u0000\u0216\u0214\u0001\u0000\u0000\u0000\u0216\u0217\u0001\u0000"+
		"\u0000\u0000\u0217K\u0001\u0000\u0000\u0000\u0218\u0216\u0001\u0000\u0000"+
		"\u0000\u0219\u021e\u0003N\'\u0000\u021a\u021b\u0007\u0000\u0000\u0000"+
		"\u021b\u021d\u0003N\'\u0000\u021c\u021a\u0001\u0000\u0000\u0000\u021d"+
		"\u0220\u0001\u0000\u0000\u0000\u021e\u021c\u0001\u0000\u0000\u0000\u021e"+
		"\u021f\u0001\u0000\u0000\u0000\u021fM\u0001\u0000\u0000\u0000\u0220\u021e"+
		"\u0001\u0000\u0000\u0000\u0221\u0226\u0003P(\u0000\u0222\u0223\u0007\u0001"+
		"\u0000\u0000\u0223\u0225\u0003P(\u0000\u0224\u0222\u0001\u0000\u0000\u0000"+
		"\u0225\u0228\u0001\u0000\u0000\u0000\u0226\u0224\u0001\u0000\u0000\u0000"+
		"\u0226\u0227\u0001\u0000\u0000\u0000\u0227O\u0001\u0000\u0000\u0000\u0228"+
		"\u0226\u0001\u0000\u0000\u0000\u0229\u022e\u0003R)\u0000\u022a\u022b\u0007"+
		"\u0002\u0000\u0000\u022b\u022d\u0003R)\u0000\u022c\u022a\u0001\u0000\u0000"+
		"\u0000\u022d\u0230\u0001\u0000\u0000\u0000\u022e\u022c\u0001\u0000\u0000"+
		"\u0000\u022e\u022f\u0001\u0000\u0000\u0000\u022fQ\u0001\u0000\u0000\u0000"+
		"\u0230\u022e\u0001\u0000\u0000\u0000\u0231\u0236\u0003T*\u0000\u0232\u0233"+
		"\u0007\u0003\u0000\u0000\u0233\u0235\u0003T*\u0000\u0234\u0232\u0001\u0000"+
		"\u0000\u0000\u0235\u0238\u0001\u0000\u0000\u0000\u0236\u0234\u0001\u0000"+
		"\u0000\u0000\u0236\u0237\u0001\u0000\u0000\u0000\u0237S\u0001\u0000\u0000"+
		"\u0000\u0238\u0236\u0001\u0000\u0000\u0000\u0239\u023a\u0007\u0004\u0000"+
		"\u0000\u023a\u023d\u0003T*\u0000\u023b\u023d\u0003Z-\u0000\u023c\u0239"+
		"\u0001\u0000\u0000\u0000\u023c\u023b\u0001\u0000\u0000\u0000\u023dU\u0001"+
		"\u0000\u0000\u0000\u023e\u0240\u0005(\u0000\u0000\u023f\u0241\u0003:\u001d"+
		"\u0000\u0240\u023f\u0001\u0000\u0000\u0000\u0240\u0241\u0001\u0000\u0000"+
		"\u0000\u0241\u0242\u0001\u0000\u0000\u0000\u0242\u0243\u0005)\u0000\u0000"+
		"\u0243\u0244\u0005\t\u0000\u0000\u0244\u0245\u0003X,\u0000\u0245W\u0001"+
		"\u0000\u0000\u0000\u0246\u0249\u0003>\u001f\u0000\u0247\u0249\u0003<\u001e"+
		"\u0000\u0248\u0246\u0001\u0000\u0000\u0000\u0248\u0247\u0001\u0000\u0000"+
		"\u0000\u0249Y\u0001\u0000\u0000\u0000\u024a\u024e\u0003\\.\u0000\u024b"+
		"\u024d\u0003^/\u0000\u024c\u024b\u0001\u0000\u0000\u0000\u024d\u0250\u0001"+
		"\u0000\u0000\u0000\u024e\u024c\u0001\u0000\u0000\u0000\u024e\u024f\u0001"+
		"\u0000\u0000\u0000\u024f\u0253\u0001\u0000\u0000\u0000\u0250\u024e\u0001"+
		"\u0000\u0000\u0000\u0251\u0252\u0005/\u0000\u0000\u0252\u0254\u0003>\u001f"+
		"\u0000\u0253\u0251\u0001\u0000\u0000\u0000\u0253\u0254\u0001\u0000\u0000"+
		"\u0000\u0254[\u0001\u0000\u0000\u0000\u0255\u0256\u0005(\u0000\u0000\u0256"+
		"\u0257\u0003>\u001f\u0000\u0257\u0258\u0005)\u0000\u0000\u0258\u0262\u0001"+
		"\u0000\u0000\u0000\u0259\u0262\u0003<\u001e\u0000\u025a\u0262\u0005\""+
		"\u0000\u0000\u025b\u0262\u0005%\u0000\u0000\u025c\u0262\u0005#\u0000\u0000"+
		"\u025d\u0262\u0005$\u0000\u0000\u025e\u0262\u0005\u001e\u0000\u0000\u025f"+
		"\u0262\u0005\u001f\u0000\u0000\u0260\u0262\u0005!\u0000\u0000\u0261\u0255"+
		"\u0001\u0000\u0000\u0000\u0261\u0259\u0001\u0000\u0000\u0000\u0261\u025a"+
		"\u0001\u0000\u0000\u0000\u0261\u025b\u0001\u0000\u0000\u0000\u0261\u025c"+
		"\u0001\u0000\u0000\u0000\u0261\u025d\u0001\u0000\u0000\u0000\u0261\u025e"+
		"\u0001\u0000\u0000\u0000\u0261\u025f\u0001\u0000\u0000\u0000\u0261\u0260"+
		"\u0001\u0000\u0000\u0000\u0262]\u0001\u0000\u0000\u0000\u0263\u0264\u0005"+
		"(\u0000\u0000\u0264\u027a\u0005)\u0000\u0000\u0265\u026e\u0005(\u0000"+
		"\u0000\u0266\u026b\u0003>\u001f\u0000\u0267\u0268\u0005&\u0000\u0000\u0268"+
		"\u026a\u0003>\u001f\u0000\u0269\u0267\u0001\u0000\u0000\u0000\u026a\u026d"+
		"\u0001\u0000\u0000\u0000\u026b\u0269\u0001\u0000\u0000\u0000\u026b\u026c"+
		"\u0001\u0000\u0000\u0000\u026c\u026f\u0001\u0000\u0000\u0000\u026d\u026b"+
		"\u0001\u0000\u0000\u0000\u026e\u0266\u0001\u0000\u0000\u0000\u026e\u026f"+
		"\u0001\u0000\u0000\u0000\u026f\u0270\u0001\u0000\u0000\u0000\u0270\u027a"+
		"\u0005)\u0000\u0000\u0271\u0272\u0005,\u0000\u0000\u0272\u0273\u0003>"+
		"\u001f\u0000\u0273\u0274\u0005-\u0000\u0000\u0274\u027a\u0001\u0000\u0000"+
		"\u0000\u0275\u0276\u0005.\u0000\u0000\u0276\u027a\u0005!\u0000\u0000\u0277"+
		"\u0278\u0005.\u0000\u0000\u0278\u027a\u0005\"\u0000\u0000\u0279\u0263"+
		"\u0001\u0000\u0000\u0000\u0279\u0265\u0001\u0000\u0000\u0000\u0279\u0271"+
		"\u0001\u0000\u0000\u0000\u0279\u0275\u0001\u0000\u0000\u0000\u0279\u0277"+
		"\u0001\u0000\u0000\u0000\u027a_\u0001\u0000\u0000\u0000Gbfn|\u0080\u0087"+
		"\u008b\u008d\u009c\u00be\u00cc\u00d1\u00d5\u00d9\u00e0\u00e4\u00e8\u00ec"+
		"\u00f0\u00f9\u00fd\u0107\u0110\u0116\u0118\u011e\u012d\u013b\u0142\u0149"+
		"\u0150\u0157\u015f\u016a\u0174\u017b\u017f\u0182\u0188\u018f\u0196\u01a8"+
		"\u01ad\u01b6\u01c1\u01c6\u01c9\u01d0\u01d8\u01de\u01e2\u01e9\u01f0\u01f5"+
		"\u01ff\u0206\u020e\u0216\u021e\u0226\u022e\u0236\u023c\u0240\u0248\u024e"+
		"\u0253\u0261\u026b\u026e\u0279";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}