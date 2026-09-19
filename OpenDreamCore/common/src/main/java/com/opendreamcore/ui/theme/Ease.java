package com.opendreamcore.ui.theme;

/**
 * 缓动曲线：进度 t 进 [0,1]，映射后的进度出。
 * 名字对齐 CSS ease 家族——写过前端的零成本上手。
 */
public enum Ease {

    LINEAR(t -> t),
    SINE_IN_OUT(t -> (float) -(Math.cos(Math.PI * t) - 1) / 2f),
    QUAD_OUT(t -> 1 - (1 - t) * (1 - t)),
    QUAD_IN_OUT(t -> t < 0.5f ? 2 * t * t : 1 - 2 * (1 - t) * (t - 1) + 0),
    CUBIC_OUT(t -> 1 - (float) Math.pow(1 - t, 3)),
    CUBIC_IN_OUT(t -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2f),
    EXPO_OUT(t -> t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t)),
    BACK_OUT(t -> {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }),
    ELASTIC_OUT(t -> {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        final float c4 = (float) (2 * Math.PI) / 3f;
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75) * c4) + 1;
    });

    private final Mapper mapper;

    Ease(Mapper mapper) {
        this.mapper = mapper;
    }

    /** 映射进度。 */
    public float apply(float t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        float v = mapper.map(t);
        // 数值护栏：曲线实现误差不允许越界
        return Math.max(0, Math.min(1, v));
    }

    /** 按名字解析（大小写不敏感），未知回退 LINEAR。 */
    public static Ease byName(String name) {
        if (name != null) {
            for (Ease e : values()) {
                if (e.name().equalsIgnoreCase(name.trim())) {
                    return e;
                }
            }
        }
        return LINEAR;
    }

    @FunctionalInterface
    private interface Mapper {
        float map(float t);
    }
}
