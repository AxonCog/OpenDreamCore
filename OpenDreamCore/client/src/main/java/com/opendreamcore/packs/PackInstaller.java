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
     * spec：本地路径 或 https url
     * password：密码（可空）
     * top：置顶覆盖
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
            Path pack = resolve(spec);
            if (pack == null) {
                return new Result(false, "无法定位包文件: " + spec);
            }
            // 统一准备成目录形式（跨版本注入最稳），顺带完成密码校验
            Path extracted = preparePack(pack, password);
            boolean ok = injector.inject(extracted, password, top);
            if (ok) {
                applyReload();
            }
            return ok ? new Result(true, "材质包已注入: " + pack.getFileName())
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
                Path pack = resolve(p.spec());
                if (pack == null) {
                    r = new Result(false, "无法定位包文件: " + p.spec());
                } else {
                    extracted = preparePack(pack, p.password());
                    r = new Result(true, pack.getFileName().toString());
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
                if (ok) {
                    applyReload();
                }
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

    /** 注入成功后补一次资源重载：选中只是登记，不重载玩家看不到效果。
     *  丢回主线程队列执行，避开启动期与首次加载的竞态。 */
    private static void applyReload() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    mc.reloadResourcePacks();
                } catch (Throwable t) {
                    chat("§e[OpenDreamCore] 资源重载失败: " + t);
                }
            });
        } catch (Throwable ignored) {
            // 客户端还没起来就不管了，预置场景下次启动自然重试
        }
    }

    /** spec → 本地包（zip 文件或现成的文件夹包）。https 走 RemoteMedia 安全下载并缓存。 */
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
        if (Files.isRegularFile(p) || Files.isDirectory(p)) {
            return p;
        }
        return null;
    }

    private static Path packsCacheDir() throws IOException {
        Path dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("packs").resolve("cache");
        Files.createDirectories(dir);
        return dir;
    }

    // zip 处理

    /** 加密包且无 zip4j 时的专用异常（message 面向用户）。 */
    public static final class EncryptedZipException extends IOException {
        public EncryptedZipException(String msg) {
            super(msg);
        }
    }

    /**
     * 包来源统一准备：zip → 解压到托管目录；文件夹包 → 原样直用；
     * 单张 png → 铺成迷你包。图根定死在 assets/minecraft/textures/，名字决定包内路径：
     * 丢 gui/标题.png → 覆原版 gui 贴图；丢 1.png → 落自留区 gui/opendreamcore/1.png。
     * 返回值一定是可注入的目录。加密包走 zip4j 分支，明文包顺带补 pack.mcmeta。
     */
    private static Path preparePack(Path pack, String password) throws IOException {
        if (Files.isDirectory(pack)) {
            Path dir = pack.toAbsolutePath().normalize();
            ensureMcmeta(dir);
            return dir;
        }
        if (isImage(pack)) {
            return singleImagePack(pack);
        }
        // 解过一次就不重解了：托管目录和源包同路径会自锁（目录名撞上包名），
        // 而且重解一遍也白费功夫——上次解压成功过，mcmeta 齐就说明是完整的
        Path managed = managedPackDir(pack).toAbsolutePath().normalize();
        if (managed.startsWith(pack.toAbsolutePath().normalize())) {
            // 包名和目录名撞了（比如 OpenDreamCore.zip 旁边就是 OpenDreamCore/）：
            // 换个带后缀的名解，别往自己头上堆
            managed = managedPackDir(Path.of(pack.getFileName() + "_installed")).toAbsolutePath().normalize();
        }
        if (Files.isDirectory(managed) && Files.isRegularFile(managed.resolve("pack.mcmeta"))) {
            return managed;
        }
        return extractForRead(pack, password);
    }

    /**
     * 把 zip 内容解到托管目录（resourcepacks/OpenDreamCore/<包名>）后返回目录路径。
     * 目录形式注入跨版本最稳；落在 resourcepacks/ 下与 DreamCore 形态一致——
     * vanilla 扫描即可在资源包界面看到，重进游戏持久可用；顺带完成密码校验。
     */
    private static Path extractForRead(Path zip, String password) throws IOException {
        Path dest = managedPackDir(zip).toAbsolutePath().normalize();
        if (Files.exists(dest) && !Files.isDirectory(dest)) {
            // 同名普通文件占着坑（比如玩家手工放过一个无后缀文件），别覆盖别人的东西
            throw new IOException("解压目标被同名文件占用: " + dest);
        }
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

    /** 单文件图：只认 png（游戏就吃这格式），别的扩展名不费事。 */
    private static boolean isImage(Path p) {
        return p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png");
    }

    /**
     * 把一张散图铺成迷你包（堆在扫描根的 _single/ 下，_ 开头不参与扫描不会被二次安装）。
     * 一张图就一个包，不拆不嵌：
     * <ul>
     *   <li>躺在子目录里（根/gui/标题.png 或根/assets/minecraft/textures/gui/标题.png）
     *       → 从根到图的相对路径整条当包内 textures 路径，直接覆对应原版贴图；</li>
     *   <li>裸名躺根下（根/1.png）→ 落自留区 gui/opendreamcore/1.png，不碰原版任何文件。</li>
     * </ul>
     * 源图没变不重建，变了原地覆写（改图重进世界即生效）。
     */
    private static Path singleImagePack(Path img) throws IOException {
        Path src = img.toAbsolutePath().normalize();
        String stem = src.getFileName().toString();
        // 往上找到扫描根（名为 OpenDreamCore 那层），迷你包统一堆它底下的 _single/
        Path scanRoot = src.getParent();
        for (Path dir = src.getParent(); dir != null; dir = dir.getParent()) {
            if (String.valueOf(dir.getFileName()).equals("OpenDreamCore")) {
                scanRoot = dir;
                break;
            }
        }
        Path root = scanRoot;
        String rel = stem; // 从根往回拼相对路径，裸图就留纯名走自留区
        for (Path dir = src.getParent(); dir != null && !dir.equals(root); dir = dir.getParent()) {
            rel = dir.getFileName() + "/" + rel;
        }
        // 用户自己写了完整命名空间路径（assets/minecraft/textures/...）就别再套 textures 前缀
        String target = rel.startsWith("assets/") ? rel : "assets/minecraft/textures/" + rel;
        if (stem.toLowerCase(Locale.ROOT).endsWith(".png") && !rel.contains("/")) {
            target = "assets/minecraft/textures/gui/opendreamcore/" + stem; // 裸图进自留区
        }
        String safe = safeDirName(stem.substring(0, stem.length() - 4));
        Path dest = root.resolve("_single").resolve(safe).toAbsolutePath().normalize();
        Path out = dest.resolve(target).normalize();
        if (!out.startsWith(dest)) {
            throw new IOException("图名解析出非法路径: " + stem);
        }
        Files.createDirectories(out.getParent());
        if (!Files.isRegularFile(out) || Files.size(out) != Files.size(src)
                || !Files.getLastModifiedTime(out).equals(Files.getLastModifiedTime(src))) {
            Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(out, Files.getLastModifiedTime(src));
        }
        ensureMcmeta(dest);
        return dest;
    }

    /** 目录名净化：非法字符换下划线，中文保留（同 managedPackDir 那套）。 */
    private static String safeDirName(String stem) {
        String safe = stem.replaceAll("[^\\w\u4e00-\u9fa5./-]", "_");
        return safe.isBlank() ? "pack" : safe;
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
