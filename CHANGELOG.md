# 更新日志

## v0.1.2.2（2026-09-29）

这一版拖得有点长。原本只是想把实机反馈的「开光影之后东西变半透明」修掉，结果顺着线头一路拽到反射底座，把字形、名牌、物品图标、材质包注入全翻了一遍。下面按方向分组写清「原来什么样、为什么、现在什么样」，方便对号入座。

**顺带说一句：0.1.2.2 是 0.1.x 的最后一个版本。** 0.1 这条线从开源起干的就是一件事——把老架构底座搬完：16 个版本一条共享核心、页面格式与自研协议对齐、DreamLang 脚本能力对齐、渲染语义对齐。到这一版为止，该搬的都搬完了，剩下的是把它跑稳。**下一个版本号是 0.2.x**，从那儿开始是在已经搬完的地基上往前走，方向不变，包袱更轻。

### 光影（Iris / Oculus）共存：世界渲染不再穿透

开光影之后物品、生物、粒子、水体「部分透明」，不是画错了，是状态和时机的账：

- **GL 状态只写不回**：面板渲染器进场时开混合、关背面剔除、关深度写入，出场却**无条件写成固定值**（depthMask(true) / enableCull / disableBlend），而不是还原进场前的原值。原版在这些阶段本来就处于「不写深度」的状态，被我们一改，后面的半透明几何就跟深度缓冲对不上了。现在改成进出一对一对齐：进场先快照，出场按快照还原，中途出异常也走 finally 还原。
- **高版本上「关深度」其实从来没生效**：`RenderSystem` 的 enableBlend / disableDepthTest / depthMask / enableCull / defaultBlendFunc / setShaderColor 这一批在 1.21.8 / 1.21.11 / 26.1.2 上**已经被拆掉**（实测命中 0；1.20.1 / 1.21.1 / 1.21.4 还在），而我们的调用是「找不到就静默跳过」，于是高版本上 billboard 一直在写深度，把水、粒子、实体挡出个半透明。现在每个目标各写一份 GL 状态桥（`GlStateBridge` + 12 份实现）直调各自时代还在的 API，保存和恢复都走它。
- **渲染时机错位，还冲掉了原版批次**：12 个目标里原来大半挂在中段（fabric-1.21.11 更早，挂在 `GameRenderer.renderLevel` 的 HEAD）。在这些时机对共享的 `bufferSource()` 调 endBatch()，会把原版正在攒的实体/粒子批次一起结掉。现在 12/12 统一到帧末等价阶段（fabric 系 `WorldRenderEvents.LAST` / `END_MAIN` / `AFTER_SOLID_FEATURES`；forge 与 neoforge 系 `RenderLevelStageEvent.Stage.AFTER_LEVEL`）。
- **不再用 `RenderType.gui()` 画世界里的东西**：世界调用链里混进 GUI 渲染类型，光影按 RenderType 分拣 draw call 时会把它归错档。现在世界链里 `RenderType.gui()` 命中 0（名牌背景改用 `textBackgroundSeeThrough()`）。1.21.6 起（1.21.8 / 1.21.11 / 26.1.2）改走自建渲染类型 + 管线化绘制；1.21.4 及以下保持立即模式——这是刻意的，老版本没那套管线抽象，硬套更容易出问题。
- **穿透模式（`depthMode: always` 与 transparent 的第二遍）在高版本上原本是空转**：这两趟要的是「整趟不参与深度测试」，而这恰好落在被拆掉的那批状态 API 上——高版本里 `disableDepthTest()` 已是空操作，于是「穿墙显示」和「隔墙残影」在 1.21.8 / 1.21.11 / 26.1.2 上根本没发生。根子在我们一直以为「渲染管线表达不了深度测试函数」就绕开了它，实测不成立：1.21.8 / 1.21.11 有 `DepthTestFunction.NO_DEPTH_TEST`，26.1.2 有 `CompareOp.ALWAYS_PASS`，而自建管线本来就在设 `withDepthTestFunction`。现在补了一条关掉深度测试的管线变体（`WORLD_TEXTURED_NO_DEPTH`）并单独登记，穿透趟也走渲染类型；常规趟仍是 LEQUAL + 不写深度（照常被方块挡住、也不在深度缓冲里留痕）。两种变体的渲染类型分开缓存，避免同张贴图在「穿透」和「常规」之间串味。

