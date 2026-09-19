package com.opendreamcore.client.render;

import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.client.spi.EntitySource;
import com.opendreamcore.client.visual.LegacyVisualSkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 页面出场导演：每帧决定哪些页上屏、在哪上屏。
 *
 * 几条规矩（实机截图定的稿，世界页那条是第三轮截图改的版）：
 *  世界页住世界相位：不再投屏幕。屏幕相位（overlay）里世界页一概不出场，
 *     由各版的 RenderWorldLast 钩子调 renderWorldPhase，把面板锚在世界坐标上
 *     billboard 朝镜头。锚点三档跟现代端 updateWorldPanelAnchors 一个口径：
 *     world.anchor {x,y,z} 绝对坐标 > world.follow: true 每帧跟人 >
 *     默认进相位第一帧钉住不再动（WorldPanel.pinnedAnchor 同款，
 *     实机转圈就是这条没落地的锅）。
 *  屏幕页单槽：activePageId 指向谁就只画谁；没 OPEN 过时沿用老语义，
 *     取库存第一页兜底（服务端 push-pages 预推、没发控制指令的场景）。
 *  视觉规则页分流：WorldTexture/HeadTag 生成的世界面板走世界相位，
 *     其余的常驻叠加在屏幕上，不参与互斥。
 */
public final class PageDirector {

    private PageDirector() { }

    /** 世界画布比例：1 面板平面像素 = 0.025 格（现代端 HoloTextRender 同尺度）。 */
    public static final double BLOCKS_PER_PX = 0.025;
    /** 反过来：一格多少像素。面板元素声明的是世界单位，乘这个换算成画布像素。 */
    public static final double WORLD_PPU = 1.0 / BLOCKS_PER_PX;

    /** 屏幕相位每帧入口：钩子先喂分辨率/鼠标，再调这个。世界页在这里不出场。 */
    public static void render(MessageDispatcher dispatcher, LegacyRenderer renderer,
                              String activePageId) {
        if (dispatcher == null || renderer == null) {
            return;
        }
        String id = activePageId;
        if (id == null) {
            List<String> ids = dispatcher.pageIds();
            if (!ids.isEmpty()) {
                id = ids.get(0);
            }
        }
        // 先收拢本帧真出场的页面，再交给原版层开关重算（页关了自动失效）
        List<Page> visible = new ArrayList<>();
        Page active = id == null ? null : dispatcher.page(id);
        if (active != null && !WorldPageRenderer.isWorldPage(active)) {
            visible.add(active);
        }
        for (String hudId : dispatcher.hudPageIds()) {
            if (hudId.equals(id)) {
                continue;
            }
            Page hudPage = dispatcher.page(hudId);
            if (hudPage != null && !WorldPageRenderer.isWorldPage(hudPage)) {
                visible.add(hudPage);
            }
        }
        for (String visualId : dispatcher.visualPageIds()) {
            Page visualPage = dispatcher.page(visualId);
            if (visualPage != null && !WorldPageRenderer.isWorldPage(visualPage)
                    && !visible.contains(visualPage)) {
                visible.add(visualPage);
            }
        }
        com.opendreamcore.client.VanillaHud.sync(visible);
        manageScreenBridge(active);
        if (active != null && !WorldPageRenderer.isWorldPage(active)) {
            new PageRenderer(renderer).render(active);
        }
        for (Page hudPage : visible) {
            if (hudPage != active) {
                new PageRenderer(renderer).render(hudPage);
            }
        }
        Interactions.process();
    }

