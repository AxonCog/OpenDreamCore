package com.opendreamcore.client.model;

import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * gen-1.21.1 模型渲染 glue（fabric/neoforge 1.21.1 共用）。
 *
 * <p>挂点 = LivingEntityRenderer.render(HEAD, cancellable)；实体旋转由 mixin 侧处理
 * （复刻原版 setupRotations 的 yaw 部分），本类只负责：类型 id / 名字串提取、
 * 贴图注册（PNG 字节 → DynamicTexture）、顶点写出（流式 API）。
 */
public final class GenModelRenderer {

    /** bbmodel/geo 的坐标单位 = 1/16 格。 */
    public static final float SCALE = 1.0f / 16.0f;

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();

    private GenModelRenderer() {
    }

    /** 实体类型注册名（如 minecraft:pig）。 */
    public static String typeId(Entity entity) {
        try {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 实体匹配名：优先自定义名，退显示名；去样式码。 */
    public static String nameOf(Entity entity) {
        Component c = entity.getCustomName() != null ? entity.getCustomName() : entity.getDisplayName();
        String s = c == null ? "" : c.getString();
        if (s == null) {
            return "";
        }
        return s.replace("\u00a7", "").trim();
    }

    /** 贴图标签 → RL（懒注册 PNG 字节；失败回退白色占位由原版缺贴图行为兜底）。 */
    public static ResourceLocation texture(String label) {
        ResourceLocation rl = TEXTURES.get(label);
        if (rl != null) {
            return rl;
        }
        rl = ResourceLocation.fromNamespaceAndPath("opendreamcore",
                "model/" + label.replaceAll("[^a-z0-9/._-]", "_").toLowerCase(java.util.Locale.ROOT));
        try {
            byte[] png = ModelAssetStore.get().texture(label);
            if (png != null) {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(() -> "opendreamcore-model", img));
            }
        } catch (Throwable t) {
            ModelLog.warn("贴图注册失败 " + label + ": " + t);
        }
        TEXTURES.put(label, rl);
        return rl;
    }

    /** 顶点写出：quads=4 顶点/图元，tris=3 顶点/图元。 */
    public static void render(PoseStack stack, MultiBufferSource buffers, EntityModelReplacer.Hit hit, int light) {
        for (BakedModel model : hit.models) {
            ResourceLocation rl = texture(model.texture);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(rl));
            PoseStack.Pose pose = stack.last();
            draw(model.quads, 4, pose, vc, light);
            draw(model.tris, 3, pose, vc, light);
        }
    }

    private static void draw(float[] data, int vertsPerPrim, PoseStack.Pose pose, VertexConsumer vc, int light) {
        for (int i = 0; i + vertsPerPrim * 8 <= data.length; i += vertsPerPrim * 8) {
            for (int v = 0; v < vertsPerPrim; v++) {
                int o = i + v * 8;
                vc.addVertex(pose, data[o] * SCALE, data[o + 1] * SCALE, data[o + 2] * SCALE)
                        .setColor(255, 255, 255, 255)
                        .setUv(data[o + 3], data[o + 4])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
            }
        }
    }
}
