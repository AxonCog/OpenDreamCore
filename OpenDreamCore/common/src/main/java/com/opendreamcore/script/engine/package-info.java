/**
 * 脚本引擎本体：DreamLang 语法树执行器 + ANTLR 生成代码。
 *
 * 对外入口为 DreamLang/DreamLangExecutor；executeAsync 使用宿主注入的
 * AsyncScheduler，续体由显式 AST activation stack 持有，取消由宿主负责。
 * antlr/ 下是生成物，不要手改（改语法去 DreamLang.g4 重新生成）。
 * 通用注册表（NamespaceRegistry/EventBus 等）在父包 com.opendreamcore.script，
 * 底座方法索引在 com.opendreamcore.core.script。
 */
package com.opendreamcore.script.engine;
