package com.opendreamcore.mixin;

import com.opendreamcore.legacy.LegacyFontGlyphs;
import net.minecraft.client.gui.fonts.Font;
import net.minecraft.client.gui.fonts.IGlyph;
import net.minecraft.client.gui.fonts.TexturedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 全局字符替换（1.16.5）：注入字体层的字形出口，而不是去换字体实例。
 *
 * 这代每个字体实例持有自己的字形表，绘制和量宽都要经过它：
 * getGlyph 拿的是要画的那个字形，getGlyphInfo 拿的是量宽用的那个。
 * 按宽度居中、右对齐、裁切（plainSubstrByWidth / substrByWidth）全都拿这两个
 * 出口的结果去算。所以在这两处把命中字符换成我们的替换字形，绘制、量宽、裁切
 * 三条路径一次性全接管，而且_WIDTH_和_画_用的永远同一份数据，不会一个替换一个不替换。
 *
 * 关于方法名与描述符：这里写的是 srg 名（func_238559_b_ 取字形、func_238557_a_ 取量宽信息），
 * 不是源码里看到的名字。这代（1.13 起）Forge 取消了运行时反混淆，游戏里跑的就是 srg 名；
 * 而注解里的方法串只是个字符串常量，重混淆工具不认识它、不会改写它（jar 里原样保留），
 * 也没有 refmap 在运行时把它翻过去。所以源码名写在这里匹配不上，必须直接写 srg 名。
 * 名字取自 ForgeGradle 生成的 mojmap→srg 映射表，并与运行时真实类的 public 方法逐个核对过。
 *
 * 命中才返回替换字形，没命中就什么都不做、继续走原版。所以既不需要整段取消，
 * 也不会因为一行里出现一个替换字就把整行交给另一套排版。
 */
@Mixin(Font.class)
public abstract class MixinFontGlyphs {

    @Inject(method = "func_238559_b_(I)Lnet/minecraft/client/gui/fonts/TexturedGlyph;",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyph(int codePoint, CallbackInfoReturnable<TexturedGlyph> cir) {
        TexturedGlyph glyph = LegacyFontGlyphs.glyph(codePoint);
        if (glyph != null) {
            cir.setReturnValue(glyph);
        }
    }

    @Inject(method = "func_238557_a_(I)Lnet/minecraft/client/gui/fonts/IGlyph;",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyphInfo(int codePoint, CallbackInfoReturnable<IGlyph> cir) {
        IGlyph info = LegacyFontGlyphs.info(codePoint);
        if (info != null) {
            cir.setReturnValue(info);
        }
    }
}