### 反射底座换血：按 MC 名反射全部改成直调

这一版最值钱的其实是这块。先说两个被它害惨的实机症状：

- **每帧刷屏 `GL_INVALID_ENUM: <mode> is not a valid polygon mode`（单次会话 2420 条），世界几何整片不出现**：`setShaderTexture(int, int)` 在 1.21.1 上已经移除，我们的「按参数形状兜底」就按「两个 int」去猜，猜中了同形的 `polygonMode(int, int)` 并且**真的调了**——采样器和纹理 id 被当成 face 和 mode 传进 GL。修法是给解析器立规矩：`resolveMethod` 改三级（① 原名直查 ② 映射感知匹配 ③ 只有形状唯一匹配才兜底），并加危险名单（close / shutdown / dispose / clear / stop / reset / release / polygonMode / blendFunc / build / buildOrThrow / discard / end）。
- **每帧刷屏 `Clearing BufferBuilder with unused batches`**：`BufferBuilder.discard()` 在 1.21.1 不存在，兜底抽中 `build()`，返回的 `MeshData` 被丢掉，`resultCount` 只增不减。释放路径改成「接口优先」：先 `AutoCloseable.close()`，拿不到再按**精确名字**找 discard，类型驱动排空，两处名字盲兜底删掉。

再往下挖，就挖到了「为什么当初要用反射」这个根子：

- **Loom 只重映射类型引用，不重映射字符串**。`Class.forName("net.minecraft.core.registries.BuiltInRegistries")` 里那串字符原样留在常量池，生产环境一跑就是 ClassNotFoundException。更关键的是 Fabric 生产环境的映射文件**只有 `official` 和 `intermediary` 两个命名空间，没有 `named`**——所以一度想用 `MappingResolver` 在运行期把名字翻回来，那条路在生产上是死的。结论很干脆：**生产能用的只有直调**，写了 `BuiltInRegistries.ENTITY_TYPE` 这种引用，Loom 会老老实实重映射。
- 于是 10 份实体/物品桥全部改成直调：`ResourceLocation.tryParse` → `BuiltInRegistries.<ENTITY_TYPE|ITEM>.getValue(...)`（这个版本找不到返回 null，不用再拆 Optional）→ `EntityType.create(level, EntitySpawnReason.COMMAND)` / `new ItemStack(item)`；1.21.11 那边 `ResourceLocation` 已改名 `Identifier`，一并跟上。1.21.4 的 `create` 变成 2 参也是这么发现并修掉的（此前的反射版在这版上连 NeoForge 都退化成「召唤盔甲架」）。
- 顺手把 ItemModelRenderBridge 补出 `loreLines` / `tooltipLines` / `packFormat`，按版本族（1.20.1 / 1.21.1–1.21.4 / 1.21.8–1.21.11 / 26.1.2）各自直调，`CompatRender` 里旧的反射路径留着当兜底。**Fabric 上此前 `Player.getHeldItemLore` / `getHeldItemDetail` 恒返回空表**，也就是物品 lore/tooltip 在 Fabric 上一直没进来，这版一并修了。
- 完整说明、各版本签名实测、剩下的限制都记在 `output/反射与映射适配.md`。

### 头顶两套系统：HeadTag 补完，血条独立成家

- HeadTag 规则补三个键：`contains`（名字包含）、`offsetX` / `offsetY`（锚点横纵偏移，格）；变量补 `health_ratio`（0~1）与 `entity_height`（实体身高），脚本表达式和 `{vars.xx}` 两种写法都能用。
- 脚手架模板会生成带全部键位注释的 `HeadTag.yml`，照着改就行。
- 血条从 HeadTag 里拆出来独立成 Blood：`Blood.yml` / `Blood/` 两种存法（目录优先），跟 HeadTag 共用头顶 billboard 管线但**各自认领、互不覆盖**，`entity: "*"` 一条通配全体；模板自带背景条 + 动态宽度前景 + 可选数值文本。
- 龙核那边也对上了：老 `blood.yml` 自动重路由到 Blood；龙核头顶标签那套格式（match / contains / distance / offsetY 加 `名字_texture` / `名字_label` 组件块）有翻译器导入 HeadTag。
- 头顶页面新增龙核点号别名 `entity.name` / `entity.health` / `entity.health_max` / `entity.health_ratio` / `entity.height`，跟原生 `name` / `health` / `health_max` / `health_ratio` / `entity_height` 同值；`方法.取实体血量 / 最大血量 / 名 / 高度 / 比例` 五个旧桩升级为读当前头顶实体，没语境时回退到瞄准实体；`取指向实体 / 取指向生物X` 也从空转变为十字准星实体（uuid / name / health / maxHealth）。全息血条的 `progress` 的 value / min / max 支持每帧求表达式（数字、数字字符串、表达式三态），跟着 health / health_ratio 逐帧刷。

