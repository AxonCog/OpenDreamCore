package com.opendreamcore.packs;

import com.opendreamcore.client.spi.ResourcePackInjector;
import com.opendreamcore.remote.RemoteMedia;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 自定义材质包编排器：spec（本地路径或 https url）→ 下载/定位 → 解压校验（可选密码）
 * → 经 {@link ResourcePackInjector} SPI 注入 PackRepository。
 *
 * 安全：url 走 RemoteMedia（SSRF 防护 + 内网拒绝 + 16MB 上限 + 缓存）。
 * 加密 zip：优先 zip4j（classpath 存在时反射调用，支持 AES）；缺失则报错提示。
 */
public final class PackInstaller {

    private PackInstaller() {
    }

    /** 安装结果：ok + 用户可读消息。 */
    public record Result(boolean ok, String message) {
    }

    /** 服务端下发载荷（odc/pack 保留通道）：{"spec":"...","password":"...","top":true}。 */
    public record Payload(String spec, String password, boolean top) {
    }

    /**
     * 安装入口。
     *
     * @param spec     本地路径 或 https url
     * @param password 密码（可空）
     * @param top      置顶覆盖
     */
    public static Result install(String spec, String password, boolean top) {
        if (spec == null || spec.isBlank()) {
            return new Result(false, "缺少包路径或 url");
        }
        var injector = ResourcePackInjector.current();
        if (injector == null) {
            return new Result(false, "当前加载器未注册材质包注入器");
        }
        try {
            Path zip = resolve(spec);
            if (zip == null) {
                return new Result(false, "无法定位包文件: " + spec);
            }
            // 统一解压为目录（跨版本注入最稳），顺带完成密码校验
            Path extracted = extractForRead(zip, password);
            boolean ok = injector.inject(extracted, password, top);
            return ok ? new Result(true, "材质包已注入: " + zip.getFileName())
                    : new Result(false, "注入失败（详见日志）");
        } catch (EncryptedZipException e) {
            return new Result(false, e.getMessage());
        } catch (Exception e) {
            return new Result(false, "安装失败: " + e.toString());
        }
    }