    /** 世界页钉住锚点：页id → 进相位第一帧算好的世界坐标。页面下相位（关页）就扔，
     *  重开重新钉——单机锁个键值对的事，别上大组件。 */
    private static final Map<String, double[]> PINNED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 世界相位每帧入口：各版 RenderWorldLast 钩子喂相机与玩家位置。
     * 锚点三档：绝对 anchor > 显式 follow > 默认钉住（解析见 resolveAnchor）。
     * 相机位置传的是「渲染视点」，矩阵里已经扣掉了，锚点直接给世界坐标。
     */
    public static void renderWorldPhase(MessageDispatcher dispatcher, LegacyRenderer renderer,
                                        String activePageId,
                                        double camX, double camY, double camZ,
                                        double playerX, double playerY, double playerZ,
                                        double yawDeg, double pitchDeg, double fovDeg) {
        if (dispatcher == null || renderer == null) {
            return;
        }
        List<Page> pages = new ArrayList<>();
        Page active = activePageId == null ? null : dispatcher.page(activePageId);
        if (active != null && WorldPageRenderer.isWorldPage(active)
                && !isHeadTagPage(active.id())) {
            pages.add(active);
        }
        for (String visualId : dispatcher.visualPageIds()) {
            Page visualPage = dispatcher.page(visualId);
            if (visualPage != null && WorldPageRenderer.isWorldPage(visualPage)
                    && !isHeadTagPage(visualPage.id())
                    && !pages.contains(visualPage)) {
                pages.add(visualPage);
            }
        }
        if (pages.isEmpty() && LegacyVisualSkins.headTagPageIds().isEmpty()) {
            return;
        }
        // 锚点清仓：不在本轮集合里的页（关了页）旧锚点跟着扔，重开才钉新位
        if (!PINNED.isEmpty()) {
            java.util.Set<String> live = new java.util.HashSet<>();
            for (Page p : pages) {
                live.add(p.id());
            }
            PINNED.keySet().retainAll(live);
        }
        double yaw = Math.toRadians(yawDeg);
        for (Page page : pages) {
            double[] anchor = resolveAnchor(page, playerX, playerY, playerZ, yaw);
            if (!renderer.beginWorld(camX, camY, camZ, anchor[0], anchor[1], anchor[2])) {
                continue;
            }
            try {
                new WorldPageRenderer(renderer)
                        .renderWorldSpace(page, WORLD_PPU,
                                distanceFade(page, camX, camY, camZ, anchor));
            } finally {
                renderer.endWorld();
            }
            // 指针屏开着（真鼠标可用）才登记世界命中；fovDeg 非正时投影自己跳过
            if (ScreenBridge.isOpen()) {
                new WorldPageRenderer(renderer).registerProjectionHits(page,
                        camX, camY, camZ, anchor[0], anchor[1], anchor[2],
                        yawDeg, pitchDeg, fovDeg);
            }
        }
        renderHeadTagPages(dispatcher, renderer, camX, camY, camZ);
    }

    /** 兼容旧签名：不传俯仰/视场角 → 世界面板命中投影自动跳过（老壳行为不变）。 */
    public static void renderWorldPhase(MessageDispatcher dispatcher, LegacyRenderer renderer,
                                        String activePageId,
                                        double camX, double camY, double camZ,
                                        double playerX, double playerY, double playerZ,
                                        double yawDeg) {
        renderWorldPhase(dispatcher, renderer, activePageId, camX, camY, camZ,
                playerX, playerY, playerZ, yawDeg, Double.NaN, Double.NaN);
    }

    /**
     * 指针屏状态检查：活跃页需要真鼠标（菜单页带交互元素且非 through；
     * 世界页要 options.world.interact）就开屏放指针，否则收屏抓回。
     * 每帧对账，页开关/切换自动跟上，不用事件通知。
     */
    private static void manageScreenBridge(Page active) {
        if (active != null && needsPointer(active)) {
            ScreenBridge.open(active.id());
        } else if (ScreenBridge.isOpen()) {
            ScreenBridge.close();
        }
    }

