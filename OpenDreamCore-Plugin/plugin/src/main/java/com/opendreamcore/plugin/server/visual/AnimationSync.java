package com.opendreamcore.plugin.server.visual;

import com.opendreamcore.model.anim.AnimMessages;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 服务端动画文件夹同步：扫描 &lt;dataRoot&gt;/Animation/** 下全部动画/控制器文件，
 * 玩家 ready 时逐个下发（odc:anim 通道 f 帧，客户端 AnimationStore.applyRemote 落库）。
 *
 * <p>扫描规则与客户端一致：递归全收，.yml = 控制器，其余（json/bmd/bbmodel）= 动画本体；
 * 相对路径就是注册名，客户端会为子文件夹里的动画补裸名别名。
 * 超过 {@link AnimMessages#MAX_FILE_BYTES} 的文件跳过并警告（base64 后会撑爆单包）。
 */
public final class AnimationSync {

    private static final String[] EXTS = {".json", ".bmd", ".bbmodel", ".yml", ".yaml"};

    private final Path root;
    // relName(相对路径, '/' 分隔) → 文件字节
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    // 解析后的真实动画名（校验/补全用，内部名≠文件名）
    private final List<String> animNamesCache = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public AnimationSync(Path dataRoot) {
        this.root = dataRoot.resolve("Animation");
    }

    /** 重扫盘。返回收进来的文件数。 */
    public int reload() {
        files.clear();
        animNamesCache.clear();
        AnimVelocity.clear();
        warnings.clear();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(this::collect);
        } catch (Exception e) {
            warnings.add("扫描失败: " + e);
        }
        // 名单用解析后的真实动画名（一个 json 可以装多个动画，内部名≠文件名，
        // 萌芽翻滚包就是 翻滚.json 装前翻滚/闪避等）
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String lower = lower(e.getKey());
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
                // 动作包控制器：提取 velocity 三件套（原生位移，不依赖外部技能插件）
                try {
                    Object root = new org.yaml.snakeyaml.Yaml()
                            .load(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
                    if (root instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) root;
                        AnimVelocity.load(m);
                    }
                } catch (Exception ex) {
                    warnings.add(e.getKey() + " 位移配置解析失败: " + ex);
                }
                continue;
            }
            try {
                java.util.List<com.opendreamcore.model.ir.AnimationIR> list;
                if (lower.endsWith(".bbmodel")) {
                    list = new com.opendreamcore.model.format.bbmodel.BbModelCodec()
                            .decode(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8)).animations;
                } else if (lower.endsWith(".bmd")) {
                    list = new com.opendreamcore.model.format.bmd.BmdCodec()
                            .decode(e.getValue()).animations;
                } else {
                    list = com.opendreamcore.model.format.anim.BedrockAnimCodec
                            .parse(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
                }
                for (com.opendreamcore.model.ir.AnimationIR a : list) {
                    if (a != null && a.name != null && !a.name.isEmpty()) {
                        animNamesCache.add(a.name);
                    }
                }
            } catch (Exception ex) {
                warnings.add(e.getKey() + " 动画名解析失败: " + ex);
            }
        }
        return files.size();
    }

    private void collect(Path p) {
        String name = p.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        boolean hit = false;
        for (String ext : EXTS) {
            if (lower.endsWith(ext)) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            return;
        }
        try {
            byte[] data = Files.readAllBytes(p);
            if (data.length > AnimMessages.MAX_FILE_BYTES) {
                warnings.add(name + " 超过单包上限 " + AnimMessages.MAX_FILE_BYTES + "B，跳过下发");
                return;
            }
            String rel = root.relativize(p).toString().replace('\\', '/');
            files.put(rel, data);
        } catch (Exception e) {
            warnings.add(name + " 读取失败: " + e);
        }
    }

    /** 向玩家下发全部动画文件。返回发出的帧数。 */
    public int push(Player player,
                    java.util.function.BiConsumer<Player, String> sender) {
        int n = 0;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            try {
                sender.accept(player, AnimMessages.file(e.getKey(), e.getValue()));
                n++;
            } catch (Exception ex) {
                warnings.add(e.getKey() + " 下发失败: " + ex);
            }
        }
        return n;
    }

    /** push 后接一句人话日志（下发几张、谁）。 */
    public void logPushed(Player player, int frames) {
        org.bukkit.Bukkit.getLogger().info("[ODC-anim] 已向 " + player.getName()
                + " 下发动画文件 " + frames + "/" + files.size() + " 个"
                + (warnings.isEmpty() ? "" : "；警告: " + String.join("；", warnings)));
    }

    /** 补全用：解析出的真实动画名（去重保序）。 */
    public List<String> animNames() {
        return new ArrayList<>(animNamesCache);
    }

    /** 当前文件数（诊断日志用）。 */
    public int fileCount() {
        return files.size();
    }

    /** 已收文件清单（启动日志用）。 */
    public java.util.List<String> fileNames() {
        return new ArrayList<>(files.keySet());
    }

    public List<String> warnings() {
        return warnings;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
