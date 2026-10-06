package com.opendreamcore.core.addons;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5 契约：热插拔单测（注册/注销/回退/类加载器整组重建）+ 脚本引擎缺席跳过。
 */
public class AddonRegistryTest {

    // ---------------------------------------------------------------- 注册表语义

    @Test
    public void registerGetRemoveLifecycle() {
        AddonRegistry r = new AddonRegistry();
        assertTrue(r.register("a", "test", "1.0", "obj-a", null));
        assertNotNull(r.get("a"));
        assertEquals("test", r.get("a").type);
        assertFalse(r.register("a", "test", "2.0", "obj-a2", null), "重复 id 拒绝（先到者优先）");
        assertEquals("1.0", r.get("a").version);
        assertTrue(r.remove("a"));
        assertNull(r.get("a"));
        assertFalse(r.remove("a"), "重复注销安全");
    }

    @Test
    public void byTypeFilters() {
        AddonRegistry r = new AddonRegistry();
        r.register("m1", "model_format", "1", "codec1", null);
        r.register("m2", "model_format", "1", "codec2", null);
        r.register("s1", "script", "1", "pack", null);
        assertEquals(2, r.byType("model_format").size());
        assertEquals(1, r.byType("script").size());
        assertEquals(0, r.byType("none").size());
    }

    @Test
    public void badIdRejected() {
        AddonRegistry r = new AddonRegistry();
        assertFalse(r.register(null, "t", "1", "x", null));
        assertFalse(r.register("  ", "t", "1", "x", null));
        assertEquals(0, r.size());
    }

    // ---------------------------------------------------------------- jar 热加载（fixture jar）

    /** 从测试 classpath 找 fixture jar，拷进临时 addons 目录。 */
    private static Path stageFixture(Path dir) throws Exception {
        Files.createDirectories(dir);
        InputStream in = AddonRegistryTest.class.getResourceAsStream("/addons-fixture/fixture-provider.jar");
        assertNotNull(in, "fixture jar 在测试资源里");
        Path target = dir.resolve("fixture-provider.jar");
        Files.copy(in, target);
        in.close();
        return target;
    }

    @Test
    public void jarLoadDiscoverReloadAndUninstall() throws Exception {
        Path dir = Files.createTempDirectory("odc-addons-test");
        Path jar = stageFixture(dir);
        try {
            AddonRegistry registry = new AddonRegistry();
            JarAddonLoader jarLoader = new JarAddonLoader(dir, registry);

            List<AddonRegistry.Handle> first = jarLoader.load();
            assertEquals(1, first.size(), "ServiceLoader 发现 fixture");
            AddonRegistry.Handle h1 = registry.get("fixture-addon");
            assertNotNull(h1);
            assertEquals("test", h1.type);
            assertNotNull(h1.loader, "句柄携带子加载器");
            ClassLoader loader1 = h1.loader;

            List<AddonRegistry.Handle> second = jarLoader.reload();
            assertEquals(1, second.size(), "reload 整组重建后仍在");
            AddonRegistry.Handle h2 = registry.get("fixture-addon");
            assertNotNull(h2);
            assertFalse(h2.loader == loader1, "类加载器整组重建（新加载器实例）");

            // 拔：先关加载器（Windows 下打开中的 jar 不能删），删 jar，新加载器 load = 空
            jarLoader.close();
            assertNull(registry.get("fixture-addon"), "close 摘除句柄");
            Files.delete(jar);
            JarAddonLoader afterPull = new JarAddonLoader(dir, new AddonRegistry());
            try {
                assertEquals(0, afterPull.load().size(), "删 jar 后 load = 卸载");
            } finally {
                afterPull.close();
            }
        } finally {
            // 临时目录残留无害，但尽量清
            try {
                File[] rest = dir.toFile().listFiles();
                if (rest != null) {
                    for (File f : rest) {
                        f.delete();
                    }
                }
                dir.toFile().delete();
            } catch (Throwable ignored) {
            }
        }
    }

    @Test
    public void emptyOrMissingDirIsSafe() throws Exception {
        AddonRegistry registry = new AddonRegistry();
        JarAddonLoader missing = new JarAddonLoader(null, registry);
        assertEquals(0, missing.load().size(), "目录缺省 = 安全空载");
        JarAddonLoader empty = new JarAddonLoader(Files.createTempDirectory("odc-addons-empty"),
                registry);
        assertEquals(0, empty.load().size());
        empty.close();
    }

    // ---------------------------------------------------------------- 脚本包通道（引擎缺席跳过）

    @Test
    public void scriptChannelSkipsGracefullyWhenEngineAbsent() {
        // 不管引擎在不在，load() 都不抛；缺席时返回 0 并记日志（缺席即安全）
        AddonRegistry registry = new AddonRegistry();
        ScriptPackLoader loader = new ScriptPackLoader(null, registry);
        assertEquals(0, loader.load());
        if (!ScriptPackLoader.engineAvailable()) {
            AddonLog.info("宿主无 js 引擎，脚本通道跳过路径已覆盖");
        }
    }
}
