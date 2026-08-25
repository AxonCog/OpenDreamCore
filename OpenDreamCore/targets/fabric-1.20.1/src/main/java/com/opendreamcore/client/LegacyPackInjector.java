package com.opendreamcore.client;

import com.opendreamcore.client.spi.ResourcePackInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.20.1 材质包注入实现（Forge/Fabric 共用形态）：
 * 旧 API Pack.create(id,title,required,supplier,meta,position) + 反射追加 RepositorySource，
 * setSelected 调整优先级（index 0 = 最顶层）。目录注入（PackInstaller 统一解压）。
 */
public final class LegacyPackInjector implements ResourcePackInjector {

    @Override
    public boolean inject(Path packDir, String password, boolean top) {
        try {
            if (packDir == null || !Files.isDirectory(packDir)) {
                return false;
            }
            String raw = String.valueOf(packDir.getFileName());
            String id = "opendreamcore/" + raw.replaceAll("[^a-zA-Z0-9_.-]", "_");
            Minecraft mc = Minecraft.getInstance();
            PackRepository repo = mc.getResourcePackRepository();

            repo.removePack(id);

            // 1.20.1 老 API：readMetaAndCreate(id,title,required,supplier,type,position,source)
            Pack.ResourcesSupplier supplier = pid -> new PathPackResources(pid, packDir, false);
            Pack pack = Pack.readMetaAndCreate(id,
                    Component.literal("OpenDreamCore 材质包"), true, supplier,
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);

            var sourcesField = PackRepository.class.getDeclaredField("sources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<net.minecraft.server.packs.repository.RepositorySource> sources =
                    (java.util.List<net.minecraft.server.packs.repository.RepositorySource>) sourcesField.get(repo);
            sources.add(consumer -> consumer.accept(pack));
            repo.reload();

            List<String> ids = new ArrayList<>(repo.getSelectedIds());
            ids.remove(id);
            if (top) {
                ids.add(0, id);
            } else {
                ids.add(id);
            }
            repo.setSelected(ids);
            return true;
        } catch (Throwable t) {
            com.opendreamcore.client.ClientController.LOGGER.warn("材质包注入失败: {}", t.toString());
            return false;
        }
    }
}
