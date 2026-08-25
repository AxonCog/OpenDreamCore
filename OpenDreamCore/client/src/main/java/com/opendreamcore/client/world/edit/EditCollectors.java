package com.opendreamcore.client.world.edit;

import com.opendreamcore.client.WorldHologram;
import com.opendreamcore.ui.RenderNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 世界编辑几何收集器（C5 自 WorldEditor 抽出的纯静态函数）：
 * 面板中心点/边缘线收集（对齐吸附用）与射线命中深度计算。
 * 无状态、无副作用，输出写入调用方提供的 out 列表。
 */
public final class EditCollectors {

    private EditCollectors() {
    }

    /** 收集全部可见全息元素的中心点 (x,y) → out。skip 中的 id 跳过（正在拖拽的元素不参与对齐）。 */
    public static void collectCenters(List<RenderNode> nodes, String activeTab,
                                      Map<String, Object> vars, double[] parentOffset,
                                      Set<String> skip, List<double[]> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            Object raw = node.props().get("hologram");
            double bx = parentOffset == null ? 0 : parentOffset[0];
            double by = parentOffset == null ? 0 : parentOffset[1];
            double bz = parentOffset == null ? 0 : parentOffset[2];
            double[] childOffset = parentOffset;
            if (raw instanceof Map<?, ?> holo) {
                double x = bx + WorldHologram.holoNum(holo, "x", 0, vars);
                double y = by + WorldHologram.holoNum(holo, "y", 0, vars);
                if (!skip.contains(node.id())) {
                    out.add(new double[]{x, y});
                }
                childOffset = new double[]{x, y, bz + WorldHologram.holoNum(holo, "z", 0, vars)};
            }
            collectCenters(node.children(), activeTab, vars, childOffset, skip, out);
        }
    }

    /**
     * 收集全部可见全息元素的边缘线 → out：{轴(0=x/1=y), 坐标}。
     * 每元素产出 x-w/2、x、x+w/2 与 y-h/2、y、y+h/2 六条参考线。
     */
    public static void collectEdges(List<RenderNode> nodes, String activeTab,
                                    Map<String, Object> vars, double[] parentOffset,
                                    Set<String> skip, List<double[]> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            Object raw = node.props().get("hologram");
            double bx = parentOffset == null ? 0 : parentOffset[0];
            double by = parentOffset == null ? 0 : parentOffset[1];
            double bz = parentOffset == null ? 0 : parentOffset[2];
            double[] childOffset = parentOffset;
            if (raw instanceof Map<?, ?> holo) {
                double x = bx + WorldHologram.holoNum(holo, "x", 0, vars);
                double y = by + WorldHologram.holoNum(holo, "y", 0, vars);
                if (!skip.contains(node.id())) {
                    double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, vars);
                    double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, vars);
                    out.add(new double[]{0, x - w / 2});
                    out.add(new double[]{0, x});
                    out.add(new double[]{0, x + w / 2});
                    out.add(new double[]{1, y - h / 2});
                    out.add(new double[]{1, y});
                    out.add(new double[]{1, y + h / 2});
                }
                childOffset = new double[]{x, y, bz + WorldHologram.holoNum(holo, "z", 0, vars)};
            }
            collectEdges(node.children(), activeTab, vars, childOffset, skip, out);
        }
    }

    /** 射线命中深度平方（越小越近相机）；异常时返回 MAX_VALUE 排序垫底。 */
    public static double hitDepth(RenderNode node, net.minecraft.client.Camera camera,
                                  net.minecraft.world.phys.Vec3 anchor) {
        try {
            Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
            double x = WorldHologram.holoNum(holo, "x", 0, Map.of());
            double y = WorldHologram.holoNum(holo, "y", 0, Map.of());
            double z = WorldHologram.holoNum(holo, "z", 0, Map.of());
            net.minecraft.world.phys.Vec3 cam = camera.getPosition();
            double dx = anchor.x + x - cam.x;
            double dy = anchor.y + y - cam.y;
            double dz = anchor.z + z - cam.z;
            return dx * dx + dy * dy + dz * dz;
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }
}
