package com.opendreamcore.client.spi;

/**
 * 服务端让客户端装资源包时的口子。各版本装法不一样，平台壳开局把
 * 自己的实现 register 进来，收到 pack 消息的人拿 current() 去执行。
 */
public interface ResourcePackInjector {
    /**
     * url 是服务端下发的资源包地址。新版能真装，老版本至少把地址
     * 完整告诉玩家。返回有没有做出用户能感知的动作。
     */
    boolean inject(String url);

    /** 没人 register 时的默认行为：什么都不做。 */
    ResourcePackInjector DEFAULT = new ResourcePackInjector() {
        @Override
        public boolean inject(String url) {
            return false;
        }
    };

    /** 存当前实现的地方，平台壳开局 register 一次。 */
    final class Host {
        private static volatile ResourcePackInjector current;

        private Host() { }

        public static void register(ResourcePackInjector injector) {
            if (injector != null) {
                current = injector;
            }
        }

        public static ResourcePackInjector current() {
            ResourcePackInjector c = current;
            return c == null ? DEFAULT : c;
        }
    }
}
