package com.opendreamcore.visual;

/**
 * 九系统的默认示例模板。
 *
 * 每个系统首次使用时自动落盘一份——注释写全取值范围，
 * 用户照着改就能跑，不用翻文档。键名与解析器严格同步。
 */
public final class VisualTemplates {

    private VisualTemplates() {
    }

    public static final String ITEM_ICON = """
            # 物品图标替换：match 一行走天下（名称或 lore 包含即命中）
            # 需要精确时追加可选键：name / lore / id / nbt / regex（可叠加=同时满足）

            贴图1:
              match: 测试材质1
              texture: icons/amethyst_flail.png     # 材质包相对路径
              scale: 2                              # 放大倍数
              center: true                          # 手持时屏幕居中
              held: true                            # 应用于手持渲染

            神剑:
              match: 神剑
              id: diamond_sword                     # 精确限定类型（多个用逗号）
              regex: "^神剑.*"                       # 名称正则
              texture: icons/gold.png
            """;

    public static final String ITEM_EFFECT = """
            # 物品特效层：前景/背景叠加在物品图标上
            # texture 写 .gif 自动按 GIF 播放；多帧序列用 frameN 编号键

            流光:
              match: 神器
              layer: 前景                # 前景 | 背景
              texture: effects/flow.gif  # GIF 自动播放
              frame_ms: 100              # gif 换帧间隔（静态图忽略）
              width: 25
              height: 25

            序列特效:
              match: 神剑
              frame_ms: 80               # 统一换帧间隔
              frame1: fx/f1.png          # 编号帧键，数量不限
              frame2: fx/f2.png
            """;

    public static final String HEAD_TAG = """
            # 实体头顶标签：一张锚定实体的完整 HUD 页面
            # 全部页面能力可用：Functions / 变量 / bind / 任意组件

            entity: zombie              # 实体类型（逗号分隔多个）
            name: 僵尸王                # 可选：名称包含叠加过滤
            distance: 64                # 可见距离（格）
            y: 1.5                      # 头顶锚点偏移

            Functions:
              tick: |-
                Screen.设置元素("名字", "text.content", 方法.取实体名)

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
            """;

    public static final String FONT_CONFIG = """
            # 字体映射：键名即字符；区间与正则用于批量替换

            肝:                         # 单字符精确替换
              texture: fonts/1.png
              height: 8
              ascent: 8
              # fps: 12                # 可选：gif 贴图的播放帧率（不写就用 gif 自带帧间隔）

            数字组:                     # 区间批量：0-9 逐字符替换，贴图横向等分
              range: 0-9
              texture: fonts/digits.png
              height: 12
              ascent: 10

            金色汉字:                   # 正则选字：每个命中的字符都生成替换
              match: "[金银铜]"
              texture: fonts/metal.png
              height: 8

            全局字体:                   # TTF 模式
              ttf: fonts/font.ttf
            """;

    public static final String ARMOR_LAYER = """
            # 盔甲图层：按物品+名称匹配换盔甲贴图层

            烈焰头盔:
              id: leather_helmet        # 物品 id
              name: 烈焰                # 可选：名称包含
              layer1: armor/d60009_1.png
              layer2: armor/d60009_2.png
              weight: 9999              # 同物品多条规则时权重决胜
            """;

    public static final String KEY_CONFIG = """
            # KeyConfig：配置名 = ID（不要重复）；keys = 触发按键（列表，可为组合键 Ctrl+左键）；
            # commands = 触发命令（[op]临时OP / [Console]控制台 / 无前缀玩家身份）
            # 进阶：cooldown 冷却（秒）/ when 条件 / fail_message 提示 / script 脚本

            复活绑定:
              keys:
                - "R"
              commands:
                - "[op]core sound %player% 1.ogg 1 1 false"

            状态不足提示:
              keys:
                - "Ctrl+左键"
              cooldown: 1
              fail_message: "状态不足"
              when: "player.health >= 1 && player.food >= 1"
              script: |
                Title.显示("&6欢迎使用", "&aOpenDreamCore", 10, 20, 10)
            """;

    public static final String SOUNDS = """
            # 音效库：SoundAPI.播放(玩家, "example") 即可触发

            example:
              file: sounds/custom/example.ogg   # 材质包内相对路径
              range: 16                          # 可听范围（格）
              volume: 1.0
              pitch: 1.0
            """;

    public static final String WORLD_TEXTURE = """
            # 世界贴图：声明式单图世界全息

            传送阵:
              world: world
              x: 55
              y: 70
              z: 50
              rotate_y: 0
              texture: textures/lz.png    # .gif 自动播放
              frame_ms: 80
              width: 20
              height: 20
              alpha: 1.0
              glow: true                  # 自发光（夜间清晰可见）
              through_wall: false         # 是否穿墙可见
            """;

    public static final String SLOT_CONFIG = """
            # 自定义槽位：chest_slot 组件 slot: 名字 引用；
            # 服务端放入/取出时自动校验 limit 条件（全部满足才放行）

            药水槽1:
              lore_contains: "[类型]药水"      # 条件直接平铺，无容器包裹

            吊坠槽:
              lore: 吊坠                       # 必须含该行 lore
              permission: essentials.use       # 需要权限
              level: 10                        # 需要等级
              attribute: true                  # 属性插件兼容钩子透传
              skin: true                       # 时装兼容
            """;

    /** 全部系统的默认模板（system 名 → YAML）。 */
    public static java.util.Map<String, String> all() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("ItemIcon", ITEM_ICON);
        m.put("ItemEffect", ITEM_EFFECT);
        m.put("HeadTag", HEAD_TAG);
        m.put("FontConfig", FONT_CONFIG);
        m.put("ArmorLayer", ARMOR_LAYER);
        m.put("KeyConfig", KEY_CONFIG);
        m.put("Sounds", SOUNDS);
        m.put("WorldTexture", WORLD_TEXTURE);
        m.put("SlotConfig", SLOT_CONFIG);
        return m;
    }
}
