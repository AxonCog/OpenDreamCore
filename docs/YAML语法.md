# OpenDreamCore YAML 语法（定稿 v1）

> UI 页面/HUD/容器界面配置语言。用户层语法，可插拔（ConfigParser SPI），YAML 为默认。
> 作者：梦幻 QQ:2496599413
> 语法迁移自23年梦想核心与26年梦想核心正式版

## 0. 总览

```yaml
# 顶层：match 触发 + 变量 + 元素（有 type 是元素，没 type 是变量）
match: 菜单                    # ★ 触发条件（界面类型或标题）
coin: 100                      # 变量（顶层平铺）
page: 1

# 元素（有 type）
title:
  type: text
  ...
```

## 1. match 触发

匹配"界面"——类型或标题，进服/打开界面时按优先级匹配：

```yaml
match: hud                 # HUD
match: 菜单                # 标题为"菜单"的界面（中文裸写，引号可选）
match: "minecraft:chest"   # 容器类型
match: "chest:菜单"         # 类型:标题 组合
match: inventory           # 背包
match: player              # 玩家模式
```

- 引号可加可不加（YAML 裸字符串）
- 匹配器可插件注册，支持优先级 + 表达式条件

## 2. 变量

顶层无 `type` 的键即变量（标量/列表/对象均可）：

```yaml
coin: 100
server_name: "我的服务器"
settings: {enable: true, rate: 0.5}    # 复杂变量（对象）
items: [a, b, c]                       # 列表变量
```

引用（模板插值 + 表达式统一）：

| 引用 | 作用域 | 例子 |
|---|---|---|
| `vars.xxx` | 页面变量 | `{{vars.coin}}` |
| `global.xxx` | 全局（跨页面） | `{{global.server_name}}` |
| `player.xxx` / `papi.xxx` | 玩家占位符 | `{{player.health}}` |
| `parent.xxx` | 父元素 | `parent.width` |
| `组件id.xxx` | 任意组件 | `ok.x + ok.width` |
| `this.xxx` | 自己 | `this.height` |

## 3. 元素

**顶层/children 下：值是 map 且含 `type` 的键 = 元素。**

```yaml
title_text:
  type: text                # 元素类型（组件注册表）
  x: 0                      # 定位（数字或表达式字符串）
  y: 10
  width: "window.width"     # 表达式
  height: 30                # 不写 + autoHeight: true → 高度随内容折行自适应
  autoHeight: true          # 文本自动高度（按字体折行测量；命中区域/父布局随内容）
  wrap: 300                 # 折行宽度 px（设置后内容按宽度自动折行；不写 = 仅手动 \n 换行）
  text:                     # 类型专属属性（组件 schema）
    content: "标题"
    align: center
    color: "#FFD700"
    lineHeight: 12          # 行距 px（默认 9；autoHeight 计算用）
  visibleWhen: "vars.coin >= 100"   # 条件（表达式）
  actions:                  # 事件（DreamLang 脚本）
    click: |-
      方法.发送消息("点击了")
```

元素类型（组件注册表，可插件扩展）：
`text / image / gif / video / button / rect / layout / input / area_input / suggestion / dropdown / toggle / slider / progress / item_display / item_slot / hot_slot / card / flip_card / chart / compass / direction / canvas / boss_bar / grid / h_stack / v_stack / scroll / foreach / import / embed / entity`

### 3.0 type 省略与别名

**省略 type 从 id 后缀推断**：键名以 `_后缀` 结尾时自动推断 type，不用写 `type:`

```yaml
# 以下两种写法等价
fill_btn:
  type: button
  button: {label: "确定"}

fill_btn:
  button: {label: "确定"}     # _btn 推断为 button

bg_texture:
  image: {src: "bg.png"}      # _texture 别名映射到 image
```

内置后缀：`_btn/_button`→button  `_txt/_text`→text  `_img/_image`→image  `_rect`→rect
`_input`→input  `_dd/_dropdown`→dropdown  `_toggle`→toggle  `_slider`→slider
`_prog/_progress`→progress  `_tabs`→tabs  `_slot/_item_slot`→item_slot
`_video`→video  `_entity`→entity  `_layout`→layout  `_chk/_checkbox`→checkbox

**type 别名映射**：写了 `type: texture` 自动映射到 `image`，`type: label` 映射到 `text`

```yaml
# texture → image
bg:
  type: texture
  image: {src: "bg.png"}

# label → text
title:
  type: label
  text: {content: "标题"}
```

内置别名：`texture`/`pic`→image  `label`→text  `field`→input  `combo`→dropdown
`switch`→toggle  `bar`→progress

**自定义别名**（热加载）：在 `plugins/OpenDreamCore/type-aliases.yml` 里配置，保存后自动热重载：

```yaml
# type-aliases.yml
texture: image
pic: image
label: text
my_type: button    # 自定义别名
```

### 3.1 通用元素属性

```yaml
el:
  type: text
  x: 10            # 数字或表达式（window.width / parent.width / vars.xxx）
  z: 5             # 层级（同层 z 大的后画）
  opacity: 0.8     # 静态透明度 0..1（数字或表达式：opacity: "vars.alpha"）
  scale: 1.2       # 静态缩放（数字或表达式：scale: "vars.coin / 100"）
  rotation: 15     # 旋转角（度，正=顺时针，绕元素中心；数字或表达式）
  pointerEvents: none   # none = 不响应鼠标（穿透给下层）
  tooltip:          # 多行提示：字符串 / List（多行）/ Map
    - content: "第一行"
    - content: "第二行"
  hit: {scale: 0.9, duration: 150}   # 按压回弹反馈
  visibleWhen: "vars.coin >= 100"     # 条件（表达式）
  enabledWhen: "vars.online"
  actions:          # 事件（DreamLang 脚本）
    click: |-
      方法.发送消息("点击了")
```

> `opacity` / `scale` / `rotation` 与 `x/y/width/height` 一样支持表达式（布局时求值，变量一变自动刷新）。
> **元素发光**：`glow: "#33FFD700"` 或 `{color, size}` —— 四层同心扩散半透明辉光（垫底绘制，
> 随元素位移/缩放/旋转/透明度动画；与世界面板 `hologram.glow` 同参数；容器类型不发光）。
> **元素阴影**：`shadow: "#33000000"` 或 `{color, offset, size}` —— 三层向下偏移半透明暗影
> （偏移/微扩/透明度递减，圆角元素阴影随圆角；与世界面板 `hologram.shadow` 同参数）。
> `rect` 类型额外支持圆角、描边与垂直渐变：

