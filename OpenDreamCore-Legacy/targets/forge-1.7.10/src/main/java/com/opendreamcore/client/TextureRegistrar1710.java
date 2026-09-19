package com.opendreamcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/**
 * 1.7.10 散装贴图注册：RenderEngine.loadTexture(RL, DynamicTexture)。
 */
public final class TextureRegistrar1710 implements LooseTextureLoader.TextureRegistrar {

    @Override
    public String register(String rel, java.awt.image.BufferedImage img) {
        try {
            ResourceLocation rl = new ResourceLocation("opendreamcore",
                    "loose/" + LooseTextureLoader.sanitize(rel));
            TextureManager tm = Minecraft.getMinecraft().getTextureManager();
            tm.loadTexture(rl, new DynamicTexture(img));
            return rl.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
