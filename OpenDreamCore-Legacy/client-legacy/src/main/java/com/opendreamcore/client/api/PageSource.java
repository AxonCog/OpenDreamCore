package com.opendreamcore.client.api;

import java.nio.file.Path;
import java.util.Map;

/**
 * 页面源接口（老壳四版共用）：本地页面的出处，谁提供谁实现这个。
 *
 * 核心自带一个 YAML 目录源（扫 gameDir/OpenDreamCore/UI/）。附属模组想做别的
 * 来源——自己的资源目录、配置中心下发的包——注册进 PageSourceRegistry 就接进
 * 主流程，核心不用改。
 *
 * 只交原文，解析和构建仍走 dispatcher 那条管线（解密、校验、告警都在那边统一做）。
 */
public interface PageSource {

    /** 源名字，查重用（核心自带的是 "yaml-dir"）。 */
    String name();

    /**
     * 扫一遍来源，返回 页面id → YAML 原文。
     * 出错自己吞掉记日志，别往外抛——一个源挂了不该拖垮整次加载。
     *
     * uiDir：核心约定的本地 UI 目录（附属源可以不理它，改读自己的位置）
     */
    Map<String, String> scan(Path uiDir);
}