```yaml
panel_bg:
  type: rect
  x: 10
  y: 10
  width: 200
  height: 100
  color: "solid:30,35,46,230"
  radius: 12            # 圆角半径（px，0 = 直角）
  border: "#5C6BC0"     # 描边颜色（不写 = 无描边；也支持对象形式）
  borderWidth: 2        # 描边宽度（默认 1）
  gradient: "#0F1620"   # 垂直渐变：顶 color → 底 gradient（圆角/描边同步；不写 = 纯色）
  # 描边对象形式（button 同样支持）：
  #   border: {color: "#3A4A66", width: 2, flow: true, flowColor: "#FFD54F"}
  #   flow: true 时亮色段沿周长匀速流动（1.2s 一圈，段长 15% 周长），圆角弧段自然跟随；
  #   悬停时加速提亮（450ms 一圈、段长 22%、亮度 ×1.6，屏幕/世界一致）
```

**按钮四态贴图 + 圆角描边**（`normal/hover/pressed/disabled`，缺图回退纯色；贴图支持本地 `assets/`、mod 纹理、HTTP 远程图；`enabledWhen: false` 时显示禁用态）：

```yaml
start_btn:
  type: button
  x: 10
  y: 10
  width: 120
  height: 32
  button:
    label: "开始游戏"
    normal: "gui/btn_normal.png"
    hover: "gui/btn_hover.png"
    pressed: "gui/btn_pressed.png"
    disabled: "gui/btn_disabled.png"   # 可选：禁用态贴图（enabledWhen: false 时优先）
    radius: 8                          # 可选：圆角（无贴图回退底色时生效）
    border: "#66BB6A"                  # 可选：描边色
    borderWidth: 2                     # 可选：描边宽
    background: "#2A3A52"              # 可选：底色（color 为别名）
    hoverColor: "#33475F"              # 可选：悬停底色
    textColor: "#FFFFFF"               # 可选：文字色（禁用时默认置灰）
```

**复选框**（点击切换，CLICK 事件带 true/false）：

```yaml
agree:
  type: checkbox
  x: 10
  y: 10
  width: 120
  height: 16
  checkbox:
    label: "我已阅读协议"
    value: false        # 初始勾选（默认 false）
    color: "#7A8BFF"    # 勾选/边框色
```

**仪表盘 / 环形滑块**（弧形渲染，角度制，0°=右、90°=下，startAngle 默认 -90 即正上方）：

```yaml
# 仪表盘（显示型）
health_gauge:
  type: gauge
  x: 10
  y: 10
  width: 60
  height: 60
  gauge:
    min: 0
    max: 100
    value: 72          # 支持表达式（布局时求值）
    radius: 26
    thickness: 4
    startAngle: -90
    sweepAngle: 300    # 半开仪表（默认 360 整圆）
    trackColor: "#303540"
    color: "#4CAF50"
    showValue: true    # 中心显示数值（默认 true）

# 环形滑块（交互型：点击/拖拽沿弧改值，INPUT 事件带数值）
volume_dial:
  type: arc_slider
  x: 10
  y: 10
  width: 60
  height: 60
  arc_slider:
    min: 0
    max: 100
    value: 30
    radius: 26
    thickness: 4
    startAngle: -90
    sweepAngle: 270
    color: "#7A8BFF"
```

### 3.2 新组件专属属性

```yaml
# 单行输入（input / chat_input 同款样式；chat_input 回车发送聊天）
nick:
  type: input
  x: 10
  y: 10
  width: 200
  height: 22
  input:
    placeholder: "输入昵称…"          # 空文本灰字提示
    background: "#10151F"             # 底色（默认 #20242C）
    accent: "#66BB6A"                 # 焦点/边框高亮色（默认 #7A8BFF）
    radius: 6                         # 圆角（0 = 直角）
    border: "#3A4A66"                 # 描边色（不写 = 焦点色细边框）
    borderWidth: 1
    textColor: "#FFFFFF"              # 文字色（默认白）
  # 聚焦时文本末尾有闪烁光标（500ms）

# 多行输入（Enter 换行，滚轮滚动，超出自动滚动条；样式同上）
note:
  type: area_input
  x: 10
  y: 10
  width: 300
  height: 80
  area_input: {placeholder: "写点什么..."}

# 输入建议（前缀过滤，点击/回车选中，触发 input 事件）
search:
  type: suggestion
  width: 200
  height: 20
  suggestion:
    suggestions: [钻石剑, 钻石镐, 铁剑]       # 或 [{value: 1, label: "一号"}]
    max: 6

# 快捷栏（玩家 9 格物品 + 选中高亮；样式可配）
bar:
  type: hot_slot
  x: 10
  y: 10
  width: 252
  height: 26
  hot_slot:
    slots: 9                          # 格数（默认 9）
    slotColor: "#181C24"              # 普通格底色
    selectedColor: "#3A4254"          # 选中格底色
    accent: "#7A8BFF"                 # 选中高亮（下边框/外圈）
    borderColor: "#505868"            # 普通格边框
    radius: 4                         # 圆角（>0 时选中格画 accent 外圈）
# 容器页面里（当前会话绑定了真实容器）hot_slot 与 chest_slot 同管线：
# 左键拿起/放置、右键半组/放一、Shift+左键快捷移动（快捷栏 ↔ 容器双向）

# 卡片
quest_card:
  type: card
  width: 200
  height: 80
  card:
    title: "每日任务"
    subtitle: "普通"
    content: "击杀 10 只僵尸"
    footer: "奖励: 100 金币"
    icon: "minecraft:iron_sword"
    background: "solid:30,35,46,230"
    border: "#5C6BC0"

# 翻牌卡片（点击翻面动画，CLICK 事件带目标面 true/false）
lottery:
  type: flip_card
  width: 120
  height: 80
  flip_card:
    duration: 350
    front: {title: "正面", content: "点击翻面", background: "solid:58,96,180,230"}
    back: {title: "背面", content: "★ 宝箱 ★", background: "solid:160,120,40,230"}

# 图表（bar 柱状 / line 折线 / pie 饼图）
stats:
  type: chart
  width: 190
  height: 90
  chart:
    type: bar            # bar | line | pie
    data: [3, 7, 2, 9, 5]
    labels: [一, 二, 三, 四, 五]
    showLabels: true
    min: 0               # 可选
    max: 10              # 可选（默认取数据最大值）
    color: "#42A5F5"     # 单色
    colors: ["#E53935", "#42A5F5"]   # 或逐项颜色（可选）

# 指南针（随玩家朝向滚动）
compass_bar:
  type: compass
  width: 300
  height: 20
  compass: {pixelsPerDegree: 1.0, showDegrees: true}

# 方向指示（文字 + 可选指向目标坐标的箭头）
facing:
  type: direction
  width: 120
  height: 20
  direction:
    format: cn          # cn | en | 缺省（中英）
    showArrow: true
    target: [100, 200]  # 指向世界坐标 (x, z)，不写则只显示当前朝向

# 画布（笔刷顺序绘制：rect/circle/line/gradient/triangle/text/image）
art:
  type: canvas
  width: 220
  height: 90
  canvas:
    background: "solid:15,18,24,200"
    brushes:
      - {type: gradient, x: 0, y: 0, width: 220, height: 90, from: "#1A237E", to: "#0D1117", vertical: true}
      - {type: circle, cx: 110, cy: 45, radius: 30, color: "#42A5F5", fill: false}
      - {type: line, x1: 10, y1: 80, x2: 210, y2: 20, color: "#FFD54F", width: 2}
      - {type: triangle, x1: 20, y1: 10, x2: 60, y2: 10, x3: 40, y3: 40, color: "#E53935"}
      - {type: text, x: 165, y: 72, content: "画布", color: "#FFFFFF"}

# 顶部 Boss 条（进度 0..1 或 0..100 均可；overlay 覆盖层）
boss:
  type: boss_bar
  width: 182
  height: 10
  boss_bar:
    text: "末影龙"
    progress: 0.65
    color: "#E53935"
    darkenScreen: false
    overlay: {color: "#7A8BFF", progress: 0.2}

# 自动布局
grid_box:
  type: grid
  grid: {cols: 3, spacing: 4}
  children:
    a: {type: text, height: 20, text: {content: "1"}}
    b: {type: text, height: 20, text: {content: "2"}}
h_row:
  type: h_stack
  h_stack: {spacing: 6}
  children: {...}
v_col:
  type: v_stack
  v_stack: {spacing: 6}
  children: {...}

# 滚动容器（裁剪 + 滚动条 + 滚轮）
list_panel:
  type: scroll
  width: 460
  height: 200
  scroll: {background: "solid:18,22,30,140"}
  children:
    item_1: {type: text, y: 0, text: {content: "第一项"}}
    item_2: {type: text, y: 20, text: {content: "第二项"}}

# 列表动态生成（{{item}} / {{item.属性}} 预替换）
fruit_list:
  type: foreach
  foreach: {list: vars.fruits, as: fruit, spacing: 2}
  children:
    row: {type: text, height: 14, text: {content: "• {{fruit}}"}}
```

