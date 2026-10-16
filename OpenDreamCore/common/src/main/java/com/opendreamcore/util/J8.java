package com.opendreamcore.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * JDK8 兜底工具。
 *
 * 共享层需要运行在 JDK8 环境；Jabel 只能转换语法，JDK9+ 新增 API 仍需通过此类提供兼容实现。
 * 这些方法尽量保持对应 JDK API 的语义；集合包装保留插入顺序并允许 null，区别于原版 of 方法对 null 的限制。
 * 延迟执行器在到期后将任务提交给共享定时器。
 */
public final class J8 {

    private J8() {
    }

    /** 延迟任务共用的定时器，守护线程，不拖 JVM 后退。 */
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "odc-j8-timer");
            t.setDaemon(true);
            return t;
        }
    });

    /** 等价 String#isBlank：空串或全是空白符。null 同原版抛 NPE。 */
    public static boolean isBlank(String s) {
        if (s.length() == 0) {
            return true;
        }
        return s.codePoints().allMatch(Character::isWhitespace);
    }

    /** 等价 List.of。 */
    @SafeVarargs
    public static <T> List<T> list(T... v) {
        return Collections.unmodifiableList(new ArrayList<T>(Arrays.asList(v)));
    }

    /** 等价 Set.of，保序用 Linked 版。 */
    @SafeVarargs
    public static <T> Set<T> set(T... v) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(v)));
    }

    /** 等价 Map.of，参数是 k1,v1,k2,v2...。奇数个直接拒绝，不留半张表。 */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> map(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("map() 要成对出现，收到 " + kv.length + " 个参数");
        }
        Map<Object, Object> m = new LinkedHashMap<Object, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            Object prev = m.put(kv[i], kv[i + 1]);
            if (prev != null && !prev.equals(kv[i + 1])) {
                throw new IllegalArgumentException("重复键: " + kv[i]);
            }
        }
        return (Map<K, V>) Collections.unmodifiableMap(m);
    }

    /** 等价 List.copyOf。 */
    public static <T> List<T> listCopy(Collection<? extends T> c) {
        return Collections.unmodifiableList(new ArrayList<T>(c));
    }

    /** 等价 Set.copyOf。 */
    public static <T> Set<T> setCopy(Collection<? extends T> c) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(c));
    }

    /** 等价 Map.copyOf。 */
    public static <K, V> Map<K, V> mapCopy(Map<? extends K, ? extends V> m) {
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(m));
    }

    /** 等价 CompletableFuture.delayedExecutor：到点才执行，不占线程干等。 */
    public static Executor delayedExecutor(long delay, TimeUnit unit) {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                TIMER.schedule(command, delay, unit);
            }
        };
    }

    /** 等价 Files.readString，默认 UTF-8。 */
    public static String readString(Path p) throws IOException {
        return new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 带字符集的读法，喂给要显式编码的调用点。 */
    public static String readString(Path p, Charset cs) throws IOException {
        return new String(Files.readAllBytes(p), cs);
    }

    /** 等价 Files.writeString（UTF-8，建文件+截断，跟原版默认行为一致）。 */
    public static void writeString(Path p, CharSequence text) throws IOException {
        Files.write(p, text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** 显式字符集版。 */
    public static void writeString(Path p, CharSequence text, Charset cs) throws IOException {
        Files.write(p, text.toString().getBytes(cs),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** 等价 String#repeat。 */
    public static String repeat(String s, int times) {
        if (times < 0) {
            throw new IllegalArgumentException("repeat 次数不能是负数: " + times);
        }
        if (times == 0 || s.length() == 0) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            b.append(s);
        }
        return b.toString();
    }

    /** 等价 InputStream#readNBytes：读满 n 或到 EOF 为止。checked 异常剥成 unchecked，调用点不被传染。 */
    public static byte[] readNBytes(InputStream in, int n) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.min(Math.max(n, 1), 8192));
            byte[] chunk = new byte[Math.min(Math.max(n, 1), 8192)];
            int left = n;
            while (left > 0) {
                int got = in.read(chunk, 0, Math.min(left, chunk.length));
                if (got < 0) {
                    break;
                }
                buf.write(chunk, 0, got);
                left -= got;
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 等价 InputStream#readNBytes(byte[],int,int)：往给定数组里灌满 length 字节，返回实读数。 */
    public static int readNBytes(InputStream in, byte[] b, int off, int length) {
        try {
            int done = 0;
            while (done < length) {
                int got = in.read(b, off + done, length - done);
                if (got < 0) {
                    break;
                }
                done += got;
            }
            return done;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
