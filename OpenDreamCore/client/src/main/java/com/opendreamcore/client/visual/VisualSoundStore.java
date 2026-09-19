package com.opendreamcore.client.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sounds 系统客户端消费器（把规则里的音效变成玩家耳朵里的声儿）。
 *
 * 玩法：id 就是音效键。服务端脚本喊一嗓子 `SoundAPI.播放(玩家, "充值成功")`，
 * 客户端从 custom:sound 通道接到指令，照着规则表把音效放出来：
 *   音效名:                      # 内置音效（mc 声音注册表的事件名）或散装音频文件
 *     volume: 1.0                # 可选，音量（默认 1）
 *     pitch: 1.0                 # 可选，音调（默认 1）
 *     loop: true                 # 可选，循环（SoundAPI.停止("名字") 能精确掐掉）
 *
 * 音效本体两条道，跟物品贴图一个思路：
 *   1) 内置：minecraft 注册表按事件名解析（sounds.json / 资源云都算）；
 *   2) 散装：OpenDreamCore/sounds/ 底下丢 .ogg/.wav，进服时扫一遍登记。
 *
 * 规矩：规则表随 visual_rules_sync 下发，播放指令只传键名不传内容——
 * 客户端只有装了文件才放得出来，天然防作弊（听不到的东西不可能凭空响）。
 */
public final class VisualSoundStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisualSoundStore.class);

    /** 一条已解析的音效规则。 */
    public static final class SoundEntry {
        public final String id;
        public final String sound;      // 内置事件名或散装文件名
        public final float volume;
        public final float pitch;
        public final boolean loop;

        SoundEntry(String id, String sound, float volume, float pitch, boolean loop) {
            this.id = id;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.loop = loop;
        }
    }

    /** 视觉规则键 → 音效条目（整表替换，读侧无锁）。 */
    private static volatile Map<String, SoundEntry> entries = Map.of();

    /** 循环音效登记表：键 → 实例（SoundAPI.停止(id) 精确停）。 */
    private static final Map<String, SoundInstance> LOOPS = new ConcurrentHashMap<>();

    /** 散装音频注册表：相对路径/文件名 → 实际文件路径（loadFileSounds 扫描填充）。 */
    private static final Map<String, java.nio.file.Path> FILE_SOUNDS = new ConcurrentHashMap<>();

    private VisualSoundStore() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库时调用）。 */
    public static void refresh() {
        Map<String, SoundEntry> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : ClientVisualStore.get().rulesOf("Sounds").entrySet()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(e.getValue());
                if (ir == null) {
                    continue;
                }
                out.put(e.getKey(), parseEntry(e.getKey(), ir));
            } catch (Exception ignored) {
                // 单条规则坏了静默跳过——缺个音效不影响别的系统
            }
        }
        entries = java.util.Collections.unmodifiableMap(out);
    }

    /** 单条规则解析：键内是参数，或极简形态规则体就是音效名标量。 */
    private static SoundEntry parseEntry(String id, Map<String, Object> ir) {
        String sound = str(firstOf(ir, "sound", "file", "事件"));
        if (sound == null || sound.isBlank()) {
            return new SoundEntry(id, id, 1f, 1f, false);
        }
        float volume = (float) num(firstOf(ir, "volume", "音量"), 1.0);
        float pitch = (float) num(firstOf(ir, "pitch", "音调"), 1.0);
        boolean loop = Boolean.parseBoolean(String.valueOf(firstOf(ir, "loop", "循环")));
        return new SoundEntry(id, sound, volume, pitch, loop);
    }

    /** 某视觉键的音效规则；无命中 null。 */
    public static SoundEntry entryOf(String key) {
        return entries.get(key);
    }

    /** 是否有规则（调试/统计用）。 */
    public static boolean hasAny() {
        return !entries.isEmpty();
    }

    /** 已注册的散装音频数（对齐日志用）。 */
    public static int fileSoundCount() {
        return FILE_SOUNDS.size();
    }

    /** 某音效键是否是散装音频（调试/补全用）。 */
    public static boolean isFileSound(String name) {
        return FILE_SOUNDS.containsKey(name);
    }

    /**
     * 外部注册：资源云解密出来的音频直接进播放表（ResourceCacheStore 收工后调用）。
     * plainFile 是泄密的临时明文；名字沿用云里的相对路径，规则里写什么就能吹什么。
     */
    public static void registerExternal(String name, java.nio.file.Path plainFile) {
        if (name == null || plainFile == null || !java.nio.file.Files.isRegularFile(plainFile)) {
            return;
        }
        FILE_SOUNDS.put(name, plainFile);
    }

    // 内部小工具

    private static Object firstOf(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double num(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    //
    // 散装音频装载（OpenDreamCore/sounds/ 下的 .ogg/.wav，与贴图散装资源同一思路）
    //

    /** 扫描注册本地散装音频；返回注册数。进服时调用一次。 */
    public static int loadFileSounds(java.nio.file.Path gameDir) {
        java.nio.file.Path root = gameDir.resolve("OpenDreamCore").resolve("sounds");
        if (!java.nio.file.Files.isDirectory(root)) {
            return 0;
        }
        int n = 0;
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root, 2)) {
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk.filter(java.nio.file.Files::isRegularFile)::iterator) {
                String fn = String.valueOf(p.getFileName());
                if (!isAudio(fn)) {
                    continue;
                }
                // 键=根下相对路径（含一层子目录），与 LooseResourceLoader 的 registry 键同一习惯
                String path = root.relativize(p).toString().replace('\\', '/');
                FILE_SOUNDS.put(path, p);
                n++;
            }
        } catch (Exception e) {
            LOGGER.warn("散装音频扫描失败: {}", e.toString());
        }
        if (n > 0) {
            LOGGER.info("散装音频已注册 {} 个（OpenDreamCore/sounds/）", n);
        }
        return n;
    }

    private static boolean isAudio(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".mp3");
    }

    //
    // 播放（custom:sound 通道指令入口）
    //

    /** 按视觉键播放（规则声明的音量/音调）。 */
    public static void play(String key) {
        play(key, null);
    }

    /**
     * 带音量/音调覆盖的播放（指令参数覆盖规则默认值）。
     * 内置音效按事件名走 mc 声音注册表；散装音频走文件流实例。
     * loop 音效重播先停旧的，登记 LOOPS 供精确停止。
     */
    public static void play(String key, float[] override) {
        SoundEntry e = entries.get(key);
        if (e == null) {
            return;
        }
        float vol = override != null && override.length > 0 ? override[0] : e.volume;
        float pit = override != null && override.length > 1 ? override[1] : e.pitch;
        stopLoop(key); // 循环音效重播先停旧的，不然叠音
        // 内置音效优先：按事件名查 mc 声音注册表（sounds.json / 资源云注入都在这层生效）
        SoundEvent event = com.opendreamcore.client.methods.ClientMethodSupport.soundEvent(e.sound);
        if (event != null) {
            SoundInstance inst = e.loop ? new LoopingSound(event, vol, pit)
                    : SimpleSoundInstance.forUI(event, pit, vol);
            Minecraft.getInstance().getSoundManager().play(inst);
            if (e.loop) {
                LOOPS.put(key, inst);
            }
            return;
        }
        // 散装音频：文件字节流包装成 SoundInstance（无注册表条目也能播）
        java.nio.file.Path file = findFileSound(e.sound);
        if (file == null) {
            return;
        }
        SoundInstance inst = new FileSound(locationOf(file), vol, pit, e.loop);
        Minecraft.getInstance().getSoundManager().play(inst);
        if (e.loop) {
            LOOPS.put(key, inst);
        }
    }

    /** 散装音频查找：注册表精确命中优先，退回 sounds 根目录下同名文件。 */
    private static java.nio.file.Path findFileSound(String name) {
        java.nio.file.Path hit = FILE_SOUNDS.get(name);
        if (hit != null && java.nio.file.Files.isRegularFile(hit)) {
            return hit;
        }
        // 短文件名兜底：扫过的文件里取尾段匹配的那个
        for (Map.Entry<String, java.nio.file.Path> e : FILE_SOUNDS.entrySet()) {
            if (String.valueOf(e.getValue().getFileName()).equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 散装音频的播放用 ResourceLocation（客户端私有域，路径净化防非法字符）。 */
    private static ResourceLocation locationOf(java.nio.file.Path file) {
        return com.opendreamcore.client.CompatRender.rl("odc_sounds",
                sanitize(String.valueOf(file.getFileName())));
    }

    /** RL 路径净化：非 [a-z0-9_.-/] 字符替换为下划线+短哈希（与 LooseResourceLoader 同策略）。 */
    private static String sanitize(String rel) {
        StringBuilder sb = new StringBuilder();
        for (char c : rel.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '/' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_').append(Integer.toHexString(c));
            }
        }
        return sb.toString();
    }

    /** 停指定循环音效（未登记的键忽略）。 */
    public static void stopLoop(String key) {
        SoundInstance inst = LOOPS.remove(key);
        if (inst != null) {
            Minecraft.getInstance().getSoundManager().stop(inst);
        }
    }

    /** 停全部循环音效（断线时清场用）。 */
    public static void stopAllLoops() {
        for (String key : List.copyOf(LOOPS.keySet())) {
            stopLoop(key);
        }
    }

    /** 循环音效实例（looping 置位；事件或散装 RL 两构造）。 */
    private static final class LoopingSound extends AbstractSoundInstance {
        LoopingSound(SoundEvent event, float volume, float pitch) {
            super(event, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = true;
        }

        LoopingSound(ResourceLocation location, float volume, float pitch) {
            super(location, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = true;
            this.relative = true; // 散装循环按 UI 相对位置播，不随位置衰减
        }
    }

    /** 一次性文件音效实例（散装 .ogg/.wav/.mp3，UI 相对位置播放）。 */
    private static final class FileSound extends AbstractSoundInstance {
        FileSound(ResourceLocation location, float volume, float pitch, boolean loop) {
            super(location, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = loop;
            this.relative = true;
        }
    }
}