/**
 * 相机子系统的**客户端播放层**（M1.2 起）。
 *
 * <p>分层：规则 IR 与规则仓库在版本无关的
 * {@link com.opendreamcore.camera.CameraRule}/{@link com.opendreamcore.camera.CameraStore}
 * （common 层，禁止 MC 依赖）；本包是接触层，允许直接摸 MC 客户端 API，
 * 职责只有一件——把时间轴采样出的姿态落到游戏相机上。
 *
 * <p>为什么本包可以直接 {@code import net.minecraft.*}：{@code client/} 是双平台共享源码树，
 * 构建期按各目标 mojmap 原样复制编译（见 {@code client/README.md}）。
 * 因此这里**只允许直调「6 个 MC 版本签名完全一致」的成员**——写代码前用
 * {@code tools/camera-api-probe/} 逐版 javap 验证过；签名有漂移的一律走
 * {@link com.opendreamcore.client.CompatRender#invokeByShape}（例如
 * {@code EntityType.create} 在 1.21.4 起由单参变双参）。
 * 按 MC 名反射在 Fabric 生产环境必失（映射不重映射反射字符串），这条线不能破。
 *
 * <p>入口：{@link com.opendreamcore.client.camera.CameraPlayback}（单例）。
 * 驱动点：{@code ClientController.tickBindings()} 每客户端 tick 调一次。
 */
package com.opendreamcore.client.camera;
