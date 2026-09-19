# OpenDreamCore YAML 语法（定稿 v1）

> 页面 YAML 的完整语法参考。有问题联系 QQ：2496599413。

## 0. 总览

```yaml
# 顶层：match 触发 + 变量 + 元素（有 type 是元素，没 type 是变量）
match: 菜单                    # 触发条件（界面类型或标题）
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
  class: card danger_btn       # 样式类（空格分隔多个；配合 themes/*.yml 主题规则，见 §12）
  style:                       # 内联样式块（优先级最高；仅填缺失属性，style 键保留以维持导出回写）
    paddingAll: 10
    textColor: "{accent}"
  transition: "opacity 0.3 CUBIC_OUT, background 0.25 QUAD_OUT"   # 属性过渡声明（详见 §12.5）
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
    back: {title: "背面", content: "稀有宝箱", background: "solid:160,120,40,230"}

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
```

#### 流式弹性布局（h_stack / v_stack 增强）

容器与子元素均未使用下列属性时，走原始游标排布路径，行为与历史版本完全一致。
任一属性出现即进入流式解算：

```yaml
toolbar:
  type: h_stack                 # v_stack 参数相同，方向转 90°
  width: "{window.width}"
  spacing: 8                    # （原有）间距
  align: center                 # 新增：交叉轴对齐 start|center|end|stretch
  justify: between              # 新增：主轴分布 start|center|end|between|around
  wrap: true                    # 新增：放不下自动换行/换列
  reverse: true                 # 新增：反向排布
slot_a:
  parent: toolbar
  type: button
  grow: 1                       # 分剩余空间权重（都为 1 即均分）；未声明宽度的子项从 0 起参与分配
  basis: 90                     # 分配前理想尺寸（覆盖 width 参与分配）
  shrink: 1                     # 空间不足时收缩权重
  alignSelf: end                # 单独覆盖容器 align
badge:
  parent: toolbar
  type: rect
  absolute: true                # 脱离流式：不参与分配，用自身 x/y 相对容器定位（角标场景）
  x: "(parent.width - 40)"
  y: -6
```

> **语义速记**：`justify` 管这一行怎么排（挤左/居中/两端撑开），`align` 管高矮不齐怎么站队。
> 收缩优先于扩张：空间不足按 shrink 加权压缩，有富余才由 grow 分。
> `wrap` 换行后每行独立做 justify，行距 = spacing。

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
`OpenDreamCore/cache/fonts/`），按文件名（去扩展名）注册，元素用 `font:` 属性替换字体：

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

### 7.0 元素事件总表（actions 键）

六种事件，客户端/服务端/单机三端行为一致。多人模式下事件上报服务端裁决，
单机模式本地直接执行脚本：

```yaml
buy_btn:
  type: button
  actions:
    click: |-                  # ① 点击
      Toast.显示("买了")
    press: |-                  # ③ 拖动中（滑块类持续触发）
      方法.更新变量值("volume")
    hover: |-                  # ② 悬停进入
      Toast.显示("摸到了")
```

| actions 键 | 触发时机 | 适用组件 | 事件数据 |
|---|---|---|---|
| `click` | 左键点击；右键点击也触发 | 全部可交互组件 | 右键时 = `"right"`；toggle/checkbox = `"true"`/`"false"` |
| `press` | 按住拖动过程中持续触发 | slider、arc_slider | — |
| `hover` | 鼠标悬停进入 | 全部（HUD 也支持） | — |
| `input` | 内容变化 / 回车提交 | input、area_input、suggestion、dropdown、chat_input | 当前输入内容 |
| `scroll` | 滚轮滚动 | scroll 容器 | 本次滚动量 |
| `key` | 按键绑定触发（HUD 场景） | 页面级 keybind | `key:Xxx` / `mouse:Xxx` |

细节与坑：

- **toggle / checkbox** 的 click 事件数据是新状态布尔串：
  `vars.event` = `"true"`/`"false"`，脚本里判断即可，不用自己取反
- **右键区分**：非槽位组件的右键点击同样走 click，数据为 `"right"`，
  脚本里 `if vars.event == 'right' then ... end` 分流左右键逻辑
- **chat_input** 的回车发送走 input 事件（不是独立的 submit）
- **video 进度条 seek、table 行点击**内部也派发 click
- 事件脚本里 `vars.event` = 事件数据、`vars.input` 与之同值（双端统一注入）

