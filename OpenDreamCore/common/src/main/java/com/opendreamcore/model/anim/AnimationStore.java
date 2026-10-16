package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.anim.BedrockAnimCodec;
import com.opendreamcore.model.format.bbmodel.BbModelCodec;
import com.opendreamcore.model.format.bmd.BmdCodec;
import com.opendreamcore.model.bake.ModelLog;
import com.opendreamcore.util.J8;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animation 文件夹统一库：一个目录里 .yml 一律当控制器，其余当动画本体，
 * 子文件夹随便建——相对路径（去后缀、正斜杠）就是名字。
 *
 * <p>收三种动画载体：独立 json（bedrock 写法，一文件可含多条）、bmd（按骨架组拆条）、
 * bbmodel（吃它内嵌的动画）。每条动画先按内部名（animation.x.y）登记；
 * 整文件只出一条动画时，再把文件路径名也指过去，两种写法都能引用。
 *
 * <p>双根：本地根（实例 OpenDreamCore/Animation/ 等）扫盘，服务端下发的文件走
 * {@link #applyRemote} 直接进内存，同名的以后到的为准——服务器说了算，联机才有一致性。
 */
public final class AnimationStore {

    private final List<Path> roots = new ArrayList<Path>();
    private final Map<String, AnimationIR> anims = new LinkedHashMap<String, AnimationIR>();
    private final Map<String, String> controllers = new LinkedHashMap<String, String>();
    private final Map<String, byte[]> remote = new LinkedHashMap<String, byte[]>();
    private final Map<String, ControllerIR> parsedControllers = new LinkedHashMap<String, ControllerIR>();
    private int files;

    /** 多根扫描；后扫的根覆盖先扫的同名项。 */
    public synchronized void loadAll(List<Path> newRoots) {
        roots.clear();
        if (newRoots != null) {
            for (Path p : newRoots) {
                if (p != null) {
                    roots.add(p);
                }
            }
        }
        reload();
    }

    public synchronized void reload() {
        anims.clear();
        controllers.clear();
        parsedControllers.clear();
        files = 0;
        for (Path root : roots) {
            scanRoot(root, "");
        }
        if (ensureTemplates()) {
            for (Path root : roots) {
                scanRoot(root, "");
            }
        }
        // 服务端下发的最后落位，覆盖本地同名
        for (Map.Entry<String, byte[]> e : remote.entrySet()) {
            ingest(e.getKey(), e.getValue());
        }
        ModelLog.info("Animation 库就绪：动画 " + anims.size() + " 条，控制器 "
                + controllers.size() + " 个（文件 " + files + "）");
    }

    private void scanRoot(Path root, String prefix) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                ingest(rel, readFile(p));
            });
        } catch (Exception e) {
            ModelLog.warn("Animation 扫描失败 " + root + ": " + e);
        }
    }

    /** 按扩展名分流：yml=控制器，其余按载体解析；解析失败只丢这一个文件。 */
    private synchronized void ingest(String relName, byte[] data) {
        if (relName == null || data == null) {
            return;
        }
        files++;
        String lower = relName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            controllers.put(stem(relName), new String(data, StandardCharsets.UTF_8));
            parsedControllers.remove(stem(relName));
            return;
        }
        if (lower.endsWith(".geo.json")) {
            return; // 模型文件，动画库不碰
        }
        try {
            if (lower.endsWith(".json")) {
                String text = new String(data, StandardCharsets.UTF_8);
                register(stem(relName), BedrockAnimCodec.parse(text));
            } else if (lower.endsWith(".bmd")) {
                BmdCodec codec = new BmdCodec();
                codec.setAnimName(stem(relName));
                register(stem(relName), codec.decode(data).animations);
            } else if (lower.endsWith(".bbmodel")) {
                DecodeResult r = new BbModelCodec().decode(new String(data, StandardCharsets.UTF_8));
                register(stem(relName), r.animations);
            }
        } catch (Exception e) {
            ModelLog.warn("动画文件解析失败 " + relName + " -> " + e);
        }
    }

    /** 内部名全收；单条动画再补一个文件名别名。 */
    private void register(String fileStem, List<AnimationIR> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (AnimationIR a : list) {
            if (a != null && a.name != null) {
                anims.put(a.name, a);
            }
        }
        if (list.size() == 1 && list.get(0) != null) {
            anims.put(fileStem, list.get(0));
            // 子文件夹里的动画补裸名别名（Animation/翻滚/前翻滚.json → "前翻滚"）：
            // 指令/触发都用短名，完整相对路径名用户记不住；同名冲突时保留首个
            int slash = fileStem.lastIndexOf('/');
            if (slash >= 0) {
                String bare = fileStem.substring(slash + 1);
                if (!bare.isEmpty() && !anims.containsKey(bare)) {
                    anims.put(bare, list.get(0));
                }
            }
        }
    }

    private static String stem(String relName) {
        String s = relName;
        int dot = s.lastIndexOf('.');
        if (dot > s.lastIndexOf('/')) {
            s = s.substring(0, dot);
        }
        return s;
    }

    private static byte[] readFile(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }

    /** 服务端下发的单文件落位（key=相对路径）。 */
    public synchronized void applyRemote(String relName, byte[] data) {
        if (relName == null || data == null) {
            return;
        }
        remote.put(relName.replace('\\', '/'), data);
        ingest(relName.replace('\\', '/'), data);
    }

    public synchronized AnimationIR animByName(String name) {
        return name == null ? null : anims.get(name.trim());
    }

    public synchronized String controllerYaml(String name) {
        return name == null ? null : controllers.get(name.trim());
    }

    /** 是否由服务端下发的控制器覆盖/提供；用于客户端选择时保持服务端触发可达。 */
    public synchronized boolean isRemoteController(String name) {
        if (name == null) {
            return false;
        }
        String key = name.trim().replace('\\', '/');
        for (String relName : remote.keySet()) {
            String lower = relName.toLowerCase(Locale.ROOT);
            if ((lower.endsWith(".yml") || lower.endsWith(".yaml")) && stem(relName).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 控制器 IR（懒解析，结果缓存；reload 后缓存随文本一起清）。
     * 解析失败原样抛——控制器坏了必须当场喊出来，不能悄悄没动作。
     */
    public synchronized ControllerIR controllerIR(String name) throws Exception {
        String yaml = controllerYaml(name);
        if (yaml == null) {
            return null;
        }
        ControllerIR hit = parsedControllers.get(name.trim());
        if (hit == null) {
            hit = ControllerCodec.parse(yaml);
            parsedControllers.put(name.trim(), hit);
        }
        return hit;
    }

    public synchronized Map<String, String> controllers() {
        return new LinkedHashMap<String, String>(controllers);
    }

    public synchronized Map<String, AnimationIR> anims() {
        return new LinkedHashMap<String, AnimationIR>(anims);
    }

    public synchronized boolean isEmpty() {
        return anims.isEmpty() && controllers.isEmpty();
    }

    /** 现有模型侧动画池的桥：模型内嵌/旧 animations 目录照旧，本库的内容并进去。 */
    public synchronized void mergeInto(Map<String, AnimationIR> target) {
        if (target != null) {
            target.putAll(anims);
        }
    }

    /** 测试/诊断用：定位根列表。 */
    public synchronized List<Path> roots() {
        return new ArrayList<Path>(roots);
    }

    /** 首次使用（扫不出任何东西）时播示例；只补缺不覆盖，服主改过的文件永远不动。返回是否播了。 */
    private boolean ensureTemplates() {
        if (roots.isEmpty() || files > 0 || !anims.isEmpty() || !controllers.isEmpty()) {
            return false;
        }
        try {
            Path dir = roots.get(0).resolve("controller");
            Files.createDirectories(dir);
            Path nativeExample = dir.resolve("人形.yml");
            if (Files.notExists(nativeExample)) {
                J8.writeString(nativeExample, NATIVE_EXAMPLE, StandardCharsets.UTF_8);
            }
            Path dragonExample = dir.resolve("龙核写法示例.yml");
            if (Files.notExists(dragonExample)) {
                J8.writeString(dragonExample, DRAGON_EXAMPLE, StandardCharsets.UTF_8);
            }
            Path animExample = roots.get(0).resolve("示例动画.json");
            if (Files.notExists(animExample)) {
                J8.writeString(animExample, ANIM_EXAMPLE, StandardCharsets.UTF_8);
            }
            ModelLog.info("Animation 文件夹为空，已播示例（controller/人形.yml、龙核写法示例.yml、示例动画.json）");
        } catch (Exception e) {
            ModelLog.warn("Animation 示例播种失败: " + e);
            return false;
        }
        return true;
    }

    private static final String NATIVE_EXAMPLE = "# 控制器示例（原生写法）\n"
            + "# 放在 Animation/ 下任意位置的 .yml 都当控制器；动画本体（json/bmd/bbmodel 内嵌）放同层或子文件夹\n"
            + "# 实时条件：when: moving|swinging|sneak|swimming|dead|riding\n"
            + "\n"
            + "default: idle\n"
            + "\n"
            + "states:\n"
            + "  idle:   { anim: idle }\n"
            + "  walk:   { anim: walk, when: moving, transition_time: 0.15 }\n"
            + "  attack:\n"
            + "    variants: [attack_1, attack_2]      # 随机挑一条播\n"
            + "    when: swinging\n"
            + "    once: true                          # 播完自动回 default\n"
            + "    next_transition_time: 0.2\n"
            + "    priority: 10\n"
            + "  death:  { anim: death, when: dead, loop: hold }\n"
            + "\n"
            + "layers:\n"
            + "  base: { blend_type: OVERRIDE, weight: 1.0 }\n"
            + "  特效: { blend_type: ADDING, weight: 0.6 }\n"
            + "\n"
            + "triggers:                               # 插件 AnimationAPI.anim().trigger(实体, \"cast\") 用\n"
            + "  cast:\n"
            + "    layer: 特效\n"
            + "    anim: \"cast_start > cast_loop\"     # 队列：起手播完自动进循环\n"
            + "    transition_time: 0.15\n";

    private static final String DRAGON_EXAMPLE = "# 龙核写法示例：从龙核 AnimationController 拷来的文件一个字不用改\n"
            + "# 根上有 Layer/Trigger 节就自动按龙核语法认领，与原生写法可混用（每个文件独立选择）\n"
            + "\n"
            + "Layer:\n"
            + "  base:\n"
            + "    blendType: OVERRIDE\n"
            + "    weight: 1.0\n"
            + "    animations: [idle]\n"
            + "  叠加层:\n"
            + "    blendType: ADDING\n"
            + "    weight: 0.6\n"
            + "    animations: [aura]\n"
            + "\n"
            + "Trigger:\n"
            + "  施法: aura\n";

    private static final String ANIM_EXAMPLE = "{\n"
            + "  \"format_version\": \"1.8.0\",\n"
            + "  \"animations\": {\n"
            + "    \"animation.model.idle\": {\n"
            + "      \"loop\": true,\n"
            + "      \"animation_length\": 2.0,\n"
            + "      \"bones\": {\n"
            + "        \"body\": {\n"
            + "          \"rotation\": { \"0.0\": [0, 0, 0], \"1.0\": [2.5, 0, 0], \"2.0\": [0, 0, 0] },\n"
            + "          \"position\": { \"0.0\": [0, 0, 0], \"1.0\": [0, 0.4, 0], \"2.0\": [0, 0, 0] }\n"
            + "        },\n"
            + "        \"head\": {\n"
            + "          \"rotation\": { \"0.0\": [0, 0, 0], \"2.0\": [0, 0, 3] },\n"
            + "          \"easing\": \"easeInOutSine\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
}
