/**
 * 相机子系统（0.2.x 新架构首个落地样例，M1.1 起）。
 *
 * 分层约定（机检 R5/R6 管辖）：本包只放版本无关的规则 IR（{@link com.opendreamcore.camera.CameraRule}）
 * 与客户端规则仓库（{@link com.opendreamcore.camera.CameraStore}），
 * <b>禁止 import net.minecraft / com.mojang</b>；MC 触达走 per-target 桥
 * （targets 各平台的 OdcCameraBridgeImpl）与客户端层（ClientController / OdcCommands）。
 *
 * 数据流：服务端权威 visual/Camera.yml → VisualRulesSync 全量下发 → CameraStore.apply（整体覆盖）；
 * 单机路径：gameDir/OpenDreamCore/visual/Camera.yml → /codc reload → 同一 CameraStore.apply。
 */
package com.opendreamcore.camera;