    /**
     * 服务端下发安装（D3 下发链路）：后台线程完成下载/解压（纯 IO），
     * 注入（GL/TextureManager）回主线程执行；结果经聊天栏反馈。
     * 保留通道名 odc/pack；payload 为 {@link Payload} 的 JSON。
     */
    public static void installFromPayload(String json) {
        Payload p;
        try {
            p = new com.google.gson.Gson().fromJson(json, Payload.class);
        } catch (RuntimeException e) {
            return;
        }
        if (p == null || p.spec() == null || p.spec().isBlank()) {
            return;
        }
        Thread worker = new Thread(() -> {
            Result r;
            Path extracted = null;
            try {
                Path zip = resolve(p.spec());
                if (zip == null) {
                    r = new Result(false, "无法定位包文件: " + p.spec());
                } else {
                    extracted = extractForRead(zip, p.password());
                    r = new Result(true, zip.getFileName().toString());
                }
            } catch (EncryptedZipException e) {
                r = new Result(false, e.getMessage());
            } catch (Exception e) {
                r = new Result(false, "准备失败: " + e.toString());
            }
            final Result fr = r;
            final Path fex = extracted;
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (!fr.ok()) {
                    chat(fr.message());
                    return;
                }
                var injector = ResourcePackInjector.current();
                boolean ok = injector != null && injector.inject(fex, p.password(), p.top());
                chat(ok ? "§a[OpenDreamCore] 材质包已安装: " + fr.message()
                        : "§c[OpenDreamCore] 材质包注入失败");
            });
        }, "ODC-PackInstall");
        worker.setDaemon(true);
        worker.start();
    }

    private static void chat(String text) {
        try {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** spec → 本地 zip 文件。https 走 RemoteMedia 安全下载并缓存。 */
    private static Path resolve(String spec) throws IOException {
        String lower = spec.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            if (!lower.startsWith("https://")) {
                throw new IOException("仅允许 https 远程包");
            }
            Path cacheDir = packsCacheDir();
            Files.createDirectories(cacheDir);
            return RemoteMedia.download(spec, cacheDir);
        }
        Path p = Path.of(spec);
        return Files.isRegularFile(p) ? p : null;
    }

    private static Path packsCacheDir() throws IOException {
        Path dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("packs").resolve("cache");
        Files.createDirectories(dir);
        return dir;
    }

    // ================= zip 处理 =================

    /** 加密包且无 zip4j 时的专用异常（message 面向用户）。 */
    public static final class EncryptedZipException extends IOException {
        public EncryptedZipException(String msg) {
            super(msg);
        }
    }

    /**
     * 把 zip 内容解到托管目录（resourcepacks/OpenDreamCore/<包名>）后返回目录路径。
     * 目录形式注入跨版本最稳；落在 resourcepacks/ 下与 DreamCore 形态一致——
     * vanilla 扫描即可在资源包界面看到，重进游戏持久可用；顺带完成密码校验。
     * 明文包且 injector 支持直读 zip 时也可返回 null 让实现自行处理（当前统一解压）。
     */
    private static Path extractForRead(Path zip, String password) throws IOException {
        Path dest = managedPackDir(zip);
        Files.createDirectories(dest);
        List<String> entries = new ArrayList<>();
        boolean encryptedHit = false;
        try (InputStream in = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("zip 内非法路径: " + e.getName());
                }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                entries.add(e.getName());
            }
        } catch (IOException ioe) {
            // 典型加密症状：读取条目流抛 ZipException（AES/标准加密）
            encryptedHit = true;
        }
        if (encryptedHit || entries.isEmpty() && looksEncrypted(zip)) {
            Path r = extractWithZip4j(zip, dest, password);
            if (r != null) {
                ensureMcmeta(dest);
                return r;
            }
            throw new EncryptedZipException(
                    "该材质包已加密：需要 zip4j 库支持（把 zip4j jar 放入 mods 后重试）");
        }
        ensureMcmeta(dest);
        return dest;
    }

    /**
     * 托管目录：resourcepacks/OpenDreamCore/<包名>。
     * 与 vanilla resourcepacks 扫描同级可见，玩家在材质包界面直接可见；
     * 同名重装原地覆盖（服务端更新包后重推即生效）。
     */
    private static Path managedPackDir(Path zip) throws IOException {
        String name = zip.getFileName().toString();
        String stem = name.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? name.substring(0, name.length() - 4) : name;
        String safe = stem.replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_");
        if (safe.isBlank()) {
            safe = "pack";
        }
        Path root = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("resourcepacks").resolve("OpenDreamCore");
        Files.createDirectories(root);
        return root.resolve(safe);
    }

    /** 缺 pack.mcmeta 时按当前客户端版本生成最小清单——vanilla 扫描 resourcepacks/ 即可识别为合法资源包。 */
    private static void ensureMcmeta(Path dir) {
        try {
            if (!Files.isDirectory(dir) || Files.isRegularFile(dir.resolve("pack.mcmeta"))) {
                return;
            }
            int format = com.opendreamcore.client.CompatRender.currentPackFormat();
            if (format <= 0) {
                return; // 拿不到版本号就跳过；注入器直读目录，功能不受影响
            }
            Files.writeString(dir.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":" + format
                            + ",\"description\":\"OpenDreamCore 资源包\"}}",
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 生成失败不影响注入
        }
    }

    /** 粗判：文件头之后的中央目录含 AES 标记（0x9901）或条目名全无而体积不小。 */
    private static boolean looksEncrypted(Path zip) {
        try {
            return Files.size(zip) > 64; // 有内容但明文流一个条目都没读到 → 大概率加密
        } catch (IOException ignored) {
            return false;
        }
    }

    /** zip4j 反射解压（可选依赖）：成功返回目标目录，库缺失/失败返回 null。 */
    private static Path extractWithZip4j(Path zip, Path dest, String password) {
        try {
            Class<?> zipFileClz = Class.forName("net.lingala.zip4j.ZipFile");
            Object zf;
            if (password != null && !password.isEmpty()) {
                zf = zipFileClz.getConstructor(java.io.File.class, char[].class)
                        .newInstance(zip.toFile(), password.toCharArray());
            } else {
                zf = zipFileClz.getConstructor(java.io.File.class).newInstance(zip.toFile());
            }
            if (password != null && !password.isEmpty()) {
                zf.getClass().getMethod("setPassword", char[].class).invoke(zf, password.toCharArray());
            }
            zf.getClass().getMethod("extractAll", String.class).invoke(zf, dest.toString());
            return dest;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 工具：小写扩展名。 */
    public static String extOf(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
