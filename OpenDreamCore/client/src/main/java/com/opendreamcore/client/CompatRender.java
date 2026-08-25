package com.opendreamcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
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
 *   - setColorShader()：存在 getter 则等价调用；缺失（≥1.21.2）静默跳过
 *     （GuiGraphics.fill 系调用由新管线自动处理，不受影响）
 *   - blit(...)：运行时探测一次 GuiGraphics 的 11 参 blit 重载走对应分支，
 *     MethodHandle 缓存后热路径开销可忽略
 */
public final class CompatRender {

    // ---- ≥1.21.6 移除的管线开关：存在则调用，缺失静默跳过（新管线自动处理）----
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
        } catch (Exception ignored) {
        }
    }

    /** NativeImage 写像素：候选名 setPixelRGBA / setPixelABGR / setPixel 逐一试。 */
    public static void nativeSetPixel(Object image, int x, int y, int packed) {
        for (String name : new String[]{"setPixelRGBA", "setPixelABGR", "setPixel"}) {
            try {
                Method m = image.getClass().getMethod(name, int.class, int.class, int.class);
                m.invoke(image, x, y, packed);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                return;
            }
        }
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
     * 1.21.1 返回 Item；≥1.21.2 返回 Optional&lt;Reference&lt;Item&gt;&gt;——统一解包为 Item 或 null。
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

    // ================= 反射方法解析（Fabric 生产环境方法名是 intermediary）=================
    // 名称直查失败时按参数形状兜底：dev(mojmap) 与 NeoForge 生产(mojmap 运行时)名称直查命中；
    // Fabric 生产按名必失，靠唯一签名兜底。结果缓存，热路径零开销。

    private static final java.util.concurrent.ConcurrentHashMap<String, Method> METHOD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 名称优先、参数形状兜底的方法解析。
     * 形状匹配规则：参数个数一致且每个方法参数类型可赋值调用方给出的类型；
     * 跳过 Object 声明的公有方法（排除 getClass/hashCode 等零参干扰）。
     */
    static Method resolveMethod(Class<?> owner, String name, Class<?>... types) {
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
            found = owner.getMethod(name, types);
        } catch (NoSuchMethodException nameMiss) {
            outer:
            for (Method m : owner.getMethods()) {
                if (m.getDeclaringClass() == Object.class
                        || m.getParameterCount() != types.length) {
                    continue;
                }
                for (int i = 0; i < types.length; i++) {
                    if (!box(m.getParameterTypes()[i]).isAssignableFrom(box(types[i]))) {
                        continue outer;
                    }
                }
                found = m;
                break;
            }
        }
        if (found != null) {
            METHOD_CACHE.put(k, found);
        }
        return found;
    }

    private static volatile Boolean fabricMappingsProbed;
    private static volatile Object fabricResolver;

    /**
     * 类名映射（仅 Fabric Loader 存在时生效）：生产环境 com.mojang.* 同样是 intermediary 名，
     * Class.forName 需要映射后的名字；dev 与 NeoForge 原样返回。
     */
    static String mapClassName(String internalName) {
        try {
            if (fabricMappingsProbed == null) {
                synchronized (CompatRender.class) {
                    if (fabricMappingsProbed == null) {
                        Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
                        Object loader = fl.getMethod("getInstance").invoke(null);
                        fabricResolver = fl.getMethod("getMappingResolver").invoke(loader);
                        fabricMappingsProbed = Boolean.TRUE;
                    }
                }
            }
            if (fabricResolver != null) {
                return (String) fabricResolver.getClass()
                        .getMethod("mapClassName", String.class)
                        .invoke(fabricResolver, internalName);
            }
        } catch (Throwable t) {
            synchronized (CompatRender.class) {
                fabricResolver = null;
                fabricMappingsProbed = Boolean.TRUE;
            }
        }
        return internalName;
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

    // ============ 物品/注册表信息族（1.20.1 ↔ 1.20.5+ 组件化重构） ============

    /**
     * 手持物品 tooltip 行：≥1.20.5 getTooltipLines(Item.TooltipContext,Player,Flag)；
     * ≤1.20.4 getTooltip(Player,Flag)。反射双路。
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> tooltipLines(Object stack, Object level, Object player, Object flag) {
        try {
            Class<?> tc = Class.forName("net.minecraft.world.item.Item$TooltipContext");
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
        try {
            Class<?> dc = Class.forName("net.minecraft.core.component.DataComponents");
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
    public static ResourceLocation rl(String ns, String path) {
        try {
            return (ResourceLocation) ResourceLocation.class
                    .getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, ns, path);
        } catch (NoSuchMethodException legacy) {
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

    // ================= 顶点立即模式（Tesselator 方言吸收） =================

    /** 探测结果：Tesselator 有 begin(Mode,VertexFormat) = ≥1.20.2 新式；否则走 getBuilder().begin。 */
    private static volatile Boolean modernBegin;
    static boolean modernBegin() { return Boolean.TRUE.equals(modernBegin); }

    /**
     * 版本安全 begin：统一返回 CompatBuffer 自 fluent 包装。
     * 现代 Tesselator.begin(Mode,VertexFormat)；1.20.1 getBuilder().begin(Mode,VertexFormat)。
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
        return new CompatBuffer(mode, format);
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
                        Class<?> rt = Class.forName("net.minecraft.client.renderer.RenderType");
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

    // ================= GUI pose 栈方言（≥1.21.6 Matrix3x2fStack 替代 PoseStack） =================
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

    // ================= DynamicTexture 构造族（≥1.21.8 需 Supplier<String> 标签参） =================

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

    // ================= RenderSystem 纹理/染色族（世界 billboard 用） =================

    /**
     * RenderSystem.setShaderColor 的版本安全等价：缺失（≥1.21.6 移除）静默跳过。
     * 注意：新管线染色语义待运行冒烟校验，短期表现为透明度渐变降级。
     */
    public static void shaderColor(float r, float g, float b, float a) {
        rsToggle("setShaderColor", new Class<?>[]{float.class, float.class, float.class, float.class},
                new Object[]{r, g, b, a});
    }

    private static volatile MethodHandle texViewGetter;

    /**
     * RenderSystem.setShaderTexture(sampler, RL) 的版本安全等价：
     * 旧版第二参 ResourceLocation 直调；新版需 GpuTextureView —— 从 TextureManager 解析后取视图。
     */
    public static void setShaderTexture(int sampler, ResourceLocation rl) {
        try {
            for (Method m : RenderSystem.class.getMethods()) {
                if ("setShaderTexture".equals(m.getName()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == ResourceLocation.class) {
                    m.invoke(null, sampler, rl);
                    return;
                }
            }
            Object view = textureViewOf(rl);
            if (view != null) {
                for (Method m : RenderSystem.class.getMethods()) {
                    if ("setShaderTexture".equals(m.getName()) && m.getParameterCount() == 2
                            && m.getParameterTypes()[1].isInstance(view)) {
                        m.invoke(null, sampler, view);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
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

    // ================= 实体/世界查询 getter 族（改名漂移） =================

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

    // ================= Inventory 字段私有化族（≥1.21.8 selected/items/armor） =================

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

    // ================= NBT 解析（TagParser.parseTag 改名漂移） =================

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

    // ================= RenderTarget 读像素绑定族（≥1.21.6 移除 bindRead/unbindRead） =================

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
                Class<?> valueInput = Class.forName("net.minecraft.world.level.storage.ValueInput");
                Class<?> tagValueInput = Class.forName("net.minecraft.world.level.storage.TagValueInput");
                Class<?> reporterClz = Class.forName("net.minecraft.util.ProblemReporter");
                Class<?> providerClz = Class.forName("net.minecraft.core.HolderLookup$Provider");
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

    // ================= 版本信息（材质包目录化用）=================

    /**
     * 当前客户端的资源包格式版本号（pack.mcmeta 的 pack_format）。
     * 反射双路径：WorldVersion.getPackVersion()（新线）/ getDataVersion().getPackVersion()（旧线）。
     * 取不到返回 -1，调用方跳过 mcmeta 生成（注入器直读目录不依赖扫描，功能不受影响）。
     */
    public static int currentPackFormat() {
        try {
            Class<?> sc = Class.forName("net.minecraft.SharedConstants");
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
}
