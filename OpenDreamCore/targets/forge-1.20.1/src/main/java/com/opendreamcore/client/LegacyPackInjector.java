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

            // 自建 PackResources：元数据硬编码，目录里不需要 pack.mcmeta
            Pack.ResourcesSupplier supplier = pid -> new OdcManagedPack(packDir, id);
            Pack pack = Pack.readMetaAndCreate(id,
                    Component.literal("OpenDreamCore 材质包"), true, supplier,
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);

            // 生产环境是 SRG 名，按字段名找不到；改按类型扫描 List<RepositorySource> 字段
            java.lang.reflect.Field sourcesField = null;
            for (var f : PackRepository.class.getDeclaredFields()) {
                // 1.20.1 实际是 Set<RepositorySource>，按泛型内容匹配、Collection 接收
                if (java.util.Collection.class.isAssignableFrom(f.getType())
                        && String.valueOf(f.getGenericType()).contains("RepositorySource")) {
                    sourcesField = f;
                    break;
                }
            }
            if (sourcesField == null) {
                throw new NoSuchFieldException("RepositorySource collection not found");
            }
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Collection<net.minecraft.server.packs.repository.RepositorySource> sources =
                    (java.util.Collection<net.minecraft.server.packs.repository.RepositorySource>) sourcesField.get(repo);
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