### 3.3 容器组件（真实容器绑定，多人模式）

打开箱子/熔炉等容器时，服务端插件按 `match` 匹配页面并**取消原版界面**，
把真实容器绑定到会话，`container_sync` 推送槽位内容；点击槽位由服务端裁决。

```yaml
# container：自动生成 rows x cols 个 chest_slot（槽位号从 slotStart 起算）
# 生成的槽位继承容器的 actions；点击脚本里 vars.slot = 槽位号
grid:
  type: container
  x: "window.width / 2 - 178"
  y: 56
  width: 356
  height: 162
  container:
    rows: 3
    cols: 9
    slotStart: 0
    spacing: 2
    cellSize: 18
  actions:
    click: |-
      Chat.发送消息("槽位 " + vars.slot + "：" + Container.获取物品(vars.container.sessionId, vars.slot))

# chest_slot：单个槽位（绑定指定槽位号，大图标展示）
special:
  type: chest_slot
  x: 10
  y: 10
  width: 48
  height: 48
  chest_slot:
    slot: 0
    showSlot: true
```

容器脚本变量与方法：

| 项 | 说明 |
|---|---|
| `vars.slot` | 被点击的槽位号（chest_slot 点击事件注入） |
| `vars.container.sessionId` | 容器会话 id（Container.xxx 方法第一参数） |
| `vars.container.size / type / title` | 容器尺寸 / 类型 / 标题 |
| `Container.获取物品(会话, 槽位)` | 槽位物品注册表 id（空槽返回空串） |
| `Container.获取数量(会话, 槽位)` | 槽位物品数量 |
| `Container.设置物品(会话, 槽位, "minecraft:diamond", 数量)` | 写真实容器（数量省略=1；物品传空串=清空），改完自动重同步 |
| `Container.刷新(会话)` | 手动重发全量快照 |
| `Container.槽位数/标题/类型(会话)` | 容器信息 |
| `Container.关闭(会话)` | 关闭页面并解绑 |

- 外部变更自动重同步：其他玩家在原版界面改动 / 漏斗搬运 → 重新推送快照
- 玩家 ESC 关页 / 退出 → 服务端自动解绑（page_close 通知）
- 单机模式没有服务端数据源，chest_slot 显示为空槽

### 3.4 富文本与聊天通道

**RichText**：消息支持 `§` / `&` 颜色码（§c/&6...）、RGB 三种写法（`&#RRGGBB`、`&xRRGGBB`、`§x§R§R§G§G§B§B`）、
格式码 §l/§o/§n/§m/§k/§r —— chat_display 按片段渲染（一行内多色）。

**chat_display 通道过滤**：

```yaml
system_log:
  type: chat_display
  x: 10
  y: 10
  width: 300
  height: 120
  chat_display:
    channel: 系统        # 只显示"系统"通道（缺省/all = 全局聊天）
    background: "solid:10,14,20,160"
```

**服务端聊天通道方法**（插件脚本里调用，消息进通道由 chat_display 显示）：

```yaml
actions:
  click: |-
    ChatChannel.发送("系统", "&#55FF55[系统] §f欢迎回来")    # 广播全体
    ChatChannel.发送("战斗", "§c你受到了伤害", "玩家名")     # 只发给指定玩家
    ChatChannel.编辑("系统", 12, "§e修改后的内容")           # 按 id 改
    ChatChannel.删除("系统", 12)                            # 按 id 删
    ChatChannel.清空("系统")                                # 清空通道
```

- 通道消息上限 200 条/通道（自动丢最旧）
- 客户端捕获的原版聊天也转成 RichText 渲染（颜色码保留）

### 3.5 占位符（{分类.键}）

文本/标签/颜色属性里可直接用占位符（与 `{{vars.xxx}}` 页面变量互补；未知占位符保留原文）：

```yaml
greet:
  type: text
  text:
    content: "你好 {player.name}！FPS: {query.fps} 时间: {system.time}"
    color: "{color.primary}"      # 颜色属性也支持占位符
```

