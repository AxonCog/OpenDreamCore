# core-a —— 地基模块（注册表 / 编译期索引 / 能力总线 / 脚本命名空间钩子）

## 用途

把「加一个东西要改 N 处」收敛成「加一个东西 = 加一个类 + 一份声明」。本模块**只提供机制**，
不含任何具体业务（相机 / 模型 / 动画都不在这里）。

| 支柱 | 本模块的落地物 | 调用入口 |
|---|---|---|
| 一、描述符化 | `@OdcSystem` + `SystemDescriptor` + `RuleSchema`/`@Field` + `SystemRegistry` | `SystemRegistry.bootstrap(cl)` |
| 二、编译期索引 | `@OdcMethod` / `@OdcCapability` + `META-INF/odc-services.idx`（`tools/odcindex` 生成） | `ServiceIndex.load(cl)` |
| 三、能力总线 | `Capabilities` + `CapabilityFactory` + `Features` | `Capabilities.of(holder, XxxCapability.class)` |
| 四、脚本互操作钩子 | `NamespaceRegistry` + `ScriptPolicy` + `HandleCache` | `NamespaceRegistry.bootstrap(cl)` |

## 依赖

- 只依赖 `java.*`：**零 MC 依赖**（规范 §2.1 规则 5/6 —— L2 与共享层不许出现 `net.minecraft.*`）。
- 依赖方向单向：`registry → descriptor`；`capability` / `script` 互不依赖。
- 各 target 的 `*BridgeImpl`（L0）与 `client/` 可以用本模块；本模块**不**反向依赖它们。

## 扩展点

1. 新增一类「可被索引的东西」：加一个 `ServiceKind` 枚举值 + 在 `.github/scripts/gen_index.py` 加一段扫描。
2. 新增一格代际特性：在 `Features.forVersion` 加一行 —— 这是**全仓唯一**允许写版本判断的地方。
3. 新增能力：写一个接口 + 用 `@OdcCapability` 标注实现（每目标一份，或共享层给默认实现）。

## 验收

- `python .github/scripts/gen_index.py --check` 全绿（索引与源码注解一致）。
- `python tools/check-bloat/check_bloat.py --only core` 对本模块 **0 失败**（含 package-info / MODULE.md / 类行数 / 方法行数）。
- 12 个现代目标构建全绿、`collectAll` 与 `deploy_all.py` 照旧 0 失败。

## 负责人

架构（0.2.x 线）
