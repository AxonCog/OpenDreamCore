package com.opendreamcore.client.spi;

import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天栏通知 SPI：client-legacy 版本无关代码要跟玩家说话时走这。
 * 各版本壳注册自家实现（1.12.2 走 thePlayer.sendMessage，1.16.5 走
 * displayClientMessage……），拿不到玩家就静默丢弃。
 */
public interface ChatNotifier {

    /** 直接发一行（可带 § 颜色码）。没进世界就当没说。 */
    void say(String text);

    /** 常量字段放不进接口，塞嵌套类。 */
    final class Host {
        private static volatile ChatNotifier instance;
        /** 去重表：key 相同的提醒只发一次，塞满重置——宁可多提醒也不永久哑巴。 */
        private static final Set<String> WARNED = Collections
                .newSetFromMap(new ConcurrentHashMap<>());

        private Host() {
        }

        /** 平台壳开局调一次。 */
        public static void register(ChatNotifier notifier) {
            instance = notifier;
        }

        /** 当前实现，没注册返回 null。 */
        public static ChatNotifier current() {
            return instance;
        }

        /** 带去重的提醒：key 相同只发第一次。 */
        public static void warnOnce(String key, String text) {
            ChatNotifier n = instance;
            if (n == null) {
                return;
            }
            if (WARNED.size() > 256) {
                WARNED.clear();
            }
            if (WARNED.add(key)) {
                try {
                    n.say(text);
                } catch (Throwable ignored) {
                    // 提醒失败不能拖垮主流程
                }
            }
        }
    }
}
