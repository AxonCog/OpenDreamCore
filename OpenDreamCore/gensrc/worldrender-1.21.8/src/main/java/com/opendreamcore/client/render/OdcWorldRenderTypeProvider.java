package com.opendreamcore.client.render;

import com.opendreamcore.mixin.MixinCompositeStateAccessor;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.21.8 的世界语义渲染类型提供者：按贴图造出可被光影正确归类的渲染类型。
 *
 * <p>这一代的做法是把「自建管线 + 一张贴图」组装成渲染类型。状态集只设贴图一项，不设光照、不设
 * 叠加：面板因此不吃世界光照，也不吃受伤/药水染色（它是信息层，不是实体）。深度测试、面剔除、
 * 混合与深度写入全部由 {@link OdcWorldRenderPipelines#WORLD_TEXTURED} 那条管线声明，状态集里
 * 不再重复表达——这正是 1.21.6 之后渲染体系的分工方式。
 *
 * <p>两处够不着的入口各走一条路：状态集建造者的 setter 是 protected，用访问器 mixin 接出来；
 * 渲染类型的构造入口连返回类型都是包内可见，访问器写不出来，走 {@link OdcRenderTypeFactory}
 * 的反射句柄。渲染类型一旦建成，采样器就钉死在它绑定的那张贴图上、无法改绑，因此按贴图缓存：
 * 世界里的面板与图标数量有限，缓存既避免每帧重建，也保证同一张贴图只编译一份绘制状态。
 *
 * <p>纯色几何（描边、分隔线）这里不接管：它没有 UV，套贴图管线会因顶点元素不匹配而画不出东西，
 * 交给共享层继续走原有上传路径。
 */
public final class OdcWorldRenderTypeProvider implements WorldRenderTypeProvider {

    /** 缓存上限：世界几何贴图数量有限，超了整体让位重建，避免长期占用。 */
    private static final int CACHE_LIMIT = 256;

    private final Map<ResourceLocation, RenderType> cache = new ConcurrentHashMap<>();
    /**
     * 穿透变体（不测深度）的缓存：与常规变体分开存。
     *
     * <p>两类渲染类型用的是两条不同管线，而渲染类型一旦建成、采样器就钉死在那张贴图上，
     * 所以同一张贴图需要两套实例。混在一个 map 里就会出现「先画穿透、再画常规时拿到穿透那份」
     * 的串味，面板于是要么一直穿墙、要么一直不穿墙。
     */
    private final Map<ResourceLocation, RenderType> cacheNoDepth = new ConcurrentHashMap<>();

    @Override
    public Object resolve(boolean textured, boolean seeThrough, Object mode, Object texture) {
        if (!textured || !(texture instanceof ResourceLocation rl)) {
            return null;
        }
        Map<ResourceLocation, RenderType> target = seeThrough ? cacheNoDepth : cache;
        RenderType cached = target.get(rl);
        if (cached != null) {
            return cached;
        }
        try {
            RenderType type = build(rl, seeThrough);
            if (type == null) {
                return null;
            }
            if (target.size() >= CACHE_LIMIT) {
                target.clear();
            }
            target.put(rl, type);
            return type;
        } catch (Throwable ignored) {
            // 构造失败（版本差异 / 状态异常）：静默回退原有绘制路径，绝不把异常带进世界渲染
            return null;
        }
    }

    /** 组装渲染类型：只设贴图态，其余语义全部来自自建管线。 */
    private static RenderType build(ResourceLocation rl, boolean seeThrough) {
        // 建造者的 setter 是 protected：经访问器接出来（返回类型与参数类型在这一代都是公开的）。
        // 访问器方法返回的是原版建造者类型而非本接口，所以不能链式调用，分两句写到同一个对象上。
        MixinCompositeStateAccessor builder = (MixinCompositeStateAccessor)
                (Object) RenderType.CompositeState.builder();
        // 不 mipmap：面板贴图多为 UI 素材，放大会糊，mipmap 反而在缩放时发暗
        builder.opendreamcore$setTextureState(new RenderStateShard.TextureStateShard(rl, false));
        // false = 不参与方块描边（面板不是方块）
        RenderType.CompositeState composite = builder.opendreamcore$createCompositeState(false);
        // 渲染类型的构造入口是包内可见，且它的返回类型（包内的复合渲染类型）同样是包内可见——
        // 连访问器接口都写不出那个类型，所以这里经反射句柄调用，返回类型按公开父类接。
        return OdcRenderTypeFactory.createWorldTextured(
                nameFor(rl, seeThrough),
                RenderType.BIG_BUFFER_SIZE,
                seeThrough ? OdcWorldRenderPipelines.WORLD_TEXTURED_NO_DEPTH
                        : OdcWorldRenderPipelines.WORLD_TEXTURED,
                composite);
    }

    /** 渲染类型名（用于调试与性能分析面板，稳定且可读）。 */
    private static String nameFor(ResourceLocation rl, boolean seeThrough) {
        return "opendreamcore_world_" + rl.getNamespace() + "_" + rl.getPath()
                .replace('/', '_').replace('.', '_') + (seeThrough ? "_no_depth" : "");
    }
}