### 7.0.1 自定义点击音效

```yaml
btn:
  type: button
  clickSound: "minecraft:block.note_block.pling"          # 简写
  # 或对象形式调音量音调：
  # clickSound: {sound: "...", volume: 0.8, pitch: 1.5}
```

优先级：元素 clickSound > 原版 UI 按钮声。

### 7.0.2 页面生命周期——客户端钩子（七钩子）

七个钩子，全部"存在才跑"，DreamLang 多行块：

```yaml
Functions:
  open: |-              # ① 打开时（含进服自动挂载的 HUD/world）
    方法.播放声音("minecraft:block.chest.open")
  close: |-             # ② 关闭时
    方法.播放声音("minecraft:block.chest.close")
  tick: |-              # ③ 打开期间每秒一次（客户端）
    Screen.设置元素("clock", "text.content", 系统.时间())
  resize: |-            # ④ 窗口尺寸变化并重排后
    方法.刷新布局()
  wheel: |-             # ⑤ 滚轮滚动（页面级，先于 scroll 容器的事件）
    方法.取滚轮值()
  keyPress: |-          # ⑥ 任意按键按下（旧版兼容钩子；键名经 方法.取当前按下键 取得）
    (方法.取当前按下键 == 'E')?{方法.异步执行方法('打开背包')}:0;
  preRender: |-         # ⑦ 每帧预绘制前（存在才跑；高频，慎放重逻辑）
    ...
```

要点：

- `wheel` / `keyPress` 是页面级钩子，与元素级 scroll/key 事件互不冲突：
  前者管"页面上滚了一下"，后者管"某个容器滚了多少"
- `preRender` 每帧都跑，只做轻量绘制准备；每秒级逻辑请用 `tick`
- 编辑模式下 open/close/tick/preRender 被隔离不执行（防止编辑时误触发业务）

### 7.0.3 服务端触发器（Functions 的另一面）

`Functions` 里除了客户端钩子，还支持六个**服务端触发器**——玩家行为发生时，
服务端遍历所有页面执行同名脚本（页面变量 + player.* 注入作用域）：

```yaml
Functions:
  join: |-              # 玩家进服
    Toast.显示("欢迎回来, " + player.name)
  quit: |-              # 玩家退出
    方法.记录离线时间(player.uuid)
  chat: |-              # 聊天（注入 message 变量）
    (message == '签到')?{经济.发放(player.name, 100)}:0;
  death: |-             # 死亡
    方法.发送命令("title {player.name} title {\"text\":\"下次加油\"}")
  respawn: |-           # 重生
    Effect.给予(player.name, "speed", 30)
  tick: |-              # 全局每秒（无 player 注入）
    系统.刷新排行榜()
```

- `chat` 额外注入 `message`（聊天内容）
- `tick` 是全局每秒，不注入玩家上下文
- 单个页面挂了触发器就参与监听；脚本异常只 warn 不影响其他页面

### 7.0.4 页面栈与关闭通知

- 子页：`SUB_OPEN` 压栈显示在父页之上，ESC 逐层返回（`SUB_CLOSE` 弹栈）
- 客户端 ESC 关页会向服务端发 **page_close** 通知，服务端清理会话与容器绑定
- 就绪握手：进服完成资源同步后客户端发 `ready`，此后才接收页面下发

### 7.0.5 用 DreamLang 写自己的事件（Event 总线）

不用碰 Java，页面作者自己就能定义、发布、订阅自定义事件。
四个方法，全部支持中英别名（订阅/subscribe/on、发布/publish/emit）：

```yaml
Functions:
  open: |-
    # 订阅：参数用 Lambda（(参数) => { 块 }），返回订阅 id 可用于取消
    变量 订阅id = Event.订阅("boss_killed", (name) => {
      Toast.显示(name + " 被击杀了！")
    })
  close: |-
    Event.取消订阅(变量.订阅id)      # 页面关闭记得摘掉自己的订阅
```

任意地方触发（元素 actions、定时器、其他页面的脚本都行）：

```yaml
summon_btn:
  actions:
    click: "Event.发布('boss_killed', '凋零')"
```

- **发布时参数透传**给所有订阅者；单个订阅出错不影响其他订阅
- 总线是**进程内**的：客户端一个、服务端一个。同端跨页面/跨脚本通信用它；
  要跨客户端↔服务端请走 `Network.发送` / custom_packet 通道
