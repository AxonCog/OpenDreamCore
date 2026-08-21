# client/ — 双平台共享客户端源码树

> 这块还缺少我模块化改造（2026-08）：把 NeoForge / Fabric 两个 target 里**逐字重复维护**的 31 个客户端类
> 收敛到这一棵源码树。改一处，两端同时生效，不再出现"neoforge 修了 fabric 忘了"的漂移。
> 因为更新太急切，直接迁移了之前的管线，并没有模块化，此处将会在1.0.0版本之前完成全面模块化重构

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
> 这块如果你自己进行开发请务必遵循我们规范，请勿打乱规范 -梦幻敬上

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
