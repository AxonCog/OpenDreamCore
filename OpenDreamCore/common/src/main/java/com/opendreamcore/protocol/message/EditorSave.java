package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 编辑器保存（C→S）：编辑者把页面 YAML 写回服务端。
 * 服务端校验租约后落盘并热重载。
 */
public final class EditorSave implements Message {

    private final String pageId;
    private final String yaml;

    public EditorSave(String pageId, String yaml) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("页面内容为空: " + pageId);
        }
        this.pageId = pageId;
        this.yaml = yaml;
    }

    public String pageId() {
        return pageId;
    }

    public String yaml() {
        return yaml;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeString(yaml);
    }

    public static EditorSave decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        String yaml = buf.readString();
        return new EditorSave(pageId, yaml);
    }
}