- `Event.清空()` 不带参清空全部事件（慎用）

### 7.0.6 定时器族：延迟 / 循环 / 防抖 / 节流

配合事件总线，异步编排全齐了（返回任务 id，可 Script.取消）：

| 方法 | 行为 |
|---|---|
| `Script.延迟执行(ms, 脚本)` | 延迟一次 |
| `Script.计划执行(秒, 脚本)` | 每 N 秒循环 |
| `Script.防抖(ms, 脚本, 键?)` | 同键重置计时，安静后才执行（搜索输入场景） |
| `Script.节流(ms, 脚本, 键?)` | 周期内最多一次，周期末补跑合并尾调用 |

实战组合——"输入停顿 300ms 才搜索"：

```yaml
search_input:
  type: input
  input: "Script.防抖(300, \"Screen.刷新结果(text.value)\", 'search')"
```

### 7.0.7 双向自定义通道

```yaml
# 客户端订阅（页面脚本）
Network.订阅("myaddon:trade")
```
```java
// 服务端附属插件推送
CustomPacketRegistry.send(player, "myaddon:trade", payload);
```

配套 API 见《附属开发指南.md》；协议格式见《协议.md》。


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

**远程图片（http/https）**：首次引用时后台下载并缓存到 `OpenDreamCore/cache/http-cache/`（文件名 = URL 的 SHA-256 + 原扩展名），下载完成自动出现在页面上，之后秒开。
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
    condition: "player.level >= 10"   # 服务端编译期条件：不满足整体剔除（不可见/不可点）
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


---

## 12. 主题样式系统（v2 新增）

> 思路是三分：结构归页面、外观归主题、变化交给 transition。
> 全扁平语法（规则键 = 选择器本身）；嵌套 children 与扁平 parent 两种页面写法天然同享主题。
> 本章是技术规格；手把手教程、实战菜谱与常见坑见《主题系统Wiki.md》。

### 12.1 目录与加载

```
OpenDreamCore/themes/        主题目录（与 UI 同级，纳入热重载监听）
  default.yml               缺省主题（页面未声明 theme: 时自动生效）
  dark.yml
  festival.yml              换肤 = 换一个文件
```

- 文件名即主题名；`.yml/.yaml/.json` 均可，递归扫子目录
- 页面声明 `theme: <名字>` 显式选择；**支持逗号分隔多主题依次叠加**
  （`theme: default,server_patch`，后者只填前者没填到的属性，任一缺失静默跳过）；
  全部落空回退 default；连 default 都没有则不套任何主题（老行为）
- **热重载**：改 themes/ 下文件保存即触发重载（与页面同一条 WatchService 链路），
  重载后所有页面重新应用主题
- 服务端下发的页面同样套用**客户端本地**的主题库（页面下发的是 YAML 源文，
  在客户端构建时才查主题）；单机/多人行为一致
- 页面未命中任何主题时行为与旧版逐字节一致（opt-in 设计）

### 12.2 theme.yml 结构

三条硬规则：① 全文件无 `-`（一律 map 键）；② `vars:` 是唯一保留段；③ 属性名 camelCase 与组件属性一致。

```yaml
schema: 1                        # 语法版本

vars:                            # 样式变量，声明值里 {名字} 引用（支持整串引用保留原始类型）
  accent: "#71A4F4"
  panelBg: "#2C2C34DD"
  radiusLg: 8
  tBase: 0.25                    # 时长也可变量化，换肤连手感一起换

text:                            # tag 规则：该类型所有元素
  color: "{ink}"
  shadow: false

button:
  background: "{panelBg}"
  paddingAll: 3
  transition: "background {tBase} QUAD_OUT, transform 0.15 SINE_OUT"

"button:hover":                  # 状态伪类（含 : 的键加引号）
  background: "{accentSoft}"

".card":                         # class 规则：页面里 class: card 引用
  background: {color: "{panelBg}", radius: "{radiusLg}"}
  paddingAll: 10

".card > .title":                # 直接子代（后代选择器 ".modal .title" 同理）
  fontSize: 12
```

### 12.3 元素侧新键

```yaml
match: 龙核商店
theme: default                   # 可选，缺省 default

shop_card:
  type: layout
  class: card danger_btn         # 多类空格分隔
  style:                         # 内联样式块（优先级最高，仅填缺失属性；
    paddingAll: 20               #  style 键保留在 props 中维持导出回写保真）
```

- `class` / `style` 为元素新通用键；`theme` 为页面级选项
- 老页面不含这些键 → 零规则命中 → 行为不变

### 12.4 选择器全集

| 写法 | 含义 | 权重 |
|---|---|---|
| `tag` | 元素类型（`button:`） | 1 |
| `.name` | 类（`".card":`） | 10 |
| `#id` | 元素 id（`"#submit":`） | 100 |
| `A B` | 后代（任意层级） | 各段累加 |
| `A > B` | 直接子代 | 各段累加 |
| `S:pseudo` | 状态伪类（每个 +10） | 随段累加 |
| `A, B` | 多选（拆成多条规则共享声明序） | — |

- 含 `:` 或空格的选择器键必须加引号（`"button:hover"`）
- 复合段合法：`button.card:hover` = 同一元素同时满足三条件
- 解析失败的规则仅 warn 跳过，不拖垮整表
- 子代/后代对扁平 `parent:` 写法同样有效（祖先链在页面解析期已归一）

### 12.5 状态伪类

| 伪类 | 激活时机 |
|---|---|
| `:hover` | 鼠标在元素命中区内 |
| `:pressed` | 悬停且左键按下 |
| `:disabled` | `enabledWhen` 为假 |
| `:focus` | 元素持有键盘焦点（输入类组件 Tab/点击获得） |

- 多状态叠加：`"button:hover:pressed"`（需同时满足，权重累加）
- 状态规则不参与编译期级联合并，作为覆盖层随页面下发，客户端运行时按激活状态叠加取值；
  多层命中时声明序后者胜
- 运行时合并发生在渲染取值口（全组件无感知生效），不触发重排、不改写元素数据

### 12.6 级联优先级（从高到低）

| 层 | 说明 |
|---|---|
| 服务端 state_patch 运行时覆盖 | 运行时最高 |
| 元素内联 props（含 style: 展开） | 内联值永远不被主题覆盖 |
| `.class` 规则 | 权重 10 |
| `tag` 规则 | 权重 1 |
| 组件内置默认值 | 兜底 |

同权重按声明顺序后者胜。带状态伪类的规则不参与基础合并，
作为状态覆盖层随页面下发，由客户端在 hover/pressed/disabled 时叠加取值。

### 12.7 transition 过渡

语法：`transition: "<属性> <秒> [缓动] [延迟秒], ..."`

```yaml
coin_txt:
  type: text
  bind: {content: "vars.coin"}
  transition: "opacity 0.3 CUBIC_OUT"     # state_patch 推送时数字平滑淡变
```

- 缓动枚举：`LINEAR / SINE_IN_OUT / QUAD_IN / QUAD_OUT / QUAD_IN_OUT /
  CUBIC_IN / CUBIC_OUT / CUBIC_IN_OUT / EXPO_OUT / BACK_OUT / ELASTIC_OUT`（大小写不敏感，未知回退 LINEAR）
- 生效范围：任何来源的属性变化（内联改值 / state_patch 推送 / 状态切换 / visibleWhen 显隐）
- 可插值类型：数字、`#RRGGBB(AA)` 颜色；离散值过渡期满直接切换
- 实现层：纯渲染帧计算（取值口合并 + 插值），不触发布局重算、不改写元素模型
- 与 AnimationEngine 分工：transition 管「属性变化补间」，动画系统管「编排型动画」

### 12.8 流式布局（h_stack / v_stack 升级）

完整属性参考见 §3.2「流式弹性布局」。要点复述：

容器新增 `align / justify / wrap / reverse`；子元素新增 `grow / shrink / basis / alignSelf / absolute`。

**兼容保证**：容器与子元素均未声明上述属性时，走原始游标排布路径，行为与历史版本完全一致。

### 12.9 嵌套规则（CSS Nesting 风格）

规则内部可以继续写选择器，和平铺写法共存，解析结果完全等价：

```yaml
".card":
  background: {color: "{panelBg}"}
  ".title":          # = .card .title（默认后代组合）
    fontSize: 12
    color: "#ffffff"
  "> .icon":         # 子代组合，必须直接挂在 .card 下
    width: 16
```

- 组合符：默认**后代**（空格）；`>` 指**子代**；`&` 指代规则自身（`"&:hover"` 等同 `".card:hover"`）
- 嵌套层级不限，和扁平写法任选，同一份页面混着写也没问题
- 解析失败的规则仅 warn 跳过，不拖垮整表
  ">.badge":         # = .card > .badge（显式子代）
    x: -4
  "&:hover":         # = .card:hover（& 引用父选择器原文）
    background: "{accentSoft}"
