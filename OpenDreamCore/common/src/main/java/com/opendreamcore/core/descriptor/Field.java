package com.opendreamcore.core.descriptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 一个规则字段的声明（写一次，校验 / 模板 / 文档三处生成）。
 *
 * <p>保留策略是 {@code RUNTIME}：字段校验发生在**加载与热重载**（进服一次、reload 一次），
 * 不在每帧热路径上，所以这里允许一次性反射读注解。将来生成器把它烘成常量校验代码后可降到 SOURCE。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Field {

    /** 中文说明：会原样写进默认 YAML 模板的注释里。 */
    String desc();

    /** 枚举取值；非空时值必须落在其中。 */
    String[] values() default {};

    /** 默认值（YAML 字面量形式，仅用于模板展示）。 */
    String def() default "";

    /** 数组长度约束；0 表示不限制。 */
    int len() default 0;

    /** 数值下界（对数值标量与数值数组逐元素生效）。 */
    double min() default Double.NEGATIVE_INFINITY;

    /** 数值上界（对数值标量与数值数组逐元素生效）。 */
    double max() default Double.POSITIVE_INFINITY;

    /** 可选字段：缺失时不报错。 */
    boolean optional() default false;

    /** 是否允许占位符（如 %player%）；默认允许。 */
    boolean placeholder() default true;
}
