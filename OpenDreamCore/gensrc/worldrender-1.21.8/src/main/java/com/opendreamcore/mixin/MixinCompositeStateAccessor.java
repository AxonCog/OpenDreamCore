package com.opendreamcore.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.8 渲染类型状态集的建造入口。
 *
 * <p>状态集建造者的 setter 与收尾方法都是 protected，且该类与我们并包不在一起，够不着。
 * 这里用访问器把它接出来：返回类型与参数类型在这一代都是公开类型，所以访问器接口能如实声明，
 * 调用点也是编译期检查的。</p>
 *
 * <p>只接两个方法：设贴图态，以及收尾。光照态与叠加态不必设——建造者留空时，状态集构造器会把
 * 空槽填成空操作，世界面板本来就既不吃世界光照也不吃受伤/药水叠加，正好是留空的行为。</p>
 */
@Mixin(RenderType.CompositeState.CompositeStateBuilder.class)
public interface MixinCompositeStateAccessor {

    /** 设贴图态（返回建造者自身，可继续链式）。 */
    @Invoker("setTextureState")
    RenderType.CompositeState.CompositeStateBuilder opendreamcore$setTextureState(
            RenderStateShard.EmptyTextureStateShard textureState);

    /** 收尾成全状态集；false = 不参与方块描边（面板不是方块）。 */
    @Invoker("createCompositeState")
    RenderType.CompositeState opendreamcore$createCompositeState(boolean affectsOutline);
}