| 分类 | 键（示例） | 说明 |
|---|---|---|
| `player` | name / health / max_health / hunger / level / exp / x / y / z / yaw / pitch / gamemode / biome / dimension / online_time | 客户端=当前玩家；服务端=按接收者 |
| `entity` | target / target_type / count | 准星指向实体 / 16 格内实体数（客户端） |
| `item` | hand / hand_name / count / offhand / armor | 手持物品（客户端） |
| `query` | width / height / fps / gui_scale / fullscreen（客户端）；ping / tps（服务端） | 运行环境 |
| `system` | time / date / millis / uuid；online / max_players / server_name（回落 global） | 时间系统 + 服务端全局值 |
| `color` | 16 MC 色名 + primary/secondary/success/danger/warning/info | 命名颜色 → 色值 |

- 服务端通道消息按**接收者**逐人解析（`ChatChannel.发送("系统", "欢迎 {player.name}")` 各人看到自己的名字）
- `{system.online}` 等全局值走 global_state 5 秒推送

### 3.6 屏幕特效下发（服务端远程触发）

服务端脚本可远程触发客户端界面特效（ui_effect 通道）：

```yaml
actions:
  click: |-
    Screen.屏幕震动(player.name, 8, 400)      # 震动（强度, 时长ms）
    Screen.闪屏(player.name, "#FFD54F", 150)  # 闪屏（颜色, 时长ms）
    Screen.过渡(player.name, "#000000", 500)  # 全屏淡入淡出过渡
```

客户端单机同样可用（`Screen.屏幕震动(6, 400)` 不传玩家）。

### 3.7 自定义字体（TTF）

把 `.ttf` 文件放进 `OpenDreamCore/fonts/`（本地）或云端 `resources/fonts/`（自动下发到
`OpenDreamCore-Cloud/fonts/`），按文件名（去扩展名）注册，元素用 `font:` 属性替换字体：

```yaml
title:
  type: text
  font: 像素          # → OpenDreamCore/fonts/像素.ttf（找不到回退默认字体）
  text:
    content: "自定义字体标题"
    scale: 1.5        # 文本缩放（自定义字体按字形图集缩放绘制）
    color: "#FFD54F"
    shadow: true

ok_btn:
  type: button
  font: 像素
  button: {label: "开始游戏"}
```

- 支持中文等任意字符（Java 2D 软件渲染字形 → 图集缓存，按需生成）
- `/odc reload` 重新扫描字体目录
- 字体未找到时静默回退默认字体（页面不报错）

### 3.8 自适应 / 涟漪 / 嵌入页点击 / 真视频 / 世界面板交互
```yaml
# adaptive：容器尺寸按子元素内容自动撑开（宽 = 最宽子元素，高 = 子元素总高 + spacing）
# 显式写了 width/height 时以显式值为准
menu_box:
  type: adaptive
  x: 100
  y: 50
  adaptive: {spacing: 4}
  children:
    a: {type: button, width: 200, height: 24, button: {label: "第一项"}}
    b: {type: button, width: 180, height: 30, button: {label: "第二项"}}

# ripple：点击波纹（点击位置圆环扩散淡出）
ok_btn:
  type: button
  ripple: {color: "#66FFFFFF", duration: 500, radius: 24}
  button: {label: "确定"}

# embed 嵌入页元素可点击：单机执行嵌入页的 actions；多人按嵌入元素 id 上报（宿主页结构一致时可用）
mini:
  type: embed
  x: 10
  y: 10
  width: 200
  height: 100
  embed: {page: embed_target}

# 真视频（FFmpeg）：把 javacv + javacv-platform 各平台 jar 丢进 mods 即启用，
# 支持 mp4/webm/mov/mkv/avi...（本地或 https 远程直连流式解码）；未装 JavaCV 回退帧序列
cinema:
  type: video
  x: 0
  y: 0
  width: 320
  height: 180
  video:
    src: "assets/videos/trailer.mp4"    # 或 "https://example.com/video.mp4"（SSRF 防护）
    loop: true                          # 默认 true；false = 播完保持最后一帧
    fit: contain                        # contain 按原比例居中（默认拉伸铺满）
    fps: 24                             # 仅帧序列方案用（JavaCV 用视频自带帧率）
```

### 3.9 世界 3D 面板射线交互

`display: world` 页面默认只渲染不可交互；`world.interact: true` 开启后，鼠标射线拾取世界面板：
悬停/左键点击触发元素的 `hover` / `click` actions（本地脚本）。元素可单独 `hologram.interact: true` 声明。

```yaml
match: world
display: world
world:
  offsetX: 0
  offsetY: 1.7
  offsetZ: 3
  interact: true          # 页级开关：开启世界面板射线交互

menu_button:
  type: text
  hologram:
    x: 0
    y: 0
    z: 0
    scale: 0.03
    width: 2.0            # 可点区域（世界单位；文本默认 2x0.25，rect/image 默认 1x1）
    height: 0.25
  text:
    content: "左键点击打开菜单"
  actions:
    click: |-
      Screen.打开页面("menu")
    hover: |-
      Sound.播放音效("minecraft:block.lever.click", 1.0, 2.0)
```

