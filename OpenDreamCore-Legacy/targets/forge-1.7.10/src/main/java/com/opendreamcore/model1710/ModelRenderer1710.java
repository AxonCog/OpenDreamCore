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

    /** 朝向基准（与 1.16.5 同口径，实机可调）。 */
    public static float bodyYawOffset = 180.0F;

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
                    Minecraft.getMinecraft().getTextureManager()
                            .loadTexture(rl, new net.minecraft.client.renderer.texture.DynamicTexture(img));
                    RLS.put(label, rl);
                } catch (Exception e) {
                    ModelLog.warn("贴图注册失败 " + label + ": " + e);
                }
            }
        });
        ModelAssetStore.get().load(gameDir.toPath().resolve("OpenDreamCore").resolve("assets").resolve("model"));
        LegacyModelRuleStore.get().applyLocalFile(
                gameDir.toPath().resolve("OpenDreamCore").resolve("visual").resolve("Model.yml"));
        ModelLog.info("1.7.10 模型 glue 就绪");
    }

    /** 服务端规则入库。 */
    public static void applyServerBundle(java.util.Map<String, String> bundle) {
        LegacyModelRuleStore.get().applyBundle(bundle);
    }

    /** 实体替换入口：命中返回 true（调用方取消原版 doRender）。 */
    public static boolean tryRender(Entity entity, double x, double y, double z,
                                    float entityYaw, float partialTicks) {
        LegacyModelRuleStore.Hit hit = LegacyModelRuleStore.get().hit(lookup(entity));
        if (hit == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // 1.7.10 没有 GlStateManager（1.8 才有），直接 GL11
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslated(x, y, z);
        GL11.glRotated(bodyYawOffset - entityYaw, 0.0, 1.0, 0.0);
        float s = hit.scale;
        if (s != 1.0F) {
            GL11.glScaled(s, s, s);
        }
        try {
            Tessellator tess = Tessellator.instance;
            for (BakedModel bm : hit.baked) {
                ResourceLocation rl = hit.textureOverride != null
                        ? RLS.get(hit.textureOverride) : RLS.get(bm.texture);
                if (rl == null) {
                    continue;
                }
                mc.getTextureManager().bindTexture(rl);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                drawQuads(tess, bm.quads, GL11.GL_QUADS, 4);
                drawQuads(tess, bm.tris, GL11.GL_TRIANGLES, 3);
            }
        } finally {
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
        }
        return true;
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
