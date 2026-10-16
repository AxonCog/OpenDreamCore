package com.opendreamcore.model.bake;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.FormatCodecs;
import java.nio.charset.StandardCharsets;

/**
 * zip 混合包拆包：挑模型条目、png 进贴图缓存（裸名与 zip 前缀双登记）、json 进动画池。
 * 从 ModelAssetStore 拆出来——拆包逻辑自成一体，只有落库走 store。
 */
final class ZipBundleReader {

    static final ThreadLocal<String> PENDING_INNER = new ThreadLocal<String>();

    private ZipBundleReader() {
    }

    /**
     * 解 zip 混合包：挑模型条目（inner 优先，否则首个模型格式条目）；
     * png 条目进贴图缓存（裸名与 zip 前缀双登记），json 条目进动画池。
     */
    static DecodeResult load(ModelAssetStore store, String zipName, byte[] raw, String id) throws Exception {
        String inner = PENDING_INNER.get();
        PENDING_INNER.remove();
        String stem = zipName.endsWith(".zip")
                ? zipName.substring(0, zipName.length() - 4) : zipName;
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(raw));
        java.util.zip.ZipEntry e;
        String modelEntry = null;
        byte[] modelBytes = null;
        java.util.List<com.opendreamcore.model.ir.AnimationIR> zipAnims =
                new java.util.ArrayList<com.opendreamcore.model.ir.AnimationIR>();
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) {
                continue;
            }
            String en = e.getName().replace('\\', '/');
            byte[] data = AssetRefs.readAll(zin);
            String lower = en.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".png")) {
                store.textures.put(en, data);
                store.textures.put(stem + "/" + en, data);
            } else if (lower.endsWith(".json") && !lower.endsWith(".geo.json")) {
                try {
                    zipAnims.addAll(com.opendreamcore.model.format.anim.BedrockAnimCodec.parse(
                            new String(data, StandardCharsets.UTF_8)));
                } catch (Exception ex) {
                    ModelLog.warn("zip 内动画条目解析失败 " + en + ": " + ex);
                }
            } else if (AssetRefs.isModelEntry(lower)
                    && (inner == null || en.endsWith(inner) || en.equalsIgnoreCase(inner))) {
                if (modelEntry == null || (inner != null && en.endsWith(inner))) {
                    modelEntry = en;
                    modelBytes = data;
                }
            }
        }
        zin.close();
        if (!zipAnims.isEmpty()) {
            store.ensureAnimPool();
            for (com.opendreamcore.model.ir.AnimationIR a : zipAnims) {
                store.animPool.put(a.name, a);
            }
        }
        if (modelEntry == null) {
            throw new IllegalArgumentException("zip 包内没有可识别的模型条目: " + zipName);
        }
        ModelLog.info("zip 混合包命中模型条目: " + zipName + " -> " + modelEntry);
        return FormatCodecs.decodeBytes(modelEntry, modelBytes);
    }
}