> 世界面板补充：`fadeDistance: 20`（米，超过开始淡出）+ `fadeRange: 3`（淡出带宽度，默认 3 米）；
> **锚点模式**：默认相对玩家跟随（`offsetX/Y/Z` 相对偏移）；`anchor: {x, y, z}` → **绝对世界坐标**（面板固定不随玩家移动，offset 作为相对微调叠加）；`follow: false` → 打开瞬间的位置固定（pin）；`smooth: 0~1` → 平滑跟随（每帧向目标插值，漂浮感，默认 0 = 刚性跟随）——生效锚点按面板每帧计算，渲染/射线拾取/屏幕外箭头/编辑手柄严格一致；
> **悬停高亮颜色**：`hoverColor: "#66FFD700"`（缺省亮蓝 `#AA7A8BFF`，悬停框颜色随页可调）；
> **悬停音效**：`hoverSound: "minecraft:block.lever.click"` 或 `{sound, volume, pitch}`（悬停到新元素时播放）；
> **背景遮罩**：`background: "#10151FCC"` 或 `{color: "#10151FCC", padding: 0.25, border: "#3A4A66", radius: 0.15, gradient: "#1E2A3E"}` —— 按可见元素包围盒画半透明底 + 可选边框 + **圆角**（radius 世界单位，超短边一半自动钳制）+ **上下渐变**（顶 color → 底 gradient，圆角同步渐变；跟随距离淡出）；
> `offScreenArrows: true` 时元素在屏幕外 → 屏幕边缘画箭头指向其方向（`arrowColor` 可调色）；
> **点击涟漪**：点击世界元素（含开关/下拉/页签）在点击点扩散涟漪圆环（400ms 衰减，`rippleColor` 可配颜色）；
> **拖拽**：`world.drag: true`（页级）或 `hologram.draggable: true`（元素级）→ 按住左键沿射线平面移动元素（实时跟手），松手写回 hologram.x/y/z；
> **Shift 锁轴**：拖拽中按住 Shift → 按主导位移轴锁定（水平/垂直对齐移动）；
> **坐标表达式**：`hologram.x/y/z/scale/width/height/yaw` 支持数字或表达式（`x: "vars.badge_x"`、`y: "vars.offset / 2"`），每帧求值——服务端 `state_patch` 改变量即可驱动世界面板布局；
> **文本自动尺寸**：text 带 `hologram.wrap`（世界单位折行）且未显式写 width/height 时，**宽 = wrap、高 = 折行行数 × 8px × scale** —— 射线命中框/悬停框/编辑选中框/背景包围盒随内容贴合；
> **静态旋转**：`hologram.yaw`（度，绕元素中心，支持表达式）贯通全部元素类型（text/rect/物品/开关/滑块/页签…），与动画 rotation 叠加；
> **元素边框**：`hologram.border: "#FFD700"` 或 `{color, width, flow: true, flowColor: "#FFFFFF"}` —— billboard 四边描边（yaw 同步旋转、距离淡出），`flow: true` 时亮色段沿边框匀速流动（**圆角矩形同样流光**；**悬停时加速提亮**：1.2s→450ms 一圈、段长 15%→22%、亮度 ×1.6）；圆角矩形元素（rect.radius）描边自动跟随圆角；
> **元素圆角**：`rect.radius`（世界单位，超短边一半自动钳制）—— 元素级圆角矩形；`rect.gradient`（顶 color → 底 gradient，圆角同步渐变）；
> **元素角标**：`hologram.badge: true`（红点）/ `5`（数量）/ `{count, color}` —— 右上角 billboard 角标；
> **多语言**：`{lang.键名}` 占位符 → 客户端语言文件（`OpenDreamCore/lang/<locale>.properties` 覆盖 → 模组内置资源 → en_us 回退）；
> **悬停提示**：`hologram.tooltip: "按住拖动\nShift 锁轴"` 或对象 `{text, color/textColor, background, border, width}` —— 悬停时屏幕空间提示气泡（**§ 颜色码富文本**多色渲染、占位符插值 `{vars.*}/{player.*}/{lang.*}`、按码宽自动折行、可配底色/描边/文字色/最大宽度）；
> **呼吸直达**：`hologram.breathe: true` / `{amplitude: 0.08, speed: 1.2}` —— 无需 animations 块，元素直接呼吸缩放；
> **元素锁定**：`hologram.locked: true` —— 防误拖（不可拖拽/微调/手柄变换，点击仍可用）；
> **文本对齐**：`text.align: left|center|right`（多行统一对齐，编辑模式 T 键循环）；
> **元素发光**：`hologram.glow: "#33FFD700"` 或 `{color, size}` —— 四层同心半透明辉光；
> **元素倒影**：`hologram.shadow: "#33000000"` 或 `{color, offset, size}` —— 向下偏移多层暗影；
> **元素指针样式**：`hologram.cursor: "cross"/"move"/"text"` —— 悬停该元素时系统光标切换（十字/手型/文本 I 型，缺省手型），离开恢复默认；
> `display: world` 的服务端页面同样支持（服务端 `Screen.打开页面` 打开，射线事件上报服务端裁决）。

> **世界面板多面板同屏**：多个世界页面可同时打开并排显示（各自 `world.offsetX/Y/Z` 独立锚点）——
> 服务端 `Screen.打开页面(玩家, "world_quest")` 直接追加新面板（同页面 id 重开 = 原位刷新不新增）；
> 每面板**独立**页签/悬停/开关/滑块/下拉状态与生命周期，射线拾取**跨面板取最近命中**，悬停哪块面板交互即聚焦哪块；
> 服务端广播（`Screen.广播世界页签` / `广播元素可见·可用` / 编辑保存重发）对同时打开该页的玩家**全部命中**。
> 示例：`world_board.yaml`（进服自动开，offsetX 0）+ `world_quest.yaml`（`/odc page open world_quest`，offsetX -3.8 并排，独立页签/复选框/下拉/滑块/进度条）。

> **世界物品展示**：`type: item_slot` / `type: item_display` → billboard 物品模型（`hologram.height` 控制世界尺寸，默认 0.5），`count > 1` 时右下角画数量角标（跟随相机、距离淡出）。支持 **NBT/组件**：

```yaml
item_demo:
  type: item_slot
  hologram: {x: -0.7, y: -3.4, z: 0, height: 0.35}
  item_slot:
    item: "minecraft:diamond"    # 物品 id（支持变量插值，非法/空气跳过渲染）
    count: 64                    # 数量（>1 显示角标）
    nbt: "{minecraft:custom_name:'\"§b§l传说之刃\"',minecraft:enchantments:{levels:{minecraft:sharpness:5}}}"  # 可选 SNBT 组件（与 id/Count 合并）
# 或整条 SNBT 物品（item 以 { 开头，id/Count/组件全在标签里）：
item_full:
  type: item_slot
  hologram: {x: 0, y: -4, z: 0, height: 0.35}
  item_slot:
    item: "{id:'minecraft:diamond_sword',Count:1b,minecraft:unbreakable:{}}"
```

> **世界面板多页签**：`type: tabs` 页签栏元素（始终显示）+ 元素 `tab: "页签名"` 属性（只在对应页签下渲染/可点击）：

```yaml
tab_bar:
  type: tabs
  hologram: {x: 0, y: 0.45, z: 0, width: 3, height: 0.22}
  tabs:
    options: ["概览", "商店"]   # 页签列表（点击切换，INPUT 上报选项值）
    active: "概览"              # 初始页签（支持变量插值，如 "vars.tab"）
    color: "#2A3A52"            # 未激活底色
    activeColor: "#42A5F5"      # 激活底色/下划线
    textColor: "#E0E0E0"        # 未激活文字色
    textActiveColor: "#FFFFFF"  # 激活文字色
shop_item:
  type: item_slot
  tab: "商店"                   # 只在本页签显示（渲染 + 射线拾取双过滤）
  hologram: {x: -0.7, y: -4.5, z: 0, height: 0.35}
  item_slot: {item: "minecraft:emerald", count: 32}
```

