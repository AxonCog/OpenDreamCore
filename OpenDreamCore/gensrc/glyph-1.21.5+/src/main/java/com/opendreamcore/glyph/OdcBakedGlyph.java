package com.opendreamcore.glyph;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;

/**
 * 替换字形（1.21.11 / 26.1.2 两代共用）。
 *
 * 这两代的字形对象是「渲染实例工厂」：绘制时由它 createGlyph(...) 产出一个可渲染实例，
 * 实例自己携带颜色、阴影色、粗体偏移与样式，绘制语义完全由实例承担。因此接管方式变成
 * 子类化官方的数据载体实现类并原样传入我们的贴图与几何——绘制、样式、批处理全部沿官方
 * 路径，本类不覆写任何绘制逻辑，也就不需要读父类私有字段。
 *
 * 纹理通过构造参数绑进渲染类型与纹理视图，字体图集只负责原版字形，我们的贴图不参与
 * 图集打包，所以不会污染图集、也不受图集容量影响。
 *
 * 为什么必须是独立顶层类：Mixin 处理器会改写宿主类自身的字节码，定义在 @Mixin 体内的
 * 辅助类会被一并重命名，只能靠类名字符串匹配与反射私有字段来识别取值。独立顶层类归属
 * 本模组命名空间，名字与字段都稳定，直接子类化即可完成接管。
 */
public class OdcBakedGlyph extends BakedSheetGlyph {

    public OdcBakedGlyph(GlyphInfo info, GlyphRenderTypes types, GpuTextureView textureView,
                         float u0, float u1, float v0, float v1,
                         float left, float right, float up, float down) {
        // 构造顺序：字形信息、渲染类型、纹理视图、UV(u0,u1,v0,v1)、几何(left,right,up,down)
        super(info, types, textureView, u0, u1, v0, v1, left, right, up, down);
    }
}
