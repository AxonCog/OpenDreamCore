package com.opendreamcore.model.bake;

/**
 * 模型资产寻址的静态碎片：扩展名集合、常见子目录表、引用名归一。
 * 从 ModelAssetStore 拆出来——纯函数，不碰任何实例状态。
 */
final class AssetRefs {

    private AssetRefs() {
    }

    static boolean isModelEntry(String lower) {
        for (String ext : MODEL_EXT) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }


    static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1 << 14);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }


    static final String[] MODEL_EXT = {".bbmodel", ".geo.json", ".obj",
            ".smd", ".bmd", ".ysm"};


    /** 常见子目录（限定集合，不是扫描）：名字写 models/x 或 x 都能落到同一个文件。 */
    static final String[] SUB_DIRS = {
            "", "models/", "model/", "mobs/", "entity/", "blocks/", "items/",
            "assets/", "assets/model/", "assets/models/",
            "assets/opendreamcore/models/", "assets/opendreamcore/model/",
            "opendreamcore/models/", "opendreamcore/model/",
    };


    static String normalizeRef(String ref) {
        String r = ref.trim().replace('\\', '/');
        while (r.startsWith("./")) {
            r = r.substring(2);
        }
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        return r;
    }


    static boolean hasKnownExt(String ref) {
        String lower = ref.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".bbmodel") || lower.endsWith(".geo.json") || lower.endsWith(".obj")
                || lower.endsWith(".smd") || lower.endsWith(".bmd") || lower.endsWith(".ysm")
                || lower.endsWith(".png");
    }

}
