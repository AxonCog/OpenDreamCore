package com.opendreamcore.model.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 格式编解码注册表：内置 codec（classpath ServiceLoader）+ addons 扩展（P5 接 AddonRegistry）。
 *
 * 解析入口 {@link #decode(String, String)}：按扩展名选 codec，无匹配时按 sniff 兜底；
 * 失败抛 IllegalArgumentException（上层缺省即安全：跳过该文件并日志）。
 */
import com.opendreamcore.model.format.bbmodel.BbModelCodec;
import com.opendreamcore.model.format.geo.GeoJsonCodec;
import com.opendreamcore.model.format.obj.ObjCodec;

public final class FormatCodecs {

    private static volatile Map<String, IModelFormatCodec> byExtension = null;
    private static volatile List<IModelFormatCodec> all = null;

    private FormatCodecs() {
    }

    /** 全部已注册 codec（内置 + 动态）。 */
    public static List<IModelFormatCodec> all() {
        ensure();
        return all;
    }

    /** 按扩展名（小写含点）查找。 */
    public static IModelFormatCodec byExtension(String ext) {
        ensure();
        return byExtension.get(ext.toLowerCase());
    }

    /** 解码：fileName 仅用于扩展名判断。 */
    public static DecodeResult decode(String fileName, String text) {
        String ext = extensionOf(fileName);
        IModelFormatCodec codec = ext == null ? null : byExtension(ext);
        if (codec == null) {
            codec = sniff(text);
        }
        if (codec == null) {
            throw new IllegalArgumentException("未知模型格式: " + fileName);
        }
        return codec.decode(text);
    }

    /**
     * 二进制/混合入口：按扩展名找 codec，能吃字节（BMD/YSM）就吃字节，
     * 否则回文本路（SMD 是文本格式）；无扩展名时二进制嗅探 + 文本嗅探兜底。
     * zip 解包后的条目分发也走这里。
     */
    public static DecodeResult decodeBytes(String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("空模型数据: " + fileName);
        }
        String ext = extensionOf(fileName);
        IModelFormatCodec codec = ext == null ? null : byExtension(ext);
        if (codec instanceof com.opendreamcore.model.format.bmd.BmdCodec) {
            // BMD 帧动画名 = 文件名去扩展名（独立动画文件靠它被规则引用）
            String stem = fileName;
            int dot = stem.lastIndexOf('.');
            if (dot > 0) {
                stem = stem.substring(0, dot);
            }
            ((com.opendreamcore.model.format.bmd.BmdCodec) codec).setAnimName(stem);
        }
        if (codec != null) {
            DecodeResult bin = tryBinary(codec, data);
            if (bin != null) {
                return bin;
            }
            return decode(fileName, utf8(data));
        }
        for (IModelFormatCodec c : all()) {
            if (c.sniffBinary(data)) {
                if (c instanceof com.opendreamcore.model.format.bmd.BmdCodec) {
                    String stem = fileName;
                    int dot = stem.lastIndexOf('.');
                    if (dot > 0) {
                        stem = stem.substring(0, dot);
                    }
                    ((com.opendreamcore.model.format.bmd.BmdCodec) c).setAnimName(stem);
                }
                DecodeResult r = tryBinary(c, data);
                if (r != null) {
                    return r;
                }
            }
        }
        return decode(fileName, utf8(data));
    }

    private static DecodeResult tryBinary(IModelFormatCodec codec, byte[] data) {
        try {
            return codec.decode(data);
        } catch (RuntimeException e) {
            // 二进制路不认就交给文本路/下一个嗅探者
            return null;
        }
    }

    private static String utf8(byte[] data) {
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** addons 热注册（P5：AddonRegistry 调用；重复 id 覆盖旧的）。 */
    public static void register(IModelFormatCodec codec) {
        ensure();
        List<IModelFormatCodec> next = new ArrayList<IModelFormatCodec>(all);
        next.remove(new IdMatch(codec.id()));
        next.add(codec);
        publish(next);
    }

    /** addons 热注销。 */
    public static void unregister(String id) {
        ensure();
        List<IModelFormatCodec> next = new ArrayList<IModelFormatCodec>(all);
        next.remove(new IdMatch(id));
        publish(next);
    }

    private static final class IdMatch implements IModelFormatCodec {
        private final String id;

        IdMatch(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String[] extensions() {
            return new String[0];
        }

        @Override
        public boolean sniff(String text) {
            return false;
        }

        @Override
        public DecodeResult decode(String text) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IModelFormatCodec && ((IModelFormatCodec) o).id().equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    // ---------------------------------------------------------------- 内部

    private static IModelFormatCodec sniff(String text) {
        for (IModelFormatCodec c : all()) {
            if (c.sniff(text)) {
                return c;
            }
        }
        return null;
    }

    private static synchronized void ensure() {
        if (all != null) {
            return;
        }
        List<IModelFormatCodec> list = new ArrayList<IModelFormatCodec>();
        // 内置：显式登记（ServiceLoader 用于 addons jar）
        list.add(new BbModelCodec());
        list.add(new GeoJsonCodec());
        list.add(new ObjCodec());
        list.add(new com.opendreamcore.model.format.ysm.YsmCodec());
        list.add(new com.opendreamcore.model.format.smd.SmdCodec());
        list.add(new com.opendreamcore.model.format.bmd.BmdCodec());
        for (IModelFormatCodec c : ServiceLoader.load(IModelFormatCodec.class, FormatCodecs.class.getClassLoader())) {
            list.add(c);
        }
        publish(list);
    }

    private static void publish(List<IModelFormatCodec> list) {
        List<IModelFormatCodec> immutable = Collections.unmodifiableList(list);
        Map<String, IModelFormatCodec> byExt = new LinkedHashMap<String, IModelFormatCodec>();
        for (IModelFormatCodec c : immutable) {
            for (String ext : c.extensions()) {
                byExt.put(ext.toLowerCase(), c);
            }
        }
        all = immutable;
        byExtension = byExt;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase();
        // 特例：.geo.json / .geometry.json 双段扩展
        if (lower.endsWith(".geo.json")) {
            return ".geo.json";
        }
        if (lower.endsWith(".geometry.json")) {
            return ".geometry.json";
        }
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? null : lower.substring(dot);
    }
}
