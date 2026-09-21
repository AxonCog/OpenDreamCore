# 语法适配器

ConfigIR 中转层的插件式语法适配系统。每种语法格式一个独立子包，实现 `ConfigParser` 接口即可接入。

## 工作原理

```
YAML 文本 ──→ YamlParser ──────────→ ConfigIR ──→ PageSchema ──→ RenderNode 树
DreamCore 文本 → DreamCoreParser → ConfigIR ──→ 同上
JSON 文本 ────→ JsonParser ────────→ ConfigIR ──→ 同上
自定义 DSL ──→ 你的解析器 ────────→ ConfigIR ──→ 同上
```

核心渲染管线只认 ConfigIR（Map 树），不关心原始格式是什么。

## 已有适配器

| 适配器 | 格式 | 说明 |
|---|---|---|
| `YamlParser` | OpenDreamCore YAML | 默认格式 |
| `JsonParser` | JSON | 备选格式 |
| `DreamCoreParser` | DreamCore 旧语法 | 自动检测并转换 |

## 如何新增语法适配

### 方式一：代码注册（推荐）

```java
// 1. 实现 ConfigParser 接口
public class MyParser implements ConfigParser {
    @Override
    public String format() { return "my-format"; }

    @Override
    public Map<String, Object> parse(String text) {
        // 解析你的语法 → 输出标准 ConfigIR
        return myParseLogic(text);
    }
}

// 2. 注册到 AdapterRegistry
AdapterRegistry.register(new MyParser());

// 3. 页面加载时自动路由到你的解析器
```

### 方式二：自动格式检测

让解析器实现 `SelfDetecting` 接口：

```java
public class MyParser implements ConfigParser, AdapterRegistry.SelfDetecting {
    @Override
    public boolean detects(String text) {
        return text.contains("我的格式特征");
    }
    // ...
}
```

### 方式三：独立子包

在 `adapter/` 下创建新子包：

```
config/
├── adapter/
│   ├── dreamcore/       ← DreamCore 兼容
│   │   ├── DreamCoreParser.java
│   │   └── TypeMapper.java
│   ├── chaui/           ← ChaUI 兼容
│   │   └── ChaUIParser.java
│   └── myformat/        ← 你的自定义格式
│       └── MyFormatParser.java
├── YamlParser.java      ← 内置
├── JsonParser.java      ← 内置
└── ConfigParser.java    ← SPI 接口
```

## ConfigIR 输出规范

所有解析器最终输出的 Map 树必须包含以下结构：

```yaml
# 页面级
title: "页面标题"
display: screen          # screen / hud / container / world
match: "目标"
background: "#A0000000"  # 可选

variables:               # 页面变量（可选）
  gold: 100

functions:               # 生命周期脚本（可选）
  open: |
    ...
  tick: |
    ...

elements:                # 元素列表（必需）
  - id: xxx
    type: text
    x: 10
    y: 20
    text:
      content: "hello"
```

## 注意事项

- 所有 shade 进 jar 的第三方依赖必须 relocate，防止类冲突
- 新增解析器后需要同步更新 `LocalPageManager.parseAuto()` 的格式检测逻辑
