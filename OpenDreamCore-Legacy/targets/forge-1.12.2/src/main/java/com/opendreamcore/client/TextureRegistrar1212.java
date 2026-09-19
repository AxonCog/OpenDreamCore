package com.opendreamcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/**
 * 1.12.2 散装贴图注册：老 TextureManager 走 loadTexture(RL, DynamicTexture)，
 * RL 用净化名，中文文件名绕开资源包系统。
 */
public final class TextureRegistrar1212 implements LooseTextureLoader.TextureRegistrar {

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
