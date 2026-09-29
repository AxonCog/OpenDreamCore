package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.util.J8;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/**
 * 实体类旧方法：指向实体、生物属性等。
 * 走 Host 扩展（实体查询需要客户端运行时），缺省安全降级。
 */
public final class EntityLegacy {

    private EntityLegacy() {
    }

    public static void install() {
        // 指向/鼠标悬浮的实体
        LegacyMethods.register("取指向实体", a -> h().aimedEntity());
        LegacyMethods.register("取指针实体", a -> h().aimedEntity());
        LegacyMethods.register("取指向生物UUID", a -> h().field("uuid"));
        LegacyMethods.register("取指针生物UUID", a -> h().field("uuid"));
        LegacyMethods.register("取指向生物名", a -> h().field("name"));
        LegacyMethods.register("取指针生物名", a -> h().field("name"));
        LegacyMethods.register("取指向生物血量", a -> h().field("health"));
        LegacyMethods.register("取指针生物血量", a -> h().field("health"));
        LegacyMethods.register("取指向生物最大血量", a -> h().field("maxHealth"));
        LegacyMethods.register("取指针生物最大血量", a -> h().field("maxHealth"));
        LegacyMethods.register("取附近实体", a -> h().nearby(str(a, 0), num(a, 1)));
    }

    // Host 扩展口：实体能力集中在这里，免得 LegacyMethods.Host 越滚越大
    private static volatile HostExt h;

    /** 客户端可选安装实体宿主；未安装时全部返回空值。 */
    public static void installHost(HostExt ext) {
        h = ext;
    }

    private static HostExt h() {
        return h != null ? h : HostExt.NOOP;
    }

    /** 实体运行时能力（client 安装真实实现；NOOP 全空值）。 */
    public interface HostExt {
        HostExt NOOP = new HostExt() {
            @Override public Object aimedEntity() { return null; }
            @Override public String field(String f) { return ""; }
            @Override public Object nearby(String type, double range) { return J8.list(); }
        };

        Object aimedEntity();
        String field(String field);
        Object nearby(String type, double range);

        /**
         * 头顶页面语境字段：当前渲染名牌的实体（HeadTag/Blood 页面表达式里的
         * 方法.取实体血量() 等由此取值，优先于指向实体）。field 取
         * health / maxHealth / name / height / ratio；无语境返回 null。
         */
        default Object headField(String field) { return null; }
    }

    /** 头顶语境字段读取（client 安装实现；未安装或无当前实体返回 null）。 */
    public static Object headField(String field) {
        return h().headField(field);
    }

    private static String str(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }
}
