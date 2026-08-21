# OpenDreamCore common

共享源码树，零 MC 依赖。

协议模型、页面模型、表达式引擎、脚本 DSL、会话状态机、容器注册表、tooltip 模型、编辑器模型——纯逻辑，不碰任何 Minecraft 类型。

平台相关的东西（网络注册、命令注册、玩家查询、调度、配置 IO）走 `OdcServices` SPI 下沉，各 target 自己实现。

## 独立编译与测试

```powershell
.\gradlew.bat -p common test
```

## 目录

```
src/main/java/com/opendreamcore/
├── protocol/    # 协议消息编解码
├── page/        # 页面模型
├── expr/        # 表达式/脚本引擎
├── session/     # 会话状态机
├── state/       # 变量表 / state_patch
├── container/   # 容器注册表模型
├── tooltip/     # tooltip 模型
├── editor/      # 编辑器模型
└── spi/         # 平台 SPI
```
