package com.opendreamcore.client.spi;

import java.util.Map;

/**
 * 玩家信息口子：字符替换要用 {player.name}/{player.x} 这类占位符，
 * 但拿玩家坐标/名字的 API 每个版本长得都不一样（1.6.4 是 thePlayer.posX，
 * 1.16.5 是 player.getX()），client 层不可能直接摸。
 * 各 target 启动时把自己的取值器注册进来，渲染层只认这层接口。
 */
public interface PlayerInfoSource {

    /** 玩家名（未进世界时给个占位串，别返回 null）。 */
    String name();

    /** 所在维度 id（1.12.2 是 0/-1/1 数字，各版自己转成可读名）。 */
    String dimension();

    /** 世界坐标 x/y/z。 */
    double x();

    double y();

    double z();

    /** 朝向：yaw/pitch（度）。 */
    double yaw();

    double pitch();

    /** 附加量：health/hunger/ping/tps 等版本差异大的字段走这张表，缺的就不放。 */
    Map<String, Object> extras();

    /** 各 target 注册自己的实现；渲染/替换层取 current() 用。 */
    final class Host {
        private static volatile PlayerInfoSource current = EMPTY;

        public static void register(PlayerInfoSource source) {
            if (source != null) {
                current = source;
            }
        }

        public static PlayerInfoSource current() {
            return current;
        }
    }

    /** 没人注册时的兜底：全是占位值，替换链不炸。 */
    PlayerInfoSource EMPTY = new PlayerInfoSource() {
        @Override
        public String name() {
            return "玩家";
        }

        @Override
        public String dimension() {
            return "overworld";
        }

        @Override
        public double x() {
            return 0;
        }

        @Override
        public double y() {
            return 0;
        }

        @Override
        public double z() {
            return 0;
        }

        @Override
        public double yaw() {
            return 0;
        }

        @Override
        public double pitch() {
            return 0;
        }

        @Override
        public Map<String, Object> extras() {
            return java.util.Collections.emptyMap();
        }
    };
}