> 无 `tab` 属性的元素 = 公共区（所有页签都显示）；页签栏自身不带 tab 所以始终可见；
> 点击页签 → INPUT 事件（服务端裁决执行 input 脚本 / 本地脚本 + vars.input）；重开页面回到定义值。
> 切页签时页签内容按 260ms easeOutCubic **淡入过渡**（公共区/页签栏不受影响）。
> **服务端联动**：脚本 `Screen.设置世界页签(玩家, 页面, 页签)` 强制单个玩家切页签；
> `Screen.广播世界页签(页面, 页签)` 让所有正在看该页面的玩家一起切（页面 `actions.click` 里直接调用即可）。
> **页签切换生命周期**：页面 `functions.onTabChange` 脚本在每次切页签时执行（`vars.tab` = 新页签，`vars.prevTab` = 旧页签）：
>
> ```yaml
> functions:
>   onTabChange: |-
>     Sound.播放音效("minecraft:block.lever.click", player.name, 1.0, 2.0)
> ```

> **世界面板组联动**：`hologram.group: "组名"` → 同组元素拖拽时**一起移动**（相同相对偏移，渲染实时跟随；松手逐元素落位/持久化/上报）：

```yaml
item_icon:
  type: item_slot
  hologram: {x: -0.7, y: -3.4, z: 0, height: 0.35, group: demo_group}
  item_slot: {item: "minecraft:diamond", count: 64}
item_label:
  type: text
  hologram: {x: 0.6, y: -3.4, z: 0, scale: 0.015, group: demo_group}
  text: {content: "拖物品图标，标签一起跟随"}
```

> **世界面板 WYSIWYG 编辑**（服务端页面）：`/odc edit world <页面> [玩家]` → 授予编辑租约并打开世界页，客户端自动进入编辑模式：
> ① **全元素可拖拽**（绕过 `draggable`，slider 也当普通元素移动；编辑模式不产生运行时拖拽 INPUT）；**Alt + 拖拽空白区 = 面板整体移动**（全部元素同偏移，可撤消，松手写回）；
> ② **点击选中**（亮蓝脉冲选中框，顶部工具栏实时显示 id 与坐标）；
> ③ **方向键微调**：←→ = x，↑↓ = y，Shift+↑↓ = z（步长工具栏循环 0.01~1，按住自动重复）；
> ④ **网格吸附**：工具栏 [吸附] 循环 关/0.05/0.1/0.5/1，拖拽落点按绝对坐标吸附网格；
> ④½ **描边拖宽**：选中元素带 `hologram.border` 时，左边缘出现**菱形手柄**——按住左右拖拽实时调整描边宽度（沿 billboard 右轴映射，0.002~0.3 钳制，边框粗细所见即所得），松手记入未保存属性，保存写回 `hologram.border`（字符串描边自动转为 `{color, width}` 保留颜色）；
> ⑤ **属性编辑**：选中元素后第二行工具栏 [文本][颜色][尺寸] 打开输入屏（Enter 提交/ESC 取消），实时改 `text.content` / `text.color` / `hologram.scale`；
> ⑥ **Ctrl + 拖拽 = 复制**：深拷贝元素整树（新 id、子元素唯一化、位置错开），拖副本定位；
> ⑥ 工具栏 **保存** = 位置/属性写回 + **新增元素插入**（类型选择屏：text/rect/item_slot/image/slider/toggle/checkbox/dropdown/progress/tabs，text 可先输内容，元素生成在面板中心）+ **删除元素整块移除**（含子元素，快照可还原），全部手术式写回 `UI/<页面>.yaml`（保留注释与其余格式、清除 world_positions 覆盖、热重载、同页玩家实时同步）；**放弃** = 还原进入编辑时的快照（含已删元素）；**退出** = 释放编辑租约。
> ⑦ **撤消/重做**：**Ctrl+Z** 撤消 / **Ctrl+Y**（或 Ctrl+Shift+Z）重做（上限 64 步）—— 拖拽落位、旋转/缩放/描边手柄、属性输入、方向键微调、新增/复制/粘贴/删除（含批量）、编组/解组/镜像/统一尺寸/等距分布/整体对齐全部可撤；连续同类小步（输入/步进键）自动合并为一个撤消步；工具栏**第三行**提供 `[撤消][重做]` 按钮与实时历史步数提示。
> 提示：编辑会把 `hologram.x/y/z` 与属性烘焙成具体数值（表达式会被覆盖为当前位置/值）。

```yaml
match: world
display: world
world:
  offsetX: 0
  offsetY: 1.7
  offsetZ: 3
  interact: true
  offScreenArrows: true    # 屏幕外箭头指示（默认关）
  arrowColor: "#7A8BFF"
```

> **动画作用域隔离**：屏幕/HUD/世界三类页面同时渲染时，动画按页面 id 隔离（同名元素各播各的，互不串扰）；
> 命名动画按名称全局注册，`Screen.播放动画("名称")` 与服务端 `Screen.播放动画`（ui_animation 通道）触发后，匹配元素 id 的页面一起响应。

**服务端 world 页面 + 变量驱动示例**（插件 `plugins/OpenDreamCore/UI/world_board.yaml`）：

```yaml
# 服务端 world 页面：射线点击上报服务端裁决，服务端改变量后 state_patch 下发刷新
title: 世界公告板
display: world
options:
  world:
    offsetX: 0
    offsetY: 1.7
    offsetZ: 3
    interact: true
variables:
  title_text: 服务器公告
  body_text: 欢迎来到服务器
elements:
  - id: title_el
    type: text
    hologram: {x: 0, y: 0, z: 0, scale: 0.03, width: 3, height: 0.3}
    text: {content: "{vars.title_text}", color: "#FFD700"}
    actions:
      click: |-
        Screen.更新状态("world_board", {body_text: "你点击了公告牌！"})
  - id: body_el
    type: text
    hologram: {x: 0, y: -0.4, z: 0, scale: 0.025}
    text: {content: "{vars.body_text}", color: "#FFFFFF"}
```

### 3.10 动画体系（完整）

```yaml
animations:
  # 自动播放（key = 元素 id）
  title_text:
    - property: y            # x / y / scale / opacity / rotation（度）/ path
      from: -40
      to: 10
      duration: 700
      easing: bounce         # 45 种缓动
      loop: false
      pingpong: false        # loop 时往返摆（0→1→0，breathe/pulse 用）
      delay: 0
    - property: rotation
      from: 0
      to: 360
      duration: 1800
      easing: linear
      loop: true

  # 预置特效（preset，无需写 from/to）
  loading_icon:
    - preset: spin           # blink/breathe/pulse/pop/elastic/bounce/spin/shake/wave/swing/flash/
                             # slide_left/right/up/down/fade_in/out/fade_in_up/down/fade_out_down/zoom_in/out
      duration: 1200         # 可选覆盖默认时长
      loop: true

  # 命名动画（target 指向元素，脚本 Screen.播放动画("名称") 触发）
  enter_panel:
    - target: panel
      preset: slide_up
  reward_pop:
    - target: reward_icon
      preset: pop            # 弹出：1 → 1.15 → 1（to/amplitude 调幅度）
      duration: 500
  logo_loop:
    - target: logo_badge
      property: path
      points: [[0, 0], [30, -20], [60, 0], [30, 20], [0, 0]]
      duration: 3000
      loop: true
```

