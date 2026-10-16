# 动画 API — 附属插件开发指南

给任意实体的骨骼动画点火，三行代码起步。

## 依赖

compileOnly 'com.opendreamcore:opendreamcore-plugin:0.2.1'
```

## 入口

```java
import com.opendreamcore.api.OpenDreamCoreAPI;

// 点火控制器里配的触发（Animation/*.yml 的 triggers 节）
OpenDreamCoreAPI.anim().trigger(entity, "cast");

// 直接播一段（动画名 = Animation/ 下相对路径去后缀）
OpenDreamCoreAPI.anim().play(entity, "walk");

// 撂在指定层上（层 = 控制器 layers 节的名字）
OpenDreamCoreAPI.anim().play(entity, "buff", "特效");

// 停触发面（状态判定照常跑，base 不受影响）
OpenDreamCoreAPI.anim().stop(entity);
```

玩家自己（身边没别人也保证本人收到）：

```java
OpenDreamCoreAPI.anim().trigger(player, "dance");
```

返回值：`trigger/play/stop`（实体版）返回收到的观看者数量，0 = 没人看（48 格内无玩家），
消息没白发但也没人播；玩家版返回是否送达。

## 控制器怎么配（Animation/ 下任一 yml）

```yaml
default: idle

states:
  idle:   { anim: idle }
  walk:   { anim: walk, when: moving, transition_time: 0.15 }
  attack:
    variants: [attack_1, attack_2]
    when: swinging
    once: true
    next_transition_time: 0.2
    priority: 10
  death:  { anim: death, when: dead, loop: hold }

layers:
  base: { blend_type: OVERRIDE, weight: 1.0 }
  特效: { blend_type: ADDING, weight: 0.6 }

triggers:
  cast:
    layer: 特效
    anim: "cast_start > cast_loop"   # 队列：起手播完自动进循环
    transition_time: 0.15
```

`triggers` 里的名字就是 `AnimationAPI.anim().trigger(entity, 名字)` 的名字。
龙核写法（`Layer:`/`Trigger:` 节）的 yml 直接放同目录，自动识别，不用改一个字。

## 触发名之外的直播通道

不想配触发、就想播一段：`play(entity, "动画名")`。动画名来自 Animation/ 文件夹
（子文件夹算路径，比如 `Animation/小火龙/idle.json` 里的 `animation.model.idle`
既可以用 `小火龙/idle` 也可以用 `animation.model.idle` 引用，两个名字都认）。

## 骨骼名

动画打骨骼名；内置标准人形骨架 + 别名层让三家方言互通：
`leftforearm`（龙核）/ `left_fore_arm`（bedrock 风格）/ `leftForeArm` 命中同一根骨。
模型里没有动画要的骨，该通道跳过，不报错。

## 事件与注意事项

- 触发是客户端表现，服务端不需要知道控制器细节；传不存在的触发名客户端安静忽略。
- 同名触发重复点火会从头重播（重新计时）。
- `play` 直播段会覆盖当前 base 动画，直到状态判定再挑中别的状态。
- 插件未加载时所有调用安全返回默认值（false/0），不用判空。
