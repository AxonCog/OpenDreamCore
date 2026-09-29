# 贡献指南

感谢你对 OpenDreamCore 的关注！欢迎一切形式的贡献。

## 开发环境

- JDK 21（Temurin 或 Oracle）
- Git
- IDE：IntelliJ IDEA 推荐（装 Minecraft Development 插件）

## 构建

```powershell
git clone https://github.com/AxonCog/OpenDreamCore.git
cd OpenDreamCore

# 客户端模组
cd OpenDreamCore
.\gradlew.bat -p targets/neoforge-1.21.1 build
.\gradlew.bat -p targets/fabric-1.21.1 build

# 服务端插件
cd ..\OpenDreamCore-Plugin
.\gradlew.bat build
```

构建产物在 `output/` 目录。

## 项目结构

```
OpenDreamCore/
├── client/src/.../client/     ← 共享客户端代码（跨加载器）
├── common/src/.../opendreamcore/ ← 共享核心代码（零 MC 依赖）
├── gensrc/                    ← 共享「生成源树」（按版本代拆开，构建时挂进目标）
├── targets/neoforge-1.21.1/   ← NeoForge 平台适配
├── targets/fabric-1.21.1/     ← Fabric 平台适配
└── ...

OpenDreamCore-Legacy/
├── common-j8/                 ← Java 8 共享层
├── client-legacy/             ← 远古端客户端共享代码
└── targets/forge-1.7.10/      ← 各远古版本壳

OpenDreamCore-Plugin/
├── core/                      ← 与 Bukkit 无关的核心逻辑
├── plugin/src/.../api/        ← 附属插件用的 Java API
├── plugin/src/.../plugin/     ← 服务端实现
└── nms-v1_21_R1/              ← 直连服务端内部的 NMS 层
```

### 共享源码树

`client/` 和 `common/` 是共享源码树，编译时自动合并进各 target。
修改这两个目录的代码会影响所有平台。

### 共享生成源树 gensrc/

`gensrc/` 也是共享源码，但**按 MC 代际拆开写**：同一个功能在不同代际的 API 形状不一样
（字形对象从接口变类、渲染管线改名、访问器可见性变化…），一份源码加类型别名糊不过去，
于是按代各写一份，由各 target 的 `build.gradle` 用 Sync 任务复制进
`build/generated/<树名>` 再挂成源码根。六棵树：

| 目录 | 覆盖版本 | 提供什么 |
| --- | --- | --- |
| `gensrc/glyph-1.20-1.21.4` | 1.20.1 / 1.21.1 / 1.21.4 | 字形层（OdcBakedGlyph / OdcGlyphCache / OdcGlyphFactory / OdcGlyphInfo / OdcTtfGlyphInfo）+ `FontSetMixin` |
| `gensrc/glyph-1.21.5+` | 1.21.11 / 26.1.2 | 下一代字形层实现 + `FontSetSourceMixin` |
| `gensrc/worldrender-1.21.8` | 1.21.8 | 自建渲染类型/管线 + `MixinCompositeStateAccessor` / `MixinRenderPipelinesAccessor` |
| `gensrc/worldrender-1.21.5+` | 1.21.11 / 26.1.2 | 管线 API 改名后的另一份实现 + `WorldRenderPipelinesMixin` / `WorldRenderTypeMixin` |
| `gensrc/worldrender-pipe-1.21.11` | 1.21.11 | 该代独有的管线实现 |
| `gensrc/worldrender-pipe-26.1.2` | 26.1.2 | 该代独有的管线实现 |

两个容易踩的坑：

- 目录名里的 `+` 是**字面字符**（`glyph-1.21.5+` = 「1.21.5 及以后」）。PowerShell 里做匹配
  必须 `[regex]::Escape($name)`，直接丢进正则会把 `+` 当量词。
- 改这些树的名字或位置时，除了目录本身还得同步改三处：各 target 的 `build.gradle`
  （`from('...')` 路径、`$buildDir/generated/<树名>`、`prepare*` 任务名）、
  `.github/scripts/sync_check.ps1`（`$genRoot` 与 `$genDirs`）。漏改 `from` 路径时
  Gradle 的 Sync 任务是**静默 NO-SOURCE**：构建不报错，但生成目录是空的，直到编译才缺类。

平台层（targets/）只包含加载器特定代码：
入口类、网络注册、事件钩子。

## 提交规范

```
<类型>: <描述>

类型: feat / fix / refactor / docs / build / chore
```

例：`feat: 新增视频进度条点击跳转`

## 提 PR

1. Fork 仓库
2. 创建功能分支：`git checkout -b feature/my-feature`
3. 提交改动
4. 推送到你的 fork
5. 创建 Pull Request

PR 会自动触发 CI 构建和测试。

## 报告 Bug

在 [Issues](https://github.com/AxonCog/OpenDreamCore/issues) 页面创建 Issue，包含：

- MC 版本 + 加载器 + 加载器版本
- OpenDreamCore 版本
- 崩溃报告（如果有）
- 复现步骤
- 预期行为 vs 实际行为

## 附属插件开发

参考 [docs/API.md](docs/API.md) 了解如何使用 OpenDreamCore 的 Java API 开发附属插件。

## 行为准则

- 尊重所有参与者
- 保持讨论技术相关
- PR 里不要夹带未公开授权的内容（内部资料、别人的反编译产物之类）

## 架构约定：功能必须全版本对齐

客户端分两层：`common/`、`client/` 是跨加载器共享源码树，`targets/` 下每个壳只放平台胶水（入口类、事件挂接、API 接线）。

由此有两条硬规矩：

1. **功能长在共享树里**。新功能先以 neoforge-1.21.1 为标杆做通，然后把平台无关的部分沉到共享层，版本差异用 Compat*/SPI 接口吸收（比如 WorldHologram 里的 rsDepthMask 就是这么处理的）。各 target 壳里出现业务逻辑就算漂移，得回收进共享层。
2. **所有 12 个 target 行为必须一致**，不允许"先跑通某个版本再说"。提交前自查：新增的 SPI/挂点在其他壳里有没有对应接线——跑 `gradlew.bat verifyMatrix` 逐壳编译，全绿才提。

注释只写"这段代码做什么、为什么这么做"。

违反上面两条的 PR 不会被合并。
