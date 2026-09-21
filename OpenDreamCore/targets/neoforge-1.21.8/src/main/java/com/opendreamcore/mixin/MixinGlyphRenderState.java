package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.8 GUI 批处理文本渲染收口：GlyphRenderState.buildVertices 里的
 * BakedGlyph.renderChar(...) 调用。OdcBakedGlyph 覆写被 NeoForge mixin 内部类
 * 重命名吞噬时不完全可靠——这里直接 @Redirect 到自绘，100% 不依赖覆写。
 */
@Mixin(net.minecraft.client.gui.render.state.GlyphRenderState.class)
public abstract class MixinGlyphRenderState {

    @Redirect(method = "buildVertices(Lcom/mojang/blaze3d/vertex/VertexConsumer;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"
                            + "renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph$GlyphInstance;"
                            + "Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;IZ)V"))
    private void odc$redirectRenderChar(BakedGlyph self, BakedGlyph.GlyphInstance inst,
                                        Matrix4f pose, VertexConsumer consumer, int light, boolean shadow) {
        if (self.getClass().getName().contains("OdcBakedGlyph")) {
            odc$renderDirect(self, inst, pose, consumer, light, shadow);
        } else {
            self.renderChar(inst, pose, consumer, light, shadow);
        }
    }

    /** 自绘：按 BakedGlyph.render 语义用 private 字段画 quad（无 italic/bold 特判）。
     *  gif 字形动画：rl 字段反射（mixin 重命名下字段稳定）→ LooseResourceLoader 反查播放器
     *  切帧表 uv——只改顶点 UV，纹理对象固定，零 upload 零驱动风险。 */
    private static void odc$renderDirect(BakedGlyph g, BakedGlyph.GlyphInstance gi,
                                         Matrix4f pose, VertexConsumer consumer, int light, boolean shadow) {
        float x = gi.x();
        float y = gi.y();
        int color = gi.color();
        try {
            Class<?> c = BakedGlyph.class;
            float left = fieldF(c, "left", g);
            float right = fieldF(c, "right", g);
            float up = fieldF(c, "up", g);
            float down = fieldF(c, "down", g);
            float u0 = fieldF(c, "u0", g);
            float v0 = fieldF(c, "v0", g);
            float u1 = fieldF(c, "u1", g);
            float v1 = fieldF(c, "v1", g);
            // gif 动画：rl 字段反射 → gifFrames 反查播放器当前帧，切帧表 uv
            String rlPath = fieldRlPath(g);
            if (rlPath != null) {
                var gf = com.opendreamcore.client.resources.LooseResourceLoader.gifFrames(rlPath);
                if (gf.frames() > 1) {
                    float step = 1.0F / gf.frames();
                    u0 = gf.frame() * step;
                    u1 = u0 + step;
                    v0 = 0.0F;
                    v1 = 1.0F;
                }
            }
            float f = x + left;
            float f1 = x + right;
            float f2 = y + up;
            float f3 = y + down;
            float z = shadow ? 0.0F : 0.001F;
            consumer.addVertex(pose, f, f2, z).setColor(color).setUv(u0, v0).setLight(light);
            consumer.addVertex(pose, f, f3, z).setColor(color).setUv(u0, v1).setLight(light);
            consumer.addVertex(pose, f1, f3, z).setColor(color).setUv(u1, v1).setLight(light);
            consumer.addVertex(pose, f1, f2, z).setColor(color).setUv(u1, v0).setLight(light);
            } catch (Throwable t) {
            // 兜底：反射/加载失败也不能让 batch 空顶点（BufferBuilder "was empty" 会崩渲染），写最小 quad
            try {
                consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0xffffffff).setUv(0.0F, 0.0F).setLight(light);
                consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0xffffffff).setUv(0.0F, 0.0F).setLight(light);
                consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0xffffffff).setUv(0.0F, 0.0F).setLight(light);
                consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0xffffffff).setUv(0.0F, 0.0F).setLight(light);
            } catch (Throwable ignored2) {
            }
        }
    }

    /** rl 字段反射（mixin 重命名 OdcBakedGlyph 实例上字段稳定存在）。 */
    private static String fieldRlPath(Object g) {
        try {
            java.lang.reflect.Field f = g.getClass().getDeclaredField("rl");
            f.setAccessible(true);
            Object v = f.get(g);
            if (v instanceof net.minecraft.resources.ResourceLocation r && r.getPath().startsWith("gif/")) {
                return r.getPath();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float fieldF(Class<?> c, String name, Object target) throws Exception {
        java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.getFloat(target);
    }
}