```

- 嵌套深度上限 8 层；`&`/`>`/后代 三种组合可混用
- 与平写的 `.card > .title` 解析结果一致，两种风格随意混搭

### 12.10 响应式查询（@media 式）

条件是**窗口宽高像素**，每次布局重算现判现用——窗口拖动即时响应：

```yaml
"@max-width 854":              # 顶层形式：里面必须写选择器
  button:
    height: 14
".card":
  "@max-width 500":            # 规则内形式：直接写声明，目标=父选择器
    paddingAll: 4
```

- 四种原子条件：`min-width / max-width / min-height / max-height`，可任意组合
- CSS 风格同样认：`"@media (min-width: 400) and (max-height: 600)"`
- 嵌套在 @块 内的规则继承条件；@块 可嵌套 @块（条件取交集）
- **优先级**：命中的媒体覆盖压过普通规则与内联样式（响应式是刻意覆盖）；
  state_patch 的运行时写入发生得更晚，不受影响
- 支持改外观属性（颜色/文字/透明度/缩放等）；几何尺寸建议用表达式+弹性布局表达

### 12.11 状态覆盖层改布局属性

`:hover` 等状态的覆盖层里允许写 `x/y/width/height`：

```yaml
"input:focus":
  width: "{parent.width - 20}"
"card:hover":
  y: -2
```

客户端检测到状态变化且涉及布局属性时，自动请求一次重排（250ms 冷却防抖），
下一帧即按新布局渲染。颜色/文字类仍然走即时叠加通道，两者互不干扰。

### 12.12 兼容性与边界

- 老页面零改动零影响（无 class/theme 键 → 零规则命中 → 内联值优先级最高）
- 未知属性/未知选择器：warn 后跳过，不中断加载
- `:focus` 为预留位（键盘焦点基础设施就绪后开放，写了不报错也不生效）
- 根级布局属性（opacity/scale 等）由状态覆盖层改变时需一次重排才反映到定位；
  规格内颜色/文字类属性的运行时叠加是实时的
- 教程、实战菜谱与常见坑：《主题系统Wiki.md》

---

## 13. 旧格式适配器（DreamCore 兼容层）

龙核/DreamCore 老页面**丢进 `UI/` 目录即可直接运行**，无需转格式：

```yaml
# 这是一段完全合法的老页面，引擎会自动识别并翻译
match: "龙核菜单"
Functions:
  open: |-                          # 大写 F 的脚本块照常工作
    方法.异步执行方法('初始化')      # 零参裸调用自动补括号
背景:
  type: 'texture'                   # 旧类型名自动映射 → image
  x: "(w-背景.width)/1.8"          # w/h 简写、元素交叉引用照常
  texture: {src: "assets/bg.png"}   # 平铺参数自动收拢为 image.src
引导槽位:
  type: slot
  identifier: container_6           # 自动映射 → chest_slot.slot: 6
```

引擎做的事：特征指纹识别旧格式 → 类型/属性映射（Texture→image、alpha→opacity、
tip→tooltip、identifier container_N→slot…）→ 表达式改写（`界面变量.x = 1;` →
`Screen.设置变量("odc_ui_x", 1);`）→ 零参补括号 → 进入与新页面完全相同的标准管线。

- 数百个 `方法.*` 旧方法桥已内置（中英双名），按类别注册在适配层
- 完整的语法参考、改写规则明细、方法桥清单与限制：《梦想核心语法适配Wiki.md》


## 14. 视觉系统（十套配置）

> 把散在主题里的渲染规则抽成独立的规则文件，服务端打包下发，客户端照着渲。
附属接入见《附属开发指南.md》。

九类规则文件放 `plugins/OpenDreamCore/<System>.yml`（或同名文件夹，一文件一规则），
首启自动生成默认示例；服务端 ready 时打包下发客户端渲染。

### 14.0 统一匹配规范（五系统共用）

```yaml
match: 屠龙              # 一行走天下：名称或 lore 包含即命中
name: "〖传说〗屠龙"       # 可选：仅显示名包含
lore: 吸血                # 可选：任意一行 lore 包含
id: diamond_sword         # 可选：物品类型（逗号分隔多个）
nbt: {CustomModelData: 7000}
regex: "^神剑.*"           # 可选：显示名正则
```

多键同时出现 = AND；色码不影响匹配（自动去 §/& 码归一化）。

### 14.1 ItemIcon 物品图标

```yaml
贴图1:
  match: 测试材质1
  texture: icons/a.png    # 材质包路径；.gif 自动播放
  scale: 2
  held: true              # 应用于手持渲染
  center: true
