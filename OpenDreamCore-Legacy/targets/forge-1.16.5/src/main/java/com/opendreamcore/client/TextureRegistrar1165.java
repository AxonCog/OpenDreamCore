package com.opendreamcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.util.ResourceLocation;

/**
 * 1.16.5 的散装贴图注册：NativeImage.read(BufferedImage) + DynamicTexture
 * 进 TextureManager，RL 用净化名（opendreamcore:loose/...）。
 */
public final class TextureRegistrar1165 implements LooseTextureLoader.TextureRegistrar {

    @Override
    public String register(String rel, java.awt.image.BufferedImage img) {
        try {
            // 1.16.5 的 NativeImage 没有 BufferedImage 直读口子，走 PNG 字节中转
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            NativeImage ni = NativeImage.read(new java.io.ByteArrayInputStream(baos.toByteArray()));
            ResourceLocation rl = new ResourceLocation("opendreamcore",
                    "loose/" + LooseTextureLoader.sanitize(rel));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(ni));
            return rl.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}