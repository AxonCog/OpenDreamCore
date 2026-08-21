grammar DreamLang;

// 程序入口 - 支持多行表达式和分号分隔的语句
program
    : (statement SEMICOLON?)* EOF
    ;

// 语句定义
statement
    : expression
    | varDeclaration SEMICOLON?                    // 变量声明
    | loopStatement
    | forEachStatement
    | whileStatement                               // while循环
    | forStatement                                 // for循环
    | ifStatement
    | awaitStatement
    | returnStatement
    | breakStatement
    | continueStatement
    | functionDefinitionStatement
    | importStatement SEMICOLON?                   // 导入模块
    | exportStatement SEMICOLON?                   // 导出
    | classStatement                               // 类定义
    | namespaceStatement                           // 命名空间定义
    | tryStatement                                 // 异常处理
    | throwStatement SEMICOLON?                    // 抛出异常
    | javaCallStatement SEMICOLON?                 // Java调用
    ;

// loop语句 - 支持带括号和不带括号的块
loopStatement
    : LOOP '(' expression ',' blockStatement ')'  // loop(30,{...}) 或 循环(30,{...})
    | LOOP '(' expression ')' statement           // loop(30) statement 或 循环(30) 语句
    ;

// await语句 - 异步等待语句
awaitStatement
    : AWAIT '(' blockStatement ',' expression ')'  // await({...}, time) 或 等待({...}, 时间)
    ;

// for_each语句
forEachStatement
    : FOR_EACH '(' expression ',' expression ',' expression ',' expression ',' blockStatement ')'
    | FOR_EACH '(' expression ',' expression ',' expression ',' expression ')' statement
    ;

// while语句（新增）
whileStatement
    : WHILE '(' expression ')' statement  // while(条件) { ... } 或 当(条件) { ... }
    | WHILE '(' expression ')' blockStatement
    ;

// for语句（新增）
forStatement
    : FOR '(' forInit? ';' expression? ';' expression? ')' statement
    | FOR '(' forInit? ';' expression? ';' expression? ')' blockStatement
    ;

// for 初始化:变量声明或赋值表达式
forInit
    : varDeclaration
    | expression
    ;

// if语句 - 支持 else 分支
ifStatement
    : IF '(' expression ')' statement (ELSE statement)?
    ;

// return语句
returnStatement
    : RETURN expression?  // return 值 或 返回 值
    ;

// break语句
breakStatement
    : BREAK  // break 或 跳出
    ;

// continue语句
continueStatement
    : CONTINUE  // continue 或 继续
    ;

// 函数定义语句
functionDefinitionStatement
    : FUNCTION IDENTIFIER '(' parameterList? ')' blockStatement  // function name() {} 或 函数 名称() {}
    ;

// 变量声明语句（新增）
varDeclaration
    : VAR IDENTIFIER ('=' expression)?      // var x = 10 或 变量 x = 10
    | CONST IDENTIFIER ('=' expression)?    // const PI = 3.14 或 常量 PI = 3.14
    ;

// import语句（新增）
importStatement
    : IMPORT STRING (AS IDENTIFIER)?                              // import "module" as name
    | IMPORT IDENTIFIER FROM STRING                               // import name from "module"
    | IMPORT '*' AS IDENTIFIER FROM STRING                        // import * as name from "module"
    | IMPORT JAVA STRING                                          // import java "java.util.*"
    ;

// export语句（新增）
exportStatement
    : EXPORT IDENTIFIER                                             // export name 或 导出 名称
    | EXPORT '{' exportList '}'                                     // export { a, b, c }
    | EXPORT VAR IDENTIFIER ('=' expression)?                       // export var x = 10
    | EXPORT FUNCTION IDENTIFIER '(' parameterList? ')' blockStatement  // export function name() {}
    | EXPORT DEFAULT expression                                     // export default value
    ;

exportList
    : IDENTIFIER (',' IDENTIFIER)*
    ;

// 类定义语句（新增）
classStatement
    : CLASS IDENTIFIER (EXTENDS IDENTIFIER)? classBody  // class Name extends Parent { ... }
    ;

classBody
    : '{' classMember* '}'
    ;

