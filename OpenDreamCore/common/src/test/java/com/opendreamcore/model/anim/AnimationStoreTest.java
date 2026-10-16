package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Animation 文件夹扫描：yml=控制器、其余=动画、命名=相对路径、双根覆盖。 */
class AnimationStoreTest {

    @TempDir
    Path root;

    private static final String IDLE_JSON = "{\n"
            + " \"animations\": {\n"
            + "  \"animation.model.idle\": {\n"
            + "   \"loop\": true,\n"
            + "   \"animation_length\": 2.0,\n"
            + "   \"bones\": {\n"
            + "    \"body\": {\n"
            + "     \"rotation\": { \"0.0\": [0,0,0], \"1.0\": [2.5,0,0], \"2.0\": [0,0,0] }\n"
            + "    }\n"
            + "   }\n"
            + "  }\n"
            + " }\n"
            + "}";

    private static void put(Path dir, String rel, String content) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void yml就是控制器其余就是动画() throws Exception {
        put(root, "人形.yml", "default: idle\nstates:\n  idle:\n    anim: idle\n");
        put(root, "controller/飞行生物.yml", "default: glide\n"); // controller/ 只是收纳建议，不特判
        put(root, "小火龙/idle.json", IDLE_JSON);
        put(root, "备注.txt", "不是动画也不是控制器，忽略");

        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));

        assertNotNull(s.controllerYaml("人形"));
        assertEquals("default: glide\n", s.controllerYaml("controller/飞行生物"));
        assertNotNull(s.animByName("animation.model.idle"));
        assertNotNull(s.animByName("小火龙/idle")); // 单条动画补文件名别名
        assertNull(s.animByName("小火龙/idle.json"));
        assertTrue(s.animByName("备注") == null);
    }

    @Test
    void 双根后扫覆盖先扫() throws Exception {
        Path r1 = Files.createDirectories(root.resolve("本地"));
        Path r2 = Files.createDirectories(root.resolve("服务端"));
        put(r1, "人形.yml", "default: idle\n");
        put(r2, "人形.yml", "default: idle_server\n");

        AnimationStore s = new AnimationStore();
        s.loadAll(Arrays.asList(r1, r2));

        assertEquals("default: idle_server\n", s.controllerYaml("人形"));
    }

    @Test
    void 服务端下发覆盖本地同名() throws Exception {
        put(root, "cast.yml", "default: idle\n");
        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));
        s.applyRemote("cast.yml", "default: cast_home\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("default: cast_home\n", s.controllerYaml("cast"));
    }

    @Test
    void 重载后缓存清空不残留() throws Exception {
        put(root, "a.json", IDLE_JSON);
        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));
        assertEquals(2, s.anims().size()); // 内部名 + 文件别名
        Files.delete(root.resolve("a.json"));
        s.reload();
        // 用户文件清干净后，库里只剩播种的示例（不再残留用户旧缓存）
        assertTrue(s.anims().size() >= 1);
        for (String k : s.anims().keySet()) {
            assertTrue(k.contains("示例") || k.contains("animation.model.idle"));
        }
    }

    @Test
    void 坏文件只丢自己不拖垮整库() throws Exception {
        put(root, "好.json", IDLE_JSON);
        put(root, "坏.json", "{ 坏掉的 json ~~");
        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));

        assertNotNull(s.animByName("animation.model.idle"));
        assertNull(s.animByName("坏"));
    }

    @Test
    void mergeInto喂给模型侧动画池() throws Exception {
        put(root, "idle.json", IDLE_JSON);
        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));

        java.util.Map<String, AnimationIR> pool = new java.util.LinkedHashMap<String, AnimationIR>();
        s.mergeInto(pool);
        assertTrue(pool.containsKey("小火龙/idle") || pool.containsKey("animation.model.idle")
                || pool.containsKey("idle"));
    }

    @Test
    void 空文件夹播示例且可解析() throws Exception {
        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));

        assertTrue(java.nio.file.Files.exists(root.resolve("controller").resolve("人形.yml")));
        assertTrue(java.nio.file.Files.exists(root.resolve("controller").resolve("龙核写法示例.yml")));
        assertTrue(java.nio.file.Files.exists(root.resolve("示例动画.json")));
        // 播完的示例应立刻可用（重扫过）：示例动画在库、两个控制器都能解析
        assertNotNull(s.animByName("animation.model.idle"));
        assertTrue(s.controllers().containsKey("controller/人形"));
        assertNotNull(s.controllerIR("controller/人形"));
        assertNotNull(s.controllerIR("controller/龙核写法示例"));
    }

    @Test
    void 服主改过的文件不覆盖() throws Exception {
        put(root, "controller/人形.yml", "default: 自定义\nstates:\n  自定义:\n    anim: x\n");
        AnimationStore s = new AnimationStore();
        s.loadAll(Collections.singletonList(root));
        assertEquals("自定义", s.controllerIR("controller/人形").defaultAnim);
    }
}
