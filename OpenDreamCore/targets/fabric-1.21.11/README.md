# fabric-1.21.1

- Fabric Loader 0.16 + Fabric API · MC 1.21.1 · Java 21
- 状态：**测试中**

和 neoforge-1.21.1 共用 common + client 渲染代码，平台差异只在事件挂接和网络发送。
- NeoForge：`ClientEvents` + `UiChannel`（PayloadRegistrar）
- Fabric：`FabricEvents` + `FabricChannel`（ClientPlayNetworking）

网络发送统一走 `ClientController.UiSender`，入口注入。

## 构建

```
gradlew.bat build
```

产物自动收集到 `output/`。
