# neoforge-1.21.1

- NeoForge 21.1.x · MC 1.21.1 · Java 21
- 状态：**测试中**

主 target，和 fabric-1.21.1 共用 common + client 渲染代码。
平台差异只在事件挂接（`ClientEvents`）和网络注册（`UiChannel` PayloadRegistrar）。

## 构建

```
gradlew.bat build
```

产物自动收集到 `../../output/`。
