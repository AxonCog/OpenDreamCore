package com.opendreamcore.visual;

/**
 * 十系统的默认示例模板。
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
            # 存放：visual/HeadTag/ 文件夹下一文件一规则（文件名=规则 id）；
            # 旧版单文件 visual/HeadTag.yml 依旧可用（整文件一条规则）
            # 全部页面能力可用：Functions / 变量 / bind / 任意组件
            # 实时变量：name / health / health_max / health_ratio(0~1) / entity_height

            entity: zombie              # 实体类型（逗号分隔多个）
            contains: 僵尸              # 可选：名称包含过滤（旧键 name 等效）
            distance: 64                # 可见距离（格）
            offsetX: 0                  # 可选：横向偏移（格，billboard 左右）
            offsetY: 0                  # 可选：纵向偏移（格；旧键 y 等效）

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
              width: "2 * health_ratio"      # 随血量实时伸缩（0~1）
              height: 0.28
              color: "#FF55FF55"
            """;

    public static final String BLOOD = """
            # 纯血条（Blood）：锚定实体头顶的血量条（独立系统，与 HeadTag 共用头顶渲染管线）
            # 存放：visual/Blood/ 文件夹下一文件一规则（文件名=规则 id）；
            # 旧版单文件 visual/Blood.yml 依旧可用（整文件一条规则）
            # 实时变量：name / health / health_max / health_ratio(0~1) / entity_height
            # 同一实体 HeadTag 与 Blood 同时命中时 HeadTag 优先（避免两层叠加）

            entity: "*"                # 实体类型（逗号分隔多个；* 或缺省 = 全部生物）
            contains:                  # 可选：名称包含过滤（旧键 name 等效；留空=不过滤）
            distance: 32               # 可见距离（格）
            offsetX: 0                 # 可选：横向偏移（格，billboard 左右）
            offsetY: 0                 # 可选：纵向偏移（格；旧键 y 等效）

            血条底:
              type: rect
              x: -1
              y: 0
              width: 2
              height: 0.5
              color: "#AA000000"

            血条前景:
              type: rect
              x: -0.88
              y: 0.11
              width: "2 * health_ratio"    # 动态宽度：随血量比例实时伸缩（0~1）
              height: 0.28
              color: "#FF55FF55"

            血条数值:
              type: text
              x: 0
              y: 0.55
              text:
                content: "{health}/{health_max}"   # 可选：整段删除即隐藏数值
            """;

    public static final String FONT_CONFIG = """
            # 字体映射：键名即字符；区间与正则用于批量替换
            #
            # 这份文件同时是「本地自定义」的载体：解析规则时它最后合并，键名与服务端下发的一致
            # 就会覆盖服务端那条。所以默认不放开任何规则——示例一旦生效就会盖掉服务器上真正
            # 在用的贴图（示例里的 fonts/1.png 通常并不存在），表现为聊天里反复提示「字形贴图
            # 未就绪」，替换整个失效。要用示例自己去掉注释。

            # 肝:                       # 生僻字示例：单字符精确替换
            #   texture: fonts/1.png
            #   height: 8
            #   ascent: 8
            #   # fps: 12              # 可选：gif 贴图的播放帧率（不写就用 gif 自带帧间隔）
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

    public static final String CAMERA = """
            # 相机运镜规则：shot=固定机位 | path=关键帧路径 | free=自由相机
            # 服务端下发（visual/Camera.yml）或本地 /codc reload（gameDir/OpenDreamCore/visual/Camera.yml）
            # 播放：/odc camera play <id> ｜ 停止：/odc camera stop ｜ 状态：/odc camera status
            # 本文件只定义"有哪些运镜"，不自动播放
            # 字段缺省即安全：不写 lock_input 就是不锁、不写 player 就是 keep

            固定机位_城门:
              type: shot                      # 固定机位
              position: [128.5, 70.0, -32.5]  # 世界坐标（支持表达式写法，M1.3 起求值）
              rotation: [90.0, 15.0]          # yaw, pitch（与 look 二选一，look 优先）
              # look: 实体:剧情NPC_01         # 可选：看向目标（M1.4 起求值）
              perspective: third_person_back  # first_person | third_person_back | third_person_front
              player: hide_body               # hide_body=假人模式 | follow=随本体 | keep
              transition: [20, 40]            # 进/出过渡 tick
              lock_input: true                # 锁玩家移动/视角/交互
              hide_hud: true                  # 隐藏原版 HUD
              shake: [0.0, 0]                 # [强度, 时长tick]（M1.5 起生效）
              stretch: 1.0                    # 视野拉伸倍率（M1.5 起生效）
              interrupt: [damage, move]       # 打断策略（M1.5 起生效）
              players: "@a[distance=..32]"    # 目标玩家（原样保留，服务端裁决时解析）

            运镜_两点直线:
              type: path                      # 最简路径：两点一线，播完自动停并恢复相机
              duration: 6.0                   # 秒（不写则取末帧 t）
              speed: 1.0                      # 时间轴倍速（>1 更快）
              smooth: false                   # catmull-rom 平滑（M1.3 起生效；false=线性）
              keyframes:
                - t: 0.0
                  pos: [100, 72, 0]           # 起点 A
                  rot: [0, 0]
                - t: 6.0
                  pos: [120, 72, 20]          # 终点 B
                  rot: [45, 0]

            运镜_开场:
              type: path                      # 关键帧路径（多点）
              duration: 8.0                   # 秒
              loop: false
              speed: 1.0
              smooth: true                    # catmull-rom 平滑（false=线性，M1.3 起生效）
              transition: [10, 20]            # 进/出过渡 tick（过渡不占用 duration）
              keyframes:
                - t: 0.0
                  pos: [100, 72, 0]
                  rot: [0, 0]
                  fov: 70
                - t: 3.0
                  pos: [110, 74, 10]
                  rot: [45, -10]
                - t: 8.0
                  pos: [120, 68, 20]
                  look: 实体:国王              # M1.4 起求值；本期靠上一帧的 rot 延续朝向

            运镜_四点环绕:
              type: path                      # 四点方角：验 M1.3 曲线（拐角被拉圆）与不抖
              duration: 12.0                  # 秒
              loop: false
              speed: 1.0
              smooth: true                    # true=Catmull-Rom（缺省就是 true，写出来是给读配置的人看）
              transition: [10, 20]
              keyframes:
                - t: 0.0
                  pos: [120, 74, 10]          # 绕 (110, 74, 10) 转一圈，半径 10
                  rot: [90, -5]               # 朝心：yaw 90 = 面向 -X
                - t: 4.0
                  pos: [110, 74, 20]
                  rot: [180, -5]
                - t: 8.0
                  pos: [100, 74, 10]
                  rot: [270, -5]
                - t: 12.0
                  pos: [110, 74, 0]
                  rot: [0, -5]                # 360 ≡ 0：最短角一路 +90，不会反向绕圈
            """;

    private static final String ENTITY_MODEL = """
            # 实体模型替换：把生物/玩家/盔甲架换成自己的模型（本地模板 = _local_override，最后合并、覆盖服务器同名规则）
            # 基础目录两处都扫（子文件夹随建）：OpenDreamCore/assets/model 与 resourcepacks/OpenDreamCore
            # 写在 resourcepacks 里的包建议开 pack_format 字段，否则部分加载器会忽略整个包
            # path 写路径不带扩展名：bbmodel/rpg_skeleton、geo/creeper、obj/cube
            # 裸文件名也认：path: 刺锤.bbmodel 直接命中同目录同名文件
            # 贴图默认取模型同目录同名 .png；没有图就是纯色（模型照样能画出来）
            # 下面两条是真生效的示例（苦力怕 + 羊）；停用就删掉整块或给键名加前导 #
            # 实时变量与调试：/odc model rules 看已加载规则，/odc model formats 看可用格式

            示例苦力怕:
              match: "Type:minecraft:creeper"     # 实体类型（minecraft: 前缀可省）；按自定义名就写 Name:xxx
              path: geo/creeper
              scale: 1                            # 缩放大小（默认 1 倍）
              # texture: creeper                  # 指定贴图（可选）
              # y_offset: 0                       # 垂直偏移（格）
              # render_distance: 64               # 超出距离回落原版模型（省性能）
              # hide_name: true                   # 是否隐藏头顶原版名称
              # glow: false                       # 发光描边

            示例羊:
              match: "Type:minecraft:sheep"
              path: bbmodel/rpg_skeleton
              scale: 0.6

            # 多条件用 ; 连接 = 全部满足才生效；块式写法与单行等价，二选一：
            # 烈焰之王:
            #   match: "Type:minecraft:blaze;Name:烈焰之王"
            #   path: bbmodel/hz1
            # 村民僵尸:
            #   match:
            #     type: minecraft:zombie
            #     name: 村民僵尸
            #   path: bbmodel/rpg_zombie1
            """;

    private static final String ITEM_MODEL = """
            # 物品模型替换：手持/掉落/展示框里的物品按条件换模型（本地模板 = _local_override，最后合并）
            # path 同 EntityModel（assets/model 与 resourcepacks/OpenDreamCore 两处都扫，带不带扩展名都行）
            # 背包/物品栏的 gui 图标替换走 CDM 资源包通道（需要 cmd 编号），不是这个文件
            # 下面一条是真生效的示例（钻石剑换成方块模型）；停用就删掉整块或给键名加前导 #
            # 实时变量与调试：/odc model rules 看已加载规则

            示例钻石剑:
              match: "ID:diamond_sword"           # 物品 id + 可选自定义名：ID:diamond_sword;Name:幽冥剑
              path: obj/cube
              scale: 0.5
              # texture: sword                    # 指定贴图（可选）
              # held: true                        # 手持时替换（默认 true）
              # gui: true                         # 背包/物品栏图标替换
              # cmd: 1001                         # CDM 编号（gui 通道用）
              # Lore: 幽冥                        # 只看 Lore 就写 Lore:xxx；NBT:{TAG} 精确匹配
            """;;

    /** 全部系统的默认模板（system 名 → YAML）。 */
    public static java.util.Map<String, String> all() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("ItemIcon", ITEM_ICON);
        m.put("ItemEffect", ITEM_EFFECT);
        m.put("HeadTag", HEAD_TAG);
        m.put("Blood", BLOOD);
        m.put("FontConfig", FONT_CONFIG);
        m.put("ArmorLayer", ARMOR_LAYER);
        m.put("KeyConfig", KEY_CONFIG);
        m.put("Sounds", SOUNDS);
        m.put("WorldTexture", WORLD_TEXTURE);
        m.put("SlotConfig", SLOT_CONFIG);
        m.put("Camera", CAMERA);
        m.put("EntityModel", ENTITY_MODEL);
        m.put("ItemModel", ITEM_MODEL);
        return m;
    }
}
