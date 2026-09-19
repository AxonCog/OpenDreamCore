package com.opendreamcore;

import com.opendreamcore.client.spi.FolderPackInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IPackFinder;
import net.minecraft.resources.IPackNameDecorator;
import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.ResourcePackInfo;
import net.minecraft.resources.ResourcePackList;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 1.16.5 材质包注入：走 ResourcePackList.addPackFinder（这版是公开方法）。
 * 包对象用 ManagedPack1165——虚拟元数据，玩家丢个纯贴图文件夹没有
 * pack.mcmeta 也不炸。注入后 setSelected 置顶。
 */
public final class FolderPackInjector1165 implements FolderPackInjector {

    /** 注入的包 id 前缀，摘除时按这个认。 */
    private static final String ID_PREFIX = "odc-managed-";

    /** 托管根目录常驻包（DreamEngine 同款直读语义），同一次会话只挂一张。 */
    private static boolean rootInjected;

    @Override
    public void injectRoot(Path root) {
        if (rootInjected || root == null) {
            return;
        }
        if (injectOne(root, ID_PREFIX + "root",
                () -> new ManagedPack1165(root, 6))) {
            rootInjected = true;
            OdcLegacy.LOGGER.info("[ODC] 托管根目录已挂载: {}", root);
        }
    }

    @Override
    public int injectAll(java.util.List<File> packs) {
        if (packs == null || packs.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (File f : packs) {
            Path p = f.toPath();
            String id = ID_PREFIX + f.getName().replaceAll("[^a-zA-Z0-9_.-]", "_");
            if (injectOne(p, id, () -> new ManagedPack1165(p, 6, f.getName()))) {
                ok++;
            }
        }
        return ok;
    }

    /** 注册一个 finder 并置顶选中。资源重载走 reloadResourcePacks。 */
    private boolean injectOne(Path dir, String id, Supplier<IResourcePack> packSupplier) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ResourcePackList list = mc.getResourcePackRepository();
            list.addPackFinder(new IPackFinder() {
                @Override
                public void loadPacks(java.util.function.Consumer<ResourcePackInfo> consumer,
                                      ResourcePackInfo.IFactory factory) {
                    // 已存在就不重复造（addPackFinder 是追加式的，重连会再调）
                    if (list.getPack(id) != null) {
                        return;
                    }
                    consumer.accept(ResourcePackInfo.create(id, true, packSupplier,
                            factory, ResourcePackInfo.Priority.TOP, IPackNameDecorator.DEFAULT));
                }
            });
            list.reload();

            java.util.List<String> ids = new java.util.ArrayList<>(list.getSelectedIds());
            if (!ids.contains(id)) {
                ids.add(0, id);
                list.setSelected(ids);
            }
            mc.reloadResourcePacks();
            return true;
        } catch (Throwable t) {
            OdcLegacy.LOGGER.warn("[ODC] 材质包注入失败 {}: {}", dir, t.toString());
            return false;
        }
    }
}
