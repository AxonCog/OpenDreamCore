package com.opendreamcore.client.model;

import com.opendreamcore.core.addons.AddonRegistry;
import com.opendreamcore.core.addons.JarAddonLoader;
import com.opendreamcore.core.addons.ScriptPackLoader;
import com.opendreamcore.model.format.FormatSources;
import com.opendreamcore.model.format.IModelFormatCodec;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;

/**
 * addons 客户端入口：&lt;游戏目录&gt;/OpenDreamCore/addons 下的扩展（jar SPI / 脚本包）。
 *
 * <p>随本地规则装载（启动 + /odc reload）整组重建；模型格式 codec 是第一个消费者，
 * 后续任何模块扩展都走同一注册表。
 */
public final class ClientAddons {

    private static final AddonRegistry REGISTRY = new AddonRegistry();
    private static JarAddonLoader jarLoader;
    private static ScriptPackLoader scriptLoader;
    private static int lastLoaded;

    private ClientAddons() {
    }

    /** 全局注册表（插件/命令读取用）。 */
    public static synchronized AddonRegistry registry() {
        return REGISTRY;
    }

    /**
     * 重扫 addons 目录（启动与每次 reload 都调用）：
     * jar 通道整组重建（先摘句柄再关旧加载器），脚本包通道先摘旧句柄再重扫。
     *
     * @return 本次装载条数
     */
    public static synchronized int reload() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("addons");
        if (jarLoader == null) {
            jarLoader = new JarAddonLoader(dir, REGISTRY);
        }
        int n = jarLoader.reload().size();
        for (AddonRegistry.Handle h : REGISTRY.byType("script")) {
            REGISTRY.remove(h.id);
        }
        if (scriptLoader == null) {
            scriptLoader = new ScriptPackLoader(dir, REGISTRY);
        }
        n += scriptLoader.load();
        lastLoaded = n;
        return n;
    }

    /** 上次 reload 装载条数。 */
    public static synchronized int lastLoaded() {
        return lastLoaded;
    }

    /** 已注册格式 id 串（内建 + addon，"/odc model formats" 展示）。 */
    public static synchronized String formatsSummary() {
        StringBuilder sb = new StringBuilder();
        List<IModelFormatCodec> all = FormatSources.all(REGISTRY);
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(all.get(i).id());
        }
        return sb.length() == 0 ? "（无）" : sb.toString();
    }
}
