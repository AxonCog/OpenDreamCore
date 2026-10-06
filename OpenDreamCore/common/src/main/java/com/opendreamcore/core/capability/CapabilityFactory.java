package com.opendreamcore.core.capability;

/** 能力工厂：给一个持有者（玩家 / 实体 / 世界 / 连接），造出能力实现；没有这个能力就返回 null。 */
public interface CapabilityFactory<T> {

    /** 返回 null 表示「该持有者没有这个能力」，消费方据此降级。 */
    T create(Object holder);
}
