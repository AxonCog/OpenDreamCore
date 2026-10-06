# camera —— 相机子系统（0.2.x 新架构首个落地样例）

## 边界（反臃肿规则的管辖依据）

- 本包只含**版本无关**的规则 IR 与客户端仓库：`CameraRule`（IR + 解析）、`CameraStore`（规则仓库）。
- **禁止 import net.minecraft / com.mojang**（机检 R5 管辖）；触达 MC 一律走
  `targets/*/OdcCameraBridgeImpl`（版本特有）与客户端层（`ClientController` / `OdcCommands`）。
- 新架构模块清单：`check_bloat.py` 的 `NEW_ARCH_MODULES`（本目录已登记；R9 要求本文件 + 每包 `package-info.java`）。
- 每新增一个本包子包：必须带 `package-info.java`，且类总量 ≤40（R3）。

## 数据流

```
服务端权威：plugins/OpenDreamCore/visual/Camera.yml
  （VisualRuleManager.SYSTEMS 含 "Camera"；模板 VisualTemplates.CAMERA 缺省落盘）
    → /odc reload 或玩家进服 → VisualRulesSync 全量下发
    → ClientController.handleVisualRules → CameraStore.apply（整体覆盖）→ 日志「相机配置已同步：机位 N，路径 M」
本地单机：gameDir/OpenDreamCore/visual/Camera.yml → /odc reload（未连服）或 /codc reload → 同一 CameraStore.apply
```

- 查询：`/odc camera list`（客户端本地子命令，`OdcCommands` 对 camera 前缀不转发）。
- 规则只是"有哪些运镜"，**不自动播放**；播放控制 M1.2 起走 `/odc camera play|stop` 与 CameraPlay/CameraStop 消息。

## 扩展点

- 新运镜类型：`CameraRule.Type` 加枚举 + `parse` 分支；桥实现按 type 分发。
- 表达式：`positionExpr`/`rotationExpr` 本期原样保留（只存不求值），M1.3 由脚本命名空间编译缓存——
  **禁止每帧重新解析字符串**（DreamCore 把 posX 长期留作 String 的教训，规范 §4.1）。
- 播放/状态栈（M1.2+）：备份/恢复视角、HUD、输入锁，全部在桥与客户端层；本包不参与网络与渲染。

## 已知限制

- `CameraStore` 是客户端单例，整体覆盖语义：服务端下发优先；`/codc reload` 会用本地文件覆盖
  （与页面/其它视觉规则同语义，服务端模式下以推送为准）。
- 解析失败的单条规则只计数跳过（摘要尾缀注明），不拖垮其它规则。
