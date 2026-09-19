package com.opendreamcore.client.spi;

import java.util.List;

/**
 * 实体枚举口子（给名牌/血条页逐只渲染用）。
 *
 * 名牌页和普通世界页的根本差别：普通页钉在一个锚点上，名牌页要贴着
 * 场景里每一只命中的生物头顶画一份。枚举实体这活儿各版本 API 长得
 * 都不一样（1.12.2 loadedEntityList、1.16.5 level.entitiesForRendering…），
 * 跟 EntityPainter 一样走 SPI：公共导演只认快照，版本实现各自去抓。
 */
public interface EntitySource {

    /**
     * 收集相机周围 maxDist 格内的存活生物快照。
     * 实现注意：尸体（health<=0）和已标记删除的不要给——血条挂在
     * 尸体上会变成「死后永生」，DragonCore 原版也没这个行为。
     */
    List<Snapshot> nearbyLiving(double cx, double cy, double cz, double maxDist);

    /** 一只生物的渲染快照：只留名牌页用得上的几个数，不外泄 MC 类型。 */
    final class Snapshot {
        public double x;
        public double y;
        public double z;
        /** 身高（碰撞箱），名牌锚点 = y + height + 页面偏移。 */
        public double height;
        public double health;
        public double maxHealth;
        /** 显示名（自定义名优先，原版名兜底），自定义名匹配用。 */
        public String name = "";
        /** 类型 id（如 "zombie"/"creeper"），实体类型匹配用。 */
        public String typeId = "";
    }

    /** 当前版本实现的注册口（各 target 启动时装，跟 EntityPainter.Host 同款）。 */
    final class Host {
        private static volatile EntitySource current;
        private static volatile EntitySource fallback = new EntitySource() {
            @Override
            public List<Snapshot> nearbyLiving(double cx, double cy, double cz, double maxDist) {
                return java.util.Collections.emptyList();
            }
        };

        private Host() {
        }

        public static void register(EntitySource src) {
            if (src != null) {
                current = src;
            }
        }

        public static EntitySource current() {
            EntitySource s = current;
            return s != null ? s : fallback;
        }
    }
}