> 预置特效参数：`duration` 覆盖时长；`to`/`amplitude` 调幅度（pop 的弹起幅度、shake 抖动 px、wave/swing 摆动角度）；
> 复合特效（pop/flash/swing/fade_in_up/fade_out_down/zoom_out 等）自动拆成多段 Def 衔接，播完自动归位；
> 重触发同元素同名动画时**替换**旧动画（不叠加）。

动画方法：`Screen.播放动画/停止动画/暂停动画/恢复动画/播放动画序列("a","b")`。
视频控制：`Screen.视频暂停/视频继续/视频停止/视频重播/视频跳转(元素id[, 秒])/视频是否播放(元素id)`。

**动画体系全图**：
- **三类页面**：屏幕 / HUD / 世界（world）都支持 animations（同一套语法与预置特效）
- **作用域隔离**：自动/触发动画按"页面 id + 元素 id"隔离，屏幕/HUD/世界同帧共存不串扰；命名动画全局注册，同名后注册覆盖
- **远程触发**：服务端 `Screen.播放动画/停止/暂停/恢复(玩家?, 名称)` 经 ui_animation 通道下发，客户端按元素 id 匹配（屏幕/HUD/世界皆可）
- **脚本触发**：`Screen.播放动画("名称")` 客户端本地；命名动画可带 `delay` 做序列，或 `Screen.播放动画序列("a","b")` 顺序播放
- **事件数据**：元素事件脚本里 `vars.event` = 事件数据（开关/复选框 true·false、滑块数值、输入内容），`vars.input` 与 `vars.event` 同值（双端注入）；容器槽位另有 `vars.slot`/`vars.container`

## 12. 校验与错误

## 4. 子父级（多级嵌套）

`children` 嵌套，任意层级；子元素继承父坐标系：

```yaml
root:
  type: layout
  x: 0
  y: 0
  width: 300
  children:
    header:
      type: text
      y: 0
      width: "parent.width"
      height: 20
    body:
      type: layout
      y: "header.height + 5"
      children:                     # 多级嵌套
        left:
          type: layout
          width: "parent.width / 2"
          children:
            item_a: {type: button, button: {label: "A"}}
        right:
          type: layout
          width: "parent.width / 2"
```

## 4.1 import 模板复用 / embed 运行时嵌入

```yaml
# 元素级 import：把另一个页面（card_tpl.yaml）的元素内联进来
# 规则：元素 id 加前缀（默认 目标页id_）；数字 x/y 加偏移；变量并入（本页已有键优先）；vars 覆盖
my_card:
  type: import
  page: card_tpl          # 目标页面 id（文件名）
  prefix: tpl_            # 元素 id 前缀（可选，默认 card_tpl_）
  x: 10                   # 数字偏移（可选）
  y: 20
  vars: {tpl_name: 我的卡片}   # 覆盖目标页变量（可选）

# 页面级 imports 列表（顶层，整页合并）
imports:
  - page: common_header
    prefix: hdr_

# embed 运行时嵌入：把另一页面的布局画进本容器（按容器尺寸重新布局 + 裁剪）
# 目前为展示型（嵌入页的按钮等交互路由在 P3 事件转换中补）
mini_stats:
  type: embed
  x: 10
  y: 10
  width: 200
  height: 100
  embed:
    page: embed_target
```

- import 支持任意层级（children 里也能用）；嵌套 import 递归展开
- 循环引用（A→B→A）报 `import 循环引用` 解析错误
- 客户端本地页面、服务端 UI 目录、服务端 page_sync 下发页面都做同一展开

两种父子写法（可混用）：
- `children:` 缩进嵌套（自动挂父）
- `parent: "组件id"` 显式挂父

## 5. 花括号写法（可加可不加，完全等价）

**加不加 `{}` 都行**——flow 内联与块式写法等价，可自由混用：

```yaml
# 内联对象（加 {}）
item_a: {type: button, x: 0, y: 0, button: {label: "A"}}

# 块式（不加 {}）——同一元素
item_a:
  type: button
  x: 0
  y: 0
  button:
    label: "A"

# 混用
panel:
  type: layout
  children:
    a: {type: text, text: {content: "内联"}}
    b:
      type: button
      button: {label: "块式+内联"}
```

脚本代码块用 `{}`（DreamLang）：`if (x > 0) { 变量 y = 1 }`。

## 6. 显示模式（页面顶层可选声明）

```yaml
display: screen     # screen / hud / world / container（不写按 match 推断）
allowEscClose: true
background: false   # 是否暗化背景
through: true       # 鼠标穿透
hideVanilla:        # HUD 页面隐藏原版层（可选：列表/逗号串；true/all = 全部隐藏）
  - health          # 简名或完整层名（minecraft:player_health）均可
  - food
  - armor
  # 全部层：hotbar 物品栏 / crosshair 准星 / exp 经验条 / air 氧气 / boss Boss条 / chat 聊天 /
  # effects 状态效果 / scoreboard 计分板 / title 标题 / tab 玩家列表 / camera 眩晕·火药·水下滤镜 / …
  # NeoForge：逐层精确取消（RenderGuiLayerEvent）；Fabric：hideVanilla: all/true 整层跳过
  # （原版层在 Fabric 侧无命名，无法逐层；HUD 页面卸载/关闭后原版 HUD 自动恢复）
```

## 7. 事件与生命周期

```yaml
actions:              # 元素事件
  click: |-           # 点击（DreamLang 脚本，多行块）
    方法.请求购买("minecraft:diamond_sword", 1)
  hover: |-
    ...
Functions:            # 页面生命周期
  open: |-            # 打开时
    方法.播放音效("minecraft:block.chest.open")
  close: |-           # 关闭时
    方法.播放音效("minecraft:block.chest.close")
  tick: |-            # 页面打开期间每秒执行一次（客户端）
    Screen.设置元素("tick_label", "text.content", "在线时长: " + Player.在线时长() + " 秒")
  resize: |-          # 窗口尺寸变化并重排后
    ...
```

事件：click / hover / press / input / scroll / key…（DreamLang 脚本，中英双语关键字）。
生命周期：open / close / tick（每秒）/ resize（窗口变化）。

