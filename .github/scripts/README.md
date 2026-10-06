# `.github/scripts/` —— 仓库自检脚本

这里的脚本都是**纯静态检查**，不编译、不下载依赖，本机与 CI 跑的是同一份。CI 的编排见
`../workflows/ci.yml`（两道门禁）与 `../workflows/release.yml`（编译矩阵，与脚本无关）。

| 脚本 | 干什么 | 什么时候跑 |
|---|---|---|
| `sync_check.ps1` | 逐 target 断言「契约实现」齐不齐、mixin 注册一致 | CI 每次 push/PR |
| `check_bloat.py` | 《0.2.x规划-新架构设计与扩展规范》§10 的 12 条规则机检 | CI 每次 push/PR；本机改完代码随手跑 |
| `gen_index.py` | 扫源码注解，生成/校验编译期索引（四支柱之一） | 加了 `@OdcSystem/@OdcMethod/@OdcCapability` 之后由 CI 校验；生成期由构建自动跑 |
| `bloat-baseline.txt` | 反臃肿规则的**存量违规**基线（自动生成，勿手改） | 由 `--update-baseline` 写 |

---

## 反臃肿门禁 `check_bloat.py`

```bash
python .github/scripts/check_bloat.py                  # 全仓体检：报告模式，永远退出码 0
python .github/scripts/check_bloat.py --gate <目录>...  # 门禁模式：这些目录内出现新增违规即退出码 1
python .github/scripts/check_bloat.py --json out.json   # 明细落盘（给工具/评审看）
python .github/scripts/check_bloat.py --since <git-ref> # 供 R10 比对「新增了多少 target 文件」
python .github/scripts/check_bloat.py --selftest        # 造夹具自检：证明每条规则真会抓
python .github/scripts/check_bloat.py --update-baseline # 维护者动作：把当前违规写成新基线
```

### 为什么要有「基线」

规则是给**新代码**立的规矩，不可能一夜之间让 0.1.x 的存量代码全部达标。所以：

* 存量违规记进 `bloat-baseline.txt`，**报告里照旧列出来**（`基线内` 一列就是它们）；
* 门禁只对 `新增` 失败；`--gate <目录>` 再把范围收窄到指定目录，做到「新模块 0 失败」。

基线的键是 **`规则 + 相对路径 + 说明` + 允许条数**，**刻意不含行号**：

```text
R1	OpenDreamCore/client/src/main/java/com/opendreamcore/client/AnimationEngine.java	class AnimationEngine 共 521 行（上限 500）	1
```

这么设计是实测出来的必要性：如果键里带行号，存量文件只要被编辑一次（哪怕只是上面多一行注释），
违规所在行号就整体位移，键立刻失配 —— 门禁会对着「什么都没做」的改动报新增违规，最后没人愿意
留它。改成按条数记账后：行号位移不再误报（已实测：给基线文件顶部插一空行，`新增` 仍是 0），
而同键多出来的违规照样算新增（所以条数要如实记）。

### 规则一览（与实现的近似程度一并写明）

| 规则 | 要求 | 实现方式 |
|---|---|---|
| R1 | 单类 ≤500 行 | 花括号配平扫描（跳过字符串/注释里的括号） |
| R2 | 单方法 ≤60 行 | 同上（含构造方法；匿名类/lambda 记在最近的外层方法） |
| R3 | 单包 ≤40 个类 | 目录内 `.java` 计数 |
| R4 | 单文件 YAML ≤800 行 | 行数计数 |
| R5 | 领域层（`core/`）不许依赖 MC | `import net.minecraft\|com.mojang` 计数 |
| R6 | 非 L0 层不许 `import net.minecraft` | 接触层白名单之外的文件计数 |
| R7 | 热路径不许反射 | 热路径类名命中 `Class.forName/getMethod/getField`；**登记的反射底座豁免** |
| R8 | 不许重复声明 | 新架构内同名类型重复 + 索引文件重复键 |
| R9 | 新架构模块要有 `MODULE.md` 与 `package-info.java` | 存在性检查（模块清单 = `NEW_ARCH_MODULES`） |
| R10 | 单次给 target 加文件 ≤5 | `git diff --name-status <--since>` |
| R11 | 包 id / mixin 必须登记 | 索引新鲜度（调 `gen_index.py --check`）+ mixin 幽灵/漏登记 |
| R12 | `*Manager` ≤500 行、`*Util(s)` ≤300 行 | 行数计数 |

