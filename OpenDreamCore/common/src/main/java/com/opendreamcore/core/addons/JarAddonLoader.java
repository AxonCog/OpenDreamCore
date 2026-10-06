package com.opendreamcore.core.addons;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

/**
 * addons jar 热加载器：扫描目录内 *.jar → 子 URLClassLoader → ServiceLoader 发现 AddonProvider。
 *
 * <p>reload()：旧子加载器整组 close（连同其全部类），注册表中属于该加载器的句柄一并摘除，
 * 然后重扫重建 —— 无类泄漏、无残留句柄。jar 内坏类/缺 services 文件按「缺席即安全」跳过并记日志。
 */
public final class JarAddonLoader implements Closeable {

    private final Path addonsDir;
    private final AddonRegistry registry;
    private URLClassLoader loader;

    public JarAddonLoader(Path addonsDir, AddonRegistry registry) {
        this.addonsDir = addonsDir;
        this.registry = registry;
    }

    /** 已装载的句柄（本加载器来源）。 */
    public synchronized List<AddonRegistry.Handle> handles() {
        return scan();
    }

    /** 扫描并装载（可重复调用；重复 id 由注册表拒绝）。 */
    public synchronized List<AddonRegistry.Handle> load() {
        List<AddonRegistry.Handle> out = new ArrayList<AddonRegistry.Handle>();
        List<URL> urls = jarUrls();
        if (urls.isEmpty()) {
            return out;
        }
        loader = new URLClassLoader(urls.toArray(new URL[0]), JarAddonLoader.class.getClassLoader());
        try {
            for (AddonProvider p : ServiceLoader.load(AddonProvider.class, loader)) {
                try {
                    if (registry.register(p.id(), p.type(), p.version(), p, loader)) {
                        out.add(registry.get(p.id()));
                        AddonLog.info("addon 装载: " + p.id() + " (" + p.type() + " " + p.version() + ")");
                    } else {
                        AddonLog.warn("addon id 冲突，拒绝: " + p.id());
                    }
                } catch (Throwable t) {
                    AddonLog.warn("addon 装载失败（跳过）: " + t);
                }
            }
        } catch (Throwable t) {
            AddonLog.warn("ServiceLoader 扫描失败（跳过）: " + t);
        }
        return out;
    }

    /**
     * 整组重建：先摘除本加载器装过的全部句柄，再关旧子加载器，重扫。
     * 目录被删/为空 = 全部卸载（热拔插的「拔」）。
     */
    public synchronized List<AddonRegistry.Handle> reload() {
        for (AddonRegistry.Handle h : scan()) {
            registry.remove(h.id);
        }
        close();
        return load();
    }

    /** 摘除本加载器装过的句柄并关闭子加载器（幂等）。 */
    @Override
    public synchronized void close() {
        for (AddonRegistry.Handle h : scan()) {
            registry.remove(h.id);
        }
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // 关闭失败不影响后续重建
            }
            loader = null;
        }
    }

    private List<AddonRegistry.Handle> scan() {
        List<AddonRegistry.Handle> out = new ArrayList<AddonRegistry.Handle>();
        for (AddonRegistry.Handle h : registry.all()) {
            if (h.loader == loader) {
                out.add(h);
            }
        }
        return out;
    }

    private List<URL> jarUrls() {
        List<URL> urls = new ArrayList<URL>();
        if (addonsDir == null || !Files.isDirectory(addonsDir)) {
            return urls;
        }
        List<File> jars = new ArrayList<File>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(addonsDir, "*.jar")) {
            for (Path p : ds) {
                jars.add(p.toFile());
            }
        } catch (IOException e) {
            AddonLog.warn("addons 目录扫描失败: " + e);
            return urls;
        }
        // 按文件名排序保证装载顺序确定
        Object[] sorted = jars.toArray();
        Arrays.sort(sorted);
        for (Object o : sorted) {
            try {
                urls.add(((File) o).toURI().toURL());
            } catch (IOException e) {
                AddonLog.warn("jar URL 转换失败（跳过）: " + e);
            }
        }
        return urls;
    }
}
