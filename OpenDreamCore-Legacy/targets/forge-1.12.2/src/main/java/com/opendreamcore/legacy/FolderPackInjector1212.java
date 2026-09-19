package com.opendreamcore.legacy;

import com.opendreamcore.client.spi.FolderPackInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 1.12.2 材质包注入：全部塞进 Minecraft.defaultResourcePacks（反射拿这个私有
 * List，refreshResources 会拿它重建整条资源栈）。
 *
 * 为什么不走 fileResourcepacks/资源包仓库：那条路要 pack.mcmeta，玩家丢个
 * 纯贴图文件夹进来就直接炸——原版 AbstractResourcePack.getPackMetadata 读不到
 * mcmeta 抛 IOException，整个 reload 失败、所有包被移除。这里统一包一层虚拟
 * 元数据：没有 mcmeta 也照常加载。
 *
 * 重注入前按类名标记摘掉上一轮的，改包内容不会叠两层。
 */
public final class FolderPackInjector1212 implements FolderPackInjector {

    /** 我们注入的包类名都带这个标记，摘除时好认。 */
    private static final String OUR_MARKER = "OdcVmPack";

    /** 托管根目录常驻包（DreamEngine 同款直读语义），同一次会话只挂一张。 */
    private static boolean rootInjected;

    /** 挂载失败只喊一次，别在 tick 里刷屏。 */
    private static boolean rootWarned;

    @Override
    public void injectRoot(java.nio.file.Path root) {
        if (rootInjected || root == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            List<IResourcePack> stack = defaultPacks(mc);
            stack.removeIf(p -> p.getClass().getSimpleName().contains(OUR_MARKER));
            stack.add(new ManagedPack1212(root.toFile(), 3)); // 1.12.2 pack_format = 3
            rootInjected = true;
            mc.refreshResources();
            OdcLegacy.LOGGER.info("[ODC] 托管根目录已挂载: {}", root);
        } catch (Throwable t) {
            if (!rootWarned) {
                rootWarned = true;
                OdcLegacy.LOGGER.warn("[ODC] 托管根目录挂载失败: {}", t.toString());
            }
        }
    }

    @Override
    public int injectAll(List<File> packs) {
        if (packs == null || packs.isEmpty()) {
            return 0;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            List<IResourcePack> stack = defaultPacks(mc);
            stack.removeIf(p -> p.getClass().getSimpleName().contains(OUR_MARKER));
            int ok = 0;
            for (File f : packs) {
                try {
                    IResourcePack pack = f.isDirectory()
                            ? new MarkedFolderPack(f)
                            : new MarkedFilePack(f);
                    stack.add(pack);
                    ok++;
                    OdcLegacy.LOGGER.info("[ODC] 材质包挂载: {}", f.getName());
                } catch (Throwable t) {
                    OdcLegacy.LOGGER.warn("[ODC] 材质包挂载失败 {}: {}", f.getName(), t.toString());
                }
            }
            if (ok > 0) {
                mc.refreshResources();
            }
            return ok;
        } catch (Throwable t) {
            OdcLegacy.LOGGER.warn("[ODC] 材质包注入异常: {}", t.toString());
            return 0;
        }
    }

    /**
     * 拿 defaultResourcePacks 私有字段。映射名环境直接按名字取；名字对不上
     * （生产 SRG）就认内容：Minecraft 里的 List 字段不止一个，装着资源包
     * 实例的那个才是它。两边都落空就报错，别瞎猜。
     */
    @SuppressWarnings("unchecked")
    private static List<IResourcePack> defaultPacks(Minecraft mc) throws Exception {
        try {
            java.lang.reflect.Field named = Minecraft.class.getDeclaredField("defaultResourcePacks");
            named.setAccessible(true);
            return (List<IResourcePack>) named.get(mc);
        } catch (NoSuchFieldException ignored) {
            // 映射名对不上，退回内容识别
        }
        for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) {
                continue;
            }
            f.setAccessible(true);
            Object value = f.get(mc);
            if (!(value instanceof List)) {
                continue;
            }
            for (Object item : (List<?>) value) {
                if (item instanceof IResourcePack) {
                    return (List<IResourcePack>) value;
                }
            }
        }
        throw new NoSuchFieldException("Minecraft.defaultResourcePacks 没找到（名字与内容扫描都落空）");
    }

    /** 文件夹包：缺 mcmeta 时用虚拟元数据兜底，别让一张包炸整条链。 */
    static final class MarkedFolderPack extends FolderResourcePack {
        MarkedFolderPack(File folder) {
            super(folder);
        }

        @Override
        public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer,
                                                              String section) throws IOException {
            try {
                return super.getPackMetadata(serializer, section);
            } catch (IOException e) {
                return virtualMeta(serializer, section);
            }
        }
    }

    /** zip 包：同上，mcmeta 缺失不致命。 */
    static final class MarkedFilePack extends FileResourcePack {
        MarkedFilePack(File zip) {
            super(zip);
        }

        @Override
        public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer,
                                                              String section) throws IOException {
            try {
                return super.getPackMetadata(serializer, section);
            } catch (IOException e) {
                return virtualMeta(serializer, section);
            }
        }
    }

    /** 虚拟 pack 元数据：1.12.2 固定 format 3。 */
    @SuppressWarnings("unchecked")
    private static <T extends IMetadataSection> T virtualMeta(MetadataSerializer serializer,
                                                              String section) {
        if ("pack".equals(section)) {
            return (T) new PackMetadataSection(
                    new net.minecraft.util.text.TextComponentString("OpenDreamCore 材质包"), 3);
        }
        return null;
    }
}
