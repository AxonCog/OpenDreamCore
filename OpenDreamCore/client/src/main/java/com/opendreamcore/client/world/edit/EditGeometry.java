package com.opendreamcore.client.world.edit;

/**
 * 世界编辑·对齐/分布几何纯函数（C5 自 WorldEditor 抽出）。
 * bounds 约定：{minX, minY, maxX, maxY}（世界可见范围包围盒）。
 * 全部无副作用：输入坐标输出位移/目标点，写回由调用方完成。
 */
public final class EditGeometry {

    private EditGeometry() {
    }

    /**
     * 组对齐：计算整组包围盒 (minX..maxX, minY..maxY) 对齐到 bounds 所需的整体位移。
     *
     * @return {dx, dy}；mode 不支持时返回 null
     */
    public static double[] alignDelta(String mode, double[] bounds,
                                      double minX, double maxX, double minY, double maxY) {
        double dx = 0, dy = 0;
        switch (mode == null ? "" : mode) {
            case "left" -> dx = bounds[0] - minX;
            case "right" -> dx = bounds[2] - maxX;
            case "hcenter" -> dx = (bounds[0] + bounds[2]) / 2 - (minX + maxX) / 2;
            case "top" -> dy = bounds[1] - minY;
            case "bottom" -> dy = bounds[3] - maxY;
            case "vcenter" -> dy = (bounds[1] + bounds[3]) / 2 - (minY + maxY) / 2;
            default -> {
                return null;
            }
        }
        return new double[]{dx, dy};
    }

    /**
     * 单元素对齐：宽 w 高 h 的元素在 bounds 内按 mode 对齐后的中心点。
     * 未被该模式约束的一轴保持元素当前坐标。
     *
     * @return {x, y}；mode 不支持时返回 null
     */
    public static double[] alignTarget(String mode, double[] bounds, double w, double h,
                                       double curX, double curY) {
        double x = curX, y = curY;
        switch (mode == null ? "" : mode) {
            case "left" -> x = bounds[0] + w / 2;
            case "right" -> x = bounds[2] - w / 2;
            case "hcenter" -> x = (bounds[0] + bounds[2]) / 2;
            case "top" -> y = bounds[1] + h / 2;
            case "bottom" -> y = bounds[3] - h / 2;
            case "vcenter" -> y = (bounds[1] + bounds[3]) / 2;
            default -> {
                return null;
            }
        }
        return new double[]{x, y};
    }

    /**
     * 等间距分布：把若干元素（按轴中心排序后）均匀铺满 [lo, hi]。
     *
     * @param sizes 每个元素沿该轴的尺寸（与排序后顺序一致）
     * @return 每个元素的新轴中心；少于 2 个元素时返回 null
     */
    public static double[] distributeCenters(double lo, double hi, double[] sizes) {
        if (sizes == null || sizes.length < 2) {
            return null;
        }
        double totalSize = 0;
        for (double s : sizes) {
            totalSize += s;
        }
        double gap = (hi - lo - totalSize) / (sizes.length - 1);
        double[] out = new double[sizes.length];
        double cursor = lo;
        for (int i = 0; i < sizes.length; i++) {
            out[i] = cursor + sizes[i] / 2;
            cursor += sizes[i] + gap;
        }
        return out;
    }

    /** 坐标镜像：绕轴心 center 翻转（保留两位小数）。 */
    public static double mirrorCoord(double center, double coord) {
        return Math.round((2 * center - coord) * 100) / 100.0;
    }

    /** 朝向镜像：yaw 取反（保留一位小数）。 */
    public static double mirrorYaw(double yaw) {
        return Math.round(-yaw * 10) / 10.0;
    }
}
