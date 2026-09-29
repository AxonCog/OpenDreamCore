package com.opendreamcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 渲染 API 版本兼容垫片（1.21.1 ↔ 1.21.2+ 渲染重构）。
 *
 * 1.21.2 起 Mojang 移除了 GameRenderer::getPositionColorShader 等 shader getter，
 * GuiGraphics.blit(RL,…) 改为 blit(Function<ResourceLocation,RenderType>,…)。
 * 本类用"编译期只引用跨版本稳定类型 + 反射择路"的方式，让共享源码同时编译所有版本：
 *   setColorShader()：存在 getter 则等价调用；缺失（≥1.21.2）静默跳过
 *     （GuiGraphics.fill 系调用由新管线自动处理，不受影响）
 *   blit(...)：运行时探测一次 GuiGraphics 的 11 参 blit 重载走对应分支，
 *     MethodHandle 缓存后热路径开销可忽略
 */
public final class CompatRender {

    // ≥1.21.6 移除的管线开关：存在则调用，缺失静默跳过（新管线自动处理）
    private static void rsToggle(String name, Class<?>[] types, Object[] args) {
        Method m = resolveMethod(RenderSystem.class, name, types);
        if (m == null) {
            return;
        }
        try {
            m.invoke(null, args);
        } catch (Exception ignored) {
        }
    }
    public static void enableBlend() { rsToggle("enableBlend", new Class<?>[0], new Object[0]); }
    public static void disableBlend() { rsToggle("disableBlend", new Class<?>[0], new Object[0]); }
    /** RenderSystem.defaultBlendFunc() 的版本安全等价（原版即零参：SRC_ALPHA, ONE_MINUS_SRC_ALPHA）。 */
    public static void defaultBlendFunc() { rsToggle("defaultBlendFunc", new Class<?>[0], new Object[0]); }
    public static void enableDepthTest() { rsToggle("enableDepthTest", new Class<?>[0], new Object[0]); }
    public static void disableDepthTest() { rsToggle("disableDepthTest", new Class<?>[0], new Object[0]); }

    /**
     * 当前是否跑在渲染线程（Minecraft.getInstance().isSameThread() 的跨版本等价）。
     *
     * yarn 下该方法名是 isOnThread、mojmap 是 isSameThread——只做精确名探测，
     * 绝不走 resolveMethod 的"形状兜底"（0 参数兜底会误选 Minecraft.close()，
     * 实机曾因反射 invoke close 导致启动即关闭崩溃）。探测不到按渲染线程宽松放行。
     */
    public static boolean isRenderThread() {
        Object mc = Minecraft.getInstance();
        if (mc == null) {
            return true;
        }
        try {
            Method m = mc.getClass().getMethod("isSameThread");
            return Boolean.TRUE.equals(m.invoke(mc));
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        try {
            Method m = mc.getClass().getMethod("isOnThread");
            return Boolean.TRUE.equals(m.invoke(mc));
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        return true; // 探测不到按渲染线程宽松放行，兜底逻辑（tick 补 flush）照样工作
    }

    private CompatRender() {
    }

    /** RenderSystem.setShader(GameRenderer::getPositionColorShader) 的版本安全等价。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setColorShader() {
        shaderByName("getPositionColorShader");
    }

    /** 同上，贴图管线变体（getPositionTexShader）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setTextureShader() {
        shaderByName("getPositionTexShader");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void shaderByName(String getterName) {
        try {
            Method getter = resolveMethod(GameRenderer.class, getterName);
            if (getter == null) {
                return; // ≥1.21.2：shader getter 已移除，管线自动处理
            }
            Object shader = getter.invoke(null);
            if (shader == null) {
                return;
            }
            Method m = resolveMethod(RenderSystem.class, "setShader", Supplier.class);
            if (m != null) {
                m.invoke(null, (Supplier) () -> shader);
            }
        } catch (Exception ignored) {
            // 其他失败不拖垮渲染帧
        }
    }

    /**
     * GuiGraphics.setColor(r,g,b,a) 的版本安全等价（≥1.21.2 移除 → RenderSystem.setShaderColor）。
     */
    public static void setDrawColor(GuiGraphics g, float r, float gr, float b, float a) {
        try {
            Method m = GuiGraphics.class.getMethod("setColor",
                    float.class, float.class, float.class, float.class);
            m.invoke(g, r, gr, b, a);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
            return;
        }
        try {
            Method m = RenderSystem.class.getMethod("setShaderColor",
                    float.class, float.class, float.class, float.class);
            m.invoke(null, r, gr, b, a);
            return;
        } catch (Exception ignored) {
        }
        // 第三条路（也是 Fabric 生产环境唯一能走通的路）：走状态桥的编译期直调。
        // 前两条都是「按名字 getMethod」，在 Fabric 生产环境（intermediary 方法名）必然失败，
        // 于是以来在 Fabric 上染色是静默不生效的。
        com.opendreamcore.client.spi.GlState.get().setShaderColor(r, gr, b, a);
    }

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("OpenDreamCore");

    /** NativeImage 写像素：候选名 setPixelRGBA / setPixelABGR / setPixel 逐一试。
     *  传入 packed 为 BufferedImage.getRGB() 的 ARGB（0xAARRGGBB）。
     *
     *  ⚠ 打包字节序必须跟着「实际命中的方法名」走，不能凭方法名猜（符号会骗人）：
     *   - setPixelRGBA / setPixelABGR → 期望 ABGR（0xAABBGGRR）。
     *     1.20.1 / 1.21.1 只有 setPixelRGBA，写入 0x11223344 后读回 0x11443322 ⇒ 实为 ABGR；
     *     1.21.8 / 1.21.11 / 26.1.2 的 setPixelABGR 同为 ABGR。（逐版本 PNG 真值实测）
     *   - setPixel → 期望 ARGB（0xAARRGGBB，恒等）。1.21.4 只有 setPixel；1.21.8+ 也有。
     *
     *  历史 bug：这里曾无条件把 ARGB 旋成 0xRRGGBBAA，于是 1.21.8+ 命中 setPixelABGR 时
     *  四通道轮转（alpha 被读成红色通道）→ 彩色贴图整体偏色、GIF 的透明区变成不透明青块
     *  （港 那次“空白/发花”就是这么来的）。 */
    public static void nativeSetPixel(Object image, int x, int y, int packed) {
        int a = (packed >>> 24) & 0xFF;
        int r = (packed >>> 16) & 0xFF;
        int g = (packed >>> 8) & 0xFF;
        int b = packed & 0xFF;
        for (String name : new String[]{"setPixelRGBA", "setPixelABGR", "setPixel"}) {
            Method m;
            try {
                m = image.getClass().getMethod(name, int.class, int.class, int.class);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            // 序按方法名：setPixel 收 ARGB 原值；setPixelRGBA / setPixelABGR 收 ABGR
            int arg = "setPixel".equals(name) ? packed : ((a << 24) | (b << 16) | (g << 8) | r);
            try {
                m.invoke(image, x, y, arg);
            } catch (Exception e) {
                LOGGER.warn("[ODC-render] NativeImage.{} 调用失败（{} x {}）: {}",
                        name, x, y, e.toString());
            }
            return;
        }
        // 三个候选名全不存在 ⇒ 一个像素都没写，整张纹理全透明（贴图/字形会凭空消失且零日志）
        LOGGER.warn("[ODC-render] {} 无 setPixelRGBA/setPixelABGR/setPixel：像素未写入，贴图将全透明",
                image.getClass().getName());
    }

    /** NativeImage 读全像素：候选名 getPixelsRGBA / getPixelsCopy / getPixels。 */
    public static int[] nativeGetPixels(Object image) {
        for (String name : new String[]{"getPixelsRGBA", "getPixelsCopy", "getPixels"}) {
            try {
                Method m = image.getClass().getMethod(name);
                Object r = m.invoke(image);
                if (r instanceof int[] arr) {
                    return arr;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                return new int[0];
            }
        }
        return new int[0];
    }

    /**
     * Registry.get(rl) 的版本安全等价：
     * 1.21.1 返回 Item；≥1.21.2 返回 Optional<Reference<Item>>：统一解包为 Item 或 null。
     */
    public static Object registryGet(Object registry, ResourceLocation rl) {
        try {
            Object raw = registry.getClass().getMethod("get", ResourceLocation.class)
                    .invoke(registry, rl);
            if (raw instanceof java.util.Optional<?> opt) {
                return opt.orElse(null);
            }
            return raw;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 映射安全的反射底座（Fabric 生产环境把 MC 名重映射成 intermediary）
    //
    // 事实（字节码级实测）：Loom 只重映射「类型引用」，不重映射「反射字符串」——
    // 生产包里 Class.forName("net.minecraft.core.registries.BuiltInRegistries") 与
    // getMethod("value") 的字面量原样保留，于是所有「按 MC 原始名反射」的代码在 Fabric 上
    // 必然 ClassNotFoundException / NoSuchMethodException 并静默失败（NeoForge 却正常，
    // 因为它的运行时命名空间就是官方名）。这是「同一功能 NeoForge 好、Fabric 坏」的通用成因。
    //
    // 解法：Fabric 官方提供 MappingResolver，可在运行时于 named（我们编译时用的官方名）
    // 与 intermediary（生产运行时名）之间来回翻译。据此做三级解析：
    //   ① 原名直查        —— dev / NeoForge 一次命中，零开销
    //   ② 映射感知        —— 把运行时成员的名字与描述符反查回 named，再正向问
    //                        「named 的 name 会映成谁」，相等者即目标；不硬编码任何映射表
    //   ③ 唯一形状兜底    —— 参数形状在该类里只有一个候选时才用；有歧义一律放弃
    // 第③条取代了原来「按参数个数抽签」的做法：实测它把 setShaderTexture(int,int) 解析成了
    // polygonMode(int,int)（GL_INVALID_ENUM 刷屏 2420 条/会话），把 BufferBuilder.discard()
    // 解析成了 build()（每帧泄漏一个 MeshData → "Clearing BufferBuilder with unused batches"）。
    // ------------------------------------------------------------------

    private static volatile Object fabricResolver;
    private static volatile Boolean fabricMappingsProbed;
    private static volatile String fabricNamedNs;

    /** 探测 MappingResolver：只在 Fabric 上存在；NeoForge/Forge 上返回 null（表示无需翻译）。 */
    private static Object fabricResolver() {
        if (fabricMappingsProbed == null) {
            synchronized (CompatRender.class) {
                if (fabricMappingsProbed == null) {
                    Object r = null;
                    try {
                        Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
                        Object loader = fl.getMethod("getInstance").invoke(null);
                        r = fl.getMethod("getMappingResolver").invoke(loader);
                    } catch (Throwable ignored) {
                        r = null;
                    }
                    fabricResolver = r;
                    fabricMappingsProbed = Boolean.TRUE;
                }
            }
        }
        return fabricResolver;
    }

    /** 选「编译时名字」所在命名空间：loom 官方映射下叫 named，否则退回 official。 */
    private static String namedNamespace(Object r) {
        String cached = fabricNamedNs;
        if (cached != null) {
            return cached;
        }
        String ns = "named";
        try {
            Object names = r.getClass().getMethod("getNamespaces").invoke(r);
            if (names instanceof java.util.Collection<?> c && !c.contains("named") && c.contains("official")) {
                ns = "official";
            }
        } catch (Throwable ignored) {
            // 老 loader 无 getNamespaces：按 1 参 API 走，后续调用自然退化
        }
        fabricNamedNs = ns;
        return ns;
    }

    private static boolean isDangerousName(String mn) {
        // 生命周期/清理/状态写入类方法，与我们要找的方法同形、被误选就会炸或刷屏。
        return mn.equals("close") || mn.equals("shutdown") || mn.equals("destroy")
                || mn.equals("polygonMode") || mn.equals("blendFunc")
                || mn.equals("build") || mn.equals("buildOrThrow")
                || mn.equals("discard") || mn.equals("end")
                || mn.equals("dispose") || mn.equals("clear") || mn.equals("stop")
                || mn.equals("reset") || mn.equals("release");
    }

    private static boolean paramsAssignable(Class<?>[] actual, Class<?>[] wanted) {
        if (actual.length != wanted.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!box(actual[i]).isAssignableFrom(box(wanted[i]))) {
                return false;
            }
        }
        return true;
    }

    /** JVM 描述符：入参是运行时二进制名（内部类用 $，数组形如 [Lfoo.Bar;），类名先反查回 named。 */
    private static String namedDescriptor(String runtimeName) {
        if (runtimeName.startsWith("[")) {
            return "[" + namedDescriptor(runtimeName.substring(1));
        }
        if (runtimeName.startsWith("L") && runtimeName.endsWith(";")) {
            String inner = runtimeName.substring(1, runtimeName.length() - 1);
            return "L" + unmapClassName(inner).replace('.', '/') + ";";
        }
        switch (runtimeName) {
            case "void":
                return "V";
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "short":
                return "S";
            case "int":
                return "I";
            case "long":
                return "J";
            case "float":
                return "F";
            case "double":
                return "D";
            default:
                return "L" + unmapClassName(runtimeName).replace('.', '/') + ";";
        }
    }

    /** named 名 -> 运行时名（类）。非 Fabric 或查不到映射时原样返回。 */
    public static String mapClassName(String namedBinaryName) {
        Object r = fabricResolver();
        if (r == null || namedBinaryName == null) {
            return namedBinaryName;
        }
        try {
            return (String) r.getClass().getMethod("mapClassName", String.class, String.class)
                    .invoke(r, namedNamespace(r), namedBinaryName);
        } catch (Throwable ignored) {
            // 落到 loader < 0.15 的 1 参 API
        }
        try {
            return (String) r.getClass().getMethod("mapClassName", String.class).invoke(r, namedBinaryName);
        } catch (Throwable ignored) {
            return namedBinaryName;
        }
    }

    /** 运行时名 -> named 名（类）。 */
    private static String unmapClassName(String runtimeBinaryName) {
        Object r = fabricResolver();
        if (r == null || runtimeBinaryName == null) {
            return runtimeBinaryName;
        }
        try {
            return (String) r.getClass().getMethod("unmapClassName", String.class, String.class)
                    .invoke(r, namedNamespace(r), runtimeBinaryName);
        } catch (Throwable ignored) {
            // 落到老 API
        }
        try {
            return (String) r.getClass().getMethod("unmapClassName", String.class).invoke(r, runtimeBinaryName);
        } catch (Throwable ignored) {
            return runtimeBinaryName;
        }
    }

    /**
     * 映射安全地加载类（入参：named 二进制名，内部类用 $）。
     * 与 Class.forName 同语义：解析不了就抛 ClassNotFoundException。
     */
    public static Class<?> resolveClass(String namedBinaryName) throws ClassNotFoundException {
        try {
            return Class.forName(namedBinaryName);
        } catch (ClassNotFoundException first) {
            String mapped = mapClassName(namedBinaryName);
            if (!mapped.equals(namedBinaryName)) {
                return Class.forName(mapped);
            }
            throw first;
        }
    }

    /** 该方法是否就是「named 名 = name」的目标（参数可赋值由调用方先过滤）。 */
    private static boolean mappedNameMatches(Method m, String name, Class<?>[] types) {
        if (!paramsAssignable(m.getParameterTypes(), types)) {
            return false;
        }
        Object r = fabricResolver();
        if (r == null) {
            return false;
        }
        try {
            StringBuilder d = new StringBuilder("(");
            for (Class<?> p : m.getParameterTypes()) {
                d.append(namedDescriptor(p.getName()));
            }
            d.append(')').append(namedDescriptor(m.getReturnType().getName()));
            String ownerNamed = unmapClassName(m.getDeclaringClass().getName());
            Object want = r.getClass()
                    .getMethod("mapMethodName", String.class, String.class, String.class, String.class)
                    .invoke(r, namedNamespace(r), ownerNamed, name, d.toString());
            return m.getName().equals(want);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 映射安全地取公开字段（named 名）。找不到返回 null。 */
    public static java.lang.reflect.Field resolveField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            // 落到映射感知
        }
        Object r = fabricResolver();
        if (r == null) {
            return null;
        }
        for (java.lang.reflect.Field f : owner.getFields()) {
            try {
                String ownerNamed = unmapClassName(f.getDeclaringClass().getName());
                String desc = namedDescriptor(f.getType().getName());
                Object want = r.getClass()
                        .getMethod("mapFieldName", String.class, String.class, String.class, String.class)
                        .invoke(r, namedNamespace(r), ownerNamed, name, desc);
                if (f.getName().equals(want)) {
                    return f;
                }
            } catch (Throwable ignored) {
                continue;
            }
        }
        return null;
    }
    // 反射方法解析（Fabric 生产环境方法名是 intermediary）：三级解析，见上方「映射安全的反射底座」。
    // ① 原名直查（dev / NeoForge 命中）② 映射感知匹配 ③ 形状唯一才兜底。结果缓存，热路径零开销。

    private static final java.util.concurrent.ConcurrentHashMap<String, Method> METHOD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static Method resolveMethod(Class<?> owner, String name, Class<?>... types) {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> t : types) {
            key.append(':').append(t.getName());
        }
        String k = key.toString();
        Method cached = METHOD_CACHE.get(k);
        if (cached != null) {
            return cached;
        }
        Method found = null;
        try {
            // (1) 原名直查：dev 环境与 NeoForge 生产（运行时就是官方名）一次命中
            found = owner.getMethod(name, types);
        } catch (NoSuchMethodException nameMiss) {
            Method onlyShape = null;
            int shapeCount = 0;
            for (Method m : owner.getMethods()) {
                if (m.getDeclaringClass() == Object.class
                        || m.getParameterCount() != types.length
                        || isDangerousName(m.getName())
                        || !paramsAssignable(m.getParameterTypes(), types)) {
                    continue;
                }
                if (mappedNameMatches(m, name, types)) {
                    // (2) 映射感知命中：Fabric 生产环境名字是 intermediary，靠 MappingResolver 反查对上
                    found = m;
                    break;
                }
                shapeCount++;
                onlyShape = m;
            }
            if (found == null && shapeCount == 1) {
                // (3) 形状唯一才兜底；有歧义一律放弃（抽签会误选 polygonMode / build 之类）
                found = onlyShape;
            }
        }
        if (found != null) {
            METHOD_CACHE.put(k, found);
        }
        return found;
    }



    /** 按名称+参数个数在目标对象上择路调用（实体 create 等签名漂移用）；失败返回 null。 */
    public static Object invokeByShape(Object target, String name, Object[] args) {
        for (Method m : target.getClass().getMethods()) {
            if (!name.equals(m.getName()) || m.getParameterCount() != args.length) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !box(m.getParameterTypes()[i]).isInstance(args[i])) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            try {
                return m.invoke(target, args);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    // 物品/注册表信息族（1.20.1 ↔ 1.20.5+ 组件化重构）

    /**
     * 手持物品 tooltip 行：≥1.20.5 getTooltipLines(Item.TooltipContext,Player,Flag)；
     * ≤1.20.4 getTooltip(Player,Flag)。反射双路。
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> tooltipLines(Object stack, Object level, Object player, Object flag) {
        // 首选 per-target 桥（直调各版本 API）：Fabric 生产环境按 MC 名反射必失，只有直调有效。
        try {
            com.opendreamcore.client.entity.ItemModelRenderBridge br =
                    com.opendreamcore.client.entity.ItemModelViews.bridge();
            if (br != null) {
                java.util.List<Object> viaBridge = br.tooltipLines(stack, level, player, flag);
                if (viaBridge != null && !viaBridge.isEmpty()) {
                    return viaBridge;
                }
            }
        } catch (Throwable ignored) {
            // 桥失败 → 落到下面的反射兜底路径
        }
        try {
            Class<?> tc = resolveClass("net.minecraft.world.item.Item$TooltipContext");
            Object ctx = tc.getMethod("of", net.minecraft.world.level.Level.class).invoke(null, level);
            return (java.util.List<Object>) stack.getClass()
                    .getMethod("getTooltipLines", tc,
                            net.minecraft.world.entity.player.Player.class,
                            net.minecraft.world.item.TooltipFlag.class)
                    .invoke(stack, ctx, player, flag);
        } catch (ClassNotFoundException | NoSuchMethodException legacy) {
            try {
                return (java.util.List<Object>) stack.getClass()
                        .getMethod("getTooltip",
                                net.minecraft.world.entity.player.Player.class,
                                net.minecraft.world.item.TooltipFlag.class)
                        .invoke(stack, player, flag);
            } catch (Exception ignored) {
                return java.util.List.of();
            }
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    /** 附魔行组件：getEnchantments 缺失（旧版走 NBT）返回空；fullname 静态方法按形状择路。 */
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> enchantmentLines(Object stack) {
        try {
            Map<?, ?> ench = (Map<?, ?>) stack.getClass().getMethod("getEnchantments").invoke(stack);
            var out = new java.util.ArrayList<Object>();
            Method fullname = null;
            for (Method m : net.minecraft.world.item.enchantment.Enchantment.class.getMethods()) {
                if ("getFullname".equals(m.getName()) && m.getParameterCount() == 2) {
                    fullname = m;
                    break;
                }
            }
            if (fullname == null) {
                return out;
            }
            for (Map.Entry<?, ?> e : ench.entrySet()) {
                try {
                    out.add(fullname.invoke(null, e.getKey(), e.getValue()));
                } catch (Exception ignored) {
                }
            }
            return out;
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    /** 物品 Lore 行：DataComponents 为 ≥1.20.5 API，缺失返回空。 */
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> loreLines(Object stack) {
        // 首选 per-target 桥（直调各版本 API），理由同上。
        try {
            com.opendreamcore.client.entity.ItemModelRenderBridge br =
                    com.opendreamcore.client.entity.ItemModelViews.bridge();
            if (br != null) {
                java.util.List<Object> viaBridge = br.loreLines(stack);
                if (viaBridge != null && !viaBridge.isEmpty()) {
                    return viaBridge;
                }
            }
        } catch (Throwable ignored) {
            // 桥失败 → 落到下面的反射兜底路径
        }
        try {
            Class<?> dc = resolveClass("net.minecraft.core.component.DataComponents");
            Object components = stack.getClass().getMethod("getComponents").invoke(stack);
            Object key = dc.getField("LORE").get(null);
            Object lore = components.getClass().getMethod("get", Object.class).invoke(components, key);
            if (lore == null) {
                return java.util.List.of();
            }
            return (java.util.List<Object>) lore.getClass().getMethod("lines").invoke(lore);
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    /** Holder 注册名：getRegisteredName 缺失回退 unwrapKey → location。 */
    public static String holderRegisteredName(Object holder) {
        try {
            return (String) holder.getClass().getMethod("getRegisteredName").invoke(holder);
        } catch (NoSuchMethodException legacy) {
            try {
                Object key = holder.getClass().getMethod("unwrapKey").invoke(holder);
                if (key instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object rk = opt.get();
                    return String.valueOf(rk.getClass().getMethod("location").invoke(rk));
                }
            } catch (Exception ignored) {
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * ItemStack 反序列化：≥1.20.5 parse(HolderLookup.Provider, CompoundTag)；
     * ≤1.20.4 of(CompoundTag)。按名称+参数个数择路，双版本通吃。
     */
    public static Object parseStack(Object registryAccess, Object tag) {
        try {
            for (Method m : ItemStack.class.getMethods()) {
                if ("parse".equals(m.getName()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == tag.getClass()) {
                    return m.invoke(null, registryAccess, tag);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return ItemStack.class.getMethod("of", tag.getClass()).invoke(null, tag);
        } catch (Exception e) {
            return null;
        }
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }

    /**
     * ResourceLocation 构造的版本安全等价：
     * 1.20.1 = new ResourceLocation(ns, path)；1.21+ = fromNamespaceAndPath(ns, path)。
     */
    @SuppressWarnings("unchecked")
    /** 版本安全 ResourceLocation 解析：优先走平台注入的编译期工厂（生产环境类名/方法名是
     *  intermediary，反射找 \"of\"/\"fromNamespaceAndPath\" 全部落空——由各 target 在启动时
     *  setRlFactory 注入本版本编译期实现）。反射列作 dev/其它环境兜底。 */
    public interface RlFactory {
        ResourceLocation of(String ns, String path);
    }

    private static volatile RlFactory rlFactory;

    /** 平台壳注册本版本编译期 ResourceLocation 工厂（必须尽早，注入前 rl() 仍走反射兜底）。 */
    public static void setRlFactory(RlFactory f) {
        rlFactory = f;
    }

    private static volatile java.lang.reflect.Method rlOf;
    private static volatile java.lang.reflect.Method rlFromNsPath;
    private static volatile java.lang.reflect.Method rlTryBuild;

    public static ResourceLocation rl(String ns, String path) {
        RlFactory f = rlFactory;
        if (f != null) {
            try {
                ResourceLocation r = f.of(ns, path);
                if (r != null) {
                    return r;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            if (rlOf == null) {
                synchronized (CompatRender.class) {
                    if (rlOf == null) {
                        try { rlOf = ResourceLocation.class.getMethod("of", String.class, String.class); }
                        catch (NoSuchMethodException ignored) { /* 没有 of：试 fromNamespaceAndPath */ }
                        try { rlFromNsPath = ResourceLocation.class.getMethod("fromNamespaceAndPath", String.class, String.class); }
                        catch (NoSuchMethodException ignored) { /* 也没有：再试 tryBuild */ }
                        try { rlTryBuild = ResourceLocation.class.getMethod("tryBuild", String.class, String.class); }
                        catch (NoSuchMethodException ignored) { /* 全没有：留 null，走下方构造器兕底 */ }
                    }
                }
            }
            if (rlOf != null) {
                try {
                    ResourceLocation r = (ResourceLocation) rlOf.invoke(null, ns, path);
                    if (r != null) {
                        return r;
                    }
                } catch (Exception ignored) { /* 调用失败：换下一候选工厂 */ }
            }
            if (rlFromNsPath != null) {
                try {
                    return (ResourceLocation) rlFromNsPath.invoke(null, ns, path);
                } catch (Exception ignored) { /* 调用失败：换下一候选工厂 */ }
            }
            if (rlTryBuild != null) {
                try {
                    Object opt = rlTryBuild.invoke(null, ns, path);
                    if (opt instanceof java.util.Optional<?> o && o.isPresent()) {
                        return (ResourceLocation) o.get();
                    }
                } catch (Exception ignored) { /* 调用失败：落到构造器兜底 */ }
            }
            try {
                return (ResourceLocation) ResourceLocation.class
                        .getConstructor(String.class, String.class)
                        .newInstance(ns, path);
            } catch (Exception e) {
                throw new IllegalArgumentException("非法资源路径: " + ns + ":" + path, e);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("非法资源路径: " + ns + ":" + path, e);
        }
    }

    // 顶点立即模式（Tesselator 方言吸收）

    /** 探测结果：Tesselator 有 begin(Mode,VertexFormat) = ≥1.20.2 新式；否则走 getBuilder().begin。 */
    private static volatile Boolean modernBegin;
    static boolean modernBegin() { return Boolean.TRUE.equals(modernBegin); }

    /**
     * 版本安全 begin：统一返回 CompatBuffer 自 fluent 包装。
     * 现代 Tesselator.begin(Mode,VertexFormat)；1.20.1 getBuilder().begin(Mode,VertexFormat)。
     *
     * <p>处于世界绘制窗口内时（见 {@link WorldHologram#render} 与 world 包的绘制入口），
     * 这个批次会自动挂上世界语义渲染类型：不写深度 + 双面 + 透明 + 不吃光照/叠加，
     * 于是它在光影模组眼里是可归类的一等公民，会落进正确的 gbuffer 阶段。以前世界几何走
     * 立即模式 + 裸着色器，光影拿不到归类信息，一旦与半透明阶段交错就会把深度缓冲写乱，
     * 表现为物品/生物部分透明。窗口外（UI、字体、物品栏等）行为与以前完全一致。
     */
    public static CompatBuffer begin(Object mode, Object format) {
        if (modernBegin == null) {
            synchronized (CompatRender.class) {
                if (modernBegin == null) {
                    // 名称直查 + 形状兜底：Fabric 生产环境 "begin" 是 intermediary 名
                    modernBegin = Boolean.valueOf(
                            resolveMethod(com.mojang.blaze3d.vertex.Tesselator.class,
                                    "begin", mode.getClass(), format.getClass()) != null);
                }
            }
        }
        CompatBuffer buf = new CompatBuffer(mode, format);
        if (com.opendreamcore.client.render.WorldRenderTypeDispatch.inWorldDraw()) {
            // 格式决定顶点带不带 UV：贴图几何才能用原生盔甲半透明那条类型回退，
            // 纯色几何只由自建实现接管（用错格式会因顶点元素不匹配而画不出东西）
            boolean textured = isTexturedFormat(format);
            Object rt = com.opendreamcore.client.render.WorldRenderTypeDispatch.resolve(
                    textured, WorldHologram.isSeeThroughPass(), mode);
            if (rt != null) {
                buf.useWorldRenderType(rt);
            }
        }
        return buf;
    }

    /** 顶点格式是否带 UV（世界几何分纯色与贴图两种，只有贴图的能套原版贴图管线）。 */
    private static boolean isTexturedFormat(Object format) {
        if (format == null) {
            return false;
        }
        try {
            for (Method m : format.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == java.util.List.class) {
                    Object elements = m.invoke(format);
                    if (elements instanceof java.util.List<?> list) {
                        for (Object e : list) {
                            // 元素名里带 UV 就说明顶点带贴图坐标
                            if (e != null && e.toString().toLowerCase(java.util.Locale.ROOT).contains("uv")) {
                                return true;
                            }
                        }
                        return false;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 解析不出格式：当作纯色，宁可回退原有上传路径
        }
        return false;
    }

    // ── 世界渲染专用独立批次源 ───────────────────────────────────────────────
    //
    // 世界里的面板/名牌是插在原版世界渲染流程中间画的，以前直接借原版那个共享 BufferSource，
    // 画完调它的 endBatch()。问题是那个 source 原版自己正在用（实体、粒子正在往里搅顶点），
    // 我们这一调就把原版攒到一半的批次提前送走；开光影时（Iris/Oculus 以渲染类型为 draw call
    // 单位重绘世界）这种时序错位会让原版几何落到错误的 gbuffer 阶段，表现为物品、生物部分透明。
    // 所以这里自建一份完全属于我们的 source：自己的 ByteBufferBuilder + 自己的 BufferSource。
    // 每次绘制只结束自己那一份，原版的批次留给原版在阶段末自己 flush。
    //
    // Fabric 生产环境类名/方法名是 intermediary（MultiBufferSource→class_1921、immediate→method_...），
    // 因此一律走 resolveMethod 的名称+形状双重解析，不认具体字面名。

    private static volatile Method immediateMethod;
    private static volatile Method endBatchMethod;
    private static volatile Boolean worldBatchProbed;

    /** 一次世界绘制专用的独立批次源；{@link #close()} 结束批次并释放缓冲。 */
    public static final class WorldBatch implements AutoCloseable {
        private final Object source;
        private final Object byteBuffer;
        private boolean closed;

        private WorldBatch(Object source, Object byteBuffer) {
            this.source = source;
            this.byteBuffer = byteBuffer;
        }

        /** 独立源的实例（实现该世代的 MultiBufferSource）；不可用时为 null，调用方回退原版共享源。 */
        public Object source() {
            return source;
        }

        /** 只结束我们自己写出的批次。 */
        public void endBatch() {
            if (source == null || endBatchMethod == null) {
                return;
            }
            try {
                endBatchMethod.invoke(source);
            } catch (Throwable ignored) {
                // 结束失败无补救手段；close() 仍会释放底层缓冲，不会泄漏出帧
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            endBatch();
            CompatBuffer.releaseBuffer(byteBuffer);
        }
    }

    /**
     * 新建一个独立批次源。失败时返回 {@code source() == null} 的对象，
     * 调用方应回退到原版共享源（且绝不对共享源调 endBatch）。
     */
    public static WorldBatch beginWorldBatch() {
        Object src = null;
        Object byteBuf = CompatBuffer.newByteBufferBuilder(4096);
        if (byteBuf != null) {
            try {
                probeWorldBatch(byteBuf.getClass());
                if (immediateMethod != null) {
                    src = immediateMethod.invoke(null, byteBuf);
                }
            } catch (Throwable ignored) {
                // 建不出来就让调用方回退原版源
            }
        }
        if (src == null) {
            // 没建成：立刻把刚申请的缓冲还回去，避免每帧漏一个
            CompatBuffer.releaseBuffer(byteBuf);
            return new WorldBatch(null, null);
        }
        return new WorldBatch(src, byteBuf);
    }

    private static void probeWorldBatch(Class<?> bufferClass) {
        if (worldBatchProbed != null) {
            return;
        }
        synchronized (CompatRender.class) {
            if (worldBatchProbed != null) {
                return;
            }
            try {
                Class<?> mbs = Class.forName(mapClassName("net.minecraft.client.renderer.MultiBufferSource"));
                Method imm = resolveMethod(mbs, "immediate", bufferClass);
                if (imm == null) {
                    // 名字对不上（生产环境映射名）：认「静态 + 单参收缓冲 + 有返回值」的那个工厂。
                    // 这代同时存在 immediateWithBuffers(Map, 缓冲)，靠参数个数与参数类型把它排除掉。
                    for (Method m : mbs.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                                && m.getParameterCount() == 1
                                && m.getParameterTypes()[0].isAssignableFrom(bufferClass)
                                && m.getReturnType() != void.class) {
                            imm = m;
                            break;
                        }
                    }
                }
                if (imm == null) {
                    return;
                }
                Method eb = resolveMethod(imm.getReturnType(), "endBatch");
                if (eb == null || eb.getParameterCount() != 0) {
                    // 零参 endBatch 才是「全部结束」；带参那个是 endBatch(RenderType)。
                    eb = null;
                    for (Method m : imm.getReturnType().getMethods()) {
                        if (m.getDeclaringClass() != Object.class && m.getParameterCount() == 0
                                && m.getReturnType() == void.class) {
                            eb = m;
                            break;
                        }
                    }
                }
                if (eb == null) {
                    return;
                }
                immediateMethod = imm;
                endBatchMethod = eb;
                worldBatchProbed = Boolean.TRUE;
            } catch (Throwable ignored) {
                // 探测失败：保持未探测，下一帧再试（不缓存失败，避免环境就绪后被永久锁死）
            }
        }
    }

    private static volatile Boolean modernBlit;
    private static volatile MethodHandle legacyBlit;
    private static volatile MethodHandle modernFactory;

    /** 探测 GuiGraphics 的 11 参 blit 重载形态（首参 ResourceLocation=旧版 / Function=新版）。 */
    private static void detect() {
        try {
            for (Method m : GuiGraphics.class.getMethods()) {
                if (!"blit".equals(m.getName()) || m.getParameterCount() != 11) {
                    continue;
                }
                Class<?> first = m.getParameterTypes()[0];
                if (first == ResourceLocation.class) {
                    legacyBlit = MethodHandles.lookup().unreflect(m);
                    modernBlit = Boolean.FALSE;
                    return;
                }
                if (first == java.util.function.Function.class) {
                    modernBlit = Boolean.TRUE;
                    legacyBlit = MethodHandles.lookup().unreflect(m);
                    return;
                }
            }
        } catch (IllegalAccessException ignored) {
            // 反射受限：保持未解析状态，blit 调用走兜底吞异常
        }
        // 未找到 11 参重载：退回旧签名尝试（保持行为可见的失败）
        modernBlit = Boolean.FALSE;
    }

    private static Object guiTexturedFactory(ResourceLocation tex) {
        try {
            if (modernFactory == null) {
                synchronized (CompatRender.class) {
                    if (modernFactory == null) {
                        Class<?> rt = resolveClass("net.minecraft.client.renderer.RenderType");
                        Method f = rt.getMethod("guiTextured", ResourceLocation.class);
                        modernFactory = MethodHandles.lookup().unreflect(f);
                    }
                }
            }
            return modernFactory.invoke(tex);
        } catch (Throwable t) {
            return tex; // 兜底：直接传贴图（最坏情况渲染层自行处理）
        }
    }

    /**
     * GuiGraphics.blit 的版本安全等价：
     * 旧版 blit(RL, x,y,w,h, u,v,uW,vH,texW,texH) /
     * 新版 blit(Function<RL,RenderType>, 同参数表)。
     */
    public static void blit(GuiGraphics g, ResourceLocation tex,
                            int x, int y, int w, int h,
                            float u, float v, int uw, int vh, int tw, int th) {
        if (modernBlit == null) {
            synchronized (CompatRender.class) {
                if (modernBlit == null) {
                    detect();
                }
            }
        }
        try {
            if (Boolean.TRUE.equals(modernBlit)) {
                legacyBlit.invoke(g,
                        guiTexturedFactory(tex), x, y, w, h, u, v, uw, vh, tw, th);
            } else {
                legacyBlit.invoke(g, tex, x, y, w, h, u, v, uw, vh, tw, th);
            }
        } catch (Throwable ignored) {
            // 单次贴图失败不拖垮整页渲染
        }
    }

    /**
     * KongCore 式 GUI_TEXTURED 管线 blit（1.21.2+：blit(RenderPipeline, RL, x, y, u, v, w, h, tw, th, color)）。
     * 字体重绘/自定义贴图用这个比 Function 版 blit 可靠；旧版本没有 RenderPipelines 时回退 blit()。
     */
    public static void blitGuiTextured(GuiGraphics g, ResourceLocation tex,
                                       int x, int y, int w, int h,
                                       float u, float v, int uw, int vh, int tw, int th) {
        try {
            Class<?> pipelines = resolveClass("net.minecraft.client.renderer.RenderPipelines");
            Object pipeline = pipelines.getField("GUI_TEXTURED").get(null);
            Class<?> pipelineCls = resolveClass("net.minecraft.client.renderer.RenderPipeline");
            Method m = GuiGraphics.class.getMethod("blit", pipelineCls, ResourceLocation.class,
                    int.class, int.class, float.class, float.class,
                    int.class, int.class, int.class, int.class, int.class);
            m.invoke(g, pipeline, tex, x, y, u, v, uw, vh, tw, th, 0xFFFFFFFF);
        } catch (Throwable ignored) {
            blit(g, tex, x, y, w, h, u, v, uw, vh, tw, th);
        }
    }

    // GUI pose 栈方言（≥1.21.6 Matrix3x2fStack 替代 PoseStack）
    // 两分支都是直接类型调用（PoseStack 与 JOML Matrix3x2fStack 在所有目标版本 classpath 上都存在），零反射热路径。
    // 注意：2D 栈无 Z 轴/四元数，X/Y 旋转在 GUI 平面无意义 → 新版路径仅应用 Z 分量。

    /** pose().pushPose() / pushMatrix()。 */
    public static void posePush(Object pose) {
        if (pose instanceof org.joml.Matrix3x2fStack s) {
            s.pushMatrix();
        } else if (pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            p.pushPose();
        }
    }

    /** pose().popPose() / popMatrix()。 */
    public static void posePop(Object pose) {
        if (pose instanceof org.joml.Matrix3x2fStack s) {
            s.popMatrix();
        } else if (pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            p.popPose();
        }
    }

    /** 模型视图栈当前深度（joml Matrix4fStack 深度只在私有字段 curr 里）。
     *  世界渲染钩子进出做深度守卫用；反射失败返回 -1（守卫静默跳过，不影响主流程）。 */
    public static int modelViewStackDepth() {
        try {
            var stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
            var f = stack.getClass().getDeclaredField("curr");
            f.setAccessible(true);
            return f.getInt(stack);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** 把模型视图栈收回给定深度（多出来的矩阵弹掉；深度拿不到/已低于目标就什么都不做）。 */
    public static void modelViewRestoreTo(int depth) {
        var stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        try {
            if (depth < 0) {
                return;
            }
            var f = stack.getClass().getDeclaredField("curr");
            f.setAccessible(true);
            int now = f.getInt(stack);
            while (now > depth) {
                // 弹栈走版本安全等价：1.20.1 的 PoseStack 是 popPose（没有 popMatrix），新版 joml 栈才是 popMatrix
                posePop(stack);
                now = f.getInt(stack);
            }
        } catch (Throwable ignored) {
            // 反射读不到就放弃守卫（主修复在渲染阶段本身）
        }
    }

    /** pose().translate(x, y, 0) 的版本安全等价（新版 2D 无 z）。 */
    public static void poseTranslate(Object pose, double x, double y) {
        if (pose instanceof org.joml.Matrix3x2fStack s) {
            s.translate((float) x, (float) y);
        } else if (pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            p.translate((float) x, (float) y, 0.0F);
        }
    }

    /** pose().scale(sx, sy, 1) 的版本安全等价（新版 2D 双参）。 */
    public static void poseScale(Object pose, double sx, double sy) {
        if (pose instanceof org.joml.Matrix3x2fStack s) {
            s.scale((float) sx, (float) sy);
        } else if (pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            p.scale((float) sx, (float) sy, 1.0F);
        }
    }

    /** 绕 Z 轴旋转（度）：mulPose(Axis.ZP.rotationDegrees) ↔ Matrix3x2fStack.rotate(弧度)。 */
    public static void poseRotateZDegrees(Object pose, double degrees) {
        if (degrees == 0) {
            return;
        }
        if (pose instanceof org.joml.Matrix3x2fStack s) {
            s.rotate((float) Math.toRadians(degrees));
        } else if (pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            p.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) degrees));
        }
    }

    /** 三轴欧拉旋转（度）：3D 全支持；2D 栈仅 Z 生效，X/Y 静默忽略（GUI 平面无此自由度）。 */
    public static void poseRotateXYZDegrees(Object pose, double rx, double ry, double rz) {
        boolean is22 = pose instanceof org.joml.Matrix3x2fStack;
        if (!is22 && pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            if (rx != 0) {
                p.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) rx));
            }
            if (ry != 0) {
                p.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) ry));
            }
        }
        poseRotateZDegrees(pose, rz);
    }

    /**
     * 当前 GUI 矩阵：旧版 = PoseStack 栈顶 Matrix4f；新版 = Matrix3x2fStack 自身（即栈顶）。
     * 返回 Object，供 CompatBuffer.addVertex(Object matrix, …) 手工变换顶点。
     */
    public static Object guiMatrix(GuiGraphics g) {
        Object pose = g.pose();
        if (pose instanceof org.joml.Matrix3x2f m) {
            return m;
        }
        if (pose instanceof com.mojang.blaze3d.vertex.PoseStack p) {
            return p.last().pose();
        }
        return null;
    }

    // DynamicTexture 构造族（≥1.21.8 需 Supplier<String> 标签参）

    /** new DynamicTexture(NativeImage) 的版本安全等价；两参构造优先 null 标签，失败再带默认标签。 */
    public static DynamicTexture newDynamicTexture(NativeImage image) {
        for (var c : DynamicTexture.class.getConstructors()) {
            Class<?>[] ps = c.getParameterTypes();
            if (ps.length == 2 && ps[1].isAssignableFrom(NativeImage.class)) {
                try {
                    return (DynamicTexture) c.newInstance(null, image);
                } catch (Exception ignored) {
                }
                try {
                    return (DynamicTexture) c.newInstance(
                            (java.util.function.Supplier<String>) () -> "opendreamcore", image);
                } catch (Exception ignored) {
                }
            }
        }
        for (var c : DynamicTexture.class.getConstructors()) {
            Class<?>[] ps = c.getParameterTypes();
            if (ps.length == 1 && ps[0].isAssignableFrom(NativeImage.class)) {
                try {
                    return (DynamicTexture) c.newInstance(image);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    // RenderSystem 纹理/染色族（世界 billboard 用）

    /**
     * RenderSystem.setShaderColor 的版本安全等价：缺失（≥1.21.6 移除）静默跳过。
     * 注意：新管线染色语义待运行冒烟校验，短期表现为透明度渐变降级。
     */
    public static void shaderColor(float r, float g, float b, float a) {
        rsToggle("setShaderColor", new Class<?>[]{float.class, float.class, float.class, float.class},
                new Object[]{r, g, b, a});
    }

    /** 全局染色是否可用（缓存；null = 未探测）。 */
    private static volatile Boolean shaderColorAvailable;

    /**
     * 全局染色（{@link #shaderColor}）这一版是否真的生效。
     *
     * <p>为什么要问：1.21.6 的渲染体系重构把 setShaderColor 从 RenderSystem 上整体拿掉了，
     * 透明度在大改之后只能落到顶点色上。世界贴图几何因此有两种写法，二选一，不能同时用——
     * 同时用会把 alpha 乘两遍，半透明面板会比预期更淡。
     */
    public static boolean globalShaderColorAvailable() {
        Boolean cached = shaderColorAvailable;
        if (cached == null) {
            // 状态桥是权威：gen12（1.20.1/1.21.1/1.21.4）hasGlobalState()=true，全局染色确实存在；
            // 1.21.8+ 为 false（setShaderColor 已从 RenderSystem 撤掉），透明度只能落到顶点色上。
            // 反射那条留着当兜底：桥未注册（如 legacy 目标）时仍能给出答案。
            cached = com.opendreamcore.client.spi.GlState.get().hasGlobalState()
                    || resolveMethod(RenderSystem.class, "setShaderColor",
                    float.class, float.class, float.class, float.class) != null;
            shaderColorAvailable = cached;
        }
        return cached;
    }

    /**
     * 世界贴图几何该用的顶点格式。
     *
     * <p>全局染色还能用时沿旧格式（透明度由全局染色乘上去）；拿不到的版本改用带顶点色的格式，
     * 把透明度随几何一起带走。两种格式都带 UV，渲染类型解算侧按「带不带 UV」分流不受影响。
     */
    public static com.mojang.blaze3d.vertex.VertexFormat worldTextureFormat() {
        return globalShaderColorAvailable()
                ? com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX
                : com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR;
    }

    /**
     * 写一个世界贴图四边形的顶点（UV + 透明度）。
     *
     * <p>透明度分两条路：新版的渲染体系没有全局染色，必须在每个顶点上写颜色，否则 alpha 无处
     * 落脚（面板会不透明或整片消失）；旧版由全局染色负责，顶点上写颜色反而会与它叠乘。这里只在
     * 新版写法下补颜色，调用点因此不必各自判断版本。
     *
     * @param builder 目标批次（格式须来自 {@link #worldTextureFormat()}）
     * @param matrix  已含位姿的矩阵
     * @param x,y     平面内坐标（z 恒为 0）
     * @param u,v     贴图坐标
     * @param alpha   该顶点的透明度
     */
    public static CompatBuffer texturedVertex(CompatBuffer builder, Object matrix,
                                              float x, float y, float u, float v, float alpha) {
        builder.addVertex(matrix, x, y, 0).setUv(u, v);
        if (!globalShaderColorAvailable()) {
            builder.setColor(1.0F, 1.0F, 1.0F, alpha);
        }
        return builder;
    }

    private static volatile MethodHandle texViewGetter;

    /** 字形贴图 quad 完整写入。1.20.1 顶点 API 是 vertex/uv/color/light，1.21.x 是 addVertex/setUv/setColor/setLight——
     *  全链反射垫片，跨版本编译安全。 */
    public static void glyphQuad(Object consumer, Object matrix, float x, float y, float w, float h,
                                 float u0, float v0, float u1, float v1, int packedLight) {
        try {
            quadVertex(consumer, matrix, x, y, u0, v0, packedLight);
            quadVertex(consumer, matrix, x, y + h, u0, v1, packedLight);
            quadVertex(consumer, matrix, x + w, y + h, u1, v1, packedLight);
            quadVertex(consumer, matrix, x + w, y, u1, v0, packedLight);
        } catch (Throwable ignored) {
        }
    }

    private static void quadVertex(Object consumer, Object matrix, float x, float y, float u, float v,
                                   int light) throws Exception {
        Object cur = consumer;
        cur = glyphInvoke(cur, "addVertex", "vertex", matrix, x, y, 0.0F);
        cur = glyphInvoke(cur, "setUv", "uv", u, v);
        cur = glyphInvoke(cur, "setColor", "color", 255, 255, 255, 255);
        glyphInvoke(cur, "setLight", "light", light);
    }

    private static Object glyphInvoke(Object target, String n1, String n2, Object... args) throws Exception {
        for (String n : new String[]{n1, n2}) {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(n) && m.getParameterCount() == args.length
                        && glyphParamsOk(m, args)) {
                    return m.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(n1 + "/" + n2);
    }

    private static boolean glyphParamsOk(Method m, Object[] args) {
        Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof Float || a instanceof Integer) {
                Class<?> p = ps[i];
                if (p != float.class && p != int.class && p != Float.class && p != Integer.class) {
                    return false;
                }
            } else if (a != null && !ps[i].isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    /**
     * RenderSystem.setShaderTexture(sampler, RL) 的版本安全等价：
     * 旧版第二参 ResourceLocation 直调；新版需 GpuTextureView —— 从 TextureManager 解析后取视图。
     */
    public static void setShaderTexture(int sampler, ResourceLocation rl) {
        try {
            // 本地散装/动态纹理直通；opendreamcore 包资源引用缺失时跳过绑定，
            // 避免 TextureManager 对不存在的贴图反复打 Missing resource WARN
            if (rl != null && "opendreamcore".equals(rl.getNamespace())) {
                String p = rl.getPath();
                if (!p.startsWith("loose/") && !p.startsWith("gif/")) {
                    var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
                    if (rm != null) {
                        java.util.Optional<?> opt = rm.getResource(rl);
                        if (opt == null || opt.isEmpty()) {
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : RenderSystem.class.getMethods()) {
                if ("setShaderTexture".equals(m.getName()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == ResourceLocation.class) {
                    m.invoke(null, sampler, rl);
                    // 绑定成功才记账：世界几何的渲染类型要把采样器钉到这张贴图上
                    noteBoundTexture(sampler, rl);
                    return;
                }
            }
            Object view = textureViewOf(rl);
            if (view != null) {
                for (Method m : RenderSystem.class.getMethods()) {
                    if ("setShaderTexture".equals(m.getName()) && m.getParameterCount() == 2
                            && m.getParameterTypes()[1].isInstance(view)) {
                        m.invoke(null, sampler, view);
                        noteBoundTexture(sampler, rl);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 绑图成功后的记账：只记采样器 0 的那张。
     *
     * <p>世界几何建成渲染类型时要把采样器钉死在具体贴图上，而调用方是「先绑图、后建批次」，
     * 所以只有绑定时才能知道该用哪张。高版本 RenderSystem 的贴图绑定已经改走纹理视图句柄，
     * 光看 RL 无法回头查，必须在这里顺手记下。
     */
    private static void noteBoundTexture(int sampler, ResourceLocation rl) {
        if (sampler != 0 || rl == null) {
            return;
        }
        try {
            com.opendreamcore.client.render.WorldRenderTypeDispatch.noteBoundTexture(rl);
        } catch (Throwable ignored) {
            // 共享层未就绪（理论上不会）：不记也能跑，只是世界贴图会退回占位白图
        }
    }

    /** 纹理是否真的可渲染（TextureManager 已注册且不是 missing 纹理）。
     *  页面/世界引用不存在的贴图时 UiStyle.texture 会返回"假 rl"——直接绑定会渲染成紫色黑格，
     *  绘制前用它挡一道。opendreamcore 命名空间的缺失资源直接判 false，绝不触发
     *  TextureManager.getTexture 的缺失加载（那会反复打 "Failed to load texture" WARN）。 */
    public static boolean textureUsable(ResourceLocation rl) {
        if (rl == null) {
            return false;
        }
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            // opendreamcore 命名空间：散装/远程/帧表走 TextureManager 动态注册，其余按资源包判定
            if ("opendreamcore".equals(rl.getNamespace())) {
                String p = rl.getPath();
                boolean dynamic = p.startsWith("loose/") || p.startsWith("gif/") || p.startsWith("remote/");
                if (!dynamic) {
                    var rm = mc.getResourceManager();
                    if (rm != null) {
                        java.util.Optional<?> opt = rm.getResource(rl);
                        if (opt == null || opt.isEmpty()) {
                            return false;
                        }
                    }
                }
            }
            var manager = mc.getTextureManager();
            var tex = manager.getTexture(rl);
            if (tex == null) {
                return false;
            }
            var missing = manager.getTexture(CompatRender.rl("minecraft", "textures/missingno"));
            return tex != missing;
        } catch (Throwable t) {
            return false;
        }
    }

    /** RL → AbstractTexture.getTextureView()（新管线专用，旧版无此方法返回 null）。 */
    private static Object textureViewOf(ResourceLocation rl) {
        try {
            var tex = net.minecraft.client.Minecraft.getInstance()
                    .getTextureManager().getTexture(rl);
            if (tex == null) {
                return null;
            }
            if (texViewGetter == null) {
                synchronized (CompatRender.class) {
                    if (texViewGetter == null) {
                        texViewGetter = MethodHandles.lookup()
                                .unreflect(tex.getClass().getMethod("getTextureView"));
                    }
                }
            }
            return texViewGetter.invoke(tex);
        } catch (Throwable t) {
            return null;
        }
    }

    // 实体/世界查询 getter 族（改名漂移）

    /** 布尔查询按候选名择路（isInWaterRainOrBubble/isDay/isNight 等）；全缺失返回默认值。 */
    public static boolean boolQuery(Object target, String[] candidateNames, boolean def) {
        for (String n : candidateNames) {
            try {
                return (Boolean) target.getClass().getMethod(n).invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    // Inventory 字段私有化族（≥1.21.8 selected/items/armor）

    /** Inventory.selected（选中快捷栏槽位号）：getter 候选 → 公有字段 → 私有字段。 */
    public static int invSelectedIndex(Object inventory) {
        for (String n : new String[]{"getSelectedSlot", "getSelectedIndex", "getSelectedHotbarSlot"}) {
            try {
                return (Integer) inventory.getClass().getMethod(n).invoke(inventory);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                break;
            }
        }
        try {
            return (Integer) inventory.getClass().getField("selected").get(inventory);
        } catch (NoSuchFieldException priv) {
            try {
                java.lang.reflect.Field f = inventory.getClass().getDeclaredField("selected");
                f.setAccessible(true);
                return (Integer) f.get(inventory);
            } catch (Exception ignored) {
                return 0;
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Inventory.selected 写入。 */
    public static void invSetSelectedIndex(Object inventory, int index) {
        for (String n : new String[]{"setSelectedSlot", "setSelectedIndex", "setSelectedHotbarSlot"}) {
            try {
                inventory.getClass().getMethod(n, int.class).invoke(inventory, index);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                break;
            }
        }
        try {
            java.lang.reflect.Field f = inventory.getClass().getDeclaredField("selected");
            f.setAccessible(true);
            f.setInt(inventory, index);
        } catch (Exception ignored) {
        }
    }

    /** Inventory.items 主背包列表：getItems()（Container 契约）→ 公有字段。 */
    public static Object invItems(Object inventory) {
        try {
            return inventory.getClass().getMethod("getItems").invoke(inventory);
        } catch (NoSuchMethodException legacy) {
            try {
                return inventory.getClass().getField("items").get(inventory);
            } catch (Exception ignored) {
                return java.util.List.of();
            }
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    /** Inventory.armor 盔甲列表：getArmor() → 公有字段 → 私有字段。 */
    public static Object invArmor(Object inventory) {
        try {
            return inventory.getClass().getMethod("getArmor").invoke(inventory);
        } catch (NoSuchMethodException legacy) {
            try {
                return inventory.getClass().getField("armor").get(inventory);
            } catch (NoSuchFieldException priv) {
                try {
                    java.lang.reflect.Field f = inventory.getClass().getDeclaredField("armor");
                    f.setAccessible(true);
                    return f.get(inventory);
                } catch (Exception ignored) {
                    return java.util.List.of();
                }
            } catch (Exception ignored) {
                return java.util.List.of();
            }
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    // NBT 解析（TagParser.parseTag 改名漂移）

    /** TagParser.parseTag(String) 的候选名等价（parseTag/parseCompoundFully/…）；全缺失返回 null。 */
    public static Object parseNbtCompound(String text) {
        for (String name : new String[]{"parseTag", "parseCompoundFully", "parseCompound"}) {
            try {
                return TagParser.class.getMethod(name, String.class).invoke(null, text);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    // RenderTarget 读像素绑定族（≥1.21.6 移除 bindRead/unbindRead）

    /** RenderTarget.bindRead() 的版本安全等价：新版直接绑颜色纹理的 GL id。 */
    public static boolean targetBindRead(Object rt) {
        try {
            rt.getClass().getMethod("bindRead").invoke(rt);
            return true;
        } catch (NoSuchMethodException modern) {
            try {
                Object tex = rt.getClass().getMethod("getColorTexture").invoke(rt);
                Integer glId = textureGlId(tex);
                if (glId == null) {
                    return false;
                }
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, glId);
                return true;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** RenderTarget.unbindRead() 的版本安全等价：新版解绑纹理。 */
    public static void targetUnbindRead(Object rt) {
        try {
            rt.getClass().getMethod("unbindRead").invoke(rt);
            return;
        } catch (NoSuchMethodException modern) {
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);
        } catch (Exception ignored) {
        }
    }

    /** 沿类层级找 int 型 GL 句柄字段（GlTexture.id）。 */
    private static Integer textureGlId(Object tex) {
        Class<?> c = tex.getClass();
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("id");
                f.setAccessible(true);
                return f.getInt(tex);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Entity.load(tag) 的版本安全等价：
     * ≤1.21.4 load(CompoundTag)；≥1.21.6 load(ValueInput) —— 经 TagValueInput.create 包装。
     */
    public static boolean entityLoad(Object entity, net.minecraft.nbt.CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        try {
            entity.getClass().getMethod("load", net.minecraft.nbt.CompoundTag.class).invoke(entity, tag);
            return true;
        } catch (NoSuchMethodException modern) {
            try {
                Class<?> valueInput = resolveClass("net.minecraft.world.level.storage.ValueInput");
                Class<?> tagValueInput = resolveClass("net.minecraft.world.level.storage.TagValueInput");
                Class<?> reporterClz = resolveClass("net.minecraft.util.ProblemReporter");
                Class<?> providerClz = resolveClass("net.minecraft.core.HolderLookup$Provider");
                Object reporter = reporterClz.getField("DISCARDING").get(null);
                Object level = net.minecraft.client.Minecraft.getInstance().level;
                if (level == null) {
                    return false;
                }
                Object access = level.getClass().getMethod("registryAccess").invoke(level);
                Object input = tagValueInput
                        .getMethod("create", reporterClz, providerClz, net.minecraft.nbt.CompoundTag.class)
                        .invoke(null, reporter, access, tag);
                entity.getClass().getMethod("load", valueInput).invoke(entity, input);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    // 版本信息（材质包目录化用）
    /**
     * 当前客户端的资源包格式版本号（pack.mcmeta 的 pack_format）。
     * 反射双路径：WorldVersion.getPackVersion()（新线）/ getDataVersion().getPackVersion()（旧线）。
     * 取不到返回 -1，调用方跳过 mcmeta 生成（注入器直读目录不依赖扫描，功能不受影响）。
     */
    public static int currentPackFormat() {
        // 首选 per-target 桥（直调 SharedConstants），理由同上。
        try {
            com.opendreamcore.client.entity.ItemModelRenderBridge br =
                    com.opendreamcore.client.entity.ItemModelViews.bridge();
            if (br != null) {
                int viaBridge = br.packFormat();
                if (viaBridge > 0) {
                    return viaBridge;
                }
            }
        } catch (Throwable ignored) {
            // 桥失败 → 落到下面的反射兜底路径
        }
        try {
            Class<?> sc = resolveClass("net.minecraft.SharedConstants");
            Object version = sc.getMethod("getCurrentVersion").invoke(null);
            try {
                Object v = version.getClass().getMethod("getPackVersion").invoke(version);
                return v instanceof Number n ? n.intValue() : -1;
            } catch (NoSuchMethodException ignored) {
                Object dv = version.getClass().getMethod("getDataVersion").invoke(version);
                Object v = dv.getClass().getMethod("packFormat").invoke(dv);
                return v instanceof Number n ? n.intValue() : -1;
            }
        } catch (Throwable t) {
            return -1;
        }
    }
/**
     * 投影矩阵获取（版本自适应）。
     * ≤1.21.8：GameRenderer.getProjectionMatrix(float)；26.x 该方法随渲染管线重构移除，
     * 26.x 移除了该方法，返回单位阵占位。
     */
    public static org.joml.Matrix4f projectionMatrix(Object gameRenderer, float fov) {
        try {
            java.lang.reflect.Method m = gameRenderer.getClass().getMethod("getProjectionMatrix", float.class);
            return (org.joml.Matrix4f) m.invoke(gameRenderer, fov);
        } catch (NoSuchMethodException modern) {
            return new org.joml.Matrix4f();
        } catch (Exception e) {
            return new org.joml.Matrix4f();
        }
    }

    /**
     * 当前 PoseStack 类（com.mojang.blaze3d.vertex.PoseStack），不存在返回 null。
     */
    public static Class<?> poseStackClass() {
        try { return Class.forName("com.mojang.blaze3d.vertex.PoseStack"); } catch (ClassNotFoundException e) { return null; }
    }

    /**
     * 当前渲染上下文的 PoseStack 实例（GuiGraphics.pose() 或 GuiGraphics.getPoseStack()），不存在返回 null。
     */
    public static Object currentPoseStack() {
        try {
            Class<?> gg = Class.forName("com.mojang.blaze3d.systems.GuiGraphics");
            Method m = gg.getMethod("pose");
            Object pose = m.invoke(null);
            if (pose != null) return pose;
        } catch (Exception ignored) { /* GuiGraphics.pose() 不存在/失败：换下一候选 */ }
        try {
            Class<?> gg = Class.forName("com.mojang.blaze3d.systems.GuiGraphics");
            Method m = gg.getMethod("getPoseStack");
            return m.invoke(null);
        } catch (Exception ignored) { /* getPoseStack 不存在：返回 null 由调用方兜底 */ }
        return null;
    }

    /**
     * 当前 MatrixStack 类（org.joml.Matrix3x2fStack 或 com.mojang.blaze3d.matrix.MatrixStack），不存在返回 null。
     */
    public static Class<?> matrixStackClass() {
        try { return Class.forName("org.joml.Matrix3x2fStack"); } catch (ClassNotFoundException e) { /* 该版无此类：换下一候选 */ }
        try { return Class.forName("com.mojang.blaze3d.matrix.MatrixStack"); } catch (ClassNotFoundException e) { /* 该版无此类：返回 null */ }
        return null;
    }

    /**
     * 当前渲染上下文的 MatrixStack 实例，不存在返回 null。
     */
    public static Object currentMatrixStack() {
        try {
            Class<?> gg = Class.forName("com.mojang.blaze3d.systems.GuiGraphics");
            Method m = gg.getMethod("matrixStack");
            return m.invoke(null);
        } catch (Exception ignored) { /* matrixStack 不存在：换下一候选 */ }
        try {
            Class<?> gg = Class.forName("com.mojang.blaze3d.systems.GuiGraphics");
            Method m = gg.getMethod("getMatrixStack");
            return m.invoke(null);
        } catch (Exception ignored) { /* getMatrixStack 不存在：返回 null */ }
        return null;
    }
}
