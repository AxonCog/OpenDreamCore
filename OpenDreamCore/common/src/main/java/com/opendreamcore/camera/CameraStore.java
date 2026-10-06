package com.opendreamcore.camera;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 相机规则仓库（客户端）。
 *
 * 服务端下发（ClientController.handleVisualRules）与本地重载（reloadAll）共用这一处，
 * 整体覆盖语义。只存 IR 与生成摘要——日志由调用方落（保证进 latest.log），
 * 聊天栏展示走客户端层（OdcCommands / ClientController.cameraList），本类不触 MC。
 */
public final class CameraStore {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore");
    private static final CameraStore INSTANCE = new CameraStore();

    public static CameraStore get() {
        return INSTANCE;
    }

    /** 保留顶层键：运镜设置（M1.4）。它不是规则，因此不进/不列/不解析成规则。 */
    public static final String SETTINGS_KEY = "运镜设置";

    private volatile Map<String, CameraRule> rules = Collections.emptyMap();
    /** 运镜设置（M1.4）：默认「直接驱动开 + 别人占着就让位 + 光影不让位」。 */
    private volatile CameraSettings settings = CameraSettings.defaults();

    CameraStore() {
    }

    /**
     * 全量应用（整体覆盖）。yamlById：规则 id → YAML 原文；null/空不动现有规则。
     * 兼容两种形态：单规则表（服务端 bundle：字段平铺，含 type/position 等）与
     * 多规则同文件（客户端 _local：顶层键即规则 id）。返回摘要日志；无可解析内容返回 null。
     */
    @SuppressWarnings("unchecked")
    public synchronized String apply(Map<String, String> yamlById) {
        if (yamlById == null || yamlById.isEmpty()) {
            return null;
        }
        Map<String, CameraRule> next = new LinkedHashMap<>();
        // 设置与规则同文件但语义不同：本次没写就沿用上次的值（写一半的键也算部分更新）
        CameraSettings nextSettings = settings;
        boolean sawSettings = false;
        int bad = 0;
        for (Map.Entry<String, String> e : yamlById.entrySet()) {
            Map<String, Object> ir;
            try {
                ir = new com.opendreamcore.config.YamlParser().parse(e.getValue());
            } catch (Exception ex) {
                bad++;
                continue;
            }
            if (ir == null || ir.isEmpty()) {
                bad++;
                continue;
            }
            boolean single = ir.containsKey("type") || !allValuesAreMaps(ir);
            if (single) {
                try {
                    next.put(e.getKey(), CameraRule.parse(e.getKey(), ir));
                } catch (Exception ex) {
                    bad++;
                }
            } else {
                for (Map.Entry<String, Object> re : ir.entrySet()) {
                    if (!(re.getValue() instanceof Map)) {
                        continue;
                    }
                    if (SETTINGS_KEY.equals(re.getKey())) {
                        // 保留键：不参与规则解析，也不会出现在 /odc camera list 里
                        nextSettings = CameraSettings.parse((Map<String, Object>) re.getValue());
                        sawSettings = true;
                        continue;
                    }
                    try {
                        next.put(re.getKey(), CameraRule.parse(re.getKey(), (Map<String, Object>) re.getValue()));
                    } catch (Exception ex) {
                        bad++;
                    }
                }
            }
        }
        rules = Collections.unmodifiableMap(next);
        settings = nextSettings;
        if (sawSettings) {
            // 摘要行是 M1.1 契约的一部分（「相机配置已同步：机位 N，路径 M」），不能动，设置另起一行
            LOGGER.info("运镜设置：" + nextSettings.describe());
        }
        String summary = summarize(next, bad);
        LOGGER.info(summary);
        return summary;
    }

    /**
     * 摘要行（M1.1 契约文案：「相机配置已同步：机位 N，路径 M」，改措辞会破坏契约断言）。
     */
    private static String summarize(Map<String, CameraRule> next, int bad) {
        int shots = 0;
        int paths = 0;
        int frees = 0;
        for (CameraRule r : next.values()) {
            switch (r.type) {
                case SHOT -> shots++;
                case PATH -> paths++;
                case FREE -> frees++;
            }
        }
        return "相机配置已同步：机位 " + shots + "，路径 " + paths
                + (frees > 0 ? "，自由 " + frees : "")
                + (bad > 0 ? "（" + bad + " 条解析失败已跳过）" : "");
    }

    /** 多规则文件形态的判定：全部顶层值都是表（单规则表里 position 之类是 List/Number）。 */
    private static boolean allValuesAreMaps(Map<String, Object> ir) {
        boolean any = false;
        for (Object v : ir.values()) {
            any = true;
            if (!(v instanceof Map)) {
                return false;
            }
        }
        return any;
    }

    /** 当前规则（只读视图；key 为规则 id，保持装载顺序）。 */
    public Map<String, CameraRule> rules() {
        return rules;
    }

    public CameraRule byId(String id) {
        return rules.get(id);
    }

    /** 移除单条规则（M1.7 编辑器预览用）：返回是否真的移了。 */
    public synchronized boolean remove(String id) {
        Map<String, CameraRule> next = new LinkedHashMap<>(rules);
        boolean gone = next.remove(id) != null;
        if (gone) {
            rules = Collections.unmodifiableMap(next);
        }
        return gone;
    }

    /** 运镜设置（M1.4）；未配置时是默认值，永不返回 null。 */
    public CameraSettings settings() {
        return settings;
    }
}
