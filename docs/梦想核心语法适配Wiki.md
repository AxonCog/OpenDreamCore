# 梦想核心（DreamCore）语法适配 Wiki，老页面零改动迁移指南

> 写给两种人：手里有一堆龙核/DreamCore 老页面的服主，和想照葫芦画瓢给别的旧格式写适配器的开发者。
>
> 结论：老页面丢进 `OpenDreamCore/UI/` 就能跑，不用改一行、不用转格式。后面解释原理和差异在哪。



## 目录

1. [全景图：一条老页面的旅程](#一全景图一条老页面的旅程)
2. [自动检测：引擎怎么认出这是老格式](#二自动检测引擎怎么认出这是老格式)
3. [DreamCore 语法参考（适配前）](#三dreamcore-语法参考适配前)
4. [自动改写规则明细](#四自动改写规则明细)
5. [方法桥：几百个 方法.XXX 是怎么活的](#五方法桥几百个-方法xxx-是怎么活的)
6. [写你自己的适配器](#六写你自己的适配器)
7. [行为差异与已知限制](#七行为差异与已知限制)



## 一、全景图：一条老页面的旅程

以仓库根目录那份真实的 `菜单.yml`（龙核菜单页）为例。当它被放进 `UI/` 目录，
客户端加载时经历了这些站：

```
读取 菜单.yml 文本
    ↓
① AdapterRegistry.detect(text)          ← "闻一下"这是什么格式
    ↓  认出 DreamCore 方言
② DreamCoreParser.parse(text)           ← 语法翻译
   ├─ 类型映射      Texture → image
   ├─ 参数收拢      texture: "x.png" → image: {src: "x.png"}
   ├─ 属性改名      alpha → opacity、tip → tooltip
   ├─ 表达式改写    界面变量.X = 1; → Screen.设置变量("odc_ui_X", 1);
   └─ 零参补括号    方法.关闭界面;  → 方法.关闭界面()
    ↓
③ PageImporter.expand()                 ← import 展开（标准流程）
    ↓
④ PageSchema.build()                    ← 构建页面模型 + 应用主题（标准流程）
    ↓
正常渲染、正常交互
```

关键认知：**适配器只负责"翻译成标准 ConfigIR"，从第三步开始就是和新页面完全相同的管线**。
所以新系统的一切能力（主题、热重载、state_patch……）老页面照样享受。

三条铁律（写在 `adapter/README.md` 里的架构约束）：

1. 适配层只产标准 ConfigIR，绝不绕过页面模型自建渲染路径
2. 核心代码不 import 适配包内部类，唯一入口是 `AdapterRegistry.detect()`
3. 平台 targets 里禁止散装适配逻辑



## 二、自动检测：引擎怎么认出这是老格式

`DreamCoreParser.detects(text)` 用两层指纹，宁缺勿滥（避免把标准页面误伤）：

**第一层：特征串**（命中任意一个即判为旧格式）

| 特征 | 出处 |
|---|---|
| `hideVanillaList:` | 页面级隐藏原版 HUD 列表 |
| `界面变量` / `用户变量.` | 旧作用域变量方言 |
| `ChatDisplay` | 旧聊天显示组件 |
| `preRender:` | 旧生命周期钩子 |
| 行首 `Functions:` | 大写 F 的脚本块 |

**第二层：IR 级类型探测**，特征串都没命中时，先按 YAML 解析一遍，
看顶层元素的 `type` 是否是旧类型名（`Texture`/`label`/`slot`…能被映射表翻译的都算）。
如果顶层出现 `elements:` 键则直接判定为新扁平语法，不做旧格式尝试。

**路由规则**：实现 `SelfDetecting` 接口的解析器优先；谁"认领"文本就交给谁。
所有加载路径（本地目录扫描、`/odc reload`）统一走这一个入口，不存在绕过检测的旁门。

> 实操建议：如果你的老页面用了非常规写法导致没被认出来（表现是元素消失/报未知类型），
> 最快的办法是在文件里补一行 `hideVanillaList: []` 强制触发第一层指纹。



## 三、DreamCore 语法参考（适配前）

这一章按老页面的实际长相记录语法要素。拿 `菜单.yml` 当活教材。

### 3.1 页面级键

```yaml
match: "龙核菜单"            # 触发界面名（同新版）
allowEscClose: true          # ESC 是否直接关页
hideVanilla: true            # 隐藏原版 HUD
hideVanillaList: [...]       # 按名隐藏原版 HUD 层
Functions:                   # 注意大写 F（新版是小写 functions，两种都能识别）
  keyPress: "..."            #   按键监听
  open: |-                   #   打开时执行
    ...
  wheel: "..."               #   滚轮
  preRender: |-              #   每帧预绘制（存在才跑）
  自定义方法名: |-             #   自定义函数，供 方法.异步执行方法('名字') 调用
    ...
```

### 3.2 元素写法

```yaml
背景:                          # 顶层键 = 元素 id（中文 id 完全合法）
  type: 'texture'             # 类型大小写随意（Texture/texture 都认）
  x: "(w-背景.width)/1.8"     # 表达式：w/h 是窗口宽高的简写别名
  y: 10
  width: 背景.width           # 元素间交叉引用（裸 id.属性）
  texture:                    # 参数平铺在 type 同名的键下
    src: 'assets/背景.png'
引导槽位:
  type: slot                  # 槽位
  identifier: container_6     # 绑定容器第 6 格
  drawBackground: false       # 不画槽底
```

要点：

- **id 后缀推断**：没有 `type` 时按 id 后缀猜（`_label`→text、`_texture`→image……龙核惯例）
- **表达式方言**：`(条件)?{代码}:0` 三元式、`方法.xxx(...)` 调用、
  `界面变量.x / 用户变量.y / variable.z` 三种作用域前缀、裸调用不带括号

### 3.3 作用域变量（老方言的核心）

| 老写法 | 含义 |
|---|---|
| `界面变量.x` | 页面级变量（开页存活） |
| `用户变量.x` | 玩家级变量（跨页面） |
| `variable.x` / `Variable.x` | 动态变量 |
| `界面变量.x = 表达式;` | 赋值行（分号可省） |

赋值和读取都会被自动改写（见下一章），你在脚本里照常这么写就行。



## 四、自动改写规则明细

### 4.1 类型映射表（TYPE_MAP 全量）

| 旧类型 | 新类型 | | 旧类型 | 新类型 |
|---|---|---|---|---|
| Texture / texture | image | | Select | dropdown |
| Image | image | | CheckModule | checkbox |
| TextModule / label | text | | RangeModule | slider |
| Button | button | | Slot / ChestSlot | chest_slot |
| Input | input | | HotSlot | hot_slot |
| ChatInput | chat_input | | ContainerModule | scroll |
| ChatDisplay | chat_display | | EntityModule | entity |
| Suggestion | suggestion | | ProgressModule | progress |
| AreaInput | area_input | | VideoModule | video |
| | | | Foreach / Embed | foreach / embed |

匹配策略：精确 → 忽略大小写 → 兜底小写化。所以 `TEXTURE`、`Texture`、`texture` 都落到 image。

### 4.2 参数收拢与属性改名

| 旧写法 | 新形态 |
|---|---|
| `type: texture` + `texture: {src: x}` 或 `texture: "x.png"`（平铺） | `image: {src: x}` |
| `textureHovered: y` | `image.hoverSrc: y` |
| `alpha: 0.8` | `opacity: 0.8` |
| `tip: [...]` | `tooltip: [...]` |
| `identifier: container_6`（也认 `container6`/纯数字） | `chest_slot.slot: 6` |
| `drawBackground: false` | `chest_slot.showSlot: false` |

### 4.3 表达式改写（只动白名单键）

为了不污染纯文本内容，只有这些键的字符串会被当表达式改写：

```
x, y, width, height, z, scale, opacity, rotation,
limitX, limitY, limitWidth, limitHeight, maxDistanceX, maxDistanceY,
src, hoverSrc        ← 老页面贴图路径常是变量引用
```

spec 子对象（image/chest_slot 等）一层内同样生效；`actions` 脚本整体参与改写。

**作用域变量改写对照**：

```yaml
# 老写法                                # 改写后
界面变量.关闭时间 = 方法.取当前时间;  →  Screen.设置变量("odc_ui_关闭时间", 方法.取当前时间());
用户变量.壁纸存储                     →  odc_user_壁纸存储          （读引用换裸名）
variable.scroll = 5                  →  Screen.设置变量("odc_dyn_scroll", 5);
```

原理：LayoutEngine 把页面变量平铺进表达式求值环境，所以改成带前缀的裸变量名后
直接可读；写入则归并到统一的 `Screen.设置变量` 调用。前缀规则：
`界面变量→odc_ui_`、`用户变量→odc_user_`、`variable→odc_dyn_`。

### 4.4 零参裸调用补括号

`方法.关闭界面;` / `方法.取屏幕高度` 这种不带 `()` 的写法会在解析期自动补成
`方法.关闭界面()` / `方法.取屏幕高度()`，再交给新执行器。



## 五、方法桥：几百个 `方法.XXX` 是怎么活的

老页面的灵魂是 `方法.*` 函数库。适配层内置了一个庞大的桥接注册表，
按类别拆在 `adapter/dreamcore/methods/` 下（29 个批次模块）：

| 模块 | 覆盖范围（举例） |
|---|---|
| LegacyMethods（本体） | 关闭界面/打开GUI/播放声音/设置组件值/取组件值/替换/合并文本/延时/异步执行方法/更新变量值/聊天… |
| DisplayLegacy | 发送标题/副标题/动作栏/各类消息、开关 HUD 与界面 |
| PlayerLegacy / PlayerLegacy2 / PlayerExtLegacy | 取玩家坐标/血量/护甲/经验/维度/物品栏…全套玩家属性（中文名+snake_case 双份）|
| EntityLegacy | 准心实体/指向生物的血量、UUID、名称 |
| MouseKeyLegacy / DisplayMouseKeyLegacy | 鼠标坐标/滚轮/按键模拟、窗口尺寸/全屏/GUI 缩放 |
| ScreenLegacy / ScreenLegacy2 / ScreenOpsLegacy | 组件增删改查/批量置属性/显示隐藏/动画播放暂停/运镜过渡/卡片/视频控制 |
| CoreMathLegacy / MathLegacy | abs/sin/pow/lerp/bezier/缓动族/随机数/类型转换 |
| CoreStringLegacy | 字符串全家桶（长度/截取/分割/合并/正则替换/去色码/宽度测量）|
| CoreArrayLegacy / CoreVarLegacy | 数组操作、变量读写自增自减、动画变量 |
| TimeDelayLegacy / TimeVarLegacy / ChatSoundScheduleLegacy | 延时/定时/循环任务、时间戳、聊天声音调度 |
| ItemLegacy / YamlLegacy / ShaderYamlMiscLegacy / MiscLegacy | 物品 NBT/Lore、外部 YAML 读取、着色器参数、剪贴板等杂项 |
| FullGapLegacy / FinalGapLegacy / RoadmapGapLegacy / PumpkinGapLegacy | 补全批次：中文别名、电影相机/路标/特效/翻牌等长尾方法 |

几个设计点：

- **命名空间归并**：旧方法接到新引擎的对应模块上。例如
  `方法.关闭界面` → `Screen.关闭页面`、`方法.播放声音` → `Music.播放`、
  `方法.设置组件值(id, prop, v)` → `Screen.设置元素(...)`（属性路径还做了旧→新翻译）
- **幂等注册**：`registerOrReplace` 语义，重复安装无副作用
- **渲染线程保护**：`方法.延时` 上限 200ms，防卡死主线程
- **中英双轨**：同一能力往往同时注册了中文（`取玩家血量`）和英文
  (`get_player_health`) 两套名字，社区迁移的英文老页面同样受益

> 实操提示：想知道某个老方法还在不在？全局搜 `adapter/dreamcore/methods/`
> 目录下的 `register("名字"` 即可确认；不在的话用新方法库同名能力替代，
> 或者按第六节的模式自己 register 一个。



## 六、写你自己的适配器

适配层是公开扩展点，第三方旧格式接入只需一个类：

```java
public final class MyOldFormatParser implements ConfigParser, AdapterRegistry.SelfDetecting {

    @Override public String format() { return "my_old_format"; }

    @Override public boolean detects(String text) {
        return text.contains("你的格式的强特征串");   // 宁缺勿滥
    }

    @Override public Map<String, Object> parse(String text) {
        // 把旧格式翻译成标准 ConfigIR：
        // 顶层键 = match/title/display/functions/options/变量/元素(含 type 的 map)
        // 之后 PageImporter/PageSchema/主题管线全部免费享用
        return translate(text);
    }
}
```

注册一行（客户端初始化处，或任何早于首次页面加载的时机）：

```java
AdapterRegistry.register(new MyOldFormatParser());
```

守约三条（与 dreamcore 适配同一纪律）：

1. 只产标准 ConfigIR，不自建渲染路径
2. 检测要保守，误伤标准页面比漏检严重得多
3. 脚本桥/表达式改写放在自己的子包里，核心永远不知道旧语法的存在



## 七、行为差异与已知限制

诚实清单：

1. **表达式方言按白名单改写**：白名单外的键里若写了 `界面变量.`，不会被翻译
   （设计如此，防止污染纯文本）。遇到这种情况把该值挪进 actions 或改用标准变量。
2. **`方法.延时` 有 200ms 上限**（渲染线程安全），老页面里"延时 1000 再干嘛"的套路
   请改用 `方法.异步执行方法` 组合定时任务类方法。
3. **零参裸调用的识别是启发式**：形如 `方法.xxx;` 的行尾分号语句会补括号，
   但复杂嵌套表达式的边缘写法理论上可能漏网，遇到了按标准写法补上 `()` 即可。
4. **动画/特效类旧方法**（电影相机、路标、文本纹理生成等）桥接了入口，
   底层效果依赖新引擎对应能力的完成度，个别高级参数可能降级。
5. **`Functions` 与 `functions` 都识别**，但推荐新页面统一小写。
6. 类型兜底是"小写化"而不是报错：写了完全不认识的类型会得到一个小写的未知类型，
   在 schema 校验时报"未知元素类型"，这算特性，错误信息比静默失败有用。



*文档结束。适配层的代码纪律与目录说明见 `common/src/main/java/com/opendreamcore/adapter/README.md`；
DreamLang 新语法见《YAML语法.md》；主题系统见《主题系统Wiki.md》。*
