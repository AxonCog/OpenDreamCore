package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * D2：HUD 左上角常驻小 Logo（DreamCore logo_hud 平移）。
 * 文件：OpenDreamCore/branding/logo_hud.png（缺失则整体禁用）
 * 配置：OpenDreamCore/branding/logo.json
 * <pre>{"x":8,"y":8,"scale":2.0,"opacity":1.0}</pre>
 * 渲染：HUD 阶段末尾（各平台壳在 renderHud 后调用 {@link #render}）。
 */
public final class HudLogo {

    private record Loaded(ResourceLocation tex, int width, int height,
                          double x, double y, double scale, double opacity) {
    }

    private static volatile Loaded loaded;

    private HudLogo() {
    }

    /** 从 branding 目录加载（缺文件静默清除）。WindowBranding.apply/reload 尾部调用。 */
    public static void load() {
        loaded = null;
        try {
            Path dir = WindowBranding.brandingDir();
            Path img = dir.resolve("logo_hud.png");
            if (!Files.isRegularFile(img)) {
                return;
            }
            double x = 8, y = 8, scale = 1.0, opacity = 1.0;
            Path json = dir.resolve("logo.json");
            if (Files.isRegularFile(json)) {
                String raw = Files.readString(json);
                var map = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                x = jd(map, "x", x);
                y = jd(map, "y", y);
                scale = jd(map, "scale", scale);
                opacity = jd(map, "opacity", opacity);
            }
            NativeImage image;
            try (var in = Files.newInputStream(img)) {
                image = NativeImage.read(in);
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > 1024 || image.getHeight() > 1024) {
                image.close();
                return;
            }
            DynamicTexture texture = CompatRender.newDynamicTexture(image);
            ResourceLocation id = CompatRender.rl("opendreamcore", "hud_logo");
            Minecraft.getInstance().getTextureManager().register(id, texture);
            loaded = new Loaded(id, image.getWidth(), image.getHeight(), x, y, scale, clamp01(opacity));
        } catch (Throwable ignored) {
            loaded = null;
        }
    }

    /** HUD 渲染（GuiGraphics 已处于屏幕坐标系）。 */
    public static void render(GuiGraphics g) {
        Loaded l = loaded;
        if (l == null) {
            return;
        }
        try {
            var pose = g.pose();
            CompatRender.posePush(pose);
            CompatRender.poseTranslate(pose, l.x(), l.y());
            CompatRender.poseScale(pose, l.scale(), l.scale());
            // 透明度：旧管线经 setDrawColor 生效；新管线为尽力而为（文档已注明）
            CompatRender.setDrawColor(g, 1F, 1F, 1F, (float) l.opacity());
            CompatRender.blit(g, l.tex(), 0, 0, l.width(), l.height(),
                    0, 0, l.width(), l.height(), l.width(), l.height());
            CompatRender.setDrawColor(g, 1F, 1F, 1F, 1F);
            CompatRender.posePop(pose);
        } catch (Throwable ignored) {
            // 单帧渲染异常不拖垮 HUD
        }
    }

    private static double jd(com.google.gson.JsonObject o, String k, double def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