### 名牌消失：这次连根拔了

「生物名牌不见了、之前还是好的」，查下来是三处连线才成灾，不是配置问题：

1. 规则组装页面时**故意保留了 `背景` / `文字`**（想让整页渲染器套样式）；
2. 于是「整页规则」的判定就被这类样式规则批量命中了——**只写了样式的普通规则也被当成整页**；
3. 命中整页的各版本 Mixin 会**取消原版名牌**并登记待画，而收尾渲染器拿到的「只有样式、没有元素」的页面布局不出任何节点，直接 `return`。

结果就是：原版被取消 + 页面画不出来 = 名牌整体消失。改法没用「回滚」，而是把这条链修对：判定改用「除匹配键、样式键之外还有没有别的键」（`hasContent`），**名字条一律就地画**（整页内容只作为叠加），整页渲染器在「有内容却布局为空」时告警一次、不再静默空白。顺带把头顶页元素的世界坐标补上了——以前元素必须自己写 `hologram:`，不写就一律按 1×1 格画，你配的血条就是被这个坑掉的「配了看不见」；现在没写就按顶层 x / y / width / height 自动补一份世界单位。

12 个现代目标的 MixinNameTag 全部按这个语义重写。**遗留观察项**也记一下：state 体系那几版（1.21.4 / 1.21.8 / 1.21.11 / 26.1.2）靠一个 ThreadLocal 把实体从「抽取渲染状态」传到「渲染名牌」，如果某版本是先批量抽取再统一渲染，线程里那个实体会错配，症状是部分实体规则对不上。这轮没动它，先留着观察。

### 字符替换：整图直出、gif 真的会动、长宽随你

- **png 一律整图直出，不再被当图集切**：老四版（fabric/forge-1.20.1、fabric/neoforge-1.21.1）的 `blit(rl, x, y, 16, 16, 0, 0)` 命中了 256×256 图集语义的重载，整图只采样左上角 1/16；现在走全幅 UV 垫片，任意尺寸的图都完整显示。1.21.4 的手绘 quad、1.21.8 / 1.21.11 / 26.1.2 的 9 参归一化 blit 也全部改成帧感知 UV。
- **gif 之前根本没在动，而且是所有 gif 都没动**：`LooseResourceLoader.tickAll()` 写好了，但**全仓没有任何地方调用它**，帧索引永远停在 0；雪上加霜的是远程 url 的 gif 播放器只存在 `GifPlayer.CACHE` 里，不在被遍历的表里，fps 也没挂上。现在每客户端 tick 驱动一次，CACHE 里的播放器一并推进并补挂 fps。也就是说：本地 gif、URL gif 都会动了。
- **字符贴图的长宽现在按你想要的意思走**：以前 `width` / `height` 被当成「取图尺寸」，于是只写 `height: 8` 时宽度死板地保持 9（不等比），写 `width` 又会把大图裁成左上角一块。现在两个语义拆开了：`width` / `height` 是**显示尺寸**，取图尺寸用原生帧尺寸——只给一个就按原生帧等比补另一个，两个都给就是自定义，都不给就用原生帧尺寸（贴图还没就绪时先按 9×9 显示，就绪后自动换成真实尺寸）。
- 修掉 fabric-1.21.8 自定义字形渲染空白：缺 `renderType` 覆写，字形被丢回默认字体图集。fabric 这一版映射只有单参变体、neoforge 是双参，javap 反查确认后各按各的写。
- 帧表那套继续用：动画只是换 UV，纹理零上传——这是 v0.1.2.1 定下的路子（当年逐帧上传在 N 卡上能让驱动直接崩，Java 层拦不住），这版没有回头。

