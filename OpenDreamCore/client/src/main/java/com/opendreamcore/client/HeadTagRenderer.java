package com.opendreamcore.client;

import com.opendreamcore.client.visual.VisualNameTags;
import com.opendreamcore.client.visual.VisualWorldPages;
import com.opendreamcore.page.Page;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HeadTag 整页名牌的逐实体渲染。
 *
 * 分工：实体名牌 hook（MixinNameTag）命中带页面内容的规则时，只取消原版
 * 名牌并登记实体 id；本类在世界渲染收尾阶段（ClientController.renderNameTags；
 * 各平台取各自世界渲染的末段事件，不再挂实体中段）按实体头顶位置整页渲染规则页面，并把 name /
 * health / health_max / health_ratio / entity_height 与龙核别名 entity.name /
 * entity.health / entity.health_max / entity.health_ratio / entity.height
 * 注入页面作用域——脚本表达式与 {vars.xx} 插值都能用（两种词汇都可用），
 * 血条类名牌靠这些变量每帧刷新；方法.取实体血量() 等桩同步读 CURRENT_ENTITY。
 * 规则的 offsetX/offsetY 决定锚点在头顶基准位上的横纵偏移（格）。
 */
public final class HeadTagRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeadTagRenderer.class);

    /** 本帧要画名牌的实体：entityId → 命中的规则（实体渲染阶段登记，收尾消费后清空）。 */
    private static final Map<Integer, List<VisualNameTags.Entry>> FRAME = new ConcurrentHashMap<>();

    /** 规则 → 已构建页面 / 已布局节点（refresh 时失效重建，避免每帧重解析 yml）。 */
    private static final Map<VisualNameTags.Entry, Page> PAGES = new ConcurrentHashMap<>();
    private static final Map<VisualNameTags.Entry, List<RenderNode>> NODES = new ConcurrentHashMap<>();

    /** 已告警过「有内容却布局为空」的规则，避免每帧刷屏。 */
    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private HeadTagRenderer() {
    }

    /** 实体名牌 hook 命中整页规则时登记（渲染线程，逐实体）。 */
    public static void noteEntity(int entityId, VisualNameTags.Entry entry) {
        FRAME.computeIfAbsent(entityId, k -> new ArrayList<>(2)).add(entry);
    }

    /** 实体渲染收尾统一绘制本帧全部名牌；结束清空帧登记（下一帧实体 hook 重新登记）。 */
    public static void renderFrame(Camera camera, float partialTick) {
        try {
            if (FRAME.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            for (Map.Entry<Integer, List<VisualNameTags.Entry>> it : FRAME.entrySet()) {
                Entity entity = mc.level.getEntity(it.getKey());
                if (entity == null || !entity.isAlive()) {
                    continue;
                }
                for (VisualNameTags.Entry entry : it.getValue()) {
                    drawHeadTag(entity, entry, camera, partialTick);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("HeadTag 渲染异常: {}", e.toString());
        } finally {
            FRAME.clear();
        }
    }

    private static void drawHeadTag(Entity entity, VisualNameTags.Entry entry,
                                    Camera camera, float partialTick) {
        LegacyClientHost.install(); // 头顶页面脚本可用 方法.* 桩（含 CURRENT_ENTITY 实体语境）
        Page page = PAGES.computeIfAbsent(entry,
                e -> VisualWorldPages.headTagToPage(e.ruleId(), e.pageIr()));
        if (page == null) {
            return;
        }
        List<RenderNode> nodes = NODES.computeIfAbsent(entry,
                e -> ClientController.get().layoutPage(page, 800, 600));
        if (nodes == null || nodes.isEmpty()) {
            // 带页面内容却布局不出节点：页面定义有问题（只说样式的规则不会走到这里，
            // 它们由各平台 MixinNameTag 就地画），记一次日志免得以后又静默空白
            if (WARNED.add(entry.ruleId())) {
                LOGGER.warn("HeadTag 规则 {} 带页面内容但布局为空，该名牌不显示（检查页面元素）",
                        entry.ruleId());
            }
            return;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        Map<String, Object> base = page.variables();
        if (base != null) {
            vars.putAll(base);
        }
        vars.put("name", entity.getName().getString());
        if (entity instanceof LivingEntity living) {
            double hp = living.getHealth();
            double hpMax = living.getMaxHealth();
            vars.put("health", String.valueOf((int) Math.ceil(hp)));
            vars.put("health_max", String.valueOf((int) Math.ceil(hpMax)));
            vars.put("health_ratio", hpMax > 0.0 ? String.valueOf(hp / hpMax) : "1");
        }
        vars.put("entity_height", String.valueOf(entity.getBbHeight()));
        // 龙核点号词汇别名：entity.name/health/health_max/health_ratio/height
        // 与原生 name/health/… 同值——头顶页面表达式两种写法都可用（线C①）
        vars.put("entity.name", vars.get("name"));
        if (vars.containsKey("health")) {
            vars.put("entity.health", vars.get("health"));
            vars.put("entity.health_max", vars.get("health_max"));
            vars.put("entity.health_ratio", vars.get("health_ratio"));
        }
        vars.put("entity.height", vars.get("entity_height"));
        // 锚点：实体头顶（底边 + 身高 + 0.55 格，与原版名牌高度量级一致）+ 规则偏移
        Vec3 anchor = new Vec3(entity.getX(),
                entity.getY() + entity.getBbHeight() + 0.55 + entry.offsetY(), entity.getZ());
        if (entry.offsetX() != 0.0) {
            // 横向偏移：沿“相机→实体”连线的水平垂直方向平移（billboard 左右）
            Vec3 look = anchor.subtract(camera.getPosition());
            Vec3 right = new Vec3(-look.z, 0, look.x);
            double len = right.length();
            if (len > 1.0E-4) {
                anchor = anchor.add(right.scale(entry.offsetX() / len));
            }
        }
        String pid = "ht_" + entry.ruleId() + "_" + entity.getId();
        WorldHologram.render(nodes, page.options(), camera, partialTick,
                null, pid, vars, null, null, null, 1.0, anchor);
    }

    /** 规则集变化后清缓存（页面/节点随 refresh 重建）。 */
    public static void invalidate() {
        PAGES.clear();
        NODES.clear();
        FRAME.clear();
    }
}
