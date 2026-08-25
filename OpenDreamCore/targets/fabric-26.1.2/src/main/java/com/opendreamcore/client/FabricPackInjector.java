package com.opendreamcore.client;

import com.opendreamcore.client.spi.ResourcePackInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fabric 1.21.x 材质包注入实现：
 * vanilla PackRepository 无 addPackFinder（NeoForge 补丁方法）——
 * 反射追加自定义 RepositorySource 到 sources 字段 + reload() 纳入可用列表，
 * setSelected 调整优先级（index 0 = 最顶层）。目录注入（PackInstaller 统一解压）。
 */
public final class FabricPackInjector implements ResourcePackInjector {

    @Override
    public boolean inject(Path packDir, String password, boolean top) {
        try {
            if (packDir == null || !Files.isDirectory(packDir)) {
                return false;
            }
            Path dir = packDir;
            String raw = String.valueOf(packDir.getFileName());
            String id = "opendreamcore/" + raw.replaceAll("[^a-zA-Z0-9_.-]", "_");
            Minecraft mc = Minecraft.getInstance();
            PackRepository repo = mc.getResourcePackRepository();

            // 同 id 重装：先摘除旧条目避免 available 冲突
            repo.removePack(id);

            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(id, Component.literal("OpenDreamCore 材质包"),
                            PackSource.BUILT_IN, Optional.empty()),
                    new FilePackResources.FileResourcesSupplier(dir),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));

            // vanilla：sources 在构造时固定 → 反射追加我们的 RepositorySource
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
            ClientController.LOGGER.warn("材质包注入失败: {}", t.toString());
            return false;
        }
    }
}
