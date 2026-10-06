package com.opendreamcore.core.capability;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 代际特性开关。
 *
 * <p><b>规则（规范 §5.1）</b>：任何「版本判断」只允许出现在本类的 {@link #forVersion} 里；
 * 别处只读 {@code Features.renderPipelines()} 这类方法。这样版本差异的爆炸半径 = 1 个文件。
 *
 * <p>数据来源：各 target 构建时写出的 {@code META-INF/odc-features.idx}（一行 {@code mc=<版本>}）。
 * 各 target 只写「自己是哪个 MC 版本」，版本 → 特性的映射只此一处实现（同一个语义一个数据源）。
 */
public final class Features {

    /** 特性资源路径。 */
    public static final String RESOURCE = "META-INF/odc-features.idx";

    private static final Logger LOGGER = Logger.getLogger(Features.class.getName());

    private static volatile boolean renderPipelines;
    private static volatile boolean identifierClass;
    private static volatile boolean modelStateObjects;
    private static volatile String mcVersion = "";
    private static volatile boolean bootstrapped;

    private Features() {
    }

    /** 读各 target 写的版本行并推导特性；资源缺失 → 全 false + 一条 WARN（共享层单测/无 target 环境）。 */
    public static synchronized void bootstrap(ClassLoader cl) {
        if (bootstrapped) {
            return;
        }
        ClassLoader loader = cl != null ? cl : Features.class.getClassLoader();
        String mc = "";
        InputStream in = null;
        try {
            in = loader.getResourceAsStream(RESOURCE);
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    if (t.startsWith("mc=")) {
                        mc = t.substring(3).trim();
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 读特性资源失败：" + RESOURCE, t);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        }
        if (mc.isEmpty()) {
            LOGGER.warning("[OpenDreamCore][core] 未找到 " + RESOURCE
                    + "，代际特性按全 false 处理（共享层单测或无 target 环境属正常）");
        }
        FeatureSet fs = forVersion(mc);
        mcVersion = mc;
        renderPipelines = fs.pipelines;
        identifierClass = fs.identifier;
        modelStateObjects = fs.stateObjects;
        bootstrapped = true;
        System.out.println("[OpenDreamCore][core] 代际特性：MC=" + (mc.isEmpty() ? "?" : mc)
                + " 渲染管线=" + renderPipelines + " Identifier改名=" + identifierClass
                + " 渲染状态对象化=" + modelStateObjects);
    }

    /** 渲染管线对象化（{@code RenderPipeline}）；1.21.6 起。 */
    public static boolean renderPipelines() {
        return renderPipelines;
    }

    /** {@code ResourceLocation} 已改名 {@code Identifier}；1.21.11 起。 */
    public static boolean identifierClass() {
        return identifierClass;
    }

    /** 模型 / 渲染状态对象化；1.21.8 起。 */
    public static boolean modelStateObjects() {
        return modelStateObjects;
    }

    public static String mcVersion() {
        return mcVersion;
    }

    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    /** 单测/工具用：直接给值。 */
    public static synchronized void initForTests(String mc, boolean pipelines, boolean identifier,
                                                 boolean stateObjects) {
        mcVersion = mc == null ? "" : mc;
        renderPipelines = pipelines;
        identifierClass = identifier;
        modelStateObjects = stateObjects;
        bootstrapped = true;
    }

    /**
     * MC 版本串 → 三个代际特性。**全仓唯一的版本判断落点。**
     *
     * <p>阈值出处：{@code docs/0.2.x规划-新架构设计与扩展规范.md} §5.1 与相机探针实证
     * （1.21.11 起 ResourceLocation→Identifier；1.21.8 起渲染状态对象化；1.21.6 起渲染管线）。
     */
    public static FeatureSet forVersion(String version) {
        int[] v = parseVersion(version);
        return new FeatureSet(
                atLeast(v, 1, 21, 6),
                atLeast(v, 1, 21, 11),
                atLeast(v, 1, 21, 8));
    }

    /** 版本号解析：只取前三段数字（"1.21.11" → [1,21,11]，"26.1.2" → [26,1,2]）。 */
    public static int[] parseVersion(String s) {
        int[] out = new int[] {0, 0, 0};
        if (s == null || s.trim().isEmpty()) {
            return out;
        }
        String[] parts = s.trim().split("[^0-9]+");
        int n = 0;
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (n >= 3) {
                break;
            }
            try {
                out[n++] = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                break;
            }
        }
        return out;
    }

    private static boolean atLeast(int[] v, int a, int b, int c) {
        if (v[0] != a) {
            return v[0] > a;
        }
        if (v[1] != b) {
            return v[1] > b;
        }
        return v[2] >= c;
    }

    /** 一份代际特性取值。 */
    public static final class FeatureSet {

        public final boolean pipelines;
        public final boolean identifier;
        public final boolean stateObjects;

        public FeatureSet(boolean pipelines, boolean identifier, boolean stateObjects) {
            this.pipelines = pipelines;
            this.identifier = identifier;
            this.stateObjects = stateObjects;
        }

        @Override
        public String toString() {
            return "pipelines=" + pipelines + " identifier=" + identifier
                    + " stateObjects=" + stateObjects;
        }
    }
}
