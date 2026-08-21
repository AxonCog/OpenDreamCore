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

它是一套**完整的 Minecraft 引擎**——UI 渲染、模型渲染、动画系统、脚本语言、网络协议、资源云分发、服务端裁决……全都有。你现在看到的是 UI 模块的阶段性成果，后面的模型引擎、动作系统等模块还在排队。

简单说：服主写 YAML 就能出界面，玩家进服自动加载，单机也能玩全套。点格子买东西、扣钱发货服务端裁决，不怕作弊。HUD 随便换，世界空间悬浮面板、NPC 对话气泡、自定义 tooltip……你能想到的 UI 场景基本都覆盖了。

## 为什么开源

2022 年龙之核心出来的时候我就在关注。那时候大家都在造轮子，我也一样。2023 年正式立项搞梦想核心，一开始就是自用——自己的服务器、自己的玩法、自己折腾。2025 年小范围内测，不少朋友陪着熬了很长时间。

四年来这套系统一直在跑，功能早就全面覆盖了——只是以前闭源，现在开源。

> Minecraft 这个游戏在走下坡路，社区在萎缩，但偏偏在这个时候，大家还在各自造轮子。UI 框架造一个、渲染引擎造一个、脚本语言造一个——造完也不开源，然后下一个人继续从头造。这个死循环，已经转了四年了。

计算机最迷人的地方就是开源。国内的开源生态跟国外比差了一大截，不是技术问题，是意识问题。如果大家都把火种藏在怀里，这片原野终究会熄灭。我希望尽自己的一点能力，推着这个生态往前走半步。哪怕只有半步。

**所以梦想核心正式开源了。** 完整代码、完整协议、完整文档，毫无保留地放了出来。

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
| UI 引擎（渲染 + 交互 + 页面系统 + 可视化编辑器） | ✅ 可用 |
| 网络协议（二进制握手 + 下发 + 裁决 + 加密） | ✅ 可用 |
| 脚本语言 DreamLang（中英双语） | ✅ 可用 |
| 服务端插件（Paper 容器裁决 + 资源云） | ✅ 可用 |
| 模型渲染引擎 | 🔄 迁移中 |
| 动作/动画系统 | 🔄 迁移中 |
| 多版本覆盖（1.6.4 ~ 26.1.2） | 🔄 迁移中 |

## 它能干什么

### 给玩家
- 自定义商店、菜单、HUD、世界空间面板、NPC 对话气泡、tooltip
- 进服版本检查，不一致会提醒
- 单机也能玩全套功能

### 给服主
- YAML 写页面，改文件即生效，不用重启
- 热插拔插件目录，丢文件夹就是一套新玩法
- 资源云下发，图片/音频/字体自动同步，增量加密传输
- PlaceholderAPI 自动桥接，没有也能用内置引擎
- 游戏内可视化编辑器：拖拖拽拽就能改界面

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
客户端：自研渲染引擎（30+ 种元素 + 动画 + 完整交互）
服务端：裁决引擎（商店/容器/权限/资源云）
```

## 子项目

| 目录 | 说明 | 状态 |
|---|---|---|
| `OpenDreamCore/` | 客户端模组（common + targets） | 1.21.1 双平台测试中 |
| `OpenDreamCore-Plugin/` | 服务端 Bukkit/Paper 插件 | 1.21.1 测试中 |
| `OpenDreamCore-Legacy/` | 远古版本线（1.6.4–1.16.5 Forge） | 占位，逐步迁移 |

## 文档

- [`docs/YAML语法.md`](docs/YAML语法.md) — UI 配置语法
- [`docs/协议.md`](docs/协议.md) — 自研二进制协议

其他内部文档暂不上传，需要可以找我要。

## 构建

```powershell
# 客户端模组（在 OpenDreamCore/ 下）
.\gradlew.bat -p targets/neoforge-1.21.1 build
.\gradlew.bat -p targets/fabric-1.21.1 build

# 服务端插件（在 OpenDreamCore-Plugin/ 下）
.\gradlew.bat build
```

构建产物自动收集到 `output/` 目录。

## 版本覆盖

| 版本 | 平台 | 状态 |
|---|---|---|
| 1.21.1 | NeoForge + Fabric | ✅ 测试中 |
| 1.20.1 | Fabric | 🔄 迁移中 |
| 1.21.4 | NeoForge + Fabric | 🔄 迁移中 |
| 1.21.8 | NeoForge + Fabric | 🔄 迁移中 |
| 26.1.2 | NeoForge + Fabric | 🔄 迁移中 |
| 1.16.5 | Forge | 🔄 迁移中 |
| 1.12.2 | Forge | 🔄 迁移中 |
| 1.7.10 | Forge | 🔄 迁移中 |
| 1.6.4 | Forge | 🔄 迁移中 |

> 四年积累，全版本早已覆盖。当前工作是把已验证的功能迁移到开源架构——加版本 = 新写一个平台层，核心代码零改动。

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

OpenDreamCore is not just a UI mod. It is a **complete Minecraft engine** — UI rendering, model rendering, animation systems, a custom scripting language, binary networking protocol, cloud resource distribution, server-side authority... the works.

In short: server owners write YAML to create UIs, players get them automatically when joining, and it works in singleplayer too. Click-to-buy with server-side authority, custom HUDs, world-space holograms, NPC dialogue bubbles, custom tooltips — pretty much every UI scenario you can think of.

## Why open source

Minecraft is getting old. The community is shrinking. But what saddens me most isn't the declining player count — it's watching people who love this game still reinventing the same wheels in isolation. UI frameworks, rendering engines, scripting languages — built behind closed doors, never shared. The next person starts from scratch. This cycle has been spinning since 2022.

Open source is the soul of computing. The gap between China's MC open-source ecosystem and the international community isn't about technical skill — it's about mindset. I want to push this ecosystem forward, even if just by half a step.

**So here it is. Full code, full protocol, full docs. No strings attached.**

> *This used to be my dream.*
> *Now, it's our core.*

PRs, Issues, ideas — all welcome.

## Build

```powershell
.\gradlew.bat -p targets/neoforge-1.21.1 build
.\gradlew.bat -p targets/fabric-1.21.1 build
.\gradlew.bat build  # Server plugin
```

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
