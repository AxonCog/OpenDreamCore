package com.opendreamcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * 1.6.4 散装贴图注册：renderEngine.loadTexture(RL, DynamicTexture)。
 */
public final class TextureRegistrar164 implements LooseTextureLoader.TextureRegistrar {

    @Override
    public String register(String rel, java.awt.image.BufferedImage img) {
        try {
            ResourceLocation rl = new ResourceLocation("opendreamcore",
                    "loose/" + LooseTextureLoader.sanitize(rel));
            Minecraft.getMinecraft().renderEngine.loadTexture(rl, new DynamicTexture(img));
            return rl.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
