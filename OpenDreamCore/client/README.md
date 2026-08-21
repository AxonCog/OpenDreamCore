# client/ — 双平台共享客户端源码树

> 模块化改造（2026-08）：把 NeoForge / Fabric 两个 target 里**逐字重复维护**的 31 个客户端类
> 收敛到这一棵源码树。改一处，两端同时生效，不再出现"neoforge 修了 fabric 忘了"的漂移。

## 机制

与 `../common/` 完全同款：每个 target 的 `build.gradle` 里有

```gradle
tasks.register('prepareClientSources', Sync) {
    from('../../client/src/main/java')
    into "$buildDir/generated/client-main"
}
```

编译前把本目录原样复制进 target 的构建目录参与编译。**本目录代码不直接被 target 引用**，
改完代码直接编译 target 即可（Gradle 会先同步）。

## 放什么

- 双平台行为完全一致的客户端逻辑（渲染、编辑器、页面会话、HUD、世界面板、脚本桥……）
- 依赖：Minecraft 官方 mojmap（两端 mappings 一致）+ LWJGL + `../common/`
- **不碰**加载器 API：不 import NeoForge / Fabric 任何类

## 不放什么（留在各 target）

| neoforge-1.21.1 | fabric-1.21.1 |
|---|---|
| `OpenDreamCore.java`（mod 入口） | `FabricOpenDreamCore.java` |
| `client/ClientEvents.java`（NeoForge 命令/事件） | `client/FabricEvents.java`（Fabric 命令/事件） |
| `network/UiChannel`、`RawPayload`、`OdcFriendlyBuf`、`HandshakeHandler` | `network/FabricChannel.java` |
| — | `mixin/MixinGui`、`MixinSubtitleOverlay` |

平台层通过 `ClientController.setSender(UiSender)` 注入网络发送能力，
共享代码只依赖 `UiSender` 接口，不感知加载器。

## 当前文件清单（31 个）

核心：`ClientController`（页面会话/HUD/世界面板/编辑器状态机）、`UiRenderer`、`OdcScreen`、
`LayoutEngine` 桥接在 common。
世界：`WorldHologram`、`WorldHoloEdit`、`WorldHoloUtils`、`WorldUiStore`、`WorldLang`。
编辑器：`EditorPanels`、`ElementEditStore`、`ExternalEditor`、`AnimationEngine`。
媒体：`MusicPlayer`、`VideoPlayer`、`FfmpegVideoPlayer`、`GifPlayer`、`TtfRenderer`、
`CustomFonts`、`RemoteImageStore`、`SoundStore`、`WindowBranding`。
存储/同步：`CloudSyncClient`、`LocalPageManager`、`TooltipStore`、`ContainerStore`、
`ChatStore`、`UiFileWatcher`、`UiStyle`、`LegacyText`、`WorldLang`。
脚本桥：`ClientMethods`、`ClientPlaceholders`。

## 模块化现状（2026-08，8 轮迭代后）

| 模块 | 行数 | 职责 |
|---|---|---|
| `ClientController` | ~10.6k | 页面会话 / HUD / 世界面板管理 / 协议处理 / 渲染编排 |
| `WorldEditor` | ~4.5k | 世界编辑器（行为 + 状态：交互/手柄/undo/CRUD/对齐/模板） |
| `WorldHologram` | ~3.0k | 世界渲染核心（render 编排 + 元素渲染器 + 背景） |
| `ScreenElements` | ~1.9k | 屏幕元素渲染器（文本/按钮/输入/卡片/图表/罗盘/画布…） |
| `WorldBackgroundEditor` | ~1.2k | 世界背景子系统（调色板/渐变/预设） |
| `UiRenderer` | ~1.1k | 屏幕渲染调度 + 绘制原语 + 共享工具 |
| `WorldHoloEdit` | ~640 | 世界编辑浮层（手柄/幽灵/网格/参考线） |
| `WorldPicking` | ~270 | 射线拾取与命中检测 |
| 16 个 `WorldEdit*Screen` | — | 世界编辑器各功能屏（对齐/属性/模板/历史…） |

> 拆分原则：行为与状态同模块；跨模块经 `Xxx.get()` 单例 + 包内可见成员访问；
> 公共 API 保留转发壳，调用点零改动。插件侧 `ServerMethods`(1.4k) 为脚本 API
> 注册索引（19 个 register 分组），评估后保持现状。