### 物品图标与实体：Fabric 上一直悄悄失效的那几个接口

- 物品图标支持按**显示名**匹配：`textureFor(type, displayName)` 两个参数，12 个调用点都传 `stack.getHoverName()`。以前那个 ItemView 是空名，龙核里按物品名（`match` / `name`）写的规则**永远不会命中**，只有纯类型 id 的规则生效。
- 容器槽位、UI 覆盖层的图标统一走整图直出；`drawChestSlot` 里跟 `drawItemAt` 重复画一遍的第二层覆盖删了。
- 实体 / 物品类型解析在 Fabric 上从「恒 null」变成正常（原因见上面的反射章节）——以前 Fabric 玩家看到的是「召唤盔甲架」和「空物品栈」。

### 老四版（1.6.4 / 1.7.10 / 1.12.2 / 1.16.5）跟上

- HeadTag 的 `contains` / `offsetX` / `offsetY` 在 legacy 的 PageDirector 落地，锚点是「实体脚底 + 身高 + 页级纵向偏移」；名字匹配链补到 `name` → `contains`。
- `bd_` 前缀的血条自动认领进头顶管线，跟 `hp_` 的 HeadTag 各自独立；规则归档键（entity / name / contains / distance / offsetX / offsetY / y）全链补齐。
- 物品图标支持按显示名匹配：四个钩子（forge-1.6.4 / 1.7.10 / 1.12.2 的 GlobalRenderItem、forge-1.16.5 的 MixinItemIcon）都把手持显示名传下去，整图直出沿用共享层。
- 老版本的 gif 动画**本来就通**：它的帧索引按 `System.currentTimeMillis()` 现算，不需要 tick 驱动——这点跟现代端不一样，别被「现代端要 tick」带跑。字形长宽这版还是原生帧尺寸，没跟着改（要改就是动 legacy 自己的绘制尺寸，放后面）。
- 平台差异、能力矩阵、构建注意都在 `output/LEGACY-PARITY.md`。老两版（1.6.4 / 1.7.10）的 Gradle 2.x 启动器不认新 JDK，构建时 `JAVA_HOME` 必须指向 JDK 8，不然启动器直接退出。

### 材质包注入：1.21.11 起的 zip 语义

1.21.11+ 的 `FilePackResources` 是严格 zip 语义，用目录 supplier 去开 `resourcepacks/OpenDreamCore/…` 会报「拒绝访问」→ 元数据读成 null → null 包进仓后 `reload()` 遍历子包直接 NPE。现在新增 `PackInstaller.zipForInjection()`（纯 JDK 写真 zip，落在 `gameDir/OpenDreamCore/packs/injected/` 这个不被扫描的目录，取文件顺序排好、输出确定），10 份现代注入器统一「zip 优先」，并且加了 null 包守卫——元数据读不出来就直接抛「包元数据读取失败（缺 pack.mcmeta 或文件不可读）」，中文提示，null 包绝不进仓。forge / fabric-1.20.1 那份目录 supplier 本来就正确，补了同一个守卫，一共 13 个文件。


### 写在最后：0.1.x 到这儿收官，0.2.x 见

有件事想在这一版末尾交代清楚。

从 0.1.2.2 起，这个项目会改成 **AI 与人工协同开发**。原因不复杂：时间不够用了，手上还有别的项目要盯，梦想核心和梦想引擎那条老线也还压着不少活。与其让它慢下来、甚至停在那儿等我有空，不如把能交出去的活交出去——查十几个版本的 API 差异、扫重复代码、做对照审计、把一处改动铺到十六个目标上，这些机器确实比我快得多，也确实能把我被别的事分走的时间补回来一些。

但有几条底线先说在前头。

**不会变成那种“AI 无脑开发”的项目。** 不会为了显得更新快就堆功能、换架构、把自己没吃透的东西塞进来；不会拿“重构”当借口把老代码推倒重来。方向还是原来的方向：**基于梦想核心与梦想引擎的既有设计做全面迁移**——页面格式、自研协议、DreamLang 脚本、渲染语义，这些从自用年代一路磨出来的地基不动，仍然是一条共享核心拖着 16 个版本壳，老版本和新版本拿到的是同一份页面、同一套行为。

