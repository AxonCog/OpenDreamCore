# Target 目录结构说明

每个 target = 一个 MC 版本 × 一个加载器的适配层。

## 目录结构

```
targets/<loader>-<mcversion>/
├── build.gradle           ← 构建配置（Gradle 插件 + 依赖）
├── gradle.properties      ← 版本号 + mod 元数据
├── settings.gradle        ← （如需要）
└── src/main/
    ├── java/com/opendreamcore/
    │   ├── OpenDreamCore.java       ← NeoForge 入口（@Mod）
    │   ├── FabricOpenDreamCore.java ← Fabric 入口（ModInitializer）
    │   ├── client/
    │   │   └── ClientEvents.java    ← 平台事件注册（命令/HUD渲染/容器拦截）
    │   └── network/
    │       ├── UiChannel.java       ← 网络通道注册
    │       ├── RawPayload.java      ← 原始 payload 封装
    │       ├── OdcFriendlyBuf.java  ← 友好字节缓冲
    │       └── HandshakeHandler.java ← 握手处理
    └── resources/
        └── META-INF/neoforge.mods.toml 或 fabric.mod.json
```

## 共享源码树

所有 target 编译时自动合并以下共享源码：
- `../../common/src/main/java` → 核心逻辑（零 MC 依赖）
- `../../client/src/main/java` → 客户端逻辑（mojmap，跨加载器）

平台层只需要实现：入口类、网络注册、事件钩子。

## 新增版本步骤

1. 复制最接近版本的 target 目录
2. 修改 `gradle.properties` 里的版本号
3. 修改 `build.gradle` 里的 Gradle 插件版本和依赖
4. 修改入口类的注册代码（API 可能有变化）
5. 编译测试：`./gradlew -p targets/<新target> build`
