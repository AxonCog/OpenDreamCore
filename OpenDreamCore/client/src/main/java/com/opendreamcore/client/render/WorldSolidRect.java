package com.opendreamcore.client.render;

import com.opendreamcore.client.CompatBuffer;
import com.opendreamcore.client.CompatRender;

/**
 * 世界里画纯色矩形/边框的批次入口。
 *
 * <p>为什么不是直接 begin(POSITION_COLOR)：世界渲染的着色器要采样贴图，只给无 UV 的纯色批次
 * 是看不见的（实测血条底/前景永远不显示）。所以固定绑一张 1x1 白纹理，颜色仍由顶点色给。
 *
 * <p>为什么用贴图着色器而不是 {@link CompatRender#setColorShader()}：它依赖
 * GameRenderer.getPositionColorShader()，那个 getter 在 1.21.2 起被移除，
 * 而设不上的结果是静默沿用上一个着色器 → 整批矩形隐形。
 *
 * <p>性能决策：矩形顶点不缓存（极速化 goal task-2）。每帧生成只是一扌 float 写入，
 * 而指纹键构建（字符串拼接+哈希）本身比生成本身还贵，缓存会反向劣化；
 * 文本侧的真热点（O(n²) 折行）已在 HoloTextRender.wrapText 按对缓存。
 */
public final class WorldSolidRect {

    private WorldSolidRect() {
    }

    private static boolean textureWarned;

    /** 纯色批次：已绑好白纹理与着色器，直接加顶点即可。 */
    public static CompatBuffer begin(com.mojang.blaze3d.vertex.VertexFormat.Mode mode) {
        net.minecraft.resources.ResourceLocation white =
                com.opendreamcore.client.resources.LooseResourceLoader.whiteTexture();
        if (white != null) {
            CompatRender.setShaderTexture(0, white);
        } else if (!textureWarned) {
            // 传 null 进去等于解绑 sampler，会沿用上一张贴图 → 颜色错乱。宁可跳过绑定
            // 并报一次，也不要让整批矩形默默画成别的颜色。
            textureWarned = true;
            org.slf4j.LoggerFactory.getLogger("OpenDreamCore").warn(
                    "[OpenDreamCore] 无可用白纹理，纯色矩形将跳过贴图绑定（颜色可能不准）");
        }
        CompatRender.bindSolidColorShader();
        return CompatRender.begin(mode, CompatRender.worldTextureFormat());
    }
}