**该有的把关一样不少。** 代码审计（空 catch、诊断输出、反射与映射路径、跨版本签名实测）从这版起就是常规动作；每条改动都要讲得清“原来什么样、为什么、现在什么样”，要能在真实客户端上跑起来，而不是只在构建日志里绿。这一版里那些实机毛病——光影下的半透明、名牌凭空消失、gif 停在第一帧、字符贴图被切——都是一个一个在机器上复现、定位、改掉、再上机取证的，以后也照这个规矩来。

写这些不是为了辩解什么。这个项目从当年那个只有我自己看得懂的自用客户端，走到今天十六个版本一条线，靠的一直是“每一处改动都有人负责”。现在多了个帮手，这条规矩不变。

0.1.x 就写到这一版。往后是 0.2.x——不是推倒重来，是接着往下走：纹理与动画体系继续补，接口继续开，老版本一个都不掉队；每条改动还是那句老话，讲得清来龙去脉，能在真实客户端上跑起来。这一版把该收的口都收了，剩下的路，下个版本见。

## v0.1.2.1（2026-09-23）

### 实机反馈修复

- **gif 动画重做，彻底告别驱动崩溃**：之前运行时逐帧上传贴图，在 NVIDIA 等显卡会触发驱动原生层崩溃（Java 层拦不住）。改成帧表方案——gif 全部帧解码后拼成一张纹理，动画只是改顶点 UV，纹理固定零上传，全版本不再有 upload 崩溃。
- **字符替换支持远程 url 贴图**：字体配置里 texture 写 `http(s)://...gif/png` 现在能用了，下载完成自动出现并播放；本地/云资源继续照常。
- **世界贴图缺失不再满屏紫色**：页面/世界引用了不存在的贴图会绑 missing 纹理渲染成紫黑格，绘制前挡一道，缺失直接跳过。
- **fabric 启动崩溃修复**：渲染线程判定在 yarn 映射下会误反射到 `Minecraft.close()` 导致启动即关闭，改为 isSameThread/isOnThread 精确探测双保险。
- **/odc 命令可用**：未连服时降级为本地动作（open/close/hud/list/reload/state），连服后转发服务器插件，收发都有聊天提示；fabric 发包类名 mojmap/yarn 双候选。
- **加载日志消噪**：lz.png 这类配置引用缺失不再反复刷 Missing resource 警告。
- **legacy 远古版构建加固**：1.6.4/1.7.10 补 JDK8 配置、1.12.2 补 JDK8、1.16.5 补 JDK17，四个远古版一条命令构建通过。

### 稳定性与体验修复

- 修 1.21.1 实机 `Clearing BufferBuilder with unused batches` 每帧刷屏：CompatBuffer 生命周期结束前先排空未消费批次，共享 Tesselator 路径不再残留
- 按键指令（Key.模拟按下 / KeyConfig 触发）整体重写，修复三处跨版本编译错误，全版本编译通过
- 头顶名牌（HeadTag）修复：规则里的 entity 键此前没被解析，命中所有实体、原版名牌被顶掉后又不渲染；现在逐实体匹配 + 整页渲染，支持 always / aim / health / aimorhealth / distance_N 显示时机，health / health_max / name 变量对页面表达式可用
- 全局字体替换链路跨版本加固：DynamicTexture 构造、NativeImage 白像素写入、InputConstants.isKeyDown 的 1.21.8+ 签名变化全部走版本安全等价
- fabric 1.20.1 / 1.21.4 补上世界名牌渲染钩子（此前实体渲染后只画世界全息，服务端推送名牌不显示）
- 代码卫生：57 处空 catch 补齐注释说明，32 处诊断 println 收敛到 -Dodc.debug 开关
- 构建收口：collectOutput 只收集当前版本插件 jar；各版本 target 用自带 Gradle wrapper 构建

## v0.1.2（2026-09-20）

### 老架构迁移收官：16 个版本一条核心线

这一版把梦想核心的老底子整个搬完了。从最早的自用客户端一路跑到现在，当年那些只有我自己看得懂的角落，这次全部落到开源架构上，一条共享核心拖 16 个版本壳，谁也不用再维护第二套逻辑。

