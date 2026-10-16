package com.opendreamcore.model1212;

import com.opendreamcore.client.model.LegacyModelRuleStore;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;
import com.opendreamcore.model.rule.ModelRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * 实体静态模型替换（1.12.2）：mixin 命中规则后用固定管线画烘焙模型。
 *
 * <p>贴图注册走 getDynamicTextureLocation(String, DynamicTexture)（stable_39 MCP 名）；
 * 顶点走 Tessellator+BufferBuilder（POSITION_TEX，无光照法线，全亮绘制）。
 */
public final class ModelRenderer1212 {

    private ModelRenderer1212() {
    }

    /** 贴图标签 → 资源位置。 */
    static final Map<String, ResourceLocation> RLS = new HashMap<String, ResourceLocation>();

    /** 启动期挂接：贴图注册回调 + 资产扫描。 */
    public static void init(java.nio.file.Path gameDir) {
        ModelAssetStore.get().bind(new ModelAssetStore.TextureSink() {
            @Override
            public void register(String label, byte[] png) {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                    if (img == null) {
                        return;
                    }
                    Minecraft mc = Minecraft.getMinecraft();
                    ResourceLocation rl = mc.getTextureManager().getDynamicTextureLocation(
                            "opendreamcore_model_" + safe(label),
                            new net.minecraft.client.renderer.texture.DynamicTexture(img));
                    ResourceLocation old = RLS.put(label, rl);
                    if (old != null && !old.equals(rl)) {
                        mc.getTextureManager().deleteTexture(old);
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

    /** 服务端规则入库。 */
    public static void applyServerBundle(java.util.Map<String, String> bundle) {
        LegacyModelRuleStore.get().applyBundle(bundle);
    }

    /** Replace only the body inside vanilla world transforms; later layers remain vanilla. */
    public static boolean renderBody(Entity entity, float partialTicks) {
        LegacyModelRuleStore.Hit hit = LegacyModelRuleStore.get().hit(lookup(entity));
        if (hit == null || !bodyTexturesReady(hit)) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        if (hit.scale != 1.0F) {
            GlStateManager.scale(hit.scale, hit.scale, hit.scale);
        }
        try {
            drawBody(mc, entity, hit, partialTicks);
        } finally {
            GlStateManager.popMatrix();
        }
        return true;
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

    private static void drawBody(Minecraft mc, Entity entity,
                                 LegacyModelRuleStore.Hit hit, float partialTicks) {
        if (hit.rig != null) {
            drawRig(mc, entity, hit, partialTicks);
            return;
        }
        Tessellator tess = Tessellator.getInstance();
        for (BakedModel bm : hit.baked) {
            ResourceLocation rl = hit.textureOverride != null ? RLS.get(hit.textureOverride)
                    : RLS.get(bm.texture);
            mc.getTextureManager().bindTexture(rl);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawQuads(tess, bm.quads, GL11.GL_QUADS, 4);
            drawQuads(tess, bm.tris, GL11.GL_TRIANGLES, 3);
        }
    }


    /** 按贴图聚顶点的缓冲出口（LegacyRigRenderer 流式回调后统一绘制）。 */
    private static final class GroupSink implements
            com.opendreamcore.client.model.LegacyRigRenderer.VertexSink {
        float[] v = new float[128];
        int n;

        public void vertex(float x, float y, float z, float u, float vv,
                           float nx, float ny, float nz) {
            if (n + 5 > v.length) {
                float[] g = new float[v.length * 2];
                System.arraycopy(v, 0, g, 0, n);
                v = g;
            }
            v[n++] = x;
            v[n++] = y;
            v[n++] = z;
            v[n++] = u;
            v[n++] = vv;
        }
    }

    /** 骨骼动画路径：AutoBind 自动绑定 + 逐贴图分组绘制（全亮，与静态路径一致）。 */
    private static void drawRig(Minecraft mc, Entity entity,
                                LegacyModelRuleStore.Hit hit, float partialTicks) {
        boolean moving = entity.posX != entity.prevPosX
                || entity.posZ != entity.prevPosZ;
        boolean hurting = entity instanceof net.minecraft.entity.EntityLivingBase
                && ((net.minecraft.entity.EntityLivingBase) entity).hurtTime > 0;
        final java.util.Map<String, GroupSink> groups =
                new java.util.LinkedHashMap<String, GroupSink>();
        com.opendreamcore.client.model.LegacyRigRenderer.render(
                entity.getUniqueID(), hit.rig, hit.ruleAnims(),
                hurting ? "hurt" : (moving ? "walk" : "idle"), moving, hurting,
                entity.ticksExisted + partialTicks,
                texture -> {
                    GroupSink g = groups.get(texture);
                    if (g == null) {
                        g = new GroupSink();
                        groups.put(texture, g);
                    }
                    return g;
                });
        Tessellator tess = Tessellator.getInstance();
        for (java.util.Map.Entry<String, GroupSink> e : groups.entrySet()) {
            ResourceLocation rl = hit.textureOverride != null
                    ? RLS.get(hit.textureOverride) : RLS.get(e.getKey());
            if (rl == null) {
                continue;
            }
            mc.getTextureManager().bindTexture(rl);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GroupSink g = e.getValue();
            net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
            bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            for (int i = 0; i + 4 < g.n; i += 5) {
                bb.pos(g.v[i], g.v[i + 1], g.v[i + 2]).tex(g.v[i + 3], g.v[i + 4]).endVertex();
            }
            tess.draw();
        }
    }

    private static void drawQuads(Tessellator tess, float[] data, int mode, int vertsPerPrim) {
        if (data.length < 8 * vertsPerPrim) {
            return;
        }
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(mode, DefaultVertexFormats.POSITION_TEX);
        for (int off = 0; off + 8 * vertsPerPrim - 1 < data.length; off += 8 * vertsPerPrim) {
            for (int v = 0; v < vertsPerPrim; v++) {
                int o = off + v * 8;
                bb.pos(data[o], data[o + 1], data[o + 2]).tex(data[o + 3], data[o + 4]).endVertex();
            }
        }
        tess.draw();
    }

    /** 实体侧匹配条件：type=EntityList id，name=自定义名。 */
    static ModelRules.Lookup lookup(final Entity entity) {
        return new ModelRules.Lookup() {
            @Override
            public String get(String key) {
                if (ModelRules.TYPE.equals(key)) {
                    return net.minecraft.entity.EntityList.getEntityString(entity);
                }
                if (ModelRules.NAME.equals(key)) {
                    return entity.hasCustomName() ? entity.getCustomNameTag() : null;
                }
                return null;
            }
        };
    }

    static String safe(String s) {
        return s.replaceAll("[^a-z0-9/._-]", "_").toLowerCase(java.util.Locale.ROOT);
    }
}
