package com.opendreamcore.core.annotations;

import com.opendreamcore.core.descriptor.Side;
import com.opendreamcore.core.descriptor.Sync;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统描述符：新增一个配置系统 = 只写这一个类（外加一份 RuleSchema）。
 *
 * <p>框架据此自动完成：加载 {@code <dataRoot>/visual/<Id首字母大写>.yml}、按 schema 校验并定位到行、
 * 生成默认模板、按 {@link Sync} 下发、注册脚本命名空间（三语名）、热重载时只回调
 * {@code apply(id, rule)}、生成文档字段表。
 *
 * <p>保留策略 {@code SOURCE}：运行期读的是索引，不是注解（零扫描）。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface OdcSystem {

    /** 系统 id（小写英文）；同时决定数据文件名，如 camera → {@code visual/Camera.yml}。 */
    String id();

    /** 脚本命名空间中文名，如 相机。 */
    String displayCn();

    /** 脚本命名空间英文名，如 Camera。 */
    String displayEn();

    /** 脚本命名空间下划线名（YAML / 命令用），如 camera。 */
    String displaySnake();

    /** 生效侧。 */
    Side side() default Side.BOTH;

    /** 下发方式。 */
    Sync sync() default Sync.FULL;

    /** 是否支持 /odc reload 热重载（false 表示只在首次加载生效，改配置必须重启）。 */
    boolean hotReload() default true;

    /** 字段声明类（实现 RuleSchema），用于校验 / 模板 / 文档。 */
    Class<?> schema();
}
