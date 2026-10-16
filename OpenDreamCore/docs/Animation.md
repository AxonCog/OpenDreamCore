# Animation 动画/动作系统

一个文件夹管全部：`Animation/`。递归扫描，规则就一条——**`.yml` 是控制器，其余全是动画本体**。
子文件夹随便建、想混放就混放，相对路径（去后缀）就是名字。

```
Animation/
├── *.json / *.bmd            # 动画本体：裸放就认，一文件多动画也行
├── <自建子文件夹>/            # 想分类就建文件夹（动画名带路径前缀，如 小火龙/idle）
└── controller/               # 收纳建议（不特判）：控制器 yml 放这里好找
    ├── 人形.yml               # 原生写法
    └── 龙核写法示例.yml        # 龙核 Layer/Trigger 原格式，拷来即用
```

文件夹为空时自动播三份示例（只补缺，永不覆盖已有文件）。

## 控制器（双语法，每个文件独立选择）

**原生写法**（完整能力）：

```yaml
default: idle

states:
  idle:   { anim: idle }
  walk:   { anim: walk, when: moving, transition_time: 0.15 }
  attack:
    variants: [attack_1, attack_2]   # 随机挑一条
    when: swinging                    # moving|swinging|sneak|swimming|dead|riding
    once: true                        # 播完自动回 default
    priority: 10                      # 播到 95% 前低优先级抢不动（短动作锁）
  death:  { anim: death, when: dead, loop: hold }

layers:
  base: { blend_type: OVERRIDE, weight: 1.0 }
  特效: { blend_type: ADDING, weight: 0.6 }

triggers:
  cast:
    layer: 特效
    anim: "cast_start > cast_loop"    # 队列：> 串起来，播完一条下一条
    transition_time: 0.15
```

**龙核写法**：根上有 `Layer:`/`Trigger:` 节就自动按龙核语法认领，字段原样映射
（blendType/weight/animations 队列/next/nextTransitionTime/lockBodyAngle），
从龙核拷来的文件零修改直接跑。两种写法可混用，识别不了会给一条明确报错。

## 状态判定引擎

- 条件求值每 3 tick 一次，姿态每帧推进（省算力给渲染）
- `once` 状态播完自动回 `default`；播放中（<95% 进度）低优先级状态抢不动，高优先级（如死亡）随时打断
- `variants` 每次进入随机抽一条（种子可复现）
- 切状态带过渡（`transition_time`，缺省走全局默认）

## 骨骼与别名

内置标准人形骨架（body/head/四肢二段式），骨骼名先规范化（小写、去 `_`/空格）再查表：
`leftforearm`（龙核）/ `left_fore_arm` / `leftForeArm` 命中同一根骨。查不到的骨跳过该通道，不报错。

## 动画来源

- bedrock json（bbmodel 内嵌动画自动进池）、BMD 帧动画
- 动画名双注册：文件路径名（`小火龙/idle`）+ 文件内 `animation.x.y` 键，两个名字都能引用
- 服务端下发的动画最后落位，覆盖本地同名

## 插件 API（附属开发见 docs/Animation-API.md）

```java
OpenDreamCoreAPI.anim().trigger(entity, "cast");   // 点火 triggers 里的条目
OpenDreamCoreAPI.anim().play(entity, "walk");      // 直播一段
OpenDreamCoreAPI.anim().stop(entity);              // 停触发面
```

触发经 `odc:anim` 通道下发到客户端，按实体 id 分发到动画引擎。

## 玩家动画通路（原版六部件叠加）

玩家自身模型也能跑同一套动画：mixin 注入玩家模型 setupAnim 尾部，姿态**叠加**（不替换、不清理），
无动画时零接触，不与其他玩家动画模组冲突。三套接线按 MC 版本分：
`gensrc/playeranim-1.20-1.21.8`（EntityRenderState）、`playeranim-1.21.11`（AvatarRenderState）、
`playeranim-26.1.2`。条件取自渲染状态：walkAnimationSpeed=走、attackTime=挥、deathTime=死。

## 萌芽动作包（adapter/germ）

「通常动作.yml」这类**动作包**直接扔进 Animation/：
- 条目键 = 触发名 = 动画名（前翻滚/左翻滚/右翻滚…）
- `speed`（倍速）`duration`（毫秒）收进触发；`action:`/`subject:` 不翻也不报错
- 原生写法（default/states）与龙核写法（Layer/Trigger）与动作包三形态可混用，
  按内容嗅探，认不出给一条明确报错

## 触发链

| 入口 | 用法 |
|---|---|
| 按键 | KeyConfig.yml 规则里 `anim: 前翻滚`（与 commands/script/run/event 并列） |
| 命令（服） | `/odc anim play\|trigger <玩家> <动画或触发名>`；`/odc anim stop <玩家>` |
| 命令（单机） | `/codc anim <触发名>` 点火、`/codc anim off` 停 |
| API | `OpenDreamCoreAPI.anim().play/trigger/stop(player, name)` |
| 脚本 | `EventBus.publish("事件名", 玩家名, 规则id)`（KeyConfig `event:` 键发布） |

服务端命令只对**已握手**的客户端生效（身边无广播接收者时返回失败并提示）。
