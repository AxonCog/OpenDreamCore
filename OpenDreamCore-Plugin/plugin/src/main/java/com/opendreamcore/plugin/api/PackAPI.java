package com.opendreamcore.plugin.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.CustomPacketRegistry;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * D3 服务端下发链路：向客户端推送自定义材质包安装指令。
 *
 * 两条通道（新客户端自动识别；老客户端只认 JSON 指令，会忽略字节流通道）：
 *   {@link #CHANNEL}      JSON 指令 {"spec","password","top"}——https url 原样转发，客户端自己下载；
 *   {@link #DATA_CHANNEL} 字节分片流 "push|名字|md5|大小|片数|置顶|密码B64" + "part|md5|序号|b64"，
 *                         与资源云同款 48KB 分块，远程玩家也能拿到服务端本地文件。
 *
 * 用法（附属插件）：
 * <pre>
 *     PackAPI.push(player, "https://example.com/pack.zip", null, true);
 *     PackAPI.push(player, "plugins/MyPlugin/packs/vip.zip", "123456", true); // 本地文件→自动字节下发
 *     PackAPI.pushBytes(player, "vip.zip", zipBytes, "123456", true);         // 手里就有字节
 * </pre>
 *
 * 历史包袱说明：旧版把本地路径"字符串"推给客户端，只有客户端装在同一台机器时才取得到——
 * ODC 通道本来就只转发字符串、不发文件字节，远程玩家自然拿不到。现在 {@link #push} 对
 * 确实存在的本地文件自动升级成字节分片下发，远程玩家照装不误；url 与老行为完全不变，
 * 附属插件一行不用改。
 */
public final class PackAPI {

    /** 保留通道名（与客户端 ClientController 拦截点约定一致）。 */
    public static final String CHANNEL = "odc/pack";

    /** 字节分片下发通道（与客户端 PackInstaller.receivePackData 约定一致）。 */
    public static final String DATA_CHANNEL = "odc/packdata";

    /** 分片大小：与资源云 ServerResourcePipeline.CHUNK_BYTES 保持一致（实测安全的最大单包）。 */
    private static final int CHUNK_BYTES = 48 * 1024;

    /** 单包硬上限（512MB），超过直接拒绝下发。 */
    private static final long MAX_BYTES = 512L * 1024 * 1024;

    private PackAPI() {
    }

    /**
     * 向单个玩家推送材质包安装指令。
     *
     * spec 是 https url 时原样转发给客户端下载；spec 是服务端本地文件时自动切换成字节分片
     * 下发（后台线程边读边发，玩家掉线立即中止）；两者都支持加密密码与置顶。
     *
     * @return 是否已受理（字节下发是异步的，true 只代表入队成功）
     */
    public static boolean push(Player player, String spec, String password, boolean top) {
        if (player == null || spec == null || spec.isEmpty()) {
            return false;
        }
        if (!spec.startsWith("http://") && !spec.startsWith("https://")) {
            Path file = Paths.get(spec);
            if (Files.isRegularFile(file)) {
                return pushFile(player, file, password, top);
            }
            OpenDreamCorePlugin.get().getLogger().warning(
                    "[PackAPI] 本地路径不存在，按原样转发（远程玩家将无法获取）: " + spec);
        }
        try {
            CustomPacketRegistry.send(OpenDreamCorePlugin.get(), player, CHANNEL, toJson(spec, password, top));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 广播给所有在线玩家。
     *
     * @see #push
     */
    public static int pushAll(Iterable<Player> players, String spec, String password, boolean top) {
        int n = 0;
        for (Player p : players) {
            if (push(p, spec, password, top)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 把手里现成的 zip 字节直接推给玩家（自动分片下发，远程玩家照装）。
     *
     * packName 用作客户端本地文件名（纯文件名，带路径也会被掐掉 basename），
     * 缺 .zip 后缀会自动补上；字节内容必须是 zip。
     *
     * @return 是否已受理（后台线程异步分片，true 只代表入队成功）
     */
    public static boolean pushBytes(Player player, String packName, byte[] zip, String password, boolean top) {
        if (player == null || packName == null || packName.isEmpty()
                || zip == null || zip.length == 0) {
            return false;
        }
        if (zip.length > MAX_BYTES) {
            OpenDreamCorePlugin.get().getLogger().warning("[PackAPI] " + packName + " 超过 "
                    + (MAX_BYTES / 1024 / 1024) + "MB 上限，拒绝下发");
            return false;
        }
        final String name = safeName(packName);
        final byte[] bytes = zip;
        Thread worker = new Thread(() -> streamBytes(player, name, bytes, password, top),
                "ODC-PackPush-" + player.getName());
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /** 批量版 {@link #pushBytes}。 */
    public static int pushBytesAll(Iterable<Player> players, String packName, byte[] zip, String password, boolean top) {
        int n = 0;
        for (Player p : players) {
            if (pushBytes(p, packName, zip, password, top)) {
                n++;
            }
        }
        return n;
    }

    /** 本地文件 → 后台线程分片下发。 */
    private static boolean pushFile(Player player, Path file, String password, boolean top) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return false;
        }
        if (size > MAX_BYTES) {
            OpenDreamCorePlugin.get().getLogger().warning("[PackAPI] " + file + " 超过 "
                    + (MAX_BYTES / 1024 / 1024) + "MB 上限，拒绝下发");
            return false;
        }
        String raw = file.getFileName() == null ? "pack.zip" : file.getFileName().toString();
        final String name = safeName(raw);
        final Path f = file;
        final long sz = size;
        Thread worker = new Thread(() -> streamFile(player, name, f, sz, password, top),
                "ODC-PackPush-" + player.getName());
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /** 后台干活（文件版）：先流式算 MD5，再 announce + 逐片读发，玩家掉线立即中止。 */
    private static void streamFile(Player player, String name, Path file, long size, String password, boolean top) {
        try {
            String md5 = md5Of(file);
            int total = (int) ((size + CHUNK_BYTES - 1) / CHUNK_BYTES);
            if (total <= 0) {
                return;
            }
            if (!sendAnnounce(player, name, md5, size, total, password, top)) {
                OpenDreamCorePlugin.get().getLogger().warning(
                        "[PackAPI] " + player.getName() + " 已离线，取消下发 " + name);
                return;
            }
            try (InputStream in = Files.newInputStream(file)) {
                byte[] chunk = new byte[CHUNK_BYTES];
                for (int seq = 0; seq < total; seq++) {
                    if (!player.isOnline()) {
                        OpenDreamCorePlugin.get().getLogger().warning(
                                "[PackAPI] " + player.getName() + " 掉线，中止下发 " + name);
                        return;
                    }
                    int len = 0;
                    while (len < chunk.length) {
                        int n = in.read(chunk, len, chunk.length - len);
                        if (n < 0) {
                            break;
                        }
                        len += n;
                    }
                    if (len <= 0) {
                        break;
                    }
                    sendPart(player, md5, seq, Arrays.copyOfRange(chunk, 0, len));
                }
                OpenDreamCorePlugin.get().getLogger().info("[PackAPI] " + name + "（"
                        + mb(size) + "，" + total + " 片）已下发 → " + player.getName());
            }
        } catch (Exception e) {
            OpenDreamCorePlugin.get().getLogger().warning(
                    "[PackAPI] 字节下发失败（" + name + " → " + player.getName() + "）: " + e);
        }
    }

    /** 后台干活（字节版）：announce + 分片拷贝下发。 */
    private static void streamBytes(Player player, String name, byte[] zip, String password, boolean top) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String md5 = hex(md.digest(zip));
            int total = (int) ((zip.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
            if (!sendAnnounce(player, name, md5, zip.length, total, password, top)) {
                OpenDreamCorePlugin.get().getLogger().warning(
                        "[PackAPI] " + player.getName() + " 已离线，取消下发 " + name);
                return;
            }
            for (int seq = 0; seq < total; seq++) {
                if (!player.isOnline()) {
                    OpenDreamCorePlugin.get().getLogger().warning(
                            "[PackAPI] " + player.getName() + " 掉线，中止下发 " + name);
                    return;
                }
                int from = seq * CHUNK_BYTES;
                int len = Math.min(CHUNK_BYTES, zip.length - from);
                sendPart(player, md5, seq, Arrays.copyOfRange(zip, from, from + len));
            }
            OpenDreamCorePlugin.get().getLogger().info("[PackAPI] " + name + "（"
                    + mb(zip.length) + "，" + total + " 片）已下发 → " + player.getName());
        } catch (Exception e) {
            OpenDreamCorePlugin.get().getLogger().warning(
                    "[PackAPI] 字节下发失败（" + name + " → " + player.getName() + "）: " + e);
        }
    }

    private static boolean sendAnnounce(Player player, String name, String md5, long size, int total,
                                        String password, boolean top) {
        if (!player.isOnline()) {
            return false;
        }
        String pw = (password == null || password.isEmpty())
                ? "-"
                : Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
        CustomPacketRegistry.send(OpenDreamCorePlugin.get(), player, DATA_CHANNEL,
                "push|" + name + "|" + md5 + "|" + size + "|" + total + "|" + (top ? 1 : 0) + "|" + pw);
        return true;
    }

    private static void sendPart(Player player, String md5, int seq, byte[] piece) {
        CustomPacketRegistry.send(OpenDreamCorePlugin.get(), player, DATA_CHANNEL,
                "part|" + md5 + "|" + seq + "|" + Base64.getEncoder().encodeToString(piece));
    }

    /** 文件名安全化：掐 basename、掐掉协议保留字符（与客户端 sanitizePackName 同款白名单）、补 .zip。 */
    private static String safeName(String raw) {
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceFirst("^_+", "");
        name = name.replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_").trim();
        if (name.isEmpty()) {
            name = "pack";
        }
        if (!name.toLowerCase().endsWith(".zip")) {
            name = name + ".zip";
        }
        return name;
    }

    private static String md5Of(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream raw = Files.newInputStream(file);
             DigestInputStream din = new DigestInputStream(raw, md)) {
            byte[] buf = new byte[64 * 1024];
            while (din.read(buf) != -1) {
                // 只为算 MD5，字节随手丢
            }
        }
        return hex(md.digest());
    }

    private static String hex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String mb(long bytes) {
        return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
    }

    private static String toJson(String spec, String password, boolean top) {
        return "{\"spec\":" + quote(spec)
                + ",\"password\":" + (password == null ? "null" : quote(password))
                + ",\"top\":" + top + "}";
    }

    private static String quote(String s) {
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