```

### 14.2 ItemEffect 物品特效层

```yaml
流光:
  match: 神器
  layer: 前景               # 前景 | 背景
  texture: effects/flow.gif # GIF 自动播放
  frame_ms: 100
序列特效:
  match: 神剑
  frame_ms: 80
  frame1: fx/f1.png         # 编号帧键，数量不限
  frame2: fx/f2.png
```

### 14.3 HeadTag 实体头顶标签

一张锚定实体的完整 HUD 页面（组件/Functions/变量全支持）：

```yaml
entity: zombie             # 实体类型（逗号分隔多个）
name: 僵尸王               # 可选名称过滤
distance: 64
y: 1.5                     # 头顶偏移

血条底:
  type: rect
  x: -1
  y: 0
  width: 2
  height: 0.5
  color: "#AA000000"
血条:
  type: rect
  x: -0.88
  y: 0.11
  width: "2 * entity.health_ratio"   # entity.* 实时上下文
  height: 0.28
  color: "#FF55FF55"
```

每个命中实体一份独立页面实例（变量隔离）；距离外卸载、进入重建。

### 14.4 FontConfig 字体映射

```yaml
肝:                        # 键=字符：精确替换
  texture: fonts/1.png
  height: 8
  ascent: 8
数字组:                    # 区间批量：贴图横向等分给 0..9
  range: 0-9
  texture: fonts/digits.png
金色汉字:                  # 正则选字
  match: "[金银铜]"
  texture: fonts/metal.png
全局字体:
  ttf: fonts/font.ttf
```

### 14.5 ArmorLayer 盔甲图层

```yaml
烈焰头盔:
  id: leather_helmet
  name: 烈焰
  layer1: armor/d60009_1.png
  layer2: armor/d60009_2.png
  weight: 9999
```

### 14.6 KeyConfig 按键组合

配置名 = ID（不重复，语义自明）；`keys` = 触发按键（列表，可为组合键 `Ctrl+左键` 这种）；
`commands` = 触发命令（`[op]`=临时OP执行 / `[Console]`=控制台 / 无前缀=玩家身份）；
进阶字段 `cooldown`（冷却秒）/ `when`（DreamLang 条件）/ `fail_message`（提示）/ `script`（DreamLang 脚本）平铺：

```yaml
复活绑定:
  keys:
    - "R"
  commands:
    - "[op]core sound %player% 1.ogg 1 1 false"

状态不足提示:
  keys:
    - "Ctrl+左键"          # 组合键：Ctrl/Shift/Alt 驼峰 + 主键；一个 ID 可列多组键
  cooldown: 1
  fail_message: "状态不足"
  when: "player.health >= 1 && player.food >= 1"
  script: |
    Title.显示("&6欢迎使用", "&aOpenDreamCore", 10, 20, 10)
```

组合键的修饰键写 `Ctrl`/`Shift`/`Alt`（也认 `C`/`CTRL`/`LEFT_SHIFT` 等老写法，顺序无关）；
老写法（键名直接是按键组合串，如 `R:` 或 `C+左键:`）依然能解析，存量配置不用改。
命令字段就叫 `commands`（老形态也一直是这个名），写 `trigger` 的旧配置同样兼容。

### 14.7 Sounds 音效库

```yaml
example:
  file: sounds/custom/example.ogg
  range: 16          # 可听格数
  volume: 1.0
  pitch: 1.0
```

### 14.8 WorldTexture 世界贴图

```yaml
传送阵:
  world: world
  x: 55
  y: 70
  z: 50
  rotate_y: 0
  texture: textures/lz.png   # .gif 自动播放
  frame_ms: 80
  width: 20
  height: 20
  alpha: 1.0
  glow: true                 # 自发光
  through_wall: false        # 穿墙可见
```

### 14.9 SlotConfig 自定义槽位

chest_slot 组件 `slot: 名字` 引用；放入/取出时服务端自动校验：

```yaml
药水槽1:
  lore_contains: "[类型]药水"

