# OpenDreamCore-Plugin

服务端 Bukkit/Paper 插件。

客户端模组进服 → 握手 → 服务端下发页面 / 裁决事件 / 同步资源。

## 状态

1.21.1 测试中。

## 职责

- 页面下发（YAML → 二进制协议 → 客户端模组）
- 商店/容器裁决（服务端权威，防作弊）
- 资源云下发（哈希增量同步 + AES 加密）
- 版本校验、命令、PAPI 桥接
- 脚本引擎服务端执行

## 构建

```powershell
.\gradlew.bat build
```

产物自动收集到 `../output/`。
