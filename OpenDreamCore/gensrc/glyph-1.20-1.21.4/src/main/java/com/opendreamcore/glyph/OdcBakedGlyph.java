package com.opendreamcore.glyph;

import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

/**
 * 替换字形（1.20.1 / 1.21.1 / 1.21.4 三代共用）。
 *
 * 设计要点：绘制语义（颜色、粗体加厚、斜体错切、阴影偏移与阴影色、深度层次）全部
 * 交给原版字形类自己的绘制实现，本类只负责把我们自己的贴图与算好的几何/UV 通过父类
 * 构造参数交进去：
 *   - 贴图：GlyphRenderTypes.createForColorTexture(我们的 RL) 交给父类，原版的
 *     renderType(mode) 会选中我们的渲染类型，批处理取 buffer 时自然用我们的纹理；
 *   - 几何与 UV：由构造参数写入父类字段，原版绘制直接用，本类无需覆写任何方法，
 *     也就无需读取父类私有字段。
 *
 * 这三代的字形构造不带纹理视图句柄（贴图与渲染类型绑定，由渲染类型自身解析纹理），
 * 比更高版本少一个参数，其余完全一致。
 *
 * 为什么必须是独立顶层类：Mixin 处理器会改写宿主类自身的字节码，定义在 @Mixin 体内
 * 的辅助类会被一并重命名，只能靠类名字符串匹配与反射私有字段来识别取值。独立顶层类
 * 归属本模组命名空间，名字与字段都稳定，直接子类化即可完成接管。
 */
public class OdcBakedGlyph extends BakedGlyph {

    public OdcBakedGlyph(GlyphRenderTypes types,
                         float u0, float u1, float v0, float v1,
                         float left, float right, float up, float down) {
        // 本代构造顺序：渲染类型、UV(u0,u1,v0,v1)、几何(left,right,up,down)
        super(types, u0, u1, v0, v1, left, right, up, down);
    }
}
