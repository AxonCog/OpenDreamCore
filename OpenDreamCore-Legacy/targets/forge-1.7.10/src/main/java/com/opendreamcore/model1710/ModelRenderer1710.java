package com.opendreamcore.model1710;

import com.opendreamcore.client.model.LegacyModelRuleStore;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;
import com.opendreamcore.model.rule.ModelRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * 实体静态模型替换（1.7.10）：mixin 命中规则后用固定管线画烘焙模型。
 *
 * <p>贴图注册走 RenderEngine.loadTexture(RL, DynamicTexture)（TextureRegistrar1710 同款）；
 * 顶点走 Tessellator+WorldRenderer（addVertexWithUV，全亮）。
 */
public final class ModelRenderer1710 {

    private ModelRenderer1710() {
    }

    /** 贴图标签 → 资源位置。 */
    static final Map<String, ResourceLocation> RLS = new HashMap<String, ResourceLocation>();

    /** 启动期挂接：贴图注册回调 + 资产扫描。 */
    public static void init(java.io.File gameDir) {
        ModelAssetStore.get().bind(new ModelAssetStore.TextureSink() {
            @Override
            public void register(String label, byte[] png) {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                    if (img == null) {
                        return;
                    }
                    ResourceLocation rl = new ResourceLocation("opendreamcore",
                            "modeltex/" + safe(label));
                    net.minecraft.client.renderer.texture.TextureManager tm =
                            Minecraft.getMinecraft().getTextureManager();
                    tm.loadTexture(rl, new net.minecraft.client.renderer.texture.DynamicTexture(img));
                    ResourceLocation old = RLS.put(label, rl);
                    if (old != null && !old.equals(rl)) {
                        tm.deleteTexture(old);
                    }
                } catch (Exception e) {
                    ModelLog.warn("贴图注册失败 " + label + ": " + e);
                }
            }
        });
        java.nio.file.Path config = gameDir.toPath().resolve("OpenDreamCore");
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

    /** Render only the replacement body; the caller continues armor, held item, name, and shadow layers. */
    public static boolean renderBody(Entity entity, float partialTicks) {
        LegacyModelRuleStore.Hit hit = LegacyModelRuleStore.get().hit(lookup(entity));
        if (hit == null || !bodyTexturesReady(hit)) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        if (hit.scale != 1.0F) {
            GL11.glScalef(hit.scale, hit.scale, hit.scale);
        }
        try {
            if (hit.rig != null) {
                drawRig(mc, entity, hit, partialTicks);
            } else {
                Tessellator tess = Tessellator.instance;
                for (BakedModel bm : hit.baked) {
                    ResourceLocation rl = hit.textureOverride != null
                            ? RLS.get(hit.textureOverride) : RLS.get(bm.texture);
                    mc.getTextureManager().bindTexture(rl);
                    drawQuads(tess, bm.quads, GL11.GL_QUADS, 4);
                    drawQuads(tess, bm.tris, GL11.GL_TRIANGLES, 3);
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
        return true;
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
        Tessellator tess = Tessellator.instance;
        for (java.util.Map.Entry<String, GroupSink> e : groups.entrySet()) {
            ResourceLocation rl = hit.textureOverride != null
                    ? RLS.get(hit.textureOverride) : RLS.get(e.getKey());
            if (rl == null) {
                continue;
            }
            mc.getTextureManager().bindTexture(rl);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GroupSink g = e.getValue();
            tess.startDrawingQuads();
            for (int i = 0; i + 4 < g.n; i += 5) {
                tess.addVertexWithUV(g.v[i], g.v[i + 1], g.v[i + 2], g.v[i + 3], g.v[i + 4]);
            }
            tess.draw();
        }
    }

    private static void drawQuads(Tessellator tess, float[] data, int mode, int vertsPerPrim) {
        if (data.length < 8 * vertsPerPrim) {
            return;
        }
        tess.startDrawing(mode);
        for (int off = 0; off + 8 * vertsPerPrim - 1 < data.length; off += 8 * vertsPerPrim) {
            for (int v = 0; v < vertsPerPrim; v++) {
                int o = off + v * 8;
                tess.addVertexWithUV(data[o], data[o + 1], data[o + 2], data[o + 3], data[o + 4]);
            }
        }
        tess.draw();
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

    /** 实体侧匹配条件：type=EntityList id，name=自定义名。 */
    static ModelRules.Lookup lookup(final Entity entity) {
        return new ModelRules.Lookup() {
            @Override
            public String get(String key) {
                if (ModelRules.TYPE.equals(key)) {
                    return net.minecraft.entity.EntityList.getEntityString(entity);
                }
                if (ModelRules.NAME.equals(key)) {
                    // 1.7.10：getCommandSenderName 有名牌时返回名牌，否则返回类型名
                    // （EntityPainter1710 同款取法；Name 条件用 contains 语义不冲突）
                    String n = entity.getCommandSenderName();
                    return n == null || n.isEmpty() ? null : n;
                }
                return null;
            }
        };
    }

    static String safe(String s) {
        return s.replaceAll("[^a-z0-9/._-]", "_").toLowerCase(java.util.Locale.ROOT);
    }
}
