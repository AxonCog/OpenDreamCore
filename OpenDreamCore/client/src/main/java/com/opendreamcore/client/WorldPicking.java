package com.opendreamcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

/**
 * 世界面板射线拾取与命中检测。
 * 射线构造、递归拾取、billboard 命中距离、交互/拖拽/锁定判定。
 */
final class WorldPicking {

    private WorldPicking() {
    }

    /**
     * 射线拾取：鼠标射线 vs 世界 billboard 面板（有 hologram 属性的元素）。
     * 页面 options.world.interact: true 时整页可交互；元素可单独 hologram.interact: true。
     * 命中返回最深子节点（子优先，同层取靠相机近的）；未命中 null。
     * 调用方在渲染线程（RenderLevelStageEvent）调用。
     */
    public static RenderNode raycast(List<RenderNode> nodes, Map<String, Object> options,
                                     net.minecraft.client.Camera camera, Minecraft mc) {
        return raycast(nodes, options, camera, mc, null, null, java.util.Map.of());
    }

    /** 带页签过滤的拾取：activeTab 非空时只拾取无 tab 或匹配 tab 的元素；pageId/pageVars 为本面板。 */
    public static RenderNode raycast(List<RenderNode> nodes, Map<String, Object> options,
                                     net.minecraft.client.Camera camera, Minecraft mc, String activeTab,
                                     String pageId, java.util.Map<String, Object> pageVars) {
        return raycast(nodes, options, camera, mc, activeTab, pageId, pageVars, null);
    }

    /** anchorOverride 非空时用调用方提供的锚点（与渲染一致：平滑跟随/固定锚点模式）。 */
    public static RenderNode raycast(List<RenderNode> nodes, Map<String, Object> options,
                                     net.minecraft.client.Camera camera, Minecraft mc, String activeTab,
                                     String pageId, java.util.Map<String, Object> pageVars, Vec3 anchorOverride) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        Object worldOpt = options == null ? null : options.get("world");
        boolean pageInteractive = worldOpt instanceof Map<?, ?> w
                && Boolean.parseBoolean(String.valueOf(w.get("interact")));
        if (!pageInteractive) {
            // 页级未开交互：看是否有元素单独声明 hologram.interact
            boolean any = false;
            for (RenderNode node : nodes) {
                if (WorldHologram.tabVisible(node, activeTab) && interactive(node)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return null;
            }
        }
        Vec3 anchor = anchorOverride != null ? anchorOverride : WorldHologram.anchor(mc, options);
        double[] ray = mouseRay(mc, camera);
        WorldHologram.lastPickOffset = null;
        return pick(nodes, anchor, ray, activeTab, pageId, pageVars);
    }

    /** 命中节点的世界偏移（父链累积；raycast 后读取）。 */
    public static double[] lastPickOffset() {
        return WorldHologram.lastPickOffset;
    }

    private static boolean interactive(RenderNode node) {
        Map<?, ?> holo = WorldHologram.holo(node);
        if (holo.isEmpty()) {
            return false;
        }
        if (Boolean.parseBoolean(String.valueOf(holo.get("interact")))) {
            return true;
        }
        // 控件类型默认可交互（按钮/滑块/开关等无需再写 hologram.interact: true）
        return switch (node.type() == null ? "" : node.type()) {
            case "button", "toggle", "checkbox", "slider", "arc_slider", "dropdown", "tabs" -> true;
            default -> false;
        };
    }

    /** 元素是否锁定（hologram.locked: true → 防误拖，不可拖拽/微调/手柄变换，点击仍可用）。 */
    public static boolean locked(RenderNode node) {
        Map<?, ?> holo = WorldHologram.holo(node);
        return !holo.isEmpty() && Boolean.parseBoolean(String.valueOf(holo.get("locked")));
    }