classMember
    : varDeclaration
    | functionDefinitionStatement
    | STATIC functionDefinitionStatement
    | STATIC varDeclaration
    ;

// 命名空间定义语句（新增）
namespaceStatement
    : NAMESPACE IDENTIFIER namespaceBody  // namespace Name { ... }
    ;

namespaceBody
    : '{' namespaceMember* '}'
    ;

namespaceMember
    : varDeclaration SEMICOLON?
    | constDeclaration SEMICOLON?
    | functionDefinitionStatement
    ;

constDeclaration
    : CONST IDENTIFIER ('=' expression)?
    ;

// try-catch-finally 语句（新增）
tryStatement
    : TRY blockStatement catchClause (FINALLY blockStatement)?  // try { ... } catch(e) { ... } finally { ... }
    | TRY blockStatement FINALLY blockStatement                 // try { ... } finally { ... }
    ;

catchClause
    : CATCH '(' IDENTIFIER ')' blockStatement  // catch(错误) { ... } 或 捕获(错误) { ... }
    ;

// throw语句（新增）
throwStatement
    : THROW expression  // throw error 或 抛出 错误
    ;

// Java调用语句（新增）
javaCallStatement
    : JAVA '.' IDENTIFIER ('.' IDENTIFIER)* '(' argumentList? ')'  // Java.String.valueOf(123)
    | JAVA '(' STRING ',' STRING argumentList? ')'                  // Java("java.lang.Math", "max", 1, 2)
    | NEW JAVA '.' IDENTIFIER ('.' IDENTIFIER)* '(' argumentList? ')'  // new Java.util.ArrayList()
    ;

argumentList
    : expression (',' expression)*
    ;

// 参数列表
parameterList
    : IDENTIFIER (',' IDENTIFIER)*
    ;

// 块语句 - 内部支持分号分隔的语句
blockStatement
    : '{' (statement SEMICOLON?)* '}'
    ;

// 表达式定义（DragonCore 风格 + MoLang 兼容）
// 分层优先级（经典写法，行为确定）：三元 < 空合并 ?? < || < && < 相等 < 比较 < 加减 < 乘除 < 一元
expression
    : lambdaExpression # LambdaExpr
    | pipeExpression # ExpressionBody
    ;

// 管道：x | fn → fn(x)（右侧是可调用对象：函数/Lambda/命名空间方法）
pipeExpression
    : ternaryExpression (PIPE pipeTarget)*
    ;

pipeTarget
    : unaryExpression
    | lambdaExpression
    ;

ternaryExpression
    : nullCoalesceExpression (TERNARY expression COLON expression | TERNARY expression)?
    ;

// 空合并：a ?? b（a 非 null 取 a，否则取 b；链式取首个非 null）
nullCoalesceExpression
    : orExpression (NULL_COALESCE orExpression)*
    ;

orExpression
    : andExpression ('||' andExpression)*
    ;

andExpression
    : equalityExpression ('&&' equalityExpression)*
    ;

equalityExpression
    : relationalExpression (('==' | '!=') relationalExpression)*
    ;

