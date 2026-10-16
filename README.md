<div align="center">

# OpenDreamCore

### 梦想核心 · 开源 Minecraft 引擎与 UI 框架

**YAML 写界面，脚本写逻辑，进服即用，单机也行**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Stars](https://img.shields.io/github/stars/AxonCog/OpenDreamCore?style=social)](https://github.com/AxonCog/OpenDreamCore/stargazers)
[![Issues](https://img.shields.io/github/issues/AxonCog/OpenDreamCore)](https://github.com/AxonCog/OpenDreamCore/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

**[English](#english) · [中文](#中文)**

</div>

---

# 中文

## 这是什么

OpenDreamCore 不是一个单纯的 UI 模组。

它目前以 UI 与页面系统为主，模型渲染和动作系统仍在迁移中；网络、资源与服务端能力以各子项目当前实现为准。部分功能仍在开发或仅覆盖特定版本。

服主可以使用 YAML 配置页面，玩家进服后由客户端加载；具体页面、网络和单机能力取决于对应版本与子项目的实现。

## 为什么开源

2022 年龙之核心出来的时候我就在关注。那时候大家都在造轮子，我也一样。2023 年正式立项搞梦想核心，一开始就是自用——自己的服务器、自己的玩法、自己折腾。2025 年小范围内测，不少朋友陪着熬了很长时间。

当前仓库包含已公开的代码与部分设计文档；功能覆盖和文档完整度仍在持续完善。

> **以前，这是我的梦想。**
> **以后，这是我们的核心。**

欢迎大家提 PR、提 Issue、提想法，或者只是来看看代码、抄个作业——大门都为你敞开。

## 开发时间线

| 时间 | 事件 |
|---|---|
| 2022 | 龙之核心发布，开始关注 MC UI 生态 |
| 2023 | 正式立项，梦想核心 1.0 启动开发 |
| 2025 | 小范围内测，收集反馈，迭代核心架构 |
| 2026 | 全面开源 |

### 当前进度

| 模块 | 状态 |
|---|---|
| UI 引擎（渲染 + 交互 + 页面系统） | 部分版本可用 |
| 网络协议（握手 + 下发等） | 以版本实现为准 |
| 脚本语言 DreamLang | 以当前实现为准 |
| 服务端插件与资源云 | 以插件实现为准 |
| 字符替换与位图字体 | 以版本实现为准 |
| HUD 多页叠加 | 以版本实现为准 |
| 资源云管线 | 以版本实现为准 |
| 多版本覆盖 | 现代与远古版本分为不同子项目，能力并不自动等同 |
| 模型渲染引擎 | 🔄 迁移中 |
| 动作/动画系统 | 🔄 迁移中 |
## 它能干什么

### 给玩家
- 自定义商店、菜单、HUD、世界空间面板、NPC 对话气泡、tooltip
- 进服版本检查，不一致会提醒
- 单机也能玩全套功能

### 给服主
- YAML 写页面，改文件即生效，不用重启
- 热插拔插件目录，丢文件夹就是一套新玩法
- 资源云下发，图片/音频/字体自动同步，增量加密传输
- PlaceholderAPI 自动桥接，没有也能用内置引擎；player/query/system 变量客户端本地解析
- 游戏内可视化编辑器：拖拖拽拽就能改界面
- 字符替换配一张贴图/gif，"肝"都能换成你的动画
- HUD 多张叠挂、世界全息面板、NPC 对话气泡，一页配置一页界面

### 给开发者
- DreamLang 脚本语言：中英双语关键字，类/模块/异常/异步齐全
- 万物皆插件：组件、方法、函数、页面、命令、网络包全是注册表
- 可插拔配置语法：YAML 是默认，换 JSON/TOML 只需写一个解析器

## 架构一览

```
用户层：YAML 页面 + DreamLang 脚本 + 插件目录（热插拔）
       ↓ 可插拔解析
中转层：ConfigIR（格式无关的中间表示）
       ↓ Schema 映射
模型层：common 页面模型（零 MC 依赖，服务端客户端共用）
       ↓ 编码
协议层：自研二进制协议（opendreamcore:*，版本协商，加密）
       ↓
客户端：渲染引擎（能力随目标版本而异）
服务端：服务端能力（以插件实现为准）
```

## 子项目

| 目录 | 说明 | 状态 |
|---|---|---|
| `OpenDreamCore/` | 客户端模组（现代 target） | 现代版本线 |
| `OpenDreamCore-Legacy/` | 远古版本线（1.7.10–1.16.5 Forge） | 独立维护 |

## 文档

以下文档位于现代客户端子项目的 `OpenDreamCore/docs/`：

- [`Animation.md`](OpenDreamCore/docs/Animation.md) — 动画说明
- [`Animation-API.md`](OpenDreamCore/docs/Animation-API.md) — 动画 API
- [`BMD.md`](OpenDreamCore/docs/BMD.md) — BMD 格式说明
- [`YSM.md`](OpenDreamCore/docs/YSM.md) — YSM 说明

各子项目的 README 记录其自身的构建与实现范围。仓库当前没有统一的 YAML、协议或 DreamLang 参考手册；请以代码和对应子项目文档为准。

示例页面目录目前未随仓库提供。

## 构建

现代客户端根工程位于 `OpenDreamCore/`，服务端插件位于 `OpenDreamCore-Plugin/`，Legacy 目标按自身 wrapper 独立构建。

```powershell
# 现代客户端根工程：先查看已初始化 target
.\gradlew.bat -p OpenDreamCore listTargets

# 单独构建某个现代 target
.\gradlew.bat -p OpenDreamCore -Ptarget=fabric-1.21.1 build

# 服务端插件
.\gradlew.bat -p OpenDreamCore-Plugin build

# Legacy 当前活动矩阵
.\gradlew.bat -p OpenDreamCore-Legacy verifyMatrix -PmatrixContinue=true
```

各构建命令是否需要额外 JDK、网络依赖或目标专用 wrapper，以实际环境输出为准。构建产物是否收进 `output/all/` 取决于当前构建配置。
### 本轮实测边界

- `OpenDreamCore` 的 `testCommon`：通过。
- `OpenDreamCore` 的 `fabric-1.21.1`：通过，包含共享测试、编译、打包和产物汇总。
- `OpenDreamCore-Plugin`：通过；插件使用 JDK 8 字节码编译，JDK 8/JDK 17 工具链均已定位。
- `OpenDreamCore-Legacy verifyMatrix -PmatrixContinue=true`：活动的 1.7.10、1.12.2、1.16.5 均通过。
- NeoForge 1.21.1 代表目标未完成：源码测试通过，但 `neoform-runtime:2.0.24` 下载时连接 `maven.neoforged.net` 超时。26.1.2 目标还需要 JDK 25，而当前环境未提供该 JDK。
- 当前仓库没有可直接启动的客户端 smoke 入口；画面级交互仍需在对应 Minecraft 实例中验证，不能把编译通过等同于全功能通过。


## 版本覆盖

现代客户端目标位于 `OpenDreamCore/`，远古 Forge 目标位于 `OpenDreamCore-Legacy/`。下表列出本次维护覆盖的目标版本；“可用”不代表所有功能在所有目标上完全一致。

| 版本 | 平台 | 子项目 |
|---|---|---|
| 1.20.1 | Forge + Fabric | `OpenDreamCore/` |
| 1.21.1 | Forge + Fabric + NeoForge | `OpenDreamCore/` |
| 1.21.4 | Fabric + NeoForge | `OpenDreamCore/` |
| 1.21.8 | Fabric + NeoForge | `OpenDreamCore/` |
| 1.21.11 | Fabric + NeoForge | `OpenDreamCore/` |
| 26.1.2 | Fabric + NeoForge | `OpenDreamCore/` |
| 1.16.5 | Forge | `OpenDreamCore-Legacy/` |
| 1.12.2 | Forge | `OpenDreamCore-Legacy/` |
| 1.7.10 | Forge | `OpenDreamCore-Legacy/` |

现代与远古子项目分别维护平台适配层；页面格式、协议和脚本等能力请以各目标的实际实现为准，不作全矩阵行为一致性保证。

## 参与贡献

**欢迎一切形式的贡献！**

- 发现 bug？[提 Issue](https://github.com/AxonCog/OpenDreamCore/issues)
- 有想法？[提 Issue](https://github.com/AxonCog/OpenDreamCore/issues) 讨论一下
- 想写代码？Fork → 分支 → PR，简单粗暴
- 写了好看的 UI 页面？欢迎分享出来当示例

## 社区

**梦想小屋开源交流群**

QQ 群：**1105028422**

有问题、有想法、想聊天，都欢迎进群。

---

## 作者

**梦幻**

QQ：2496599413

GitHub：[AxonCog/OpenDreamCore](https://github.com/AxonCog/OpenDreamCore)

---

<div align="center">

**以前，这是我的梦想。以后，这是我们的核心。**

**⭐ 点个 Star，让更多有需要的人看到 ⭐**

</div>

---

# English

## What is this

OpenDreamCore focuses on UI and page systems. Model rendering and animation are still being migrated, and network, resource, and server capabilities depend on the implementation of each subproject and target.

## Why open source

Minecraft is getting old. The community is shrinking. But what saddens me most isn't the declining player count — it's watching people who love this game still reinventing the same wheels in isolation. UI frameworks, rendering engines, scripting languages — built behind closed doors, never shared. The next person starts from scratch. This cycle has been spinning since 2022.

Open source is the soul of computing. The gap between China's MC open-source ecosystem and the international community isn't about technical skill — it's about mindset. I want to push this ecosystem forward, even if just by half a step.

The repository contains the currently published code and selected design documents; documentation and feature coverage are still being expanded.

> *This used to be my dream.*
> *Now, it's our core.*

PRs, Issues, ideas — all welcome.

## Build

```powershell
pwsh dev\build_matrix.ps1  # build according to the repository's current matrix configuration
.\gradlew.bat build        # or build a single target / the server plugin
```

Artifacts may be collected into `output/all/` by the current build configuration.

Modern targets are maintained in `OpenDreamCore/`; legacy Forge targets are maintained separately in `OpenDreamCore-Legacy/`. Their supported features and behavior should not be assumed identical.

## Community

**Dream Cottage Open Source Group**

QQ Group: **1105028422**

## Author

**梦幻 (MengHuan)** — [AxonCog/OpenDreamCore](https://github.com/AxonCog/OpenDreamCore)

---

<div align="center">

*This used to be my dream. Now, it's our core.*

**⭐ Star this repo if it helps you ⭐**

</div>