    /** 元素是否可拖拽：页级 world.drag: true 或元素 hologram.draggable: true（锁定元素不可拖）。 */
    public static boolean draggable(RenderNode node, Map<String, Object> options) {
        if (locked(node)) {
            return false;
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        if (holo.isEmpty()) {
            return false;
        }
        if (Boolean.parseBoolean(String.valueOf(holo.get("draggable")))) {
            return true;
        }
        Object worldOpt = options == null ? null : options.get("world");
        return worldOpt instanceof Map<?, ?> w
                && Boolean.parseBoolean(String.valueOf(w.get("drag")));
    }

    /** 递归拾取：子优先（面板叠加时点中上面的内容），返回最深命中；命中偏移写入 WorldHologram.lastPickOffset。 */
    private static RenderNode pick(List<RenderNode> nodes, Vec3 anchor, double[] ray, String activeTab,
                                   String pageId, java.util.Map<String, Object> pageVars) {
        return pick(nodes, anchor, ray, null, activeTab, pageId, pageVars);
    }

    private static RenderNode pick(List<RenderNode> nodes, Vec3 anchor, double[] ray, double[] parentOffset,
                                   String activeTab, String pageId, java.util.Map<String, Object> pageVars) {
        RenderNode best = null;
        double bestT = Double.MAX_VALUE;
        int bestZ = Integer.MIN_VALUE;
        // 快速剔除：节点中心距相机超过 96 格不参与拾取（每帧 raycast 的常驻开销控制）
        var cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (RenderNode node : nodes) {
            if (!node.visible() || !WorldHologram.tabVisible(node, activeTab) || WorldHologram.holo(node).isEmpty()
                    || !com.opendreamcore.client.ClientController.get().worldElementVisible(pageId, node.id())) {
                continue;
            }
            Map<?, ?> holo = WorldHologram.holo(node);
            double px = WorldHologram.holoNum(holo, "x", 0, pageVars);
            double py = WorldHologram.holoNum(holo, "y", 0, pageVars);
            double pz = WorldHologram.holoNum(holo, "z", 0, pageVars);
            double baseX = parentOffset == null ? 0 : parentOffset[0];
            double baseY = parentOffset == null ? 0 : parentOffset[1];
            double baseZ = parentOffset == null ? 0 : parentOffset[2];
            double cx = anchor.x + baseX + px - cameraPos.x;
            double cy = anchor.y + baseY + py - cameraPos.y;
            double cz = anchor.z + baseZ + pz - cameraPos.z;
            if (cx * cx + cy * cy + cz * cz > 96.0 * 96.0) {
                continue;
            }
            double[] childOffset = {baseX + px, baseY + py, baseZ + pz};
            RenderNode child = pick(node.children(), anchor, ray, childOffset, activeTab, pageId, pageVars);
            if (child != null) {
                double t = hitDistance(node, anchor, ray, parentOffset, pageVars);
                if (t >= 0 && better(t, node.z(), bestT, bestZ)) {
                    best = child;
                    bestT = t;
                    bestZ = node.z();
                    WorldHologram.lastPickOffset = childOffset;
                }
                continue;
            }
            double t = hitDistance(node, anchor, ray, parentOffset, pageVars);
            if (t >= 0 && better(t, node.z(), bestT, bestZ)) {
                best = node;
                bestT = t;
                bestZ = node.z();
                WorldHologram.lastPickOffset = childOffset;
            }
        }
        return best;
    }

    /**
     * 拾取优先级：距离更近优先；距离相等（同平面重叠）时 z 层级大的优先
     * （与屏幕渲染的 z 排序一致：z 大画在上面、命中优先）。
     */
    private static boolean better(double t, int z, double bestT, int bestZ) {
        if (t < bestT - 1e-6) {
            return true;
        }
        return Math.abs(t - bestT) <= 1e-6 && z > bestZ;
    }

    /** 鼠标射线（公开：世界拖拽用）：{ox, oy, oz, dx, dy, dz}（世界坐标）。 */
    public static double[] mouseRayWorld(Minecraft mc, net.minecraft.client.Camera camera) {
        return mouseRay(mc, camera);
    }

    /** 鼠标射线：{ox, oy, oz, dx, dy, dz}（世界坐标，来自相机反投影）。 */
    private static double[] mouseRay(Minecraft mc, net.minecraft.client.Camera camera) {
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        // MouseHandler.xpos() 已是缩放 GUI 坐标（与 Screen 事件一致）
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double ndcX = mouseX / scaledW * 2 - 1;
        double ndcY = 1 - mouseY / scaledH * 2;
        // 近平面反投影 → 相机空间方向
        var proj = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());
        proj.invert();
        var clip = new org.joml.Vector4f((float) ndcX, (float) ndcY, 0.0F, 1.0F);
        var view = proj.transform(clip);
        var viewDir = new org.joml.Vector3f(view.x, view.y, view.z).normalize();
        // 相机旋转四元数（世界→相机）→ 矩阵转置 = 相机→世界
        var rot = new org.joml.Matrix4f().rotation(camera.rotation()).transpose();
        var worldDir = rot.transformDirection(viewDir);
        Vec3 origin = camera.getPosition();
        return new double[]{origin.x, origin.y, origin.z, worldDir.x, worldDir.y, worldDir.z};
    }

