# OpenDreamCore-Legacy

老版本线：1.6.4 / 1.7.10 / 1.12.2 / 1.16.5，清一色 Forge + Java 8。
这摊子是块从远古时代刨出来的屎山——工具链是 Gradle 2.x/4.x + 上古 ForgeGradle，
渲染 API 每个版本一个时代，跨版本协议完全不通。能把这堆东西编出 jar 的都不是正常人。

## 目录结构

| 目录/文件 | 干什么的 |
|---|---|
| `client-legacy/` | 老壳共享客户端源码（四版本共用一份，语法层 Jabel 脱糖） |
| `common-j8/` | 现代 `../OpenDreamCore/common` 的 JDK8 成品 jar 模块（主树经 Jabel + API 黑名单烘成） |
| `core/` | 插件 core（老壳插件侧） |
| `targets/` | 四个远古 target：`forge-1.6.4 / forge-1.7.10 / forge-1.12.2 / forge-1.16.5`，每个自带 gradlew 独立构建 |
| `tools/` | 兑底脚本：JDK8 语法残留扫描、parity 校验、SRG 映射生成 |
| `libs/` | 本地 jar 依赖（forge-universal / MCP 名 MC jar 之类绝版货） |

## 为什么长得这么阴间

- **共享树只有一份**：现代 `OpenDreamCore/common` 主树。远古 target 不塞源码，吃 `common-j8/`
  的成品 jar。以前手工抄三棵树（139 个文件，55 个已经抄漂了），全删了。
- **1.7.10 / 1.6.4 不用 ForgeGradle 工作区**：FG1.x 装工作区要从 Forge S3 下官方 jar 打二进制补丁，
  S3 那批文件下架了，校验和跟补丁头对不上，工作区装不出来。只能绕开，拿 forge-universal.jar +
  缓存里的 MCP 名 MC jar 当编译依赖，plain java 硬编。
- **产物多数是开发态 jar**：除了 1.16.5 走正规 reobfJar，其余三个类名/方法名就是源码里的
  MCP 名或直名，没做 reobf。要上正题环境得自己再做 SRG 重映射，先记着这茬。

## 老版本要注意什么

- 现代插件一个都别想用（Gradle 2.x/4.x + 老 ForgeGradle）
- 网络协议跨版本不通，各版本独立跑
- 渲染从 GL11 到 GlStateManager 到 BufferBuilder，每个时代一套 API
- common 能复用，但手别痒用 JDK 9+ 的 API（var、record、List.of 想都别想）