package com.opendreamcore.client.resources;

import com.opendreamcore.client.CompatRender;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 世界纯色几何用的白纹理（1x1）。
 *
 * <p>单独成类是因为它有独立的失败兜底逻辑，而纯色矩形、血条、名牌背景全挂在它身上：
 * 拿不到贴图不会抛异常，只会让那些东西整片不显示。所以这里把三级来源与告警都收在一处，
 * 谁需要白纹理只管调 {@link #get()}。
 *
 * <p>三级来源：动态构造的 1x1 → 原版 {@code textures/block/white_wool}（26.1.2 已无
 * {@code misc/white.png}，wool 各代都在，内容纯白所以 UV 取哪都一样）→ 返回 null 由调用方跳过。
 */
final class WhiteTextureSource {

    private static final Logger LOG = LoggerFactory.getLogger("OpenDreamCore");

    private static volatile ResourceLocation dynamicRl;
    private static volatile ResourceLocation fallbackRl;
    private static volatile boolean warned;

    private WhiteTextureSource() {
    }

    /** 白纹理标识；null 表示两路都没拿到，调用方应跳过贴图绑定而不是传 null 进去解绑采样器。 */
    static ResourceLocation get() {
        ResourceLocation fallback = fallbackRl;
        if (dynamicRl == null && fallback == null) {
            synchronized (WhiteTextureSource.class) {
                if (dynamicRl == null && fallback == null) {
                    init();
                }
            }
        }
        ResourceLocation rl = dynamicRl != null ? dynamicRl : fallbackRl;
        if (rl == null && !warned) {
            warned = true;
            LOG.warn("[OpenDreamCore] 纯色矩形无贴图可用，血条/名牌背景将不显示");
        }
        return rl;
    }

    private static void init() {
        try {
            ResourceLocation rl = CompatRender.rl("opendreamcore", "loose/_white");
            Object tex = CompatRender.newDynamicTexture(
                    LooseResourceLoader.whitePixelImage());
            if (tex != null) {
                Minecraft.getInstance().getTextureManager().register(rl, (AbstractTexture) tex);
                dynamicRl = rl;
                if (Boolean.getBoolean("odc.debug")) {
                    com.opendreamcore.client.OdcDebug.log("[ODC-diag] 白色纹理注册成功: " + rl);
                }
                return;
            }
            if (Boolean.getBoolean("odc.debug")) {
                com.opendreamcore.client.OdcDebug.log("[ODC-diag] 白色纹理 newDynamicTexture 返回 null（反射构造失败）");
            }
        } catch (Throwable t) {
            if (Boolean.getBoolean("odc.debug")) {
                com.opendreamcore.client.OdcDebug.log("[ODC-diag] 白色纹理注册失败: " + t);
            }
        }
        fallbackRl = vanillaWhite();
        if (!warned) {
            warned = true;
            LOG.warn("[OpenDreamCore] 1x1 白纹理不可用，纯色矩形改用原版白 wool 贴图: {}", fallbackRl);
        }
    }

    /** 原版白 wool 的标识；连标识都造不出来时返回 null（只剩这条路可走）。 */
    private static ResourceLocation vanillaWhite() {
        try {
            return CompatRender.rl("minecraft", "textures/block/white_wool");
        } catch (Throwable ignored) {
            return null;
        }
    }
}