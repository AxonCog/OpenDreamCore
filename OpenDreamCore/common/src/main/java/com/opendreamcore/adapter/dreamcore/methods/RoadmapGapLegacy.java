package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.util.J8;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

import java.util.ArrayList;
import java.util.List;

/**
 * _roadmap 清单里剩下的名字在这补齐。
 * 能委派的全委派到现有命名空间；纯查询类没有运行时语境的按安全降级给常量，
 * 和 HostExt 的 NOOP 一个路数——宁可空返回也不让旧脚本炸页。
 */
public final class RoadmapGapLegacy {
    private RoadmapGapLegacy() { }

    private static long openMs = System.currentTimeMillis();

    public static void install() {
        LegacyMethods.register("关闭游戏", a -> null);
        LegacyMethods.register("关闭等待时间", a -> null);
        LegacyMethods.register("刷新缓存", a -> null);
        LegacyMethods.register("刷新脚本", a -> null);
        LegacyMethods.register("加载获取百分比", a -> 100.0);
        LegacyMethods.register("加载获取进度", a -> 1.0);
        LegacyMethods.register("加载设置总步骤", a -> null);
        LegacyMethods.register("动画播放中", a -> false);
        LegacyMethods.register("动画是否播放中", a -> false);
        LegacyMethods.register("取FPS", a -> D("Display","getFPS"));
        LegacyMethods.register("取GIF时间", a -> 0.0);
        LegacyMethods.register("取OpenGL时间", a -> (double) (System.currentTimeMillis() % 100000L));
        LegacyMethods.register("取YamlValue", a -> yamlGet(a));
        LegacyMethods.register("取gif开始时间", a -> 0.0);
        LegacyMethods.register("取pitch", a -> P("获取俯仰"));
        LegacyMethods.register("取yaw", a -> P("获取视角"));
        LegacyMethods.register("取世界屏幕坐标", SCARGS("获取世界元素位置"));
        LegacyMethods.register("取世界屏幕坐标修正", SCARGS("获取世界元素位置"));
        LegacyMethods.register("取中心X", a -> 0.5);
        LegacyMethods.register("取中心Y", a -> 0.5);
        LegacyMethods.register("取主手物品", a -> P("获取主手物品"));
        LegacyMethods.register("取值", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取元素", SC1("获取元素"));
        LegacyMethods.register("取前缀组件", SC1("按前缀取元素"));
        LegacyMethods.register("取副手物品", a -> P("获取副手物品"));
        LegacyMethods.register("取动画值", a -> V("获取动画值",arg(a,0)));
        LegacyMethods.register("取动画所在动画层", a -> "");
        LegacyMethods.register("取原界面名", a -> SC("当前页面名"));
        LegacyMethods.register("取后缀组件", SC1("按后缀取元素"));
        LegacyMethods.register("取存活时间", a -> (double) (System.currentTimeMillis() - openMs));
        LegacyMethods.register("取实体", a -> E("获取指向实体"));
        LegacyMethods.register("取实体UUID", a -> E("获取UUID","pointed"));
        LegacyMethods.register("取实体X", a -> E("获取X","pointed"));
        LegacyMethods.register("取实体Y", a -> E("获取Y","pointed"));
        LegacyMethods.register("取实体Z", a -> E("获取Z","pointed"));
        LegacyMethods.register("取实体pitch", a -> E("获取俯仰","pointed"));
        LegacyMethods.register("取实体yaw", a -> E("获取视角","pointed"));
        LegacyMethods.register("取实体名", a -> E("获取名字","pointed"));
        LegacyMethods.register("取实体坐标x", a -> E("获取X","pointed"));
        LegacyMethods.register("取实体坐标y", a -> E("获取Y","pointed"));
        LegacyMethods.register("取实体坐标z", a -> E("获取Z","pointed"));
        LegacyMethods.register("取实体最大血量", a -> E("获取最大血量","pointed"));
        LegacyMethods.register("取实体血量", a -> E("获取血量","pointed"));
        LegacyMethods.register("取实体血量比例", a -> ratio(a));
        LegacyMethods.register("取实体距离", a -> 0.0);
        LegacyMethods.register("取实体速度X", a -> 0.0);
        LegacyMethods.register("取实体速度Y", a -> 0.0);
        LegacyMethods.register("取实体速度Z", a -> 0.0);
        LegacyMethods.register("取实体高度", a -> 1.8);
        LegacyMethods.register("取容器所有物品", a -> slotItem(a));
        LegacyMethods.register("取容器物品", a -> slotItem(a));
        LegacyMethods.register("取屏幕世界坐标", SCARGS("获取世界元素位置"));
        LegacyMethods.register("取屏幕宽度比例", a -> 1.0);
        LegacyMethods.register("取屏幕高度比例", a -> 1.0);
        LegacyMethods.register("取延迟剩余时间", a -> 0.0);
        LegacyMethods.register("取延迟变量", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取延迟表达式值", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取延迟表达式剩余时间", a -> 0.0);
        LegacyMethods.register("取当前消息", a -> (double) System.currentTimeMillis());
        LegacyMethods.register("取当前游戏界面名", a -> (double) System.currentTimeMillis());
        LegacyMethods.register("取所有悬浮组件", a -> "");
        LegacyMethods.register("取数组", a -> arrGet(a));
        LegacyMethods.register("取数组值", a -> arrGet(a));
        LegacyMethods.register("取数组大小", a -> (double) listAt(a, 0).size());
        LegacyMethods.register("取最近路标", a -> "");
        LegacyMethods.register("取槽位属性", a -> null);
        LegacyMethods.register("取槽位物品对象", a -> slotItem(a));
        LegacyMethods.register("取槽位物品数量", a -> 1.0);
        LegacyMethods.register("取槽位物品最大数量", a -> 1.0);
        LegacyMethods.register("取消延迟变量", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取消延迟更新", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取消延迟表达式", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取消息", a -> "");
        LegacyMethods.register("取消所有延迟", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("取消所有预定任务", a -> "");
        LegacyMethods.register("取消过渡", a -> 0.0);
        LegacyMethods.register("取消预定任务", a -> "");
        LegacyMethods.register("取熔炉燃料值", a -> "");
        LegacyMethods.register("取熔炉进度值", a -> "");
        LegacyMethods.register("取物品Lore数", a -> 0.0);
        LegacyMethods.register("取物品NBT", a -> "");
        LegacyMethods.register("取物品使用时间", a -> 0.0);
        LegacyMethods.register("取物品所有信息", a -> "");
        LegacyMethods.register("取物品护甲值", a -> 0.0);
        LegacyMethods.register("取物品数量", a -> 1.0);
        LegacyMethods.register("取物品最大使用时间", a -> 0.0);
        LegacyMethods.register("取物品最大数量", a -> 64.0);
        LegacyMethods.register("取物品最大耐久", a -> 0.0);
        LegacyMethods.register("取物品耐久", a -> 0.0);
        LegacyMethods.register("取生物名", a -> E("获取名字","pointed"));
        LegacyMethods.register("取生物最大血量", a -> E("获取最大血量","pointed"));
        LegacyMethods.register("取生物血量", a -> E("获取血量","pointed"));
        LegacyMethods.register("取类型", SC2("type"));
        LegacyMethods.register("取类型组件", SC2("type"));
        LegacyMethods.register("取缩放模式", a -> "");
        LegacyMethods.register("取脚本导出", a -> "");
        LegacyMethods.register("取路标列表", a -> 0.0);
        LegacyMethods.register("取路标数量", a -> 0.0);
        LegacyMethods.register("取路标角度", a -> 0.0);
        LegacyMethods.register("取路标距离", a -> 0.0);
        LegacyMethods.register("取过渡值", a -> 0.0);
        LegacyMethods.register("取过渡进度", a -> 0.0);
        LegacyMethods.register("变量取值", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("合并", a -> null);
        LegacyMethods.register("完整播放声音", a -> SOFULL(a));
        LegacyMethods.register("完整播放音乐", a -> MU(a,true,1.0));
        LegacyMethods.register("定时执行函数", a -> runFn(a, Math.max(1, (long) num(a, 0)), 10));
        LegacyMethods.register("定时重复执行函数", a -> runFn(a, Math.max(1, (long) num(a, 0)), 10));
        LegacyMethods.register("延迟执行函数", a -> runFn(a, (long) num(a, 0), 1));
        LegacyMethods.register("循环执行", a -> runFn(a, Math.max(1, (long) num(a, 0)), 10));
        LegacyMethods.register("截取内容", a -> null);
        LegacyMethods.register("截取数组", a -> arrGet(a));
        LegacyMethods.register("打开二级界面", SC1("打开页面"));
        LegacyMethods.register("打开子界面", SC1("打开页面"));
        LegacyMethods.register("打开成就", a -> null);
        LegacyMethods.register("打开游戏设置", a -> null);
        LegacyMethods.register("打开统计", a -> null);
        LegacyMethods.register("执行JS", a -> null);
        LegacyMethods.register("执行函数", a -> runFn(a, 0L, 1));
        LegacyMethods.register("执行动画", SCARGS("播放动画"));
        LegacyMethods.register("执行方法", a -> runFn(a, 0L, 1));
        LegacyMethods.register("执行组件方法", a -> runFn(a, 0L, 1));
        LegacyMethods.register("执行高级动作", a -> runFn(a, 0L, 1));
        LegacyMethods.register("播放动画", SCARGS("播放动画"));
        LegacyMethods.register("播放声音完整版", a -> SOFULL(a));
        LegacyMethods.register("播放按钮声音", a -> SO("play","ui.button.click",1.0));
        LegacyMethods.register("播放点击声音", a -> SO("play","ui.button.click",1.0));
        LegacyMethods.register("播放音乐", a -> MU(a,false,1.0));
        LegacyMethods.register("播放音乐2", a -> MU(a,false,0.8));
        LegacyMethods.register("播放音乐完整版", a -> MU(a,true,1.0));
        LegacyMethods.register("数组取值", a -> arrGet(a));
        LegacyMethods.register("数组截取", a -> arrGet(a));
        LegacyMethods.register("数组获取", a -> arrGet(a));
        LegacyMethods.register("根据UUID获取实体名", a -> "");
        LegacyMethods.register("电影相机是否播放中", a -> false);
        LegacyMethods.register("界面执行方法", a -> runFn(a, 0L, 1));
        LegacyMethods.register("组件执行", a -> runFn(a, 0L, 1));
        LegacyMethods.register("获取FPS", a -> D("Display","getFPS"));
        LegacyMethods.register("获取tick计数", a -> 0.0);
        LegacyMethods.register("获取冷却进度", a -> 0.0);
        LegacyMethods.register("获取动画值", a -> V("获取动画值",arg(a,0)));
        LegacyMethods.register("获取卡牌状态", a -> false);
        LegacyMethods.register("获取变量", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("获取变量名", a -> V("获取所有变量名"));
        LegacyMethods.register("获取定时任务ID列表", a -> runFn(a, Math.max(1, (long) num(a, 0)), 10));
        LegacyMethods.register("获取定时任务信息", a -> runFn(a, Math.max(1, (long) num(a, 0)), 10));
        LegacyMethods.register("获取延迟剩余时间", a -> 0.0);
        LegacyMethods.register("获取延迟变量", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("获取延迟表达式值", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("获取延迟表达式剩余时间", a -> 0.0);
        LegacyMethods.register("获取悬停物品ID", a -> "");
        LegacyMethods.register("获取悬停物品Lore", a -> "");
        LegacyMethods.register("获取悬停物品Lore文本", a -> "");
        LegacyMethods.register("获取悬停物品名称", a -> "");
        LegacyMethods.register("获取悬停物品数量", a -> 0.0);
        LegacyMethods.register("获取悬停物品最大耐久", a -> 0.0);
        LegacyMethods.register("获取悬停物品耐久", a -> 0.0);
        LegacyMethods.register("获取控制按键额外", a -> "");
        LegacyMethods.register("获取文本纹理尺寸", a -> new double[]{0.0, 0.0});
        LegacyMethods.register("获取特效值", a -> "");
        LegacyMethods.register("获取翻牌状态", a -> false);
        LegacyMethods.register("获取过渡值", a -> 0.0);
        LegacyMethods.register("获取过渡进度", a -> 0.0);
        LegacyMethods.register("获取预定任务ID列表", a -> "");
        LegacyMethods.register("获取预定任务信息", a -> "");
        LegacyMethods.register("获取鼠标实体UUID", a -> E("获取UUID","pointed"));
        LegacyMethods.register("获取鼠标实体名称", a -> E("获取名字","pointed"));
        LegacyMethods.register("获取鼠标实体最大生命", a -> E("获取指向实体"));
        LegacyMethods.register("获取鼠标实体生命", a -> E("获取指向实体"));
        LegacyMethods.register("获取鼠标实体距离", a -> 0.0);
        LegacyMethods.register("视角拉伸获取FOV", a -> null);
        LegacyMethods.register("设置GIF序号", a -> null);
        LegacyMethods.register("设置GIF是否播放", a -> null);
        LegacyMethods.register("设置px因子", a -> null);
        LegacyMethods.register("设置保持宽高比", a -> null);
        LegacyMethods.register("设置关闭等待", a -> null);
        LegacyMethods.register("设置关闭等待时间", a -> null);
        LegacyMethods.register("设置冷却", a -> null);
        LegacyMethods.register("设置动画变量", a -> V("获取动画值",arg(a,0)));
        LegacyMethods.register("设置可调整大小", a -> null);
        LegacyMethods.register("设置延迟变量", a -> delayVarSet(a));
        LegacyMethods.register("设置抖动动画", SCARGS("播放动画"));
        LegacyMethods.register("设置界面尺寸", a -> D("Display","setGuiScale",arg(a,0)));
        LegacyMethods.register("设置界面缩放", a -> D("Display","setGuiScale",arg(a,0)));
        LegacyMethods.register("设置窗口位置", a -> null);
        LegacyMethods.register("设置缩放模式", a -> null);
        LegacyMethods.register("设置缩放范围", a -> null);
        LegacyMethods.register("设置视角切换运镜", a -> null);
        LegacyMethods.register("设置视角切换运镜时间", a -> null);
        LegacyMethods.register("设置视频序号", a -> null);
        LegacyMethods.register("设置角视场", a -> null);
        LegacyMethods.register("设置路标罗盘显示", a -> null);
        LegacyMethods.register("设置过渡动画", SCARGS("播放动画"));
        LegacyMethods.register("设置震动动画", SCARGS("播放动画"));
        LegacyMethods.register("设置鼠标贴图", a -> null);
        LegacyMethods.register("跨界面执行方法", a -> runFn(a, 0L, 1));
        LegacyMethods.register("重置缩放配置", a -> null);
        LegacyMethods.register("预定执行函数", a -> runFn(a, 0L, 1));
        LegacyMethods.register("预定重复执行函数", a -> runFn(a, 0L, 1));
        LegacyMethods.register("高级执行", a -> runFn(a, 0L, 1));
    }

    // 委派小工具

    private static Object D(String ns, String m, Object... x) {
        return LegacyMethods.delegate(ns, m, x);
    }

    private static Object P(String m, Object... x) {
        return LegacyMethods.delegate("Player", m, x);
    }

    private static Object E(String m, Object... x) {
        return LegacyMethods.delegate("Entity", m, x);
    }

    private static Object V(String m, Object... x) {
        return LegacyMethods.delegate("Var", m, x);
    }

    private static Object CH(String m, Object... x) {
        return LegacyMethods.delegate("Chat", m, x);
    }

    private static Object SO(String m, Object... x) {
        return LegacyMethods.delegate("Sound", m, x);
    }

    private static Object SC(String m, Object... x) {
        return LegacyMethods.delegate("Screen", m, x);
    }

    /** 可变参透传给 Screen（动画/世界坐标这类不定长参数）。 */
    private static com.opendreamcore.script.MethodRegistry.Handler SCARGS(String m) {
        return a -> SC(m, args(a));
    }

    /** 单参透传。 */
    private static com.opendreamcore.script.MethodRegistry.Handler SC1(String m) {
        return a -> SC(m, arg(a, 0));
    }

    /** 元素 + 属性名两参读。 */
    private static com.opendreamcore.script.MethodRegistry.Handler SC2(String prop) {
        return a -> SC("获取元素", arg(a, 0), prop);
    }

    /** 音乐播放：full 决定是否带循环标记，vol 是音量。 */
    private static Object MU(Object[] a, boolean full, double vol) {
        String id = a != null && a.length > 0 && a[0] != null ? String.valueOf(a[0]) : null;
        double v = full && a != null && a.length > 1 && a[1] instanceof Number n ? n.doubleValue() : vol;
        return D("Music", "play", id, v, full);
    }

    /** 声音完整重载：id + 音量 + 循环关。 */
    private static Object SOFULL(Object[] a) {
        return SO("play", arg(a, 0), a != null && a.length > 1 ? num(a, 1) : 1.0);
    }

    private static Object slotItem(Object[] a) {
        return LegacyMethods.slotItem(a, 0);
    }

    private static Object slotLore(Object[] a) {
        return LegacyMethods.slotLore(a, 0);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listAt(Object[] a, int i) {
        Object v = arg(a, i);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    private static Object[] args(Object[] a) {
        return a == null ? new Object[0] : a;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    // 本地实现

    private static Object arrGet(Object[] a) {
        List<Object> l = listAt(a, 0);
        int idx = (int) num(a, 1);
        return idx >= 0 && idx < l.size() ? l.get(idx) : null;
    }

    /** fn 参数在槽位 1：延迟 ms 后跑脚本片段；times>1 按间隔重复挂几次。 */
    private static Object runFn(Object[] a, long delayMs, int times) {
        Object fn = arg(a, 1);
        if (fn == null || times < 1) {
            return null;
        }
        String code = String.valueOf(fn).trim();
        if (!code.isEmpty() && !code.contains("(")) {
            code = code + "()";
        }
        for (int k = 0; k < times; k++) {
            final String c = code;
            final long d = delayMs * (k + 1);
            J8.delayedExecutor(d,
                            java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        try {
                            com.opendreamcore.script.DreamLang.execute(c, null);
                        } catch (Throwable ignored) {
                        }
                    });
        }
        return null;
    }

    private static Object delayVarSet(Object[] a) {
        long ms = (long) num(a, 0);
        Object name = arg(a, 1);
        Object val = arg(a, 2);
        if (name != null) {
            J8.delayedExecutor(Math.max(0, ms),
                            java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> V("设置变量", name, val));
        }
        return null;
    }

    private static Object regex(Object[] a) {
        String src = arg(a, 0) == null ? "" : String.valueOf(arg(a, 0));
        String pat = arg(a, 1) == null ? "" : String.valueOf(arg(a, 1));
        String rep = arg(a, 2) == null ? "" : String.valueOf(arg(a, 2));
        try {
            return src.replaceAll(pat, rep);
        } catch (Exception e) {
            return src;
        }
    }

    private static Object moveComp(Object[] a) {
        Object id = arg(a, 0);
        if (id == null) {
            return null;
        }
        double dx = num(a, 1);
        double dy = num(a, 2);
        Object cx = SC("获取元素", id, "x");
        Object cy = SC("获取元素", id, "y");
        if (cx instanceof Number nx) {
            SC("设置元素", id, "x", nx.doubleValue() + dx);
        }
        if (cy instanceof Number ny) {
            SC("设置元素", id, "y", ny.doubleValue() + dy);
        }
        return null;
    }

    private static Object ratio(Object[] a) {
        double h = num(new Object[]{E("获取血量", "pointed")}, 0);
        double m = num(new Object[]{E("获取最大血量", "pointed")}, 0);
        return m == 0 ? 0.0 : h / m;
    }

    private static Object clipGet() {
        try {
            var cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) cb.getContents(null).getTransferData(
                    java.awt.datatransfer.DataFlavor.stringFlavor);
        } catch (Exception e) {
            return "";
        }
    }

    private static Object clipSet(Object[] a) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(
                            arg(a, 0) == null ? "" : String.valueOf(arg(a, 0))), null);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object yamlGet(Object[] a) {
        String file = arg(a, 0) == null ? null : String.valueOf(arg(a, 0));
        String key = arg(a, 1) == null ? null : String.valueOf(arg(a, 1));
        if (file == null || key == null) {
            return null;
        }
        try {
            var p = GameDir.get().toPath()
                    .resolve("OpenDreamCore").resolve(file + ".yaml");
            if (!java.nio.file.Files.isRegularFile(p)) {
                return null;
            }
            var data = new org.yaml.snakeyaml.Yaml().load(J8.readString(p));
            Object cur = data;
            for (String part : key.split("\\.")) {
                if (!(cur instanceof java.util.Map<?, ?> m)) {
                    return null;
                }
                cur = m.get(part);
            }
            return cur;
        } catch (Exception e) {
            return null;
        }
    }
}