吊坠槽:
  lore: 吊坠
  permission: essentials.use
  level: 10
  attribute: true            # 属性插件兼容钩子透传（未装则忽略）
  skin: true                 # 时装兼容
```

### 14.10 ItemTip tooltip 迷你页面

`ItemTip/` 目录下一文件一条规则；**文件本体就是标准页面**（组件/Functions/
变量/bind/主题全部支持），追加少量专属键：

```yaml
match: 屠龙                 # 统一匹配规范
shift: any                  # any | pressed | released
priority: 100
x: 10                       # 相对鼠标偏移
y: -7
keep_vanilla: false         # true = 原版 tooltip 叠画在下层

标题:
  type: text
  x: "mouse.x + 8"
  y: "mouse.y + 6"
  color: "#FFD54F"
  content: "〖传说〗{item.name}"
词条:
  type: text
  x: "mouse.x + 8"
  lines: "{item.lore}"
```

tip 专属上下文：`mouse.x / mouse.y / item.name / item.type / item.lore(行数组)`。

---

*文档结束。*
### 14.11 实体组件（`type: entity` / `type: model`）

GUI 里渲染 3D 实体——`entity` 渲染实时实体（玩家背包模型那种看鼠标转的），
`model` 纯模型展示（不绑实体数据，省资源）。渲染内核同一套，解析层分开。

```yaml
角色:
  type: entity
  entity: owner              # 渲染谁：owner（自己）/ 玩家名 / 实体UUID / {player.*} 占位符 / 表达式
  model: player              # 用哪套模型画：player / armor_stand / 原版生物类型
  followMouse: true          # 身体跟着鼠标转（原版背包模型手感）
  head: true                 # 头部额外跟随
  hideName: true             # 隐藏名牌
  scale: 1.0                 # 缩放，支持 DreamLang 表达式
  rotateX/Y/Z: 0             # 额外旋转（度），支持表达式
  alpha: 1.0                 # 透明度
  tip: "点击查看详情"         # hover 提示
  cooldown: 1                # 点击冷却（秒）
  Functions:
    tick: "变量.旋转 += 1"    # 每帧驱动 rotateY
    onClick: 'Title.显示("&6", "&a点到了", 10, 20, 10)'

展示柜:
  type: model                # 纯展示：只画模型 + 姿态
  model: zombie
  followMouse: true
  scale: 1.5
```

- 字段都支持 DreamLang 表达式与占位符（`scale: "player.level > 10 ? 1.5 : 1.0"` 这种）
- 点击走统一事件管线：`Functions.onClick` 触发脚本，服务端会话下上报事件
- 12 个 target（fabric/forge/neoforge × 各 MC 版本）渲染 API 各版本不同，桥按版本实现：
  1.20.1 走七参 renderEntityInInventory、1.21.1/4 走带偏移八参、1.21.8/11 走 FollowsMouse，
  实机效果一致（鼠标跟随由原版内部完成）
- 26.1.2 的提取式渲染管线（GuiGraphics→GuiGraphicsExtractor）在专属适配里跟进，
  当前组件静默不渲染、不炸页面，不影响其它功能
## transforms：ConfigIR 声明式变换（4.5）

页面顶层可写 `transforms` 列表，在页面建模之前用 DreamLang 表达式批量改写任意配置路径。适合做"按环境/版本铺量"的配置加工——写进文件就是声明式 DSL，改文件即热重载重跑。

```yaml
# 页面级
transforms:
  - field: variables.gold        # 点路径，改哪（变量表/options 等任意 map 路径）
    set: "value * 2"             # DreamLang 表达式，求值结果写回该路径
    when: "vars.doubleGold"      # 可选：条件为假/0 就跳过本条
  - path: options.hud.baseline   # field 的别名（二选一）
    expr: "'1920x1080'"
```

字段说明：

| 键 | 必填 | 说明 |
|---|---|---|
| `field` / `path` | 是 | 要改写的点路径，如 `variables.gold`、`options.hud.baseline`；中间层会自动补 map |
| `set` / `expr` | 是 | DreamLang 表达式，求值结果写回该路径 |
| `when` | 否 | 条件表达式，`false`/`0` 跳过本条 |

表达式上下文：

- `value`：路径当前值
- 页面 `variables`：裸名（`gold`）或 `vars.gold` 都行
- 顶层非标准键（extras 透传）：裸名可读

`transforms` 键在处理完自动摘除，不会进变量表/元素表。
