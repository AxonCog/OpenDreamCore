package com.opendreamcore.core.registry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 读 {@code META-INF/odc-services.idx} —— **一次 IO、零注解反射、零类路径扫描**。
 *
 * <p>索引由 {@code .github/scripts/gen_index.py} 在构建期从源码注解生成，CI 有新鲜度门禁
 * （{@code --check}），因此运行期看到的一定与源码一致。
 */
public final class ServiceIndex {

    /** 索引资源路径。 */
    public static final String RESOURCE = "META-INF/odc-services.idx";

    private static final Logger LOGGER = Logger.getLogger(ServiceIndex.class.getName());

    private ServiceIndex() {
    }

    /** 读全部条目：多个 jar 里的同名资源会合并，重复 (kind,key) 保留先出现的那条并记 WARN。 */
    public static List<ServiceEntry> load(ClassLoader cl) {
        ClassLoader loader = cl != null ? cl : ServiceIndex.class.getClassLoader();
        List<ServiceEntry> out = new ArrayList<ServiceEntry>();
        Map<String, ServiceEntry> seen = new LinkedHashMap<String, ServiceEntry>();
        try {
            Enumeration<URL> urls = loader.getResources(RESOURCE);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                InputStream in = null;
                try {
                    in = url.openStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    int lineNo = 0;
                    while ((line = r.readLine()) != null) {
                        lineNo++;
                        String t = line.trim();
                        if (t.isEmpty() || t.startsWith("#")) {
                            continue;
                        }
                        ServiceEntry e = ServiceEntry.parseLine(line);
                        if (e == null) {
                            LOGGER.warning("[OpenDreamCore][core] 索引行不合法，已跳过 " + url + " 第 "
                                    + lineNo + " 行：" + t);
                            continue;
                        }
                        String dup = e.kind().name() + "/" + e.key();
                        if (seen.containsKey(dup)) {
                            LOGGER.warning("[OpenDreamCore][core] 索引条目重复，保留先出现的 " + dup);
                            continue;
                        }
                        seen.put(dup, e);
                        out.add(e);
                    }
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable ignored) {
                            // 关不掉不影响结果
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 读索引失败：" + RESOURCE, t);
        }
        return out;
    }

    /** 只取某一类条目。 */
    public static List<ServiceEntry> load(ClassLoader cl, ServiceKind kind) {
        List<ServiceEntry> all = load(cl);
        List<ServiceEntry> out = new ArrayList<ServiceEntry>();
        for (ServiceEntry e : all) {
            if (e.kind() == kind) {
                out.add(e);
            }
        }
        return out;
    }

    /** 只解析文本（单测与生成器自检用，不碰类路径）。 */
    public static List<ServiceEntry> parse(List<String> lines) {
        List<ServiceEntry> out = new ArrayList<ServiceEntry>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            ServiceEntry e = ServiceEntry.parseLine(line);
            if (e != null) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