## 7.1 数据绑定（bind）

元素属性 ← 变量自动更新：`bind` 映射（路径 → DreamLang 表达式）在每次布局时求值并覆盖属性。
服务端 state_patch / 脚本改变量 → 页面刷新 → 绑定自动重算（无需手写 Screen.设置元素）：

```yaml
bind_var: 开
label:
  type: text
  bind:
    text.content: "'当前: ' + vars.bind_var"          # 字符串字面量用引号
    text.color: "vars.bind_var == '开' ? '#66BB6A' : '#E57373'"
    visible: "vars.show"                               # 顶层属性也能绑
button_ok:
  type: button
  bind:
    button.background: "vars.btn_color"                # 点路径写嵌套属性
```

- 表达式环境：vars（页面变量）/ global（服务端全局）/ player / window / parent / this
- 绑定失败保持原属性（不拖垮整页）；`bind` 键本身不进元素属性

## 7.2 焦点与指针路由

- **Tab / Shift+Tab**：在页面内可聚焦元素（input/area_input/suggestion/chat_input/dropdown）间循环焦点
- **悬停提示**：元素 `tooltip` 支持字符串 / 多行 List / 对象 `{text/content, color/textColor, background, border, width}` —— § 颜色码富文本、占位符插值、可配底色/描边/文字色/宽度（屏幕/世界同管线；服务端 tooltip 注册表为纯文本默认样式）
- **ESC**：先收起展开的下拉/建议列表 → 再释放焦点 → 再按才关闭页面（allowEscClose 仍生效）
- **下拉键盘**：聚焦并展开后 ↑/↓ 移动光标（高亮），Enter 确认，点击选项同步光标；**关闭时 Enter 直接展开并选中第一项**
- **滑块键盘**：slider / arc_slider 聚焦后 **←/→ 按 `step` 步进**（默认 1，钳制 min/max，INPUT 上报）
- **确认键**：button / toggle / checkbox 聚焦 + **Enter = 确认点击**（开关/复选框自动切换值）
- **建议键盘**：suggestion 聚焦后 ↑/↓ 移动光标（过滤后列表内循环，深蓝高亮）、Enter 选中光标项、ESC 收起；输入/退格/点击后光标复位
- **键位绑定**：页面顶层 `keybinds: {名称: "key.keyboard.f"}` / `mousebinds: {名称: 1}`（1=右键 2=中键）—— 页面打开期间按 F 上报 KEY 事件（服务端 OdcKeyEvent，插件监听）；**HUD 页面声明的键位是全局热键**（HUD 挂载即常驻，无页面打开也响应，经 HUD 会话路由）
- **聚焦高亮**：输入类组件聚焦时上下边框变蓝（#7A8BFF）
- **禁用穿透**：`enabledWhen: false` 的元素不拦截点击/滚轮（hover/tooltip 仍显示）
- **pointerEvents**：`none`（自己和子都不响应）/ `children`（只响应子）/ `auto`（默认）

## 8. 资源引用

| 写法 | 来源 |
|---|---|
| `gui/logo.png` | opendreamcore 命名空间（默认） |
| `minecraft:textures/...` | 显式命名空间 |
| `assets/xxx.png` | 本地文件（游戏目录 assets/ 下） |
| `https://xxx` / `http://xxx` | 远程图片（自动下载，见下） |
| `solid:0,0,0,100` | 内联色（r,g,b,alpha） |

**远程图片（http/https）**：首次引用时后台下载并缓存到 `OpenDreamCore-Cloud/http-cache/`（文件名 = URL 的 SHA-256 + 原扩展名），下载完成自动出现在页面上，之后秒开。
安全防护：只允许 http/https；DNS 解析后的任意地址属于内网/回环/链路本地/组播/保留段即拒绝（fail-closed）；禁止 userinfo；禁止重定向跟随；单文件上限 16MB。
图片元素示例：

```yaml
banner:
  type: image
  x: 0
  y: 0
  width: 320
  height: 120
  image:
    src: "https://example.com/banner.png"   # 未下载完成前占位，就绪自动显示
```

## 9. 注释

YAML `#` 注释；插件/服务端可下发动态覆盖。

## 10. 校验与错误

- schema 校验到行：`第 12 行：未知元素类型 "botton"（想写 button？）`
- 显式类型标注（必要时）
- 组件注册表自动生成文档

## 11. 服务端扁平语法（服务端 GUI 编译器）

服务端页面（`plugins/OpenDreamCore/UI/`）支持两种写法：
标准嵌套语法（前面所有章节）与**扁平语法**（`elements`/`lines` 列表，服务端按玩家编译 + 加密下发）：

```yaml
# menu.yaml（扁平语法）
title: 主菜单
options: {allowEscClose: true}
variables:
  menu_name: 服务器菜单
functions:                  # 生命周期（open/close/tick/resize）
  open: |-
    Sound.播放音效("minecraft:block.chest.open", player.name, 1.0, 1.0)
elements:                   # 元素列表（id 省略自动 el_1/el_2...）
  - id: header
    type: text
    x: "window.width / 2 - 150"
    y: 20
    width: 300
    text: {content: "{menu_name}", align: center, color: "#FFD54F"}
  - type: button
    x: "window.width / 2 - 100"
    y: 72
    width: 200
    height: 24
    condition: "player.level >= 10"   # ★ 服务端编译期条件：不满足整体剔除（不可见/不可点）
    button: {label: "VIP 传送门"}
    actions:
      click: |-
        Teleport.传送到出生点(player.name)

# 或 lines：纵向自动排布（y 自动叠加，x 默认 0，width 默认 window.width）
# lineSpacing: 4   # 行间距（默认 4）
lines:
  - type: button
    height: 22
    button: {label: "第一行"}
  - type: button
    height: 22
    button: {label: "第二行"}
```

**服务端编译行为**（每次打开按玩家重新编译）：
- `condition:` 用 DreamLang 求值（`player.level`/`player.health`...），false 的元素连同子元素整体剔除 —— 与客户端 `visibleWhen`（运行时显隐）互补
- 元素字符串属性做**占位符替换**：`{player.display_name}` / `{system.online}` 等按接收玩家解析
- **PlaceholderAPI 可选集成**：服务器装了 PAPI 时 `%player_name%` 等令牌编译期替换（未装原样保留）
- `actions` 脚本不替换（避免破坏 DreamLang 语法）
- 编译结果序列化后 **AES-GCM 加密下发**（会话 key 随 ready_ack 分配，每玩家独立；与云资源同密钥体系）
- 标准嵌套语法页面保持原文下发（占位符由客户端各自解析），同样加密

