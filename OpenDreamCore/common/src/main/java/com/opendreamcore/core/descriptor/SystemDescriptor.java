package com.opendreamcore.core.descriptor;

/**
 * 系统元信息。**从编译期索引读回来**（不是运行期注解反射）—— 索引由 {@code tools/odcindex}
 * 在构建期从 {@code @OdcSystem} 生成。
 *
 * <p>不可变；由 {@code registry} 包在 bootstrap 时构造。
 */
public final class SystemDescriptor {

    private final String id;
    private final String displayCn;
    private final String displayEn;
    private final String displaySnake;
    private final String implClass;
    private final String schemaClass;
    private final Side side;
    private final Sync sync;
    private final boolean hotReload;

    public SystemDescriptor(String id, String displayCn, String displayEn, String displaySnake,
                            String implClass, String schemaClass, Side side, Sync sync,
                            boolean hotReload) {
        this.id = id == null ? "" : id;
        this.displayCn = displayCn == null ? this.id : displayCn;
        this.displayEn = displayEn == null ? this.id : displayEn;
        this.displaySnake = displaySnake == null ? this.id : displaySnake;
        this.implClass = implClass == null ? "" : implClass;
        this.schemaClass = schemaClass == null ? "" : schemaClass;
        this.side = side == null ? Side.BOTH : side;
        this.sync = sync == null ? Sync.FULL : sync;
        this.hotReload = hotReload;
    }

    public String id() {
        return id;
    }

    public String displayCn() {
        return displayCn;
    }

    public String displayEn() {
        return displayEn;
    }

    public String displaySnake() {
        return displaySnake;
    }

    public String implClass() {
        return implClass;
    }

    public String schemaClass() {
        return schemaClass;
    }

    public Side side() {
        return side;
    }

    public Sync sync() {
        return sync;
    }

    public boolean hotReload() {
        return hotReload;
    }

    /**
     * 数据文件名：id 首字母大写 + {@code .yml}（camera → Camera.yml），落在
     * {@code <dataRoot>/visual/} 下。与 0.1.x 的既有文件名（Font.yml 等）保持一致。
     */
    public String yamlFileName() {
        if (id.isEmpty()) {
            return "System.yml";
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1) + ".yml";
    }

    /** 三种脚本命名空间名（去重前的原始三个）。 */
    public String[] namespaces() {
        return new String[] {displayCn, displayEn, displaySnake};
    }

    @Override
    public String toString() {
        return "SystemDescriptor{" + id + " side=" + side + " sync=" + sync
                + " hotReload=" + hotReload + " impl=" + implClass + "}";
    }
}