relationalExpression
    : additiveExpression (('>' | '>=' | '<' | '<=') additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression (('*' | '/' | '%') unaryExpression)*
    ;

unaryExpression
    : ('!' | '-') unaryExpression
    | primary
    ;

// Lambda 表达式 (箭头函数)
lambdaExpression
    : '(' parameterList? ')' '=>' lambdaBody
    ;

// Lambda 体（可以是表达式或语句块）
lambdaBody
    : expression
    | blockStatement
    ;

primary
    : atom (suffix)* ('=' expression)? # PrimaryExpression
    ;

atom
: '(' expression ')' # ParenAtom
| blockStatement # BlockAtom
| NUMBER # NumberAtom
| STRING # StringAtom
| MC_COLOR_STRING # StringAtom
| MC_SECTION_COLOR_STRING # StringAtom
| BOOLEAN # BooleanAtom
| NULL # NullAtom
| IDENTIFIER # IdentifierAtom
;

suffix
    : '(' ')' # FunctionCallSuffix
    | '(' (expression (',' expression)*)? ')' # FunctionCallWithArgsSuffix
    | '[' expression ']' # ArrayAccessSuffix
    | '.' IDENTIFIER # DotAccessSuffix
    | '.' NUMBER # DotAccessNumberSuffix
    ;

// 词法规则 - 关键字和布尔值要在IDENTIFIER之前定义
// ========== 控制流关键字 ==========
IF: 'if' | '如果';
ELSE: 'else' | '否则';
LOOP: 'loop' | '循环';
FOR_EACH: 'for_each' | '遍历';
WHILE: 'while' | '当';
FOR: 'for' | '对于';

// ========== 函数相关关键字 ==========
FUNCTION: 'function' | 'def' | '函数';
RETURN: 'return' | '返回';
ARROW: '=>';

// ========== 流程控制关键字 ==========
BREAK: 'break' | '跳出';
CONTINUE: 'continue' | '继续';
AWAIT: 'await' | '等待';

// ========== 模块相关关键字 ==========
IMPORT: 'import' | '导入';
EXPORT: 'export' | '导出';
FROM: 'from';
AS: 'as';

// ========== 类和对象关键字 ==========
CLASS: 'class' | '类';
NAMESPACE: 'namespace' | '命名空间';
NEW: 'new';
THIS: 'this' | '当前';
EXTENDS: 'extends';

// ========== 异常处理关键字 ==========
TRY: 'try' | '尝试';
CATCH: 'catch' | '捕获';
FINALLY: 'finally' | '最后';
THROW: 'throw' | '抛出';

// ========== 变量声明关键字 ==========
VAR: 'var' | 'let' | '变量';
CONST: 'const' | '常量';
STATIC: 'static';
DEFAULT: 'default';

// ========== 布尔值 ==========
BOOLEAN
    : 'true' | '真'
    | 'false' | '假'
    ;

// ========== 空值 ==========
NULL: 'null' | '空值';

// ========== Java 互调用关键字 ==========
JAVA: 'Java' | 'java' | 'JAVA';

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    | [\u4e00-\u9fa5] [a-zA-Z0-9_\u4e00-\u9fa5]*  // 中文开头，可以包含英文和中文
    ;

NUMBER
    : [0-9]+ ('.' [0-9]+)?
    ;

// Minecraft 颜色代码字符串 - 支持 &c离开副本 这种格式
MC_COLOR_STRING
    : '&' [0-9A-Fa-fklnorKLNOR] [a-zA-Z0-9_\u4e00-\u9fa5 ]*
    ;

// Minecraft 颜色代码字符串 - 支持 §c离开副本 这种格式
MC_SECTION_COLOR_STRING
    : '\u00A7' [0-9A-Fa-fklnorKLNOR] [a-zA-Z0-9_\u4e00-\u9fa5 ]*
    ;

STRING
    : '"' (~["\\] | '\\' . | '\u00A7')* '"'
    | '\'' (~['\\] | '\\' . | '\u00A7')* '\''
    ;

COMMA
    : ','
    ;

SEMICOLON
    : ';'
    ;


LPAREN
    : '('
    ;

RPAREN
    : ')'
    ;

LBRACE
    : '{'
    ;

RBRACE
    : '}'
    ;

LBRACKET
    : '['
    ;

RBRACKET
    : ']'
    ;

DOT
    : '.'
    ;

ASSIGN
    : '='
    ;

PLUS
    : '+'
    ;

MINUS
    : '-'
    ;

MULT
    : '*'
    ;

DIV
    : '/'
    ;

MOD
    : '%'
    ;

EQ
    : '=='
    ;

NEQ
    : '!='
    ;

LT
    : '<'
    ;

GT
    : '>'
    ;

LE
    : '<='
    ;

GE
    : '>='
    ;

AND
    : '&&'
    ;

OR
    : '||'
    ;

NOT
    : '!'
    ;

TERNARY
    : '?'
    ;

NULL_COALESCE
    : '??'
    ;

COLON
    : ':'
    ;

PIPE
    : '|'
    ;

INT
    : [0-9]+
    ;

WHITESPACE
    : [ \t\r\n\u000C]+ -> skip
    ;

COMMENT
    : '/*' .*? '*/' -> skip
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;