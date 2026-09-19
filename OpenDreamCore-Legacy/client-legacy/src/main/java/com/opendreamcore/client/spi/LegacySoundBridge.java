package com.opendreamcore.client.spi;

/**
 * 远古音效桥：click/hoverSound 自管——交互命中时共享层只管发"播什么",
 * 各版本壳用自家 SoundHandler 落地。soundId 为空串=原版按钮声，
 * 否则传配置里的资源 id（可带命名空间，缺省补 minecraft:）。
 */
public interface LegacySoundBridge {

    /** 播一个音效。id 为 null/空串 = 原版 UI 按钮声。失败静默。 */
    void play(String soundId, double volume, double pitch);

    final class Host {
        private static volatile LegacySoundBridge current;

        private Host() {
        }

        public static void register(LegacySoundBridge bridge) {
            current = bridge;
        }

        public static LegacySoundBridge current() {
            return current;
        }
    }
}