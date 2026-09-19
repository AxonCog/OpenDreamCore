package com.opendreamcore;

import net.minecraft.resources.IPackFinder;
import net.minecraft.resources.IPackNameDecorator;
import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.ResourcePackInfo;
import net.minecraft.resources.ResourcePackList;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.data.IMetadataSectionSerializer;
import net.minecraft.resources.data.PackMetadataSection;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 托管根目录常驻包（1.16.5）：resourcepacks/OpenDreamCore 本身当一张包。
 *
 * 两种布局都认：标准 assets/opendreamcore/gui/hp.png 和直接在根下丢
 * gui/hp.png（DreamEngine 同款体验）。元数据虚拟返回，根目录不强制放
 * pack.mcmeta。minecraft: 命名空间只走标准布局，防止一张贴图顶掉原版资源。
 */
public final class ManagedPack1165 implements IResourcePack {

    /** 托管根目录。 */
    private final Path base;
    /** 虚拟元数据里报的包格式（1.16.5 = 6）。 */
    private final int packFormat;
    /** 包名（日志与仓库里显示用）。 */
    private final String name;

    public ManagedPack1165(Path base, int packFormat) {
        this(base, packFormat, "OpenDreamCore/托管根");
    }

    public ManagedPack1165(Path base, int packFormat, String name) {
        this.base = base.toAbsolutePath().normalize();
        this.packFormat = packFormat;
        this.name = name;
    }

    /** 按托管语义解析：先标准布局，opendreamcore 命名空间再根目录直放。 */
    @Nullable
    private Path fileFor(ResourcePackType type, ResourceLocation location) {
        String dir = type.getDirectory();
        Path standard = base.resolve(dir).resolve(location.getNamespace())
                .resolve(location.getPath());
        if (Files.isRegularFile(standard)) {
            return standard;
        }
        if ("opendreamcore".equals(location.getNamespace())) {
            Path direct = base.resolve(location.getPath());
            if (direct.startsWith(base) && Files.isRegularFile(direct)) {
                return direct;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public InputStream getRootResource(String fileName) throws IOException {
        // 根资源（pack.mcmeta/pack.png 等）：虚拟包不提供，元数据走 getMetadataSection
        return null;
    }

    @Override
    public InputStream getResource(ResourcePackType type, ResourceLocation location) throws IOException {
        Path p = fileFor(type, location);
        if (p == null) {
            throw new IOException(location.toString());
        }
        return Files.newInputStream(p);
    }

    @Override
    public boolean hasResource(ResourcePackType type, ResourceLocation location) {
        return fileFor(type, location) != null;
    }

    @Override
    public Collection<ResourceLocation> getResources(ResourcePackType type, String namespace,
                                                     String path, int maxDepth,
                                                     java.util.function.Predicate<String> filter) {
        Set<ResourceLocation> out = new HashSet<>();
        // 标准布局枚举
        Path dir = base.resolve(type.getDirectory()).resolve(namespace);
        if (Files.isDirectory(dir)) {
            collect(type, namespace, dir, dir, path, maxDepth, 0, out);
        }
        // opendreamcore 直放布局也进枚举（贴图找得到才算存在）
        if ("opendreamcore".equals(namespace)) {
            collect(type, namespace, base, base, path, maxDepth, 0, out);
        }
        return out;
    }

    private void collect(ResourcePackType type, String namespace, Path root, Path dir,
                         String prefix, int maxDepth, int depth, Set<ResourceLocation> out) {
        if (depth > maxDepth) {
            return;
        }
        File[] children = dir.toFile().listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                collect(type, namespace, root, f.toPath(), prefix, maxDepth, depth + 1, out);
            } else {
                String rel = root.relativize(f.toPath()).toString().replace('\\', '/');
                if (!prefix.isEmpty() && !rel.startsWith(prefix)) {
                    continue;
                }
                if (rel.equals("pack.mcmeta") || rel.equals("pack.png")
                        || rel.startsWith("_") || rel.equals("README.txt")) {
                    continue;
                }
                out.add(new ResourceLocation(namespace, rel));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(ResourcePackType type) {
        return Collections.singleton("opendreamcore");
    }

    /** 虚拟元数据：不读 pack.mcmeta，直接编一个回去。 */
    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getMetadataSection(IMetadataSectionSerializer<T> type) {
        if (PackMetadataSection.SERIALIZER == type) {
            return (T) new PackMetadataSection(
                    new net.minecraft.util.text.StringTextComponent(name), packFormat);
        }
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {
    }
}
