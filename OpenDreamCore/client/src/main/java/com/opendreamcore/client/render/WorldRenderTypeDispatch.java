package com.opendreamcore.client.render;

import com.opendreamcore.client.CompatRender;

import java.lang.reflect.Method;

/**
 * 世界语义渲染类型的共享侧解算与取用（纯反射，不含任何版本的编译期类型）。
 *
 * <p>为什么是反射：共享层的这份源码会被复制进全部 12 个现代目标的编译单元，必须能同时编过
 * 1.20.1 与 26.1.2。渲染类型与管线只从 1.21.6 之后才存在，直接写类型名会让老目标整片编不过，
 * 所以这里一律按「类名 + 方法形状」解析，解析不到就返回 null 让调用方回退。
 */
public final class WorldRenderTypeDispatch {

    /** 世界几何的贴图名（面板底、描边等纯色几何都用它，UV 恒为 0 → 取白色像素）。 */
    private static final String WHITE_TEXTURE_NAME = "opendreamcore:white";

    /** 世界绘制窗口标记：窗口内的顶点批次都视为世界几何，可挂世界语义类型。 */
    private static final ThreadLocal<Boolean> IN_WORLD_DRAW = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 最近一次绑到采样器 0 的贴图。
     *
     * <p>世界几何分两类：贴图几何（面板底图、图标）与纯色几何（描边、分隔线）。渲染类型一旦
     * 建好就把采样器钉死在某个贴图上，而调用方是在建批次之前才调 setShaderTexture 换图的。
     * 所以这里把「刚绑的那张」记下来，建类型时取它——否则所有面板都会拿同一张占位白图去画，
     * 表现是底图全白、图标消失。
     */
    private static volatile Object boundTexture;

    private static Class<?> whiteTextureClass;
    private static Object whiteTexture;
    private static boolean whiteTextureProbed;


    private static Method drawMethod;
    private static Class<?> drawMethodOwner;
    private static boolean drawProbed;

    private WorldRenderTypeDispatch() {
    }

    // ── 世界绘制窗口 ─────────────────────────────────────────────────────────

    /** 进入世界绘制窗口：此后到退出为止建立的顶点批次都按世界几何处理。 */
    public static void enterWorldDraw() {
        IN_WORLD_DRAW.set(Boolean.TRUE);
    }

    /** 退出世界绘制窗口（异常路径也必须调用，否则标记会泄漏到后续 UI 绘制）。 */
    public static void exitWorldDraw() {
        IN_WORLD_DRAW.set(Boolean.FALSE);
        // 贴图记账同样要清：它是「本批次刚绑的那张」，跨出窗口就没有意义，
        // 留着会让下一帧第一块没有显式绑图的几何意外套用上一帧的贴图。
        clearBoundTexture();
    }

    /** 当前是否在世界绘制窗口内。 */
    public static boolean inWorldDraw() {
        return Boolean.TRUE.equals(IN_WORLD_DRAW.get());
    }

    /** 记录调用方刚绑到采样器 0 的贴图（由状态绑定层在成功绑定后回调）。 */
    public static void noteBoundTexture(Object texture) {
        boundTexture = texture;
    }

    /** 清掉记账（退出世界绘制窗口时调用，避免把世界贴图泄漏给后续 UI 绘制）。 */
    public static void clearBoundTexture() {
        boundTexture = null;
    }

    /** 当前应绑给世界几何的贴图：优先用调用方刚绑的那张，没记到才退回占位白图。 */
    private static Object geometryTexture() {
        Object bound = boundTexture;
        return bound != null ? bound : whiteTexture();
    }

    // ── 基础取用 ─────────────────────────────────────────────────────────────

    /** 白色贴图（世界几何用；未解析到时返回 null，调用方应跳过贴图几何而不是拿 null 去画）。 */
    public static Object whiteTexture() {
        if (!whiteTextureProbed) {
            synchronized (WorldRenderTypeDispatch.class) {
                if (!whiteTextureProbed) {
                    whiteTexture = probeWhiteTexture();
                    whiteTextureProbed = true;
                }
            }
        }
        return whiteTexture;
    }

