package com.opendreamcore.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * 托管目录资源包：把 resourcepacks/OpenDreamCore 当作常驻材质包。
 * 元数据硬编码，目录里不需要 pack.mcmeta；读文件走 File API，中文路径稳定。
 */
public class OdcManagedPack implements PackResources {
    private static final int PACK_FORMAT = 15;
    private final Path base;
    private final String name;

    public OdcManagedPack(Path base, String name) {
        this.base = base;
        this.name = name;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... fileNames) {
        return fileNames.length == 0 ? null : file(new File(base.toFile(), fileNames[0]));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        // 资源按 <typeDir>/<namespace>/<path> 相对托管目录布局存放
        File f = new File(base.toFile(), type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
        return file(f);
    }

    private IoSupplier<InputStream> file(File f) {
        if (!f.isFile()) {
            return null;
        }
        return () -> {
            try {
                return new FileInputStream(f);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public void listResources(PackType type, String namespace, String path,
                              PackResources.ResourceOutput out) {
        File root = new File(base.toFile(), type.getDirectory() + "/" + namespace + "/" + path);
        Path rootPath = root.toPath();
        File[] stack = {root};
        java.util.List<File> all = new java.util.ArrayList<>();
        collectFiles(root, all);
        for (File f : all) {
            String rel = rootPath.relativize(f.toPath()).toString().replace(File.separatorChar, '/');
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, rel);
            out.accept(id, file(f));
        }
    }

    private void collectFiles(File dir, java.util.List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                collectFiles(f, out);
            } else {
                out.add(f);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        if ("pack".equals(serializer.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("OpenDreamCore 材质包"), PACK_FORMAT);
        }
        return null;
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return Set.of("minecraft", "opendreamcore");
    }

    @Override
    public String packId() {
        return name;
    }

    @Override
    public void close() {
    }
}
