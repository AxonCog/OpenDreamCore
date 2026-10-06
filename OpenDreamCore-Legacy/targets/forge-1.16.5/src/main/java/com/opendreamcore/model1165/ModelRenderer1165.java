package com.opendreamcore.model1165;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.util.math.vector.Vector3f;
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

    /** 朝向基准：与原版 setupRotations 一致的 180-yaw（bbmodel 正面朝 -Z 时的经验值，实机可调）。 */
    public static float bodyYawOffset = 180.0F;

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
                    RLS.put(label, rl);
                } catch (Exception e) {
                    ModelLog.warn("贴图注册失败 " + label + ": " + e);
                }
            }
        });
        ModelAssetStore.get().load(gameDir.resolve("OpenDreamCore").resolve("assets").resolve("model"));
        LegacyModelRuleStore.get().applyLocalFile(
                gameDir.resolve("OpenDreamCore").resolve("visual").resolve("Model.yml"));
        ModelLog.info("1.16.5 模型 glue 就绪");
    }

    /** 服务端规则入库（MessageDispatcher visual_rules 调）。 */
    public static void applyServerBundle(java.util.Map<String, String> bundle) {
        LegacyModelRuleStore.get().applyBundle(bundle);
    }

    /** 实体替换入口：命中返回 true（调用方取消原版绘制）。 */
    public static boolean tryRender(Entity entity, float entityYaw, float partialTicks,
                                    MatrixStack stack,
                                    net.minecraft.client.renderer.IRenderTypeBuffer buffer,
                                    int packedLight) {
        LegacyModelRuleStore.Hit hit = LegacyModelRuleStore.get().hit(lookup(entity));
        if (hit == null) {
            return false;
        }
        float s = hit.scale;
        if (s != 1.0F) {
            stack.scale(s, s, s);
        }
        stack.mulPose(Vector3f.YP.rotationDegrees(bodyYawOffset - entityYaw));
        net.minecraft.util.math.vector.Matrix4f mat = stack.last().pose();
        net.minecraft.util.math.vector.Matrix3f nrm = stack.last().normal();

        for (BakedModel bm : hit.baked) {
            ResourceLocation rl = hit.textureOverride != null ? RLS.get(hit.textureOverride) : RLS.get(bm.texture);
            if (rl == null) {
                continue; // 缺贴图整条跳过（缺省即安全）
            }
            IVertexBuilder b = buffer.getBuffer(RenderType.entityCutout(rl));
            float[] q = bm.quads;
            for (int off = 0; off + 31 < q.length; off += 32) {
                for (int v = 0; v < 4; v++) {
                    int o = off + v * 8;
                    b.vertex(mat, q[o], q[o + 1], q[o + 2]);
                    b.color(255, 255, 255, 255);
                    b.uv(q[o + 3], q[o + 4]);
                    b.overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    b.uv2(packedLight);
                    b.normal(nrm, q[o + 5], q[o + 6], q[o + 7]);
                    b.endVertex();
                }
            }
            float[] t = bm.tris;
            for (int off = 0; off + 23 < t.length; off += 24) {
                for (int v = 0; v < 3; v++) {
                    int o = off + v * 8;
                    b.vertex(mat, t[o], t[o + 1], t[o + 2]);
                    b.color(255, 255, 255, 255);
                    b.uv(t[o + 3], t[o + 4]);
                    b.overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    b.uv2(packedLight);
                    b.normal(nrm, t[o + 5], t[o + 6], t[o + 7]);
                    b.endVertex();
                }
            }
        }
        return true;
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
