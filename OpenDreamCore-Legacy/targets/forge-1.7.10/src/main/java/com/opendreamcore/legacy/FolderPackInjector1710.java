package com.opendreamcore.legacy;

import com.opendreamcore.client.spi.FolderPackInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;

import java.io.File;
import java.util.List;

/**
 * 1.7.10 材质包注入：全部塞进 Minecraft.defaultResourcePacks（反射拿这个
 * 私有 List，refreshResources 会拿它重建整条资源栈）。
 *
 * 包一层虚拟元数据：玩家丢个纯贴图文件夹没有 mcmeta 也照常加载，
 * 不会像原版 FolderResourcePack 那样读不到 pack.mcmeta 就把整条链炸了。
 */
public final class FolderPackInjector1710 implements FolderPackInjector {

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
            List stack = defaultPacks(mc);
            stack.removeIf(p -> p.getClass().getSimpleName().contains(OUR_MARKER));
            stack.add(new ManagedPack1710(root.toFile(), 1)); // 1.7.10 pack_format = 1
            rootInjected = true;
            mc.refreshResources();
            OdcLegacy1710.LOGGER.info("[ODC] 托管根目录已挂载: {}", root);
        } catch (Throwable t) {
            if (!rootWarned) {
                rootWarned = true;
                OdcLegacy1710.LOGGER.warn("[ODC] 托管根目录挂载失败: {}", t.toString());
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
            List stack = defaultPacks(mc);
            stack.removeIf(p -> p.getClass().getSimpleName().contains(OUR_MARKER));
            int ok = 0;
            for (File f : packs) {
                try {
                    IResourcePack pack = f.isDirectory()
                            ? new MarkedFolderPack(f)
                            : new MarkedFilePack(f);
                    stack.add(pack);
                    ok++;
                    OdcLegacy1710.LOGGER.info("[ODC] 材质包挂载: {}", f.getName());
                } catch (Throwable t) {
                    OdcLegacy1710.LOGGER.warn("[ODC] 材质包挂载失败 {}: {}", f.getName(), t.toString());
                }
            }
            if (ok > 0) {
                mc.refreshResources();
            }
            return ok;
        } catch (Throwable t) {
            OdcLegacy1710.LOGGER.warn("[ODC] 材质包注入异常: {}", t.toString());
            return 0;
        }
    }

    /**
     * 拿 defaultResourcePacks 私有字段。映射名环境直接按名字取；名字对不上
     * （生产 SRG）就认内容：装着资源包实例的那个 List 字段才是它。
     */
    private static List defaultPacks(Minecraft mc) throws Exception {
        try {
            java.lang.reflect.Field named = Minecraft.class.getDeclaredField("defaultResourcePacks");
            named.setAccessible(true);
            return (List) named.get(mc);
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
            for (Object item : (List) value) {
                if (item instanceof IResourcePack) {
                    return (List) value;
                }
            }
        }
        throw new NoSuchFieldException("Minecraft.defaultResourcePacks 没找到（名字与内容扫描都落空）");
    }

    /** 文件夹包：缺 mcmeta 时用虚拟元数据兜底。 */
    static final class MarkedFolderPack extends FolderResourcePack {
        MarkedFolderPack(File folder) {
            super(folder);
        }

        @Override
        public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) {
            try {
                return super.getPackMetadata(serializer, section);
            } catch (Exception e) {
                return virtualMeta(section);
            }
        }
    }

    /** zip 包：同上。 */
    static final class MarkedFilePack extends FileResourcePack {
        MarkedFilePack(File zip) {
            super(zip);
        }

        @Override
        public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) {
            try {
                return super.getPackMetadata(serializer, section);
            } catch (Exception e) {
                return virtualMeta(section);
            }
        }
    }

    /** 虚拟 pack 元数据：1.7.10 固定 format 1。 */
    private static IMetadataSection virtualMeta(String section) {
        if ("pack".equals(section)) {
            return new PackMetadataSection(
                    new net.minecraft.util.ChatComponentText("OpenDreamCore 材质包"), 1);
        }
        return null;
    }
}
