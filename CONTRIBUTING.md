# 贡献指南

感谢你对 OpenDreamCore 的关注！欢迎一切形式的贡献。

## 开发环境

- JDK 21（root/common/插件与大部分现代 target）；1.20.1 target 需要 JDK 17，26.1.2 target 需要 JDK 25，完整发布还需要 legacy 的 JDK 8/17。
- Git
- IDE：IntelliJ IDEA 推荐（装 Minecraft Development 插件）

## 构建

```powershell
git clone https://github.com/AxonCog/OpenDreamCore.git
cd OpenDreamCore
# 进入仓库里的现代端根 Gradle 工程
cd OpenDreamCore

# 共享层测试（root build/buildFull/verifyMatrix 都会先执行此门禁）
.\gradlew.bat testCommon

# 编译指定 target（默认首个 active target）；完整矩阵会使用各 target 要求的 JDK
.\gradlew.bat -Ptarget=neoforge-1.21.1 build
.\gradlew.bat verifyMatrix

# 全量现代 target、在建 legacy target、插件及 output/all 产物门禁
.\gradlew.bat buildFull
```

独立 target 仍可直接构建，例如 `.\gradlew.bat -p targets/fabric-1.21.1 build`；服务端插件在 `..\OpenDreamCore-Plugin` 中运行其 wrapper。构建产物在 `output/` 目录。

Root Gradle 8.8 使用 JDK 21 运行。按需设置 `JAVA8_HOME`、`JAVA17_HOME`、`JAVA21_HOME`、`JAVA25_HOME`，或传入对应的 `-Pjava8Home=...`、`-Pjava17Home=...`、`-Pjava21Home=...`、`-Pjava25Home=...`。`build` 与 `verifyMatrix` 复用同一套 target JDK 选择；`testCommon` 和插件使用 JDK 21，`buildLegacy` 使用 JDK 17 并向 legacy 编排传递 JDK 8/17 配置。
`buildFull` 先通过 common 测试，再构建并汇总现代、Legacy（1.7.10、1.12.2、1.16.5）和插件产物，最后运行 `verifyArtifacts`。

所有 wrapper 使用腾讯 Gradle 镜像，保留原有 Gradle 版本；wrapper 本身不自动切换下载地址，镜像不可达时可将 `distributionUrl` 改为同版本的 `https://services.gradle.org/distributions/gradle-<版本>-bin.zip`。Maven/插件依赖优先使用国内镜像，后置官方仓库作为未命中时的回退，并保留 Fabric/Forge/NeoForge 等专用仓库。

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
└── plugin/src/.../plugin/     ← 服务端实现
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

## 反臃肿门禁（规范 §10）

改完代码顺手跑一遍（纯静态，不用编译）：

```bash
python .github/scripts/check_bloat.py                  # 全仓体检（报告模式，永远不失败）
python .github/scripts/check_bloat.py --gate OpenDreamCore/common/src/main/java/com/opendreamcore/core
python .github/scripts/check_bloat.py --selftest        # 改了规则必须跑：证明规则真会抓
```

规则是给**新代码**立的，存量违规记在 `.github/scripts/bloat-baseline.txt`，所以门禁只对**新增**违规失败。
细则（12 条规则的实现近似程度、豁免名单、基线为什么不带行号、已知未覆盖）都在 `.github/scripts/README.md`。
提 PR 前请自查三点：① 新写的类 ≤500 行、方法 ≤60 行；② 热路径要反射得说明理由，否则走句柄缓存/索引；
③ 新增/修改规则时，必须同时在 `--selftest` 里加夹具。

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

当前仓库没有统一的 `docs/API.md`。服务端插件 API 以 `OpenDreamCore-Plugin/plugin/src/main/java/com/opendreamcore/plugin/api/` 下的源码和各模块构建配置为准。

## 行为准则

- 尊重所有参与者
- 保持讨论技术相关
- PR 里不要夹带未公开授权的内容（内部资料、别人的反编译产物之类）

## 架构约定：按共享边界维护

客户端分两层：`common/`、`client/` 是跨加载器共享源码树，`targets/` 下的壳负责平台入口、事件挂接和 API 接线。现代目标优先复用共享实现；Legacy 线因 Java 8 和 Minecraft API 差异保留独立适配代码。

修改功能时应同时检查共享层消费者和所有受影响 target，不把尚未验证的跨版本行为写成统一承诺。注释只说明代码做什么、为什么这样做。

违反上面约定的 PR 不会被合并。