### 两条规则的判定口径（踩过坑，写清楚免得后人误判）

**R7 的豁免名单是「登记过的反射底座」**：`Compat*` / `Mc*` / `Odc*` 前缀 + 几个具名类
（`CompatRender`/`CompatBuffer`/`McNames`/`CompatClassShim`/`GlStateBridge`/`GlState`/`McReflect`）。
理由：`Compat*`/`Mc*` 是版本适配层，它存在的意义就是「用反射把版本差异吃掉」；`Odc*` 是各代自建的
渲染类型/管线适配器，必须接包内可见的构造入口。**新写的热路径代码要反射，得先说明为什么不能直调**
（对应规范 §10 R7 的「人工 + 关键类白名单」）。

**R11 的共享 mixin 只在「真死代码」时报**：`gensrc/` 下的 mixin 是跨平台共享的，而某些平台有官方
替代登记路径 —— 例如 `WorldRenderPipelinesMixin` 在 NeoForge 侧刻意不登记，因为那边有官方的
`RegisterRenderPipelinesEvent`（`targets/neoforge-1.21.11/.../ClientEvents.java`）。所以判定口径是
「**编译它的目标全都没登记**」才报错；也可以在类里写 `odc:mixin-alternative` 标记显式声明替代路径。
（逐目标判定会把「按平台设计」误报成「漏登记」，这条也是实测踩出来的。）

### 已知未覆盖（诚实登记，别当成「已经保证」）

* R1/R2 是**文本级近似**，不是真正的 PSI/AST 分析：极端的字符串/泛型写法可能算错行数。
* R10 依赖 git 上下文；浅克隆或缺 `--since` 时**跳过并说明**，不失败。
* R11 的「包 id」以生成索引为事实来源，所以索引新鲜度必须先过（CI 里排在前面单独一步）。
* 规则只扫 `OpenDreamCore/`、`OpenDreamCore-Legacy/`、`OpenDreamCore-Plugin/` 三个工程目录；
  `dev/`、`docs/` 下放着第三方参考模组源码，不是受检对象（也正因为把它们排除在外，全仓体检
  才从 6 分多钟降到 1 分钟以内）。

### 加一条新规则 / 改豁免名单

1. 在 `check_bloat.py` 里写 `rule_rNN(scope)`，返回 `[(相对路径, 行号, 说明), ...]`（或 `(bad, exempt)` 两元组）；
2. 注册进 `RULES` 列表（R10 形态特殊，见 `run()` 里对它的特判）；
3. **在 `selftest()` 里加一条夹具**，断言「这条规则真会抓到必然违规的样本」——
   没有夹具的规则等于没写：它可能因为一个正则笔误而永远静默通过；
4. 跑 `--selftest` 与全仓报告，再 `--update-baseline`（基线变动要随代码评审一起过目）。

### 新增一个 0.2.x 模块时必须做的一件事

在 `check_bloat.py` 顶部的 `NEW_ARCH_MODULES` 里加上模块目录名（如 `"camera"`）。
R5（领域层不得依赖 MC）/R8（重复声明）/R9（MODULE.md + package-info.java）的管辖范围
**只认这张表**：目录存在但未登记，这三条规则就不管它——门禁会看上去「全绿」，实际上是漏检。
自检里那对夹具（`core/` 与 `camera/` 各一个缺 `package-info` 的包）就是用来防这件事的：
只要注册表的机制被改坏（比如又退化成只查 `core/`），`R9相机` 那条断言就会 FAIL。
