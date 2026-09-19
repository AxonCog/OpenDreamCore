package com.opendreamcore.adapter;

import java.util.Map;

/**
 * 方言适配器——热拔插协议的「插头」本尊。
 *
 * 一句话：认领一种你眼熟的外来格式，把它翻译成 ODC 自己人能听懂的规则。
 *
 * 想给 ODC 接一种新语法？三步走完：
 *   1）新建一个方言包 {@code com.opendreamcore.adapter.<你的方言名>/}（龙核的
 *      就住 dragoncore/，谁家的东西进谁家的门——一个文件夹 = 一个方言大家庭）；
 *   2）在包里面写个类实现本接口：accepts() 决定「这文件我熟不熟」，
 *      translate() 负责把内容翻译成 ODC 规则；
 *   3）启动时 AdapterChain.register(new 你的适配器()) 插进链子。
 * 完事。核心代码一个字符都不用动——这就是热拔插该有的样子。
 *
 * 别被「视觉规则」这几个字带偏：这接口啥都能适配。任何「外来 YAML/脚本 →
 * ODC 的某个规则仓库（视觉九系统 / tooltip / 主题 / 按键表…）」的转换
 * 都长这样。产物统一是 Map<目标系统, Map<id, IR>>——往哪个仓库塞，
 * 看你 translate() 里把 key 写成了谁。
 *
 * 三方来路对应三种热拔插姿势：
 *   1) 内置：adapter/dragoncore/ 里的龙核适配器，永远在链上头（priority 最小）；
 *   2) 附属插件：Java 侧 register，随装随用，reload 不清；
 *   3) 服主脚本：extensions/adapters/*.yml 声明 + Extension.适配器() 登记，
 *      跟着 /odc reload 走，热拔插界的快餐。
 */
public interface Adapter {

    /** 适配器名（同一链上不许重名，重名注册直接覆盖；日志和规则前缀都靠它认人）。 */
    String name();

    /**
     * 认领判定：这个文件是不是我的菜？
     * 文件名 + 解析好的根 IR 都摆在桌上，看清楚再拍板，别靠猜后缀蒙事。
     */
    boolean accepts(String fileName, Map<String, Object> rootIr);

    /**
     * 翻译主厨：外来根 IR → ODC 规则集。
     * 返回 Map<目标系统, Map<规则id, 规则 IR>>；空 map = 看走眼了没货，让给下一个。
     */
    Map<String, Map<String, Map<String, Object>>> translate(String fileName, Map<String, Object> rootIr);

    /** 是不是脚本临时工的活？脚本登记的重载时全清，Java 自带的常驻不挪窝。 */
    default boolean scripted() {
        return false;
    }

    /**
     * DreamLang 方言改写（觉得外星语法不顺眼的适配器在这动手脚）：
     * 执行一段脚本前，链子挨个问过来，谁有改写谁发言。
     * 不实现 = 原样放行；实现 = 把不属于 DreamLang 的老写法掰成 DreamLang 的样子。
     * 什么"完全自定义语法"？秘诀就在这——改写规则写死在自己包里，
     * 想支持哪种方言就怎么写，核心执行器一个字不用动。
     */
    default String rewriteScript(String script) {
        return script;
    }
}