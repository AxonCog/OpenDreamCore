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

            // vanilla 的 sources 构造时就固定了，只能反射塞 finder。
            // 别按 "sources" 查字段——线上环境是混淆名/intermediary 名，必炸；
            // 全类唯一的 Set 字段就是它（available 是 Map、selected 是 List），按类型扫最稳。
            // 字段实际类型各版本有出入，一律按 Collection 接，别强转 List。
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
            com.opendreamcore.client.ClientController.LOGGER.warn("材质包注入失败: {}", t.toString());
            return false;
        }
    }
}
