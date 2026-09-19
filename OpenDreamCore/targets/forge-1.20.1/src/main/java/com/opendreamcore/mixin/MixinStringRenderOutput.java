package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualFontReplace;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 全局字符替换（font 级）：FontConfig 命中的字符在任意文本渲染路径生效——
 * 聊天、输入框、书、箱子名、原版界面全走 Font#StringRenderOutput.accept，
 * 这里在字符落笔前拦截，命中就画彩色贴图 quad 顶替字模并推进游标。
 *
 * 与 UI 内逐字符替换（TextElements.drawCharReplaced）并存但不重复：
 * UI 内那个不打开（无命中）时不进混合路径；这边规则为空时零开销返回。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class MixinStringRenderOutput {

    @Shadow
    private MultiBufferSource bufferSource;
    @Shadow
    private boolean dropShadow;
    @Shadow
    private Matrix4f pose;
    @Shadow
    private int packedLightCoords;
    @Shadow
    float x;
    @Shadow
    float y;

    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyph(int index, Style style, int codepoint,
                                            CallbackInfoReturnable<Boolean> cir) {
        // 无替换规则直接放行（快路径）
        if (!VisualFontReplace.hasAny()) {
            return;
        }
        // 只处理 BMP（代理对的高位由 accept 拆开两次，各自命中率低又容易撕裂字形）
        char c = (char) codepoint;
        if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
            return;
        }
        VisualFontReplace.CharGlyph g = VisualFontReplace.glyphFor(c);
        if (g == null) {
            return;
        }
        ResourceLocation rl = LooseResourceLoader.lookup(g.texture());
        if (rl == null) {
            return;
        }
        // 彩色贴图 quad 顶替字模：position = 当前游标 (x, y)。uv 用实际贴图像素归一化
        // （同 TextElements 的 blit 语义：u/v/frameW/frameH 是像素，total 尺寸来自 sizeOf）。
        var consumer = bufferSource.getBuffer(RenderType.text(rl));
        float w = Math.max(1, g.frameW());
        float h = Math.max(1, g.frameH());
        LooseResourceLoader.Size size = LooseResourceLoader.sizeOf(g.texture());
        float texW = size != null && size.width() > 0 ? size.width() : Math.max(1, g.frameW());
        float texH = size != null && size.height() > 0 ? size.height() : Math.max(1, g.frameH());
        float u0 = g.u() / texW;
        float v0 = g.v() / texH;
        float u1 = (g.u() + g.frameW()) / texW;
        float v1 = (g.v() + g.frameH()) / texH;
        float z = 0.0F;
        // 1.20.1 的 VertexConsumer 还是老一套 vertex/uv/color/uv2 链（1.21 才改叫 addVertex/setUv/setColor/setLight）
        consumer.vertex(pose, this.x, this.y, z).uv(u0, v0).color(255, 255, 255, 255).uv2(this.packedLightCoords).endVertex();
        consumer.vertex(pose, this.x, this.y + h, z).uv(u0, v1).color(255, 255, 255, 255).uv2(this.packedLightCoords).endVertex();
        consumer.vertex(pose, this.x + w, this.y + h, z).uv(u1, v1).color(255, 255, 255, 255).uv2(this.packedLightCoords).endVertex();
        consumer.vertex(pose, this.x + w, this.y, z).uv(u1, v0).color(255, 255, 255, 255).uv2(this.packedLightCoords).endVertex();
        // 游标推进：替换宽度 + 阴影余量（与原 addCharacter 的 advance 语义对齐）
        this.x += g.fontWidth() + (this.dropShadow ? 1.0F : 0.0F);
        cir.setReturnValue(Boolean.TRUE);
    }
}