    /** 射线与节点 billboard 面板求交：命中返回 t（>0），未命中 -1。parentOffset 为父链位置累积。 */
    private static double hitDistance(RenderNode node, Vec3 anchor, double[] ray, double[] parentOffset,
                                      java.util.Map<String, Object> pageVars) {
        Map<?, ?> holo = WorldHologram.holo(node);
        double baseX = parentOffset == null ? 0 : parentOffset[0];
        double baseY = parentOffset == null ? 0 : parentOffset[1];
        double baseZ = parentOffset == null ? 0 : parentOffset[2];
        double x = baseX + WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = baseY + WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = baseZ + WorldHologram.holoNum(holo, "z", 0, pageVars);
        // 文本：wrap 折行时按内容自适应命中框（宽 = wrap、高 = 行数×8px×scale）
        double w = WorldHologram.holoNum(holo, "width", defaultQuadW(node), pageVars);
        double h = WorldHologram.holoNum(holo, "height", defaultQuadH(node), pageVars);
        if ("text".equals(node.type())) {
            double[] sz = WorldHologram.textAutoSize(node, pageVars);
            w = sz[0];
            h = sz[1];
        }
        if (w <= 0 || h <= 0) {
            return -1;
        }
        Vec3 center = anchor.add(x, y, z);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 n = camera.getPosition().subtract(center).normalize(); // 朝向相机
        double ox = ray[0], oy = ray[1], oz = ray[2];
        double dx = ray[3], dy = ray[4], dz = ray[5];
        double denom = dx * n.x + dy * n.y + dz * n.z;
        if (Math.abs(denom) < 1e-9) {
            return -1; // 平行
        }
        double t = ((center.x - ox) * n.x + (center.y - oy) * n.y + (center.z - oz) * n.z) / denom;
        if (t < 0) {
            return -1; // 面板在射线背后
        }
        double hx = ox + dx * t - center.x;
        double hy = oy + dy * t - center.y;
        double hz = oz + dz * t - center.z;
        // 面板局部坐标（billboard 右/上轴 = 相机右/上轴）
        var rot = new org.joml.Matrix4f().rotation(camera.rotation()).transpose();
        var right = rot.transformDirection(new org.joml.Vector3f(1, 0, 0));
        var up = rot.transformDirection(new org.joml.Vector3f(0, 1, 0));
        double u = hx * right.x + hy * right.y + hz * right.z;
        double v = hx * up.x + hy * up.y + hz * up.z;
        if (Math.abs(u) > w / 2 || Math.abs(v) > h / 2) {
            return -1;
        }
        return t;
    }

    /** 文本默认命中框（世界单位）：2×0.25 的可点区域；button 1×0.25；rect/image 等默认 1×1。 */
    static double defaultQuadW(RenderNode node) {
        return "text".equals(node.type()) ? 2.0 : 1.0;
    }

    static double defaultQuadH(RenderNode node) {
        if ("text".equals(node.type()) || "button".equals(node.type())) {
            return 0.25;
        }
        return 1.0;
    }

}
