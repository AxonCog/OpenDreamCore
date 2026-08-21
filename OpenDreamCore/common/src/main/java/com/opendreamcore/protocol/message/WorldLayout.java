package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界布局保存（C→S）：WYSIWYG 编辑模式下，客户端把元素的 hologram.x/y/z
 * 以及属性编辑（text.content / text.color / hologram.scale 等点路径键值对）
 * 发回服务端，服务端校验编辑租约后写回页面 YAML（烘焙为页面默认值）。
 */
public final class WorldLayout implements Message {

    /** 条目：元素 id + 绝对坐标（hologram.x/y/z）+ 可选属性编辑（点路径 → 字符串值）。 */
    public record Entry(String elementId, double x, double y, double z, Map<String, String> props) {
        public Entry {
            props = props == null ? Map.of() : Map.copyOf(props);
        }
    }

    private final String pageId;
    private final List<Entry> entries;
    /** 页面级选项编辑（点路径 → 值；"__unset__" = 删除该键；如 world.background.color / world.alpha / world.follow）。 */
    private final Map<String, String> optionsProps;
    /** 页面标题编辑（非空 = 写回 YAML 顶层 title 键；null = 不改）。 */
    private final String pageTitle;
    /** 页面变量编辑（变量名 → 值；"__unset__" = 删除该变量）。 */
    private final Map<String, String> variablesProps;

    public WorldLayout(String pageId, List<Entry> entries) {
        this(pageId, entries, Map.of(), null, Map.of());
    }

    public WorldLayout(String pageId, List<Entry> entries, Map<String, String> optionsProps) {
        this(pageId, entries, optionsProps, null, Map.of());
    }

    public WorldLayout(String pageId, List<Entry> entries, Map<String, String> optionsProps, String pageTitle) {
        this(pageId, entries, optionsProps, pageTitle, Map.of());
    }

    public WorldLayout(String pageId, List<Entry> entries, Map<String, String> optionsProps, String pageTitle,
                       Map<String, String> variablesProps) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        this.pageId = pageId;
        this.entries = entries == null ? new ArrayList<>() : List.copyOf(entries);
        this.optionsProps = optionsProps == null ? Map.of() : Map.copyOf(optionsProps);
        this.pageTitle = pageTitle == null || pageTitle.isBlank() ? null : pageTitle;
        this.variablesProps = variablesProps == null ? Map.of() : Map.copyOf(variablesProps);
    }

    public String pageId() {
        return pageId;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Map<String, String> optionsProps() {
        return optionsProps;
    }

    public String pageTitle() {
        return pageTitle;
    }

    public Map<String, String> variablesProps() {
        return variablesProps;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeString(entry.elementId());
            buf.writeLong(Double.doubleToLongBits(entry.x()));
            buf.writeLong(Double.doubleToLongBits(entry.y()));
            buf.writeLong(Double.doubleToLongBits(entry.z()));
            buf.writeVarInt(entry.props().size());
            for (Map.Entry<String, String> prop : entry.props().entrySet()) {
                buf.writeString(prop.getKey());
                buf.writeString(prop.getValue() == null ? "" : prop.getValue());
            }
        }
        buf.writeVarInt(optionsProps.size());
        for (Map.Entry<String, String> prop : optionsProps.entrySet()) {
            buf.writeString(prop.getKey());
            buf.writeString(prop.getValue() == null ? "" : prop.getValue());
        }
        buf.writeString(pageTitle == null ? "" : pageTitle);
        buf.writeVarInt(variablesProps.size());
        for (Map.Entry<String, String> prop : variablesProps.entrySet()) {
            buf.writeString(prop.getKey());
            buf.writeString(prop.getValue() == null ? "" : prop.getValue());
        }
    }

    public static WorldLayout decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        int count = buf.readVarInt();
        if (count < 0 || count > 100000) {
            throw new IllegalStateException("世界布局条目数非法: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String elementId = buf.readString();
            double x = Double.longBitsToDouble(buf.readLong());
            double y = Double.longBitsToDouble(buf.readLong());
            double z = Double.longBitsToDouble(buf.readLong());
            int propCount = buf.readVarInt();
            if (propCount < 0 || propCount > 1000) {
                throw new IllegalStateException("属性编辑数非法: " + propCount);
            }
            Map<String, String> props = new LinkedHashMap<>();
            for (int j = 0; j < propCount; j++) {
                String key = buf.readString();
                String value = buf.readString();
                props.put(key, value);
            }
            entries.add(new Entry(elementId, x, y, z, props));
        }
        int optCount = buf.readVarInt();
        if (optCount < 0 || optCount > 10000) {
            throw new IllegalStateException("选项编辑数非法: " + optCount);
        }
        Map<String, String> optionsProps = new LinkedHashMap<>();
        for (int i = 0; i < optCount; i++) {
            String key = buf.readString();
            String value = buf.readString();
            optionsProps.put(key, value);
        }
        String pageTitle = buf.readString();
        int varCount = buf.readVarInt();
        if (varCount < 0 || varCount > 10000) {
            throw new IllegalStateException("变量编辑数非法: " + varCount);
        }
        Map<String, String> variablesProps = new LinkedHashMap<>();
        for (int i = 0; i < varCount; i++) {
            String key = buf.readString();
            String value = buf.readString();
            variablesProps.put(key, value);
        }
        return new WorldLayout(pageId, entries, optionsProps, pageTitle.isEmpty() ? null : pageTitle, variablesProps);
    }
}
