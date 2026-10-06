/**
 * 脚本引擎本体：DreamLang 语法树执行器 + ANTLR 生成代码。
 *
 * <p>对外只暴露 DreamLang/DreamLangExecutor 两个入口；antlr/ 下是生成物，不要手改
 * （改语法去 DreamLang.g4 重新生成）。通用注册表（NamespaceRegistry/EventBus 等）在
 * 父包 com.opendreamcore.script，底座方法索引在 com.opendreamcore.core.script。
 */
package com.opendreamcore.script.engine;
