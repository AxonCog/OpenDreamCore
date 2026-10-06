/**
 * addons 通用热拔插底座：jar SPI 热加载 + 脚本包通道 + 注册表生命周期。
 *
 * <p>任何模块（模型格式 codec、未来动画/粒子/HUD 扩展）共用同一发现/注册/卸载语义；
 * 模型格式是第一个消费者（{@link com.opendreamcore.model.format.FormatSources}）。
 */
package com.opendreamcore.core.addons;
