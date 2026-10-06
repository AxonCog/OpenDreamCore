package com.opendreamcore.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 能力实现登记：标注在能力实现类上，值是被实现的接口。
 *
 * <p>索引生成器据此写出 CAPABILITY 条目；各平台/代际的 bootstrap 只把自己那份实现
 * 注册进 {@link com.opendreamcore.core.capability.Capabilities}，消费方查不到就降级，
 * 不需要在任何业务代码里写「哪个版本有没有这个能力」。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface OdcCapability {

    /** 被实现的能力接口。 */
    Class<?> value();
}
