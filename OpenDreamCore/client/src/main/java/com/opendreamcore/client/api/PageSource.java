package com.opendreamcore.client.api;

import java.nio.file.Path;
import java.util.Map;

/**
 * 页面源接口：本地页面的出处，谁提供谁实现这个。
 *
 * 核心默认自带 YAML 目录源（扫游戏目录 OpenDreamCore/UI/）。附属模组想做别的
 * 来源——可视化编辑器的实时页、附属自己的资源目录、配置中心下发的包——注册一个
 * 实现进 PageSourceRegistry 就接进主流程，核心不用改。
 *
 * 只管交出原文，解析/模板展开/构建仍走核心那条管线（import 要跨页查 IR，
 * 拆散了就做不了），所以这里返回的是 id → YAML 文本。
 */
public interface PageSource {

    /** 源名字，查重用（核心自带的是 "yaml-dir"）。 */
    String name();

    /**
     * 扫一遍来源，返回 页面id → YAML 原文。id 用相对路径风格（"hud/help"）。
     * 出错就自己吞掉记日志，别往外抛——一个源挂了不该拖垮整次加载。
     *
     * uiDir：核心约定的本地 UI 目录（附属源可以不理它，改读自己的位置）
     */
    Map<String, String> scan(Path uiDir);
}