    /**
     * 解算世界几何该用的渲染类型。
     *
     * <p>只有一条来源：自建实现（开关打开且已注册）。其余情况一律返回 null，由调用方走原有
     * 上传路径。这里刻意不保留「退回某个原版类型」的兜底——世界几何的顶点格式是
     * POSITION_TEX / POSITION_COLOR，而一眼能找到的原版候选（盔甲半透明、GUI 类）都按别的
     * 顶点格式建管线，硬套的结果不是画不出来，就是画成一次非法调用。
     */
    public static Object resolve(boolean textured, boolean seeThrough, Object mode) {
        // 穿透趟（depthMode=always/transparent 的后半）不走渲染类型：渲染管线的构建器只能设
        // 「写不写深度」「剔不剔面」「混合方式」，不提供「深度测试函数」这一项——而那趟要的正是
        // 把深度测试整个关掉。硬套渲染类型会让穿透失效（面板重新被方块挡住），所以这趟继续走
        // 原有绘制路径，在那里手动关深度测试。常规趟（要测深度、不写深度）才是渲染类型的正题。
        WorldRenderTypeProvider custom = customProvider();
        if (custom != null) {
            Object texture = textured ? geometryTexture() : null;
            if (!textured || texture != null) {
                try {
                    Object rt = custom.resolve(textured, seeThrough, mode, texture);
                    if (rt != null) {
                        return rt;
                    }
                } catch (Throwable ignored) {
                    // 自建实现内部出错：静默回退，绝不把渲染异常带进世界绘制
                }
            }
        }
        // 自建实现拿不到类型就到此为止，不再退回原版盔甲半透明类型。
        // 那条类型按 NEW_ENTITY 顶点格式建管线，而世界几何是 POSITION_TEX / POSITION_COLOR，
        // 顶点元素对不上：拿它去画就是一次非法绘制——实测每帧两条 GL_INVALID_ENUM
        // "<mode> is not a valid polygon mode"，几何整片不出现。宁可返回 null 让调用方走
        // 原本就验证过的上传路径，也不拿格式不匹配的类型去试。
        return null;
    }

    /**
     * 用渲染类型把已建好的网格画出去（原版自刷新路径，光影可归类）。
     * 成功返回 true；{@code mesh} 的释放责任始终在调用方。
     */
    public static boolean draw(Object renderType, Object mesh) {
        if (renderType == null || mesh == null) {
            return false;
        }
        Method m = drawMethod(renderType.getClass());
        if (m == null) {
            return false;
        }
        try {
            m.invoke(renderType, mesh);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 自建实现（仅在开关打开且已注册时返回，否则 null → 走原版回退路径）。 */
    private static WorldRenderTypeProvider customProvider() {
        if (!WorldRenderTypes.enabled()) {
            return null;
        }
        return WorldRenderTypes.provider();
    }

    // ── 反射解算 ─────────────────────────────────────────────────────────────

    /** 白色贴图：优先用共享资源加载器已注册的那张；拿不到就自己拼一个。 */
    private static Object probeWhiteTexture() {
        try {
            Class<?> loader = Class.forName("com.opendreamcore.client.resources.LooseResourceLoader");
            Method white = loader.getMethod("whiteTexture");
            Object r = white.invoke(null);
            if (r != null) {
                return r;
            }
        } catch (Throwable ignored) {
            // 没注册（未开客户端 / 尚未加载）→ 自己拼一个
        }
        try {
            Class<?> rlClass = Class.forName(
                    CompatRender.mapClassName("net.minecraft.resources.ResourceLocation"));
            whiteTextureClass = rlClass;
            Method from = CompatRender.resolveMethod(rlClass, "fromNamespaceAndPath", String.class, String.class);
            if (from != null) {
                return from.invoke(null, "opendreamcore", "white");
            }
            for (String mn : new String[]{"of", "parse", "tryParse"}) {
                from = CompatRender.resolveMethod(rlClass, mn, String.class);
                if (from != null) {
                    return from.invoke(null, WHITE_TEXTURE_NAME);
                }
            }
        } catch (Throwable ignored) {
            // 拼不出来：调用方回退
        }
        return null;
    }

    /** 渲染类型的 draw(mesh)：按形状解析一次后缓存（各代方法名相同，参数类型名不同）。 */
    private static Method drawMethod(Class<?> type) {
        if (!drawProbed || drawMethodOwner != type) {
            synchronized (WorldRenderTypeDispatch.class) {
                if (!drawProbed || drawMethodOwner != type) {
                    Method found = null;
                    for (Method m : type.getMethods()) {
                        if (m.getName().equals("draw")
                                && m.getParameterCount() == 1
                                && m.getReturnType() == void.class) {
                            found = m;
                            break;
                        }
                    }
                    drawMethod = found;
                    drawMethodOwner = type;
                    drawProbed = true;
                }
            }
        }
        return drawMethod;
    }
}
