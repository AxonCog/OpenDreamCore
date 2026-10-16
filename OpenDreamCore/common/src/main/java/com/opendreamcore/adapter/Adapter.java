package com.opendreamcore.adapter;

import java.util.Map;

/**
 * 适配器：把一种外来格式的规则文件翻译成 ODC 的规则 IR。
 *
 * 接入步骤：
 *   1）新建包 {@code com.opendreamcore.adapter.<格式名>/}（一格式一包）；
 *   2）实现本接口：accepts() 判断文件是否属于本格式，translate() 产出规则；
 *   3）AdapterChain.register(new ...) 注册，核心代码不动。
 *
 * 产物统一是 Map<目标系统, Map<规则id, 规则 IR>>，目标系统决定规则进哪个仓库
 * （视觉系统 / tooltip / 主题 / 按键表等）。
 *
 * 注册来源三种：
 *   1) 内置：adapter/dragoncore/（priority 最小，链头）；
 *   2) 附属插件：Java 侧 register，reload 不清；
 *   3) 服主脚本：extensions/adapters/*.yml 声明 + Extension.适配器() 登记，
 *      /odc reload 时随脚本重载。
 */
public interface Adapter {

    /** 适配器名（同一链上不许重名，重名注册直接覆盖；日志和规则前缀都靠它认人）。 */
    String name();

    /**
 * 认领判定：该文件是否属于本格式（按文件名 + 解析后的根 IR 内容判断，
 * 不靠后缀猜测）。
 */
    boolean accepts(String fileName, Map<String, Object> rootIr);

    /**
     * 翻译：外来根 IR → ODC 规则集。
     * 返回 Map<目标系统, Map<规则id, 规则 IR>>；空 map = 不认领，让给下一个。
     */
    Map<String, Map<String, Map<String, Object>>> translate(String fileName, Map<String, Object> rootIr);

    /** 是否脚本登记（脚本适配器 reload 时全部清除，Java 侧常驻）。 */
    default boolean scripted() {
        return false;
    }

    /**
     * DreamLang 方言改写：执行脚本前链上适配器依次调用，返回改写后的文本。
     * 不实现 = 原样放行；实现 = 把外来语法转换成 DreamLang 等价写法，
     * 核心执行器不需要为此改动。
     */
    default String rewriteScript(String script) {
        return script;
    }
}