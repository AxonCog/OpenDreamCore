package com.opendreamcore.client.remote;

/**
 * 远程资源预取：规则/页面一下发就立即后台下载所有 http(s) url 资源并注册，
 * 保证玩家渲染时 lookup 直接命中（字符替换/纹理即时生效，不等渲染时才下载）。
 * 完成回调由 GifPlayer/RemoteImageStore 自己处理（CACHE/TEXTURES + 字形缓存清空），
 * 这里只负责"尽早触发 + 去重"。
 */
public final class RemotePrefetch {

    /** 已发起预取的 url（同一会话只发起一次；下载结果落在 GifPlayer/RemoteImageStore 缓存）。 */
    private static final java.util.Set<String> PREFETCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 每个 url 的重试次数（上限 3，防死图床无限重试刷聊天栏）。 */
    private static final java.util.Map<String, Integer> RETRIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 失败自动重试：延时后允许重新预取（图床抖动一次就把功能摁死一整个会话，代价太大）。 */
    public static void retryAfter(String url, long delayMs) {
        if (url == null || url.isBlank()) {
            return;
        }
        int n = RETRIES.merge(url.trim(), 1, Integer::sum);
        if (n > 3) {
            return; // 重试三次还不行，认命：聊天栏已提醒，重连/重启再战
        }
        java.util.concurrent.CompletableFuture
                .delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> {
                    PREFETCHED.remove(url.trim());
                    prefetch(url);
                });
    }

    private RemotePrefetch() {
    }

    /** 预取单个 url（仅 http/https；去重）。 */
    public static void prefetch(String url) {
        if (url == null) {
            return;
        }
        String u = url.trim();
        if (!(u.startsWith("https://") || u.startsWith("http://"))) {
            return;
        }
        if (!PREFETCHED.add(u)) {
            return;
        }
        boolean gif = u.toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
        if (Boolean.getBoolean("odc.debug")) { System.out.println("[ODC-font] 预取: " + (gif ? "gif" : "图片") + " " + u); }
        try {
            if (gif) {
                com.opendreamcore.client.GifPlayer.of(u);
            } else {
                com.opendreamcore.client.RemoteImageStore.get(u);
            }
        } catch (Throwable t) {
            if (Boolean.getBoolean("odc.debug")) { System.out.println("[ODC-font] 预取触发失败: " + u + " 原因=" + t); }
        }
    }

    /** 递归扫描任意对象里的 http(s) 字符串（页面元素 / 规则 body / yaml 解析结果通用）。 */
    public static void scanAndPrefetch(Object o) {
        scan(o);
    }

    private static void scan(Object o) {
        if (o instanceof java.util.Map<?, ?> m) {
            for (Object v : m.values()) {
                scan(v);
            }
        } else if (o instanceof Iterable<?> it) {
            for (Object v : it) {
                scan(v);
            }
        } else if (o instanceof String s) {
            int idx = s.indexOf("https://");
            if (idx < 0) {
                idx = s.indexOf("http://");
            }
            if (idx >= 0) {
                int end = s.length();
                for (int i = idx + 7; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c <= ' ' || c == '"' || c == '\'' || c == ')' || c == ']' || c == '}' || c == ',' || c == '。') {
                        end = i;
                        break;
                    }
                }
                prefetch(s.substring(idx, end));
            }
        }
    }
}