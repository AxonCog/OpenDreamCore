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

    /** 朝向基准（与 1.16.5 同口径，实机可调）。 */
    public static float bodyYawOffset = 180.0F;

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
                    RLS.put(label, rl);
                } catch (Exception e) {
                    ModelLog.warn("贴图注册失败 " + label + ": " + e);
                }
            }
        });
        ModelAssetStore.get().load(gameDir.resolve("OpenDreamCore").resolve("assets").resolve("model"));
        LegacyModelRuleStore.get().applyLocalFile(
                gameDir.resolve("OpenDreamCore").resolve("visual").resolve("Model.yml"));
        ModelLog.info("1.12.2 模型 glue 就绪");
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
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(bodyYawOffset - entityYaw, 0.0F, 1.0F, 0.0F);
        float s = hit.scale;
        if (s != 1.0F) {
            GlStateManager.scale(s, s, s);
        }
        try {
            Tessellator tess = Tessellator.getInstance();
            for (BakedModel bm : hit.baked) {
                ResourceLocation rl = hit.textureOverride != null
                        ? RLS.get(hit.textureOverride) : RLS.get(bm.texture);
                if (rl == null) {
                    continue;
                }
                mc.getTextureManager().bindTexture(rl);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                drawQuads(tess, bm.quads, GL11.GL_QUADS, 4);
                drawQuads(tess, bm.tris, GL11.GL_TRIANGLES, 3);
            }
        } finally {
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
        return true;
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
