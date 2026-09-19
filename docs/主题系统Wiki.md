# 主题系统 Wiki，写给服主和页面作者

> 主题系统 v1 的写法说明，和《YAML语法.md》第 12 节配套。
> 默认你写过 OpenDreamCore 页面；没写过的话先看《YAML语法.md》第 0~3 节再回来。



## 目录

1. [这是什么？为什么要有它？](#一这是什么为什么要有它)
2. [三分钟上手：你的第一个主题](#二三分钟上手你的第一个主题)
3. [心智模型：先把道理讲通](#三心智模型先把道理讲通)
4. [目录结构与加载机制](#四目录结构与加载机制)
5. [语法全参考：theme.yml](#五语法全参考themeyml)
6. [transition：让界面自己会动](#六transition让界面自己会动)
7. [流式布局：h_stack / v_stack 的进化](#七流式布局h_stack--v_stack-的进化)
8. [实战菜谱](#八实战菜谱)
9. [老页面怎么办（迁移指南）](#九老页面怎么办迁移指南)
10. [常见坑 FAQ](#十常见坑-faq)
11. [边界与已知限制](#十一边界与已知限制)
12. [速查表](#十二速查表)



## 一、这是什么？为什么要有它？

一句话：**以前写页面是"装修每间房"，现在是"定一套家装风格，全楼生效"。**

你肯定遇到过这些事：

- 想换个主色调，结果要开十几个页面 YAML，一个一个改颜色值，漏一个就花
- 每个按钮都要准备 hover/pressed 两张贴图，美术跑断腿
- 想让按钮悬停有个渐变效果？要么上动画系统写一段脚本，要么放弃
- 金币数字变了是"啪"一下跳过去的，想要滚动/淡入效果又得写动画

这些问题本质上是同一件事：**外观和结构长在一起了**。

主题系统把它们拆开：

```
页面 YAML   → 只管"有什么、在哪、点了干什么"
themes/*.yml → 只管"长什么样、怎么变"
```

页面里挂个 `class: card`，卡片长什么样由主题说了算。哪天你想换季配色，改一个文件，
所有页面跟着变，保存即生效，不用重启不用 reload。

另外说清楚它**不是什么**：它不是新页面格式，不是必学的新语言。
你完全可以无视它继续用老写法，老页面一个字节都不会变。



## 二、三分钟上手：你的第一个主题

**第一步**，建目录。在游戏目录（单机）下：

```
OpenDreamCore/
├─ UI/
└─ themes/          ← 新建这个
   └─ default.yml
```

**第二步**，写第一个规则。打开 `default.yml`：

```yaml
vars:
  accent: "#71A4F4"        # 先定义一个变量，叫 accent

text:                        # 所有 text 元素……
  color: "{accent}"          # ……文字颜色都用 accent
  shadow: false              # 并且不要阴影
```

就这么多。保存。

**第三步**，随便开一个带 `text` 的页面（或者 `/odc reload` 一次再看 HUD）。
你会发现文字统一变成了那个蓝色，阴影没了。

**第四步**，玩玩热重载：把 `#71A4F4` 改成 `#FF5555`，保存。
回到游戏，已经变了。没有重启，没有命令，存盘即生效。

恭喜，你已经会用主题系统了。剩下的都是细节。



## 三、心智模型：先把道理讲通

### 3.1 选择器就是"点名"

主题文件里的每个顶层键都是一条"规则"，键名就是在**点名哪些元素适用这条规则**：

```yaml
text:        {...}      # 点名方式一：按类型 —— "所有 text 元素"
".card":     {...}      # 点名方式二：按类 —— "挂了 class: card 的元素"
"#start_btn": {...}     # 点名方式三：按 id —— "id 叫 start_btn 的那一个"
"button:hover": {...}   # 还可以加状态 —— "鼠标悬停中的按钮"
".card > .title": {...} # 甚至可以说"card 的直接子代里 class 是 title 的"
```

写过 CSS 的朋友会心一笑；没写过的也无妨，就三条：
`.` 开头是类，`#` 开头是 id，什么都不带是类型。空格表示"里面"，`>` 表示"直接儿子"。

### 3.2 页面侧只需要两个新键

```yaml
my_card:
  type: layout
  class: card danger_btn     # ← 挂类（可以挂多个，空格隔开）
  style:                     # ← 内联样式（可选，优先级最高）
    paddingAll: 20
```

以及页面顶层的 `theme:` 键选主题（不写就用 `default.yml`）。

没了。真的就这两个半键。

### 3.3 级联：谁说了算

同一个属性可能同时被内联、class 规则、tag 规则盯上，听谁的？
规则很简单，从高到低：

```
① 内联 props（包括 style: 块）   ← 你手写的永远最大
② ".class" 规则                  ← 权重 10
③ "tag" 规则                     ← 权重 1
④ 组件内置默认                    ← 兜底
```

两条铁律记住就行：

- **你手写的值永远不会被主题覆盖**。主题只填你没写的格子。
- 同权重撞车时，写在后面的赢。

带状态的规则（`:hover` 这些）不参与上面的排队，它们单独存成一份
"状态时的样子"，等你鼠标真悬上去才套上来。这就是为什么主题能做到按钮四态。

### 3.4 {var} 和 {{vars.x}} 不是一回事

容易混，专门说一句：

| 写法 | 在哪用 | 谁解析 | 干什么 |
|---|---|---|---|
| `{accent}` | **主题文件内部** | 主题加载器 | 引用主题自己的 vars 段 |
| `{{vars.coin}}` | 页面 | 运行时插值 | 引用页面变量（会变的数据） |
| `{player.name}` | 页面 | 占位符引擎 | 玩家占位符 |

主题变量是"设计常量"（主色、圆角、时长），页面变量是"运行数据"（金币、血量）。
井水不犯河水。



## 四、目录结构与加载机制

```
OpenDreamCore/
├─ UI/                      # 页面（原有）
├─ themes/                  # 主题目录（本系统新增）
│  ├─ default.yml           #    缺省主题：页面没声明 theme: 就用它
│  ├─ dark.yml              #    文件名 = 主题名
│  └─ festival.yml
└─ fonts/
```

几条实际行为，都实测过：

- 支持 `.yml` `.yaml` `.json` 三种后缀，递归扫子目录
- **热重载**：改 themes/ 下任何文件保存即触发重载（和页面同一条监听链路）
- 页面声明 `theme: dark` 就用 dark.yml；没声明或名字写错，回退 default；
  连 default 都没有，就完全不套主题（老行为）
- 服务端下发的页面同样会套客户端的主题，因为页面 YAML 下发到本地后
  才构建，构建那一刻查的是本地主题库。
  这意味着一个有趣的副作用：**玩家理论上可以自己给服务器界面换肤**。
  服主想要强制统一风格的话，把颜色直接写在页面里（内联），别用 class。



## 五、语法全参考：theme.yml

### 5.1 三条硬规则

1. **整个文件不允许出现 `-`**（列表）。规则键 = 选择器本身，天然扁平。
2. **`vars:` 是唯一保留段**。其余任何顶层键都会被当成选择器去解析。
3. **属性名一律 camelCase**（`paddingAll`、`textColor`、`flexGrow`），
   与组件属性同一风格，不要混 kebab-case。

### 5.2 vars 段

```yaml
vars:
  accent: "#71A4F4"       # 颜色
  radiusLg: 8             # 数字也行
  tBase: 0.25             # 动画时长也能变量化
  fontFamily: "default"   # 字符串随意
```

引用方式：声明值里写 `{名字}`。

- 整串恰好就是一个引用时，保留原始类型：`fontSize: "{base}"` 且 `base: 12` → 得到数字 12
- 混在字符串里则做文本替换：`background: "gradient:{a}|{b}"`
- 变量可以引用别的变量，深度上限 16 层防循环（超了原样返回，不炸）

### 5.3 选择器全集

| 写法 | 含义 | 示例 |
|---|---|---|
| `tag` | 按元素类型 | `button: {...}` |
| `.name` | 按类 | `".card": {...}` |
| `#id` | 按元素 id | `"#submit": {...}` |
| `A B` | 后代（B 在 A 里面，任意层级） | `".modal .title": {...}` |
| `A > B` | 直接子代 | `".card > .title": {...}` |
| `S:pseudo` | 带状态 | `"button:hover": {...}` |
| `A, B` | 一条规则点多个名 | `"text, label": {...}` |

细节与坑：

- 键里含 `:` 或空格的**记得加引号**（`"button:hover"`）；裸键如 `text:` 不用
- 复合段合法：`button.card:hover` 表示"既是 button 类型又有 card 类还悬停着"
- 权重计算：`#id` 每个 100，`.class` 和每个伪类各 10，`tag` 每个 1，累加
- 解析失败的选择器只会打一行 warn 然后跳过，**不会拖垮整个主题文件**
- 子代/后代选择器对扁平写法（`parent:` 挂父）同样有效，祖先链在页面解析时已归一，
  主题不关心你是嵌套写的还是平铺写的

### 5.4 状态伪类（目前四个）

| 伪类 | 什么时候激活 |
|---|---|
| `:hover` | 鼠标悬停在元素命中区内 |
| `:pressed` | 悬停且左键按下 |
| `:disabled` | `enabledWhen` 为假 |
| `:focus` | *预留*（键盘焦点基础设施到位后开放，写了不报错也不生效）|

两个特性要知道：

- 多状态可叠加：`"button:hover:pressed"` 表示两态同时满足（权重也叠加）
- 状态规则**只在客户端运行时判定**。所以它们不参与编译期的级联合并，
  而是作为"覆盖层"随页面下发，命中哪层叠哪层。多个命中的覆盖层之间，
  后声明的属性赢。

### 5.5 可用的属性（首版高频集合）

通用外观：`color` / `radius` / `borderColor` / `borderWidth` /
`paddingAll`（及 `paddingX` `paddingY`）/ `gapAll`（及 `gapRow` `gapColumn`）/
`fontSize` / `textColor` / `shadow`

组件规格属性用点路径直达：`button.hoverColor`、`button.textColor`、
`text.strokeWidth`、`item_slot.background`……

无点路径的属性会尝试并入它命中的所有规格表；带点路径只进对应规格。
拿不准的时候，优先用点路径，语义最明确。

### 5.6 transition 属性（详见第六节）

`transition` 本身也是一个可声明的属性，写法和 CSS 过渡类似但更短。



## 六、transition：让界面自己会动

这是我个人最喜欢的一块。它的理念是：

> 你不需要"播放一个动画"，你只需要声明"这个属性变化时要过渡"。
> 至于值是谁改的，脚本改的、服务端推的、鼠标悬停切的状态，统统不管，一律平滑过渡。

### 6.1 语法

```
transition: "<属性> <秒> [缓动] [延迟秒], 可以逗号分隔多条"
```

```yaml
ok_btn:
  type: button
  transition: "background 0.25 QUAD_OUT, opacity 0.3 CUBIC_OUT 0.1"
```

### 6.2 缓动函数速览

| 名字 | 手感 |
|---|---|
| `LINEAR` | 匀速，机械感强，一般不用 |
| `SINE_IN_OUT` | 温柔的一进一出，万能默认 |
| `QUAD_OUT` | 快出慢停，适合淡出、收起 |
| `CUBIC_IN_OUT` | 明显的加速减速，大位移用它 |
| `EXPO_OUT` | 疾停，干脆利落，提示框出场首选 |
| `BACK_OUT` | 过冲回弹一点，活泼 |
| `ELASTIC_OUT` | 弹簧，慎用，多了很闹 |

（另有 QUAD_IN / CUBIC_IN 等纯入系列，完整清单见 Ease 枚举）

### 6.3 什么能被插值

- 数字（x/y/width/opacity/进度值……）：线性中间值
- `#RRGGBB` / `#AARRGGBB` 颜色：逐通道混合
- 其它（贴图路径、枚举字符串）：过渡期满瞬间切换

### 6.4 典型场景对照

| 你想要的 | 写法 |
|---|---|
| state_patch 推金币，数字平滑淡变 | `bind: {content: ...}` + `transition: "opacity 0.3 CUBIC_OUT"` |
| 提示语出现/消失有淡入淡出 | `visibleWhen` + `transition: "opacity 0.25 EXPO_OUT"` |
| 按钮 hover 渐变而不是跳变 | 主题里 `button.transition: "background 0.25 QUAD_OUT"` |
| 血条平滑跟随真实血量 | progress + `transition: "percentage 0.4 SINE_IN_OUT"` |

和 AnimationEngine 的分工：**transition 管"属性变化的补间"，动画系统管"编排好的演出"**
（打字机、翻牌、震屏那些）。两者不冲突，各干各的。



## 七、流式布局：h_stack / v_stack 的进化

老版 h_stack/v_stack 只会把孩子依次排开，宽度写死。现在补上了弹性布局的核心能力。

### 7.1 容器新属性

```yaml
toolbar:
  type: h_stack            # v_stack 参数完全相同，方向转 90°
  width: "{window.width}"
  gap: 8                   # （原有）间距
  align: center            # 新增：交叉轴对齐 start|center|end|stretch
  justify: between         # 新增：主轴分布 start|center|end|between|around
  wrap: true               # 新增：放不下自动换行/换列
  reverse: true            # 新增：反向排布
```

### 7.2 子元素新属性

```yaml
btn_a:
  parent: toolbar
  type: button
  grow: 1        # 分剩余空间的权重（大家都 1 就是均分）
  basis: 90      # 分配前的理想尺寸
  shrink: 1      # 空间不够时收缩权重
  alignSelf: end # 单独覆盖容器 align
badge:
  parent: toolbar
  absolute: true # 脱离流式！用自己 x/y 相对容器定位（做角标神器）
```

### 7.3 语义速记

- `justify` 管"这一行怎么排"（挤左边？居中？撑开两端？）
- `align` 管"高矮不一怎么站队"（顶部对齐？垂直居中？拉伸到行高？）
- `grow` 的孩子**初始尺寸按 0 算**再分空间（CSS flex-basis:auto 的近似），
  所以 `grow: 1` 的孩子会真正填满，而不是"缺省尺寸+一点补偿"
- 空间不足时优先收缩（`shrink` 加权），收缩完还有富余才轮到 `grow` 分
- `wrap` 换行后每行独立做 justify，行与行沿交叉轴推进

### 7.4 最重要的承诺

**容器和孩子都没写任何新属性时，走的是原始排布路径**，和你现有页面
逐字节一致的行为。所有新能力都是显式开启的，不存在"升级后布局悄悄变了"这回事。



## 八、实战菜谱

直接抄作业区。每份配方都可以单独食用。

### 菜谱 1：全站换肤

需求：夏天一套配色，冬天一套，随时切换。

```yaml
# themes/summer.yml
vars:
  accent: "#4CAF50"
  panelBg: "#1B2E20EE"

# themes/winter.yml
vars:
  accent: "#42A5F5"
  panelBg: "#16202EEE"
```

页面不动。想换肤就把目标主题改名成 `default.yml`（或者页面里 `theme: winter`）。
保存即生效。

### 菜谱 2：免贴图按钮四态

需求：不想画 normal/hover/pressed 三张图。

```yaml
button:
  background: "#2A3038"
  textColor: "#E8E8EC"
  radius: 5
"button:hover":
  background: "#39424E"
"button:pressed":
  background: "#2A3038"
  textColor: "#AAAAAA"
"button:disabled":
  textColor: "#666670"
button:
  transition: "background 0.2 SINE_IN_OUT"
```

（transition 写在基础规则里，四态切换全部自动带渐变。）

### 菜谱 3：金币滚动

```yaml
coin:
  type: text
  bind: {content: "{player.money}"}
  transition: "opacity 0.3 CUBIC_OUT"
```

服务端每次推钱数，数字区域轻微淡变刷新，视觉上"活"了很多。
想更夸张可以配合 scale：`transition: "opacity 0.3, scale 0.3 BACK_OUT"`。

### 菜谱 4：余额不足提示淡入淡出

```yaml
poor_tip:
  type: text
  content: "钱不够啦"
  visibleWhen: "vars.coin < price"
  transition: "opacity 0.25 EXPO_OUT"
```

条件切换的瞬间不再是生硬的出现/消失。

### 菜谱 5：响应式工具栏

```yaml
hotbar:
  type: h_stack
  x: 10
  y: "(h-40)"
  width: "(w-20)"
  justify: between
slot_1: { parent: hotbar, type: item_slot, grow: 1 }
slot_2: { parent: hotbar, type: item_slot, grow: 1 }
slot_3: { parent: hotbar, type: item_slot, grow: 1 }
```

窗口多宽都均匀铺满。以前要写 `(w-20)/3` 这种表达式还得数间距，
现在 `grow: 1` 完事。

### 菜谱 6：商品角标

```yaml
goods_card:
  type: layout
  class: card
goods_badge:
  parent: goods_card
  type: rect
  absolute: true
  x: "(parent.width - 40)"
  y: -6
  width: 40
  height: 16
  color: "#E04E4E"
  radius: 4
```

角标钉在卡片右上角，不参与卡片内部的流式排列，互不打架。

### 菜谱 7：卡片体系

主题里定义一次：

```yaml
".card":
  background: {color: "#2C2C34DD", radius: 8, border: {color: "#7F8084", width: 1}}
  paddingAll: 10
".card > .title":
  fontSize: 12
  color: "{accent}"
```

页面里任何容器只要 `class: card`、标题 `class: title`，样式全自动。
十个商店页共用一张皮。

### 菜谱 8：危险按钮

```yaml
".danger_btn":
  background: "#E04E4E"
"button.danger_btn:hover":
  background: "#F07474"
```

页面：`class: danger_btn`（甚至可以和 card 同时挂：`class: card danger_btn`）。
删除、出售这类操作一眼可辨。



## 九、老页面怎么办（迁移指南）

结论先行：**什么都不做也完全没问题。**

主题系统是严格 opt-in 的：

- 没有 `class` 键 → 一条 class 规则都不命中
- 页面没写 `theme:` → 回退 default，default 也没有不套任何东西
- 内联写的颜色/字号优先级最高，主题碰不到它们

想渐进迁移的话，推荐顺序：

1. **第一步（零风险）**：把主题文件建起来，只放 `vars` 段和一两个 tag 规则
   （比如全局 text 阴影关掉）。观察一周。
2. **第二步**：新页面开始用 `class:`，老页面不动。
3. **第三步**：哪个老页面要改版了，顺手把它的高频样式抽进主题、挂上 class。
4. **永远不要**一次性把所有页面迁完，总有一两个页面依赖了你想不到的细节。



## 十、常见坑 FAQ

**Q：我的规则写了就是不生效？**
按顺序排查：
一 键里有 `:` 或空格却没加引号（YAML 会解析错，看启动日志有没有 warn）；
二 页面元素的 `class` 拼写和选择器对不上（区分大小写）;
三 该属性已经被元素内联写死了，内联永远赢，删掉内联试试；
四 用的是带状态的规则，它不会体现在基础外观上，只有状态激活时才看得到。

**Q：{accent} 解析不出来，原样显示在界面上了？**
八成是 vars 段里没这个名字，或者拼错了。主题变量解析失败会静默保留原文（不炸），
去 vars 里核对一遍。

**Q：transition 写了没动画？**
三种可能：属性值不是数字也不是颜色（比如贴图路径，只能瞬切）；
时长写 0 了；或者这个属性的变化根本没走渲染取值口（极少见，来群里反馈）。

**Q：服务器下发的页面能用主题吗？**
能用。页面下发到客户端后才构建，构建时查的是**客户端本地**的主题库。
所以服主想让全员统一风格，得保证玩家端有对应主题文件，更稳的做法是
重要颜色直接内联在页面里。反过来这也是特性：玩家可以自定义服务器界面的皮肤。

**Q：Paper 端插件生成的页面呢？**
插件仓库不在本代码库。common 层的 `ThemeLibrary` API 是公开的，
插件侧接一下目录加载即可（方法签名见 `ThemeLibrary.loadFromDir`）。

**Q：主题规则能覆盖 bind 绑定的值吗？**
不建议这么用。`bind:` 是数据驱动的动态值，每次布局都会重新求值覆盖；
主题管静态外观。两者抢一个属性的话 bind 赢，这不是 bug 是分工。

**Q：性能怎么样？**
主题匹配做了桶索引（按类型/类/id 分桶圈候选，不全量扫描），
规则合并只发生在页面构建时，运行时状态叠加是每帧轻量字典操作。
几百个元素的页面无感。

**Q：为什么全文件不让写 `-`？**
三个原因：和页面扁平语法哲学一致；选择器做键天然就是 map 结构；
以及避免 YAML 序列在"规则集合"这种语义下的歧义。习惯了反而觉得清爽。



## 十一、能力边界（当前真实状态）

早期版本的限制清单已经全部清零，现在的边界只剩语义约定，不是功能缺失：

**已全面支持的**

- **嵌套规则**：`.card { .title: {...} }` 直接写，默认后代组合；`&` 引用父选择器、
  `>` 显式子代也都认，和平铺写法共存，解析结果完全等价
- **响应式查询**：`"@max-width 854"` / `"@media (min-width: 400) and (max-height: 600)"`
  都支持，条件是窗口宽高像素，每次布局重算现判现用，窗口拖动即时响应；
  可以顶层成块，也可以嵌在规则内部直接写声明
- **状态覆盖层改布局属性**：`:hover/:focus` 里写 x/y/width/height，
  自动触发一次重排（250ms 冷却防抖）落地生效；颜色/文字类依旧走实时叠加通道
- **多主题叠加**：`theme: default,server_patch` 逗号分隔依次应用
- **`:focus` 状态**：键盘焦点由屏幕层每帧上报，输入类组件 Tab/点击获得
- **插件侧 ThemeAPI**：附属插件一行注册自带皮肤（见《附属开发指南.md》）

**语义约定（了解即可，不算坑）**

1. 响应式覆盖命中时压过普通规则与内联样式，敢写进 @块 就是敢覆盖；
   state_patch 运行时写入发生得更晚，不受影响
2. 状态改 x/y/宽高走"重排落地"，比颜色类慢一帧（250ms 冷却内只重排一次）；
   需要亚帧级平滑就用 transition 补间
3. 嵌套深度上限 8 层（防手滑写出无限递归的主题文件）

## 十二、速查表

```yaml
# theme.yml 一页纸
schema: 1                       # 版本
vars: { accent: "#71A4F4" }     # 变量段（唯一保留段）

tag: { color: "{accent}" }                    # 类型规则
".cls": { paddingAll: 10 }                    # 类规则（权重10）
"#someid": { opacity: 0.9 }                   # id 规则（权重100）
"button:hover": { background: "#333" }        # 状态
".card > .title": { fontSize: 12 }            # 直接子代
".modal .title": { fontSize: 12 }             # 后代
"text, label": { shadow: false }              # 多选

transition: "opacity 0.3 CUBIC_OUT"           # 属性过渡
# 缓动：LINEAR SINE_IN_OUT QUAD_* CUBIC_* EXPO_OUT BACK_OUT ELASTIC_OUT
```

```yaml
# 页面这边
theme: dark                    # 选主题（缺省 default）
class: card danger_btn         # 挂类（空格分隔）
style: { paddingAll: 20 }      # 内联样式（优先级最高）

# stack 弹性
# 容器：align / justify / wrap / reverse / spacing
# 孩子：grow / shrink / basis / alignSelf / absolute
```



*文档结束。有问题来群里喊梦幻（QQ:2496599413），或者直接提 Issue，
这份文档本身就是开源的一部分，写得不清楚的地方欢迎 PR。*
