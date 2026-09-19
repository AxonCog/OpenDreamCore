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

            // vanilla 的 sources 构造时就固定了，只能反射塞 finder。
            // 别按 "sources" 查字段——线上环境是混淆名/intermediary 名，必炸；
            // 全类唯一的 Set 字段就是它（available 是 Map、selected 是 List），按类型扫最稳。
            // 字段实际类型各版本有出入，一律按 Collection 接，别强转 List（26.x 就是这么栽的）。
            java.lang.reflect.Field sourcesField = null;
            int setFields = 0;
            for (var f : PackRepository.class.getDeclaredFields()) {
                if (java.util.Set.class.isAssignableFrom(f.getType())) {
                    sourcesField = f;
                    setFields++;
                }
            }
            if (setFields != 1) {
                throw new NoSuchFieldException("PackRepository 应该只有一个 Set 字段，扫到 " + setFields);
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
            ClientController.LOGGER.warn("材质包注入失败: {}", t.toString());
            return false;
        }
    }
}
