package com.opendreamcore.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * OpenDreamCore 自定义资源包：resourcepacks/OpenDreamCore/ 目录直接映射 opendreamcore: 命名空间。
 * 无需 zip/pack.mcmeta/原版菜单/注入器——文件即资源，目录即包根。
 * 照抄 DreamCore CustomResourcePack 逻辑，只改命名空间为 opendreamcore。
 */
public final class CustomResourcePack implements PackResources {

    private final Path basePath;
    private final String namespace;           // "opendreamcore"
    private final PackLocationInfo info;

    public CustomResourcePack(Path basePath, String namespace, PackLocationInfo info) {
        this.basePath = basePath.toAbsolutePath().normalize();
        this.namespace = namespace;
        this.info = info;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (!id.getNamespace().equals(this.namespace)) {
            return null;
        }
        String path = id.getPath();
        Path resourcePath = this.basePath.resolve(path);
        File file = new File(this.basePath.toFile(), path);
        if (file.exists() && file.isFile()) {
            return () -> {
                try {
                    return new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            };
        }
        if (Files.exists(resourcePath) && Files.isRegularFile(resourcePath)) {
            return () -> {
                try {
                    return Files.newInputStream(resourcePath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        if (paths.length == 1 && paths[0].equals("pack.mcmeta")) {
            String packMetaContent = "{\"pack\":{\"pack_format\":"
                    + com.opendreamcore.client.CompatRender.currentPackFormat()
                    + ",\"description\":\"OpenDreamCore 自定义资源包\"}}";
            return () -> new ByteArrayInputStream(packMetaContent.getBytes(StandardCharsets.UTF_8));
        }
        Path resourcePath = this.basePath;
        for (String p : paths) {
            resourcePath = resourcePath.resolve(p);
        }
        if (!Files.exists(resourcePath) || !Files.isRegularFile(resourcePath)) {
            return null;
        }
        final Path fp = resourcePath;
        return () -> {
            try {
                return Files.newInputStream(fp);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public boolean contains(PackType type, ResourceLocation id) {
        if (!id.getNamespace().equals(this.namespace)) {
            return false;
        }
        File file = new File(this.basePath.toFile(), id.getPath());
        if (file.exists()) {
            return true;
        }
        Path resourcePath = this.basePath.resolve(id.getPath());
        return Files.exists(resourcePath);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return Collections.singleton(this.namespace);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput consumer) {
        if (!namespace.equals(this.namespace)) {
            return;
        }
        Path searchPath = this.basePath.resolve(path);
        if (!Files.exists(searchPath)) {
            return;
        }
        try {
            Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relativePath = this.basePath.relativize(file).toString().replace('\\', '/');
                        ResourceLocation id = ResourceLocation.tryBuild(this.namespace, relativePath);
                        if (id != null) {
                            IoSupplier<InputStream> supplier = () -> {
                                try {
                                    return Files.newInputStream(file);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            };
                            consumer.accept(id, supplier);
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metaReader) throws IOException {
        String packMetaContent = "{\"pack\":{\"pack_format\":"
                + com.opendreamcore.client.CompatRender.currentPackFormat()
                + ",\"description\":\"OpenDreamCore 自定义资源包\"}}";
        // PackResourceMetadataReader 需要 pack 对象
        var parser = new com.google.gson.JsonParser();
        var jsonObject = parser.parse(packMetaContent).getAsJsonObject();
        var packObject = jsonObject.getAsJsonObject("pack");
        try {
            return metaReader.fromJson(packObject);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        // 无需关闭
    }

    @Override
    public PackLocationInfo location() {
        return this.info;
    }

    public Path getBasePath() {
        return this.basePath;
    }
}