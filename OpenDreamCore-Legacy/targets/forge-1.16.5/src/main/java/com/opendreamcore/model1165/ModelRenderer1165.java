package com.opendreamcore.model1165;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.opendreamcore.client.model.LegacyModelRuleStore;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;
import com.opendreamcore.model.rule.ModelRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体静态模型替换（1.16.5）： mixin 命中规则后用这里把烘焙模型画进矩阵栈。
 *
 * <p>贴图注册走 NativeImage+DynamicTexture（official 映射）；顶点走
 * IVertexBuilder.vertex(Matrix4f,...) 链（1.16.5 无固定管线）。
 */
public final class ModelRenderer1165 {

    private ModelRenderer1165() {
    }

    /** 贴图标签 → 资源位置（注册成功后缓存）。 */
    static final Map<String, ResourceLocation> RLS = new HashMap<String, ResourceLocation>();

    /** 启动期挂接：贴图注册回调 + 资产扫描。 */
    public static void init(java.nio.file.Path gameDir) {
        ModelAssetStore.get().bind(new ModelAssetStore.TextureSink() {
            @Override
            public void register(String label, byte[] png) {
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                    DynamicTexture tex = new DynamicTexture(img);
                    TextureManager tm = Minecraft.getInstance().getTextureManager();
                    ResourceLocation rl = tm.register("opendreamcore_model_" + safe(label), tex);
                    ResourceLocation old = RLS.put(label, rl);
                    if (old != null && !old.equals(rl)) {
                        tm.release(old);
                    }
                } catch (Exception e) {
                    ModelLog.warn("贴图注册失败 " + label + ": " + e);
                }
            }
        });
        java.nio.file.Path config = gameDir.resolve("OpenDreamCore");
        ModelAssetStore.get().load(config.resolve("assets").resolve("model"));
        LegacyModelRuleStore.get().applyLocalFile(config.resolve("visual").resolve("Model.yml"));
        java.nio.file.Path itemFile = config.resolve("ItemModel.yml");
        if (!java.nio.file.Files.isRegularFile(itemFile)) {
            itemFile = config.resolve("visual").resolve("ItemModel.yml");
        }
        LegacyModelRuleStore.get().applyLocalItemFile(itemFile);
    }

    /** 服务端规则入库（MessageDispatcher visual_rules 调）。 */
    public static void applyServerBundle(java.util.Map<String, String> bundle) {
        LegacyModelRuleStore.get().applyBundle(bundle);
    }

    /** Render replacement body only; caller owns world transform and all later layers. */
    public static boolean renderBody(Entity entity, float partialTicks,
                                     MatrixStack stack,
                                     net.minecraft.client.renderer.IRenderTypeBuffer buffer,
                                     int packedLight) {
        LegacyModelRuleStore.Hit hit = LegacyModelRuleStore.get().hit(lookup(entity));
        if (hit == null || !bodyTexturesReady(hit)) {
            return false;
        }
        if (hit.scale != 1.0F) {
            stack.pushPose();
            stack.scale(hit.scale, hit.scale, hit.scale);
        }
        try {
            if (hit.rig != null) {
                renderRigBody(entity, partialTicks, hit, stack, buffer, packedLight);
            } else {
                renderStaticBody(hit, stack, buffer, packedLight);
            }
        } finally {
            if (hit.scale != 1.0F) {
                stack.popPose();
            }
        }
        return true;
    }

    private static void renderStaticBody(LegacyModelRuleStore.Hit hit, MatrixStack stack,
                                         net.minecraft.client.renderer.IRenderTypeBuffer buffer,
                                         int packedLight) {
        net.minecraft.util.math.vector.Matrix4f mat = stack.last().pose();
        net.minecraft.util.math.vector.Matrix3f nrm = stack.last().normal();
        for (BakedModel bm : hit.baked) {
            ResourceLocation rl = RLS.get(hit.textureOverride != null ? hit.textureOverride : bm.texture);
            if (rl == null) {
                continue;
            }
            IVertexBuilder b = buffer.getBuffer(RenderType.entityCutout(rl));
            emit(b, mat, nrm, bm.quads, 4, packedLight);
            emit(b, mat, nrm, bm.tris, 3, packedLight);
        }
    }

    private static void emit(IVertexBuilder b, net.minecraft.util.math.vector.Matrix4f mat,
                             net.minecraft.util.math.vector.Matrix3f nrm, float[] data,
                             int sourceCount, int packedLight) {
        for (int off = 0; off + sourceCount * 8 <= data.length; off += sourceCount * 8) {
            for (int v = 0; v < 4; v++) {
                int o = off + (v == 3 && sourceCount == 3 ? 2 : v) * 8;
                b.vertex(mat, data[o], data[o + 1], data[o + 2]);
                b.color(255, 255, 255, 255);
                b.uv(data[o + 3], data[o + 4]);
                b.overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                b.uv2(packedLight);
                b.normal(nrm, data[o + 5], data[o + 6], data[o + 7]);
                b.endVertex();
            }
        }
    }

    private static void renderRigBody(Entity entity, float partialTicks,
                                       LegacyModelRuleStore.Hit hit, MatrixStack stack,
                                       net.minecraft.client.renderer.IRenderTypeBuffer buffer,
                                       int packedLight) {
        net.minecraft.util.math.vector.Matrix4f mat = stack.last().pose();
        net.minecraft.util.math.vector.Matrix3f nrm = stack.last().normal();
        boolean moving = entity.getDeltaMovement().x * entity.getDeltaMovement().x
                + entity.getDeltaMovement().z * entity.getDeltaMovement().z > 1.0e-6;
        boolean hurting = entity instanceof net.minecraft.entity.LivingEntity
                && ((net.minecraft.entity.LivingEntity) entity).hurtTime > 0;
        com.opendreamcore.client.model.LegacyRigRenderer.render(
                entity.getUUID(), hit.rig, hit.ruleAnims(),
                hurting ? "hurt" : (moving ? "walk" : "idle"), moving, hurting,
                entity.tickCount + partialTicks,
                texture -> {
                    ResourceLocation rl = RLS.get(hit.textureOverride != null ? hit.textureOverride : texture);
                    if (rl == null) {
                        return null;
                    }
                    IVertexBuilder b = buffer.getBuffer(RenderType.entityCutout(rl));
                    return (x, y, z, u, v, nx, ny, nz) -> {
                        b.vertex(mat, x, y, z).color(255, 255, 255, 255).uv(u, v)
                                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                .uv2(packedLight).normal(nrm, nx, ny, nz).endVertex();
                    };
                });
    }

    public static ResourceLocation texture(String label) {
        return label == null ? null : RLS.get(label);
    }

    public static boolean texturesReady(LegacyModelRuleStore.Hit hit) {
        if (hit.textureOverride != null) {
            return RLS.get(hit.textureOverride) != null;
        }
        if (hit.rig != null) {
            for (com.opendreamcore.model.bake.BakedRig.Bone bone : hit.rig.bones) {
                for (com.opendreamcore.model.bake.BakedRig.Part part : bone.parts) {
                    if (RLS.get(part.texture) == null) {
                        return false;
                    }
                }
            }
        } else {
            for (BakedModel bm : hit.baked) {
                if (RLS.get(bm.texture) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean bodyTexturesReady(LegacyModelRuleStore.Hit hit) {
        return texturesReady(hit);
    }

    /** 实体侧匹配条件：type=注册名，name=自定义名。 */
    static ModelRules.Lookup lookup(final Entity entity) {
        return new ModelRules.Lookup() {
            @Override
            public String get(String key) {
                if (ModelRules.TYPE.equals(key)) {
                    ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
                    return id == null ? null : id.toString();
                }
                if (ModelRules.NAME.equals(key)) {
                    return entity.hasCustomName() && entity.getCustomName() != null
                            ? entity.getCustomName().getString() : null;
                }
                return null; // lore/nbt 实体侧不适用
            }
        };
    }

    static String safe(String s) {
        return s.replaceAll("[^a-z0-9/._-]", "_").toLowerCase(java.util.Locale.ROOT);
    }
}
