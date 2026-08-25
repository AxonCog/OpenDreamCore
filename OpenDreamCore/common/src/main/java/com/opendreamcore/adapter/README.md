# adapter —— 语法/协议适配层（唯一入口）

> 本包是 OpenDreamCore 的**全部**适配代码所在地。任何"让旧东西在新引擎上跑"的逻辑只允许写在这里。

## 目录

```
adapter/
├── AdapterRegistry.java   语法解析器注册中心：register/get/all/detect
└── dreamcore/             DreamCore 旧版语法（菜单.yml 等）适配
    └── DreamCoreParser.java   旧格式检测 + 类型/属性映射 → 标准 ConfigIR
```

## 三条铁律

1. **适配层只产标准 ConfigIR**——与 `config/PageSchema` 吃同一张桌子，绝不绕过页面模型自建渲染路径。
2. **核心不 import 本包内部类**。唯一合法入口：
   - 客户端本地页自动检测：`LocalPageManager.parseAuto()` → `AdapterRegistry.detect(text)`
   - 服务端 GUI 编译器如需多格式，同样走 `AdapterRegistry`
3. **平台 targets 里禁止散装适配**。发现版本相关逻辑想塞进 target 的公共代码时，优先下沉 SPI；确属旧语法兼容的，一律迁入本包对应子包。

## 编写一个新适配器

以 dreamcore 适配为例（本包唯一现役适配子包），任何新适配照同一模式：

```java
package com.opendreamcore.adapter.dreamcore;

public final class DreamCoreParser implements ConfigParser, AdapterRegistry.SelfDetecting {
    @Override public String format() { return "dreamcore"; }
    @Override public boolean detects(String text) {
        return DreamCoreParser.isDreamCoreFormat(text);   // 特征指纹
    }
    @Override public Map<String, Object> parse(String text) { /* → 标准 ConfigIR */ }
}
```

注册时机：客户端初始化处（与 ClientMethods.registerAll 同级）调用
`AdapterRegistry.register(new DreamCoreParser())`。`detect()` 按 SelfDetecting 优先自动路由。
配套的脚本桥/表达式改写（LegacyMethods、LegacyExpressionRewriter）同样只放在对应子包内，
核心代码永远不感知旧语法的存在。


## dreamcore 子包路线图：这是我给你们展示的一个迁移步骤例子

| 文件 | 职责 |
|---|---|
| DreamCoreParser | 入口：格式检测 + transform 调度 |
| LegacyTypeMap | 类型名映射（Texture/label/slot…，含大小写变体） |
| LegacyPropsMap | 属性映射（alpha→opacity、tip→tooltip、texture→image.src…） |
| LegacyExpressionRewriter | w/h 简写、界面变量./用户变量.、variable.scroll → 标准表达式 |
| LegacyMethods | 方法.* 旧方法桥（异步执行方法/延时/播放声音/设置组件值…） |
| LegacyEvents | Functions.keyPress/wheel/preRender → 新事件模型 |
