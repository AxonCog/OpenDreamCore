package com.opendreamcore.mixin;
import com.opendreamcore.ItemEffect1165;
import com.opendreamcore.client.LooseTextureLoader;
import com.opendreamcore.client.visual.LegacyVisualItemIcons;
import com.opendreamcore.model1165.LegacyItemModel1165;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写（1.16.5）：ItemIcon 规则命中就把原版图标换成自定义贴图。
 * renderGuiItem 是 2D 图标入口，画完 cancel 掉原版那套。
 *
 * 注入串写 srg 名：这代没有运行时反混淆，注解里的名字不会被重映射。
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemIcon {

    @Inject(method = "func_175042_a(Lnet/minecraft/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIcon(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (LegacyItemModel1165.tryGui(this, stack, x, y)) {
            ItemEffect1165.render(stack, x, y);
            ci.cancel();
            return;
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        LegacyVisualItemIcons.Selection selection =
                LegacyVisualItemIcons.selectionForStack(id, stack);
        if (selection == null || selection.texture == null || !(selection.scale > 0.0)
                || Double.isNaN(selection.scale) || Double.isInfinite(selection.scale)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LooseTextureLoader.Sheet sheet;
        try {
            sheet = LooseTextureLoader.sheetOf(selection.texture, selection.frameMs);
        } catch (Exception ignored) {
            return;
        }
        ResourceLocation rl;
        try {
            if (sheet != null) {
                rl = new ResourceLocation(sheet.rl);
            } else {
                String loose = LooseTextureLoader.lookup(selection.texture);
                if (loose != null) {
                    rl = new ResourceLocation(loose);
                } else {
                    rl = resourceLocation(selection.texture);
                    try (java.io.InputStream in =
                                 mc.getResourceManager().getResource(rl).getInputStream()) {
                        // Existence check; close the stream immediately.
                    }
                }
            }
            mc.getTextureManager().bind(rl);
        } catch (Exception ignored) {
            return;
        }
        boolean blending = false;
        try {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            blending = true;
            com.mojang.blaze3d.systems.RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            double size = 16.0D * selection.scale;
            double left = x + 8.0D - size / 2.0D;
            double top = y + 8.0D - size / 2.0D;
            float u0 = sheet == null ? 0.0F : (float) sheet.frame / (float) sheet.frames;
            float u1 = sheet == null ? 1.0F : (float) (sheet.frame + 1) / (float) sheet.frames;
            Tessellator tt = Tessellator.getInstance();
            BufferBuilder bb = tt.getBuilder();
            bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
            bb.vertex(left, top + size, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u0, 1.0F).endVertex();
            bb.vertex(left + size, top + size, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u1, 1.0F).endVertex();
            bb.vertex(left + size, top, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u1, 0.0F).endVertex();
            bb.vertex(left, top, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u0, 0.0F).endVertex();
            tt.end();
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            ItemEffect1165.render(stack, x, y);
        } catch (Exception ignored) {
            if (blending) {
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
        }
    }

    private static ResourceLocation resourceLocation(String texture) {
        int colon = texture.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(texture);
        }
        return new ResourceLocation("opendreamcore", texture);
    }
}