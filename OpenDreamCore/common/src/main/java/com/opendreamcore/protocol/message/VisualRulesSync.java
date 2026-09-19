package com.opendreamcore.protocol.message;

import com.opendreamcore.util.J8;

import com.opendreamcore.protocol.OdcByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉规则同步（S→C）。
 *
 * 服务端把九类视觉系统（ItemIcon/ItemEffect/HeadTag/FontConfig/ArmorLayer/
 * KeyConfig/Sounds/WorldTexture/SlotConfig）的规则 YAML 原文打包下发——
 * 与 PageSync 传页面源文同一个思路：服务端只管"谁生效"，客户端自行解析渲染。
 * 贴图/音效等资产本体不走这条消息（资源云负责），所以这里传的都是小文本。
 */
public final class VisualRulesSync implements Message {

    /** 一条规则：所属系统 + 规则 id + 规则 YAML 全文。system 用固定英文标识。 */
    public record Entry(String system, String id, String yaml) {
    }

    private final List<Entry> entries;
    private final String versionHash;

    public VisualRulesSync(List<Entry> entries, String versionHash) {
        this.entries = entries == null ? J8.list() : J8.listCopy(entries);
        this.versionHash = versionHash == null ? "" : versionHash;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** 规则集版本哈希（服务端全量内容的 SHA-256 截断；用于调试对齐）。 */
    public String versionHash() {
        return versionHash;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeString(e.system());
            buf.writeString(e.id());
            byte[] yaml = e.yaml().getBytes(StandardCharsets.UTF_8);
            buf.writeVarInt(yaml.length);
            buf.writeBytes(yaml);
        }
        buf.writeString(versionHash);
    }

    public static VisualRulesSync decode(OdcByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IllegalStateException("视觉规则数量非法: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String system = buf.readString();
            String id = buf.readString();
            int len = buf.readVarInt();
            if (len < 0 || len > 1 << 20) {
                throw new IllegalStateException("规则内容长度非法: " + len);
            }
            byte[] yaml = buf.readBytes(len);
            entries.add(new Entry(system, id, new String(yaml, StandardCharsets.UTF_8)));
        }
        String hash = buf.readString();
        return new VisualRulesSync(entries, hash);
    }

    /** 便捷构造：从"系统 → (规则id → YAML)"的装载结果打包。 */
    public static VisualRulesSync fromBundles(Map<String, Map<String, String>> bundles, String versionHash) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> sys : bundles.entrySet()) {
            for (Map.Entry<String, String> r : sys.getValue().entrySet()) {
                entries.add(new Entry(sys.getKey(), r.getKey(), r.getValue()));
            }
        }
        return new VisualRulesSync(entries, versionHash);
    }

    /** 还原为按系统分组的装载视图（客户端入库用）。 */
    public Map<String, Map<String, String>> toBundles() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            out.computeIfAbsent(e.system(), k -> new LinkedHashMap<>()).put(e.id(), e.yaml());
        }
        return out;
    }
}
