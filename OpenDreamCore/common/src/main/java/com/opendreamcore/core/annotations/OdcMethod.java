package com.opendreamcore.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 脚本方法：标注在**静态方法**上，方法签名必须是
 * {@code (ScriptContext ctx, Object[] args) → Object}。
 *
 * <p>三种名字（中文 / 英文 / 下划线）由索引生成器写进 {@code odc-services.idx}，
 * 运行期装进 {@link com.opendreamcore.core.script.NamespaceRegistry}，因此
 * {@code 相机.播放(...)}、{@code Camera.play(...)}、{@code camera.play(...)} 是同一件事。
 *
 * <p>参数提示 {@link #args()} 只用于文档与报错信息（不参与校验）。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface OdcMethod {

    /** 命名空间（与 {@link OdcSystem#displayCn()} 或 displayEn/displaySnake 之一一致）。 */
    String namespace();

    /** 中文方法名。 */
    String cn();

    /** 英文方法名。 */
    String en();

    /** 下划线方法名。 */
    String snake();

    /** 参数名列表（文档 / 报错用，可含「可选参数...」这类说明项）。 */
    String[] args() default {};

    /** 一句话说明。 */
    String desc() default "";
}
