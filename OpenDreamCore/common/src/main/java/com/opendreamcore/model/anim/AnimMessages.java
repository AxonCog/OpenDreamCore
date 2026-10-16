package com.opendreamcore.model.anim;

/**
 * 动画触发消息的载荷编解码（服务端构造、客户端解析，两侧共用这一份格式）。
 *
 * <p>通道名固定 odc:anim；载荷是竖线分隔的平文本：
 * <pre>
 *   t|&lt;实体 id，-1=发消息的玩家自己&gt;|&lt;触发名&gt;        —— 点火控制器里配的触发
 *   p|&lt;实体 id&gt;|&lt;动画名&gt;[|&lt;层名&gt;]                      —— 直接播一段
 *   s|&lt;实体 id&gt;                                        —— 停掉该实体的触发面
 *   f|&lt;相对路径&gt;|&lt;base64 文件内容&gt;                     —— 服务端下发动画文件（进服 ready 时逐个发）
 * </pre>
 * 解析对坏输入不炸：字段不够、类型不对一律返回 null，调用方安静丢包。
 */
public final class AnimMessages {

    public static final String CHANNEL = "odc:anim";

    private AnimMessages() {
    }

    /** 触发：点火控制器里配的 triggers 条目。 */
    public static String trigger(long entityId, String triggerName) {
        return "t|" + entityId + "|" + safe(triggerName);
    }

    /** 直接播：指定实体播一段动画（可选挂层）。 */
    public static String play(long entityId, String animName, String layer) {
        return "p|" + entityId + "|" + safe(animName) + (layer == null || layer.isEmpty()
                ? "" : "|" + safe(layer));
    }

    /** 停触发面。 */
    public static String stop(long entityId) {
        return "s|" + entityId;
    }

    /**
     * 文件帧上限：分片通道（sendRaw 超 30000B 自动切 CHUNK 流）接管后，
     * 这里只防呆超大文件（动画文件典型 5~40KB，bbmodel 内嵌动画可到几 MB）。
     */
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;

    public static String file(String relName, byte[] data) {
        return "f|" + safe(relName) + "|" + java.util.Base64.getEncoder().encodeToString(data);
    }

    /** 解析；不认识/字段残缺返回 null。 */
    public static Msg parse(String payload) {
        if (payload == null) {
            return null;
        }
        // 文件帧不带实体 id，前缀特判（不能进下面的 Long.parseLong）
        if (payload.startsWith("f|")) {
            String[] fp = payload.split("\\|", 3);
            if (fp.length < 3 || fp[1].isEmpty()) {
                return null;
            }
            try {
                return Msg.file(fp[1], java.util.Base64.getDecoder().decode(fp[2]));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 2) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        switch (parts[0]) {
            case "t":
                return parts.length >= 3 ? Msg.trigger(id, parts[2]) : null;
            case "p":
                return parts.length >= 3 ? Msg.play(id, parts[2],
                        parts.length >= 4 ? parts[3] : null) : null;
            case "s":
                return Msg.stop(id);
            default:
                return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("|", "/");
    }

    /** 解析结果：kind + 实体 id + 名字（+可选层 / 文件帧带 relName+data）。 */
    public static final class Msg {
        public final String kind;   // t / p / s / f
        public final long entityId;
        public final String name;   // 触发名或动画名
        public final String layer;  // 仅 play 可带
        public final String relName; // 仅文件帧：相对路径
        public final byte[] data;   // 仅文件帧：文件内容

        private Msg(String kind, long entityId, String name, String layer,
                    String relName, byte[] data) {
            this.kind = kind;
            this.entityId = entityId;
            this.name = name;
            this.layer = layer;
            this.relName = relName;
            this.data = data;
        }

        static Msg trigger(long entityId, String name) {
            return new Msg("t", entityId, name, null, null, null);
        }

        static Msg play(long entityId, String name, String layer) {
            return new Msg("p", entityId, name, layer, null, null);
        }

        static Msg stop(long entityId) {
            return new Msg("s", entityId, null, null, null, null);
        }

        static Msg file(String relName, byte[] data) {
            return new Msg("f", -1, null, null, relName, data);
        }
    }
}