- **1.6.4 / 1.7.10 / 1.12.2 / 1.16.5 四个远古版本整体迁完**：现代线和远古线共用同一份共享层——同一套页面格式、同一套协议、同一套 DreamLang 脚本能力、同一套渲染语义。每个老版本名下只剩薄薄一层版本适配壳，1.6.4 的客户端跟 26.x 的客户端拿到的是同一份页面，行为没有差别。
- 共享层源码走 Jabel 脱糖，字节码锁死 release 8；yaml / gson / antlr 全部打进包内，老服不用额外装库。
- 版本矩阵收口为 16 个 target：1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.11 / 26.1.2 的 Forge / Fabric / NeoForge，加远古四版，再叠服务端插件与 common 测试。

### 替换类功能一个不落

字符替换、标签替换、物品贴图替换这些当年自用时的看家本领，这次全部完成迁移：

- 字符替换：规则下发两端对齐，服务端拍平成 `id=字符` 加一段配置，客户端就按这个格式解析，中文字符想换哪张贴图就是哪张；纹理注册统一走渲染线程冲刷队列，图片到位下一帧就位
- gif 动画整套迁进来：按内容识别解码，不看扩展名，均匀抽帧，角色贴图、动画图标、会跳动的字都能动
- 标签替换：名字标签、悬停标签全版本一个样
- 物品贴图替换：物品图标、容器界面、tooltip 的老版本画法各自按自家时代的 API 落地
- 盔甲层、光标这些细碎一并迁完，中文字体贴图、图集、字距、阴影在新老版本线上一套表现，中英文混排不打架

### 变量这块迁成客户端自产

- 占位符客户端本地优先：player / query / system 在本地每帧解析，只有 PAPI 占位符才走网络向服务器要数。单机不装 PAPI 照样有变量。
- 握手当场把全局状态整包带下来，断线重连、页面重载之后状态照旧在。

### HUD 迁完能叠着挂

- HUD 按页面 id 叠加：hud、hud_promo、hud_global 想同屏就同屏，互不挤占。
- autoMount 会把 `match: hud` 的一族页面全部挂上，新增一行配置就是一张新常驻条。

### 命令与权限理顺

- /odc 权限模型理顺：普通玩家能开页面（`/odc open <页面>`），管理子命令只认管理员权限，没权限就明明白白告诉你。
- 客户端到插件的转发链路全通，服务端每收一条都留日志，前后端能对上话。
- 插件侧整体搬成纯 Bukkit/Paper API，整包 Java 8 字节码，从 1.16.5 的老服一路吃到最新版。

### 渲染全版本收口

- 名牌、光标、盔甲层贴图、ItemIcon、tooltip 在 16 个版本上各自按自家时代的 API 落地，一版不落。
- 世界全息面板卡进世界渲染的收尾阶段，模型视图栈有了深度守卫。

### 服务端与配套

- 服务端全局状态、页面、视觉规则、匹配规则各自落位；握手后缺失的会话页面会补发。
- 新增 /codc 纯客户端诊断命令：看连接状态、导出当前页面、开关常驻条、查各版本适配信息，全程不碰服务器权限。
- 资源云管线整体搬完：图片/音频/字体自动同步，增量加密传输，按内容去重；文件夹直接丢进 OpenDreamCore 材质包目录即生效，中文文件名也没问题。
- Ctrl+R 一键重载材质包，改图片不用重进游戏。

> 更多更新，进游戏里慢慢探索吧。

## v0.1.1（2026-08-24）

- 双平台补齐到 12 个 target：新增 Fabric / NeoForge 1.21.11，neoforge 26.1.2 与 fabric 26.1.2 完成 API 漂移收敛，remapDrift 机械重映射机制建立，新版本适配成本大幅降低。
- ResourcePackInjector 平台注入器覆盖全部 12 个 target；新增本地预置加载 `resourcepacks/OpenDreamCore/`，zip / 文件夹包直接置顶注入，支持中文文件名。
- 服务端插件 api-version 调整到 1.20，Paper/Spigot 通吃；就绪握手后补发当前会话页面，页面不再丢失。
- /odc list 连服时能列出服务器下发的页面清单；内置资源包补 pack.mcmeta。

## v0.1.0

- 首个开源版本：UI 引擎、DreamLang 脚本、自研二进制协议、服务端裁决与资源云。