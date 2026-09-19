package com.opendreamcore.client.spi;

import java.util.Map;

/**
 * 实体渲染口子：世界页的 entity 元素真身从这走。
 *
 * entity 元素的完整格式（和高版本对齐）：
 *   entity: {type: "minecraft:villager", nbt: "...", bob: true,
 *            bobAmplitude: 0.05, bobSpeed: 1.0,
 *            orthographic: true, lookAtPlayer: true,
 *            name: "显示名", nameVisible: true, glowing: true}
 * 定位/缩放照旧在 hologram 段（x/y/z/scale/yaw）。
 *
 * 版本壳负责把临时实体建出来画上去（不进世界，不走 AI），
 * 共享层只管把元素规格解析成干净的参数包。
 */
public interface EntityPainter {

    /**
     * 画一个临时实体。
     *
     * typeId：实体 ID，如 "minecraft:villager"
     * nbt：实体 NBT 快照（JSON 串），可空；创建时应用一次，
     *                  村民职业/盔甲架姿势这些自定义数据全靠它
     * px：锚点屏幕 X（脚底中点，逻辑像素）
     * py：锚点屏幕 Y（脚底中点，逻辑像素）
     * scale：缩放（1.0 = 原生大小，已乘好像素换算）
     * yaw：水平朝向（度）
     * orthographic：true = 完全正对镜头（贴片式展示，无侧脸）
     * lookAtPlayer：true = 视线追踪玩家眼睛（orthographic 关着才生效）
     * name：自定义名牌文本，null/空 = 不设
     * nameVisible：名牌是否常显
     * glowing：发光轮廓
     * 返回：true = 这版画上了；false = 类型不存在之类，调用方可以兜底
     */
    boolean render(String typeId, String nbt, double px, double py, double scale, double yaw,
                   boolean orthographic, boolean lookAtPlayer,
                   String name, boolean nameVisible, boolean glowing);

    /**
     * 世界画布相位：同一套画笔落进世界 billboard 画布里画。
     * px/py 还是画布像素系（脚底中点），但原点已是面板锚点，
     * z 抬升不能拿屏幕那 50px 硬套——世界画布 1px = 0.025 格，
     * 50px 就是 1.25 格，实体直接飘到面板前方老远。
     * 默认转调屏幕版：画笔没单独适配时至少不空窗。
     */
    default boolean renderWorld(String typeId, String nbt, double px, double py, double scale, double yaw,
                                boolean orthographic, boolean lookAtPlayer,
                                String name, boolean nameVisible, boolean glowing) {
        return render(typeId, nbt, px, py, scale, yaw, orthographic, lookAtPlayer,
                name, nameVisible, glowing);
    }

    /** 注册入口：版本壳启动时挂上来，全局唯一。 */
    final class Host {
        /** 空实现：没注册时静默跳过，渲染链不炸。 */
        private static final EntityPainter EMPTY = new EntityPainter() {
            @Override
            public boolean render(String typeId, String nbt, double px, double py, double scale, double yaw,
                                  boolean orthographic, boolean lookAtPlayer,
                                  String name, boolean nameVisible, boolean glowing) {
                return false;
            }
        };

        private static volatile EntityPainter current = EMPTY;
        private static volatile EntityPainter fallback = EMPTY;

        public static void register(EntityPainter painter) {
            if (painter != null) {
                current = painter;
            }
        }

        /** 兜底画笔（占位框那套），真身画不上时顶上。 */
        public static void registerFallback(EntityPainter painter) {
            if (painter != null) {
                fallback = painter;
            }
        }

        public static EntityPainter current() {
            return current;
        }

        public static EntityPainter fallback() {
            return fallback;
        }

    }

    /** 元素规格 → 渲染参数，一眼对得上高版本的 entity 段字段。 */
    final class Spec {
        public final String typeId;
        public final String nbt;
        public final double scale;
        public final double yaw;
        public final double bobOff;
        public final boolean orthographic;
        public final boolean lookAtPlayer;
        public final String name;
        public final boolean nameVisible;
        public final boolean glowing;

        public Spec(String typeId, String nbt, double scale, double yaw,
                    double bobOff, boolean orthographic, boolean lookAtPlayer,
                    String name, boolean nameVisible, boolean glowing) {
            this.typeId = typeId;
            this.nbt = nbt;
            this.scale = scale;
            this.yaw = yaw;
            this.bobOff = bobOff;
            this.orthographic = orthographic;
            this.lookAtPlayer = lookAtPlayer;
            this.name = name;
            this.nameVisible = nameVisible;
            this.glowing = glowing;
        }

        /** 从元素 props 里解析（entity 段 + hologram 段各取各的）。 */
        public static Spec of(Map<String, Object> props, Map<String, Object> holo) {
            Map<String, Object> entity = mapOf(props.get("entity"));
            String typeId = str(entity.get("type"));
            String nbt = str(entity.get("nbt"));
            double scale = num(holo.get("scale"), 1.0);
            double yaw = num(holo.get("yaw"), 0);

            boolean bob = bool(entity.get("bob"));
            double bobAmp = num(entity.get("bobAmplitude"), 0.05);
            double bobSpeed = num(entity.get("bobSpeed"), 1.0);
            double bobOff = bob ? Math.sin(System.currentTimeMillis() / 1000.0 * bobSpeed) * bobAmp : 0;

            String rawName = str(entity.get("name"));
            return new Spec(typeId, nbt, scale, yaw, bobOff,
                    bool(entity.get("orthographic")),
                    bool(entity.get("lookAtPlayer")),
                    rawName.isEmpty() ? null : rawName,
                    bool(entity.get("nameVisible")),
                    bool(entity.get("glowing")));
        }
        // 轻量取值：不引 render 包（那边也引这边的 SPI，来回绕会循环依赖）
        private static Map<String, Object> mapOf(Object v) {
            if (v instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) v;
                Map<String, Object> out = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> en : raw.entrySet()) {
                    out.put(String.valueOf(en.getKey()), en.getValue());
                }
                return out;
            }
            return java.util.Collections.emptyMap();
        }

        private static String str(Object v) {
            return v == null ? "" : String.valueOf(v);
        }

        private static double num(Object v, double fallback) {
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v != null) {
                try {
                    return Double.parseDouble(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }

        private static boolean bool(Object v) {
            if (v == null) {
                return false;
            }
            String s = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
            return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
        }
    }
}