    /** 指针判定：屏幕页非 through 且带可交互元素；世界页要 world.interact。 */
    private static boolean needsPointer(Page page) {
        if (WorldPageRenderer.isWorldPage(page)) {
            Object world = page.options() == null ? null : page.options().get("world");
            return world instanceof java.util.Map
                    && Painters.bool(((java.util.Map<?, ?>) world).get("interact"));
        }
        java.util.Map<String, Object> options = page.options();
        if (options != null && Painters.bool(options.get("through"))) {
            return false; // 纯叠加页（HUD 那类）永不拦鼠标
        }
        if (page.elements() == null) {
            return false;
        }
        for (Element e : page.elements()) {
            if (Interactions.interactive(Painters.normType(e.type()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * ESC 关页（指针屏壳回传）：发 page_close 给服务器（现代端 onClose 同款，
     * 服务端清会话/容器绑定），再通知壳清本地活跃页，状态检查就不会再把屏开回去。
     */
    public static void onScreenDismissed(String pageId) {
        com.opendreamcore.client.ClientControllerLegacy.sendPageClose(
                MessageDispatcher.sessionOf(pageId));
        ScreenBridge.notifyDismissed(pageId);
    }

    /**
     * 头顶页（HeadTag/Blood 来的）单独走一遍：这类页不是钉在世界上，
     * 是钉在每只生物头顶——逐实体开锚、逐实体渲染，实体变量
     * （entity.health 之类）在渲染堆栈里压入，画完弹出。
     * 龙核 Blood.yml 的语义：半径内每只带血条名的生物头顶一条血。
     */
    private static void renderHeadTagPages(MessageDispatcher dispatcher, LegacyRenderer renderer,
                                           double camX, double camY, double camZ) {
        java.util.Set<String> headIds = LegacyVisualSkins.headTagPageIds();
        if (headIds.isEmpty()) {
            return;
        }
        EntitySource source = EntitySource.Host.current();
        for (String headId : headIds) {
            Page page = dispatcher.page(headId);
            if (page == null || !WorldPageRenderer.isWorldPage(page)) {
                continue;
            }
            java.util.Map<String, Object> match = LegacyVisualSkins.headTagMatchOf(headId);
            double maxDist = Painters.num(match.get("distance"), 32.0);
            if (maxDist <= 0.0) {
                maxDist = 32.0;
            }
            String entPattern = match.get("entity") == null ? ""
                    : String.valueOf(match.get("entity")).toLowerCase();
            String namePattern = match.get("name") == null ? ""
                    : String.valueOf(match.get("name")).toLowerCase();
            double pageY = Painters.num(match.get("y"), 0.0);
            java.util.List<EntitySource.Snapshot> crowd =
                    source.nearbyLiving(camX, camY, camZ, maxDist);
            for (EntitySource.Snapshot snap : crowd) {
                if (!matchesHeadTag(snap, entPattern, namePattern)) {
                    continue;
                }
                // 锚点=实体脚底+身高+页级 y 偏移（血条挂在头顶偏上一点）
                if (!renderer.beginWorld(camX, camY, camZ,
                        snap.x, snap.y + snap.height + pageY, snap.z)) {
                    continue;
                }
                try {
                    Placeholders.pushEntity(snap);
                    new WorldPageRenderer(renderer).renderWorldSpace(page, WORLD_PPU, 1.0);
                } finally {
                    Placeholders.popEntity();
                    renderer.endWorld();
                }
            }
        }
    }

    /** 实体筛选：entity 匹配方块 id（villager 与 minecraft:villager 互通），
     *  name 匹配自定义名；都是空就全过（全生物头顶都画）。 */
    private static boolean matchesHeadTag(EntitySource.Snapshot snap,
                                          String entPattern, String namePattern) {
        if (!entPattern.isEmpty()) {
            String tid = snap.typeId == null ? "" : snap.typeId.toLowerCase();
            if (!tid.equals(entPattern) && !tid.contains(entPattern)
                    && !entPattern.contains(tid)) {
                return false;
            }
        }
        if (!namePattern.isEmpty()) {
            String nm = snap.name == null ? "" : snap.name.toLowerCase();
            return nm.contains(namePattern);
        }
        return true;
    }

    /** 头顶页识别：visual 规则里 HeadTag/Blood 翻出来的页 id。 */
    private static boolean isHeadTagPage(String pageId) {
        return pageId != null && LegacyVisualSkins.headTagPageIds().contains(pageId);
    }

    /** 距离淡出（现代端同款公式）：world.fadeDistance 起步、world.fadeRange 跨度，
     *  超程线性收到 0；没配 fadeDistance 就不淡，别自作多情。 */
    private static double distanceFade(Page page, double cx, double cy, double cz, double[] anchor) {
        Map<?, ?> world = worldSection(page);
        double start = world == null ? 0.0 : Painters.num(world.get("fadeDistance"), 0.0);
        if (start <= 0.0) {
            return 1.0;
        }
        double range = world == null ? 0.0 : Painters.num(world.get("fadeRange"), 0.0);
        double dx = anchor[0] - cx, dy = anchor[1] - cy, dz = anchor[2] - cz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= start) {
            return 1.0;
        }
        double fade = 1.0 - (dist - start) / Math.max(range, 0.1);
        return Math.max(0.0, Math.min(1.0, fade));
    }

    /** 页面 world 段（没配返回 null），锚点/淡出/插值三处共用的取段口。 */
    private static Map<?, ?> worldSection(Page page) {
        Map<String, Object> options = page.options();
        Object rawWorld = options == null ? null : options.get("world");
        return rawWorld instanceof Map ? (Map<?, ?>) rawWorld : null;
    }

    /** 页锚点三档（跟现代端 updateWorldPanelAnchors 一个口径）：
     *  绝对 anchor 定世界坐标；follow: true 每帧跟人（配 world.smooth 就
     *  逐帧向目标插值，0~1 越大追得越紧）；默认钉住——进相位
     *  第一帧算一次存档，之后原地不动，玩家怎么走面板都不追。 */
    private static double[] resolveAnchor(Page page, double px, double py, double pz, double yaw) {
        Map<?, ?> world = worldSection(page);
        double[] off = worldOffset(page);
        // 绝对锚点 world.anchor {x,y,z}：钉在世界坐标，offset 只当微调叠加
        Object rawAnchor = world == null ? null : world.get("anchor");
        if (rawAnchor instanceof Map) {
            Map<?, ?> am = (Map<?, ?>) rawAnchor;
            return new double[]{
                    Painters.num(am.get("x"), 0.0) + off[0],
                    Painters.num(am.get("y"), 0.0) + off[1],
                    Painters.num(am.get("z"), 0.0) + off[2],
            };
        }
        // 显式跟随 world.follow: true：每帧重算（不写就是不跟，钉住为默认档）
        if (world != null && Boolean.parseBoolean(String.valueOf(world.get("follow")))) {
            double[] target = new double[]{
                    px + off[0] - Math.sin(yaw) * off[2],
                    py + off[1],
                    pz + Math.cos(yaw) * off[2],
            };
            return smoothToward(page, target, world);
        }
        // 默认钉住：第一帧存档，后面直接用旧账
        double[] pin = PINNED.get(page.id());
        if (pin == null) {
            pin = new double[]{
                    px + off[0] - Math.sin(yaw) * off[2],
                    py + off[1],
                    pz + Math.cos(yaw) * off[2],
            };
            PINNED.put(page.id(), pin);
        }
        return pin;
    }

    /** smooth 插值：当前位（复用 PINNED 存档）向目标每帧收拢 smooth 比例；
     *  smooth 未配/为 0 就直达目标。没存过档就先存目标。 */
    private static double[] smoothToward(Page page, double[] target, Map<?, ?> world) {
        double s = Painters.num(world.get("smooth"), 0.0);
        if (s <= 0.0) {
            return target;
        }
        s = Math.min(1.0, s);
        double[] cur = PINNED.get(page.id());
        if (cur == null) {
            PINNED.put(page.id(), target);
            return target;
        }
        for (int i = 0; i < 3; i++) {
            cur[i] += (target[i] - cur[i]) * s;
        }
        return cur;
    }

    /** world 段的偏移：offsetX/Y/Z（单位格）。缺省值跟现代端一个口径。 */
    private static double[] worldOffset(Page page) {
        Object world = page.options() == null ? null : page.options().get("world");
        Map<?, ?> m = world instanceof Map ? (Map<?, ?>) world : null;
        return new double[]{
                m == null ? 0.0 : Painters.num(m.get("offsetX"), 0.0),
                m == null ? 1.6 : Painters.num(m.get("offsetY"), 1.6),
                m == null ? 3.0 : Painters.num(m.get("offsetZ"), 3.0),
        };
    }
}
