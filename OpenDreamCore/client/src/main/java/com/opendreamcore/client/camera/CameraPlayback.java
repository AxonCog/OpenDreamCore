package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraAccess;
import com.opendreamcore.camera.CameraRule;
import com.opendreamcore.camera.CameraSettings;
import com.opendreamcore.camera.CameraStore;
import com.opendreamcore.camera.CameraTakeover;
import com.opendreamcore.camera.CameraTimeline;
import com.opendreamcore.camera.OdcCameraDriver;
import com.opendreamcore.script.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 运镜播放器（M1.2 最小播放）。
 *
 * <p>做法：造一个「未加入世界」的临时相机实体（盔甲架），把 {@code Minecraft.cameraEntity}
 * 指到它，每客户端 tick 把时间轴采样出的姿态写进去。选这条路是因为它**不碰 mixin**：
 * 1.20.1~26.1.2 的 {@code setCameraEntity(Entity)} / {@code Options.setCameraType} /
 * {@code Entity} 姿态方法签名经 javap 逐版验证完全一致（tools/camera-api-probe/），
 * 直调即可，不用像旧客户端层那样靠按 MC 名反射（Fabric 生产环境按名反射必失）。
 *
 * <p>为什么不直接改玩家相机：M1.4 才上 detached 直接驱动（需要 mixin 注入 {@code Camera.setup}）。
 * 本期的临时实体方案已经能给出「镜头从 A 到 B」的可见效果，且天然与光影共存
 * ——它不接管渲染管线，只用原版相机路径。
 *
 * <p>状态栈与兜底（规范 §4.1：不能把玩家锁死）：
 * <ul>
 *   <li>播放前把 [相机实体, 视角模式, HUD 开关, 玩家隐身] 压栈，停止/自动结束/任何异常路径都弹栈写回；</li>
 *   <li>每 tick 校验「世界对象没换、玩家还在」；断线/切维/退出世界时 MC 自己已重置相机，
 *       此时只清状态、不写回旧世界的对象（写回反而是事故源）；</li>
 *   <li>硬上限 {@value #HARD_CAP_SECONDS} 秒：任何规则都不可能把玩家永久锁在运镜里；</li>
 *   <li>规则被热重载删掉时（播放中 reload）立即收干净恢复。</li>
 * </ul>
 *
 * <p>时间源：默认**帧时钟**（每客户端 tick 累加 20tps 标称步长），不受系统时间跳变影响；
 * 保留 {@link #setUseWallClock(boolean)} 开关（M1.3 对照用）。
 */
public final class CameraPlayback {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore");
    private static final CameraPlayback INSTANCE = new CameraPlayback();

    public static CameraPlayback get() {
        return INSTANCE;
    }

    /** 一客户端 tick 的标称时长：50ms（20tps）。 */
    private static final double SECONDS_PER_TICK = 0.05;
    /** 硬上限：5 分钟。 */
    private static final double HARD_CAP_SECONDS = 300.0;

    /** 播放前的状态备份栈（M1.2）；备份与写回的细节都在 {@link CameraStateStack} 里。 */
    private final CameraStateStack stack = new CameraStateStack();
    private CameraActive active;
    private boolean useWallClock;
    private long lastNanos;

    private CameraPlayback() {
    }

    /** M1.3 对照开关：true 时用系统单调时钟求 delta（默认 false = 帧时钟）。 */
    public void setUseWallClock(boolean use) {
        this.useWallClock = use;
        this.lastNanos = 0L;
    }

    public boolean isPlaying() {
        return active != null;
    }

    public String playingId() {
        return active == null ? null : active.rule.id;
    }

    // ------------------------------------------------------------------ 命令入口

    /** /odc camera play &lt;id&gt;：开始播放。返回给聊天栏的行。 */
    public List<String> play(String id) {
        List<String> out = new ArrayList<>();
        if (id == null || id.isEmpty()) {
            out.add("§c用法: /odc camera play <id>（/odc camera list 看现有规则）");
            return out;
        }
        CameraRule rule = CameraStore.get().byId(id);
        if (rule == null) {
            out.add("§c没有这条运镜规则: " + id + "（/odc camera list 看现有规则）");
            return out;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            out.add("§c还没进入世界，无法播放运镜。");
            return out;
        }
        // 相机是单例资源，嵌套播放无意义：先把上一条收干净（弹栈写回），再起新的
        if (active != null) {
            String prev = active.rule.id;
            restoreNow();
            out.add("§7（已先停止上一条运镜: " + prev + "）");
        }
        startPlayback(mc, rule, out);
        return out;
    }

    /**
     * 决定驱动路线并完成接管/建体/备份/起播（从 play 拆出：反臃肿 R2，单方法 ≤60 行）。
     *
     * <p>接管让位判定（M1.4）：能接管就走 detached 直接驱动——不需要临时实体，
     * 也不会为了取景把玩家的 cameraEntity 换掉；不能接管就如实说明原因并走 M1.2 老路。
     */
    private void startPlayback(Minecraft mc, CameraRule rule, List<String> out) {
        // M1.4：先做接管让位判定——能接管就走 detached 直接驱动（不需要临时实体，
        // 也不会为了取景把玩家的 cameraEntity 换掉）；不能接管就如实说明并走 M1.2 老路
        CameraSettings settings = CameraStore.get().settings();
        CameraAccess access = mainCameraAccess(mc);
        boolean othersOwn = mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
        CameraTakeover.Decision decision = CameraTakeover.decide(
                settings, access != null, othersOwn, CameraTakeover.shaderEnvironment());
        boolean detached = decision == CameraTakeover.Decision.TAKE_OVER;

        Entity body = null;
        if (!detached) {
            body = CameraMcBridge.createBody(mc.level);
            if (body == null) {
                out.add("§c相机实体创建失败：EntityType.ARMOR_STAND.create 没有可用重载。");
                return;
            }
        }
        CameraStateStack.Entry b = stack.push(mc);
        CameraActive a = new CameraActive(rule, body, mc.level, b, detached);
        // M1.6：消帧一次的过渡预设（Camera.过渡）写进刚建好的活动态
        CameraLive.applyNextTransition(a);
        CameraPoseIo.captureCurrentView(mc, a.fromPos, a.fromRot);
        // 直接驱动：上一帧 = 当前帧 = 播放瞬间视角 → 第一帧不会从原点弹过来
        a.prevPos = a.fromPos.clone();
        a.prevRot = a.fromRot.clone();
        a.curPos = a.fromPos.clone();
        a.curRot = a.fromRot.clone();
        active = a;

        if (detached) {
            if (!OdcCameraDriver.get().takeOver(access, this::framePose, rule.id)) {
                // 兜底：takeOver 只在相机/姿态源为空时才会失败，这里把状态清干净再退出
                active = null;
                stack.clear();
                out.add("§c相机接管失败（写入面不可用），已取消本次播放。");
                return;
            }
        } else {
            body.setInvisible(true);
            body.setNoGravity(true);
            CameraPoseIo.applyPose(body, a.fromPos, a.fromRot);
            body.setOldPosAndRot();
            CameraMcBridge.syncOldHeadRot(body);
            mc.setCameraEntity(body);
            out.add("§7（" + CameraTakeover.why(decision) + "，本次按临时实体路线播放）");
        }
        CameraMcBridge.applyPerspective(mc, rule);
        CameraMcBridge.applyHideHud(mc, rule);
        if (a.hideBody) {
            mc.player.setInvisible(true);
        }

        announceStart(a, rule, detached, out);
    }

    /** 起播提示：聊天栏 + 日志（文案是给玩家看的契约，别改措辞）。 */
    private void announceStart(CameraActive a, CameraRule rule, boolean detached, List<String> out) {
        out.add("§e[OpenDreamCore] §f开始运镜 §b" + rule.id + "§f（" + rule.typeLabel()
                + (a.autoStop ? "，§f" + fmt(a.inSeconds + a.pathSeconds) + "s 后自动结束" : "，§f/odc camera stop 结束")
                + (a.inSeconds > 0 ? "，进过渡 " + rule.transitionIn + "tick" : "")
                + "，驱动=" + (detached ? "直接" : "临时实体")
                + "）");
        // java.util.logging 无 {} 占位重载，用拼接/format
        LOGGER.info("运镜开始：" + rule.id + "（" + rule.typeLabel() + "，时长 "
                + fmt(a.inSeconds + a.pathSeconds) + "s，进过渡 " + rule.transitionIn
                + "tick，锁输入 " + rule.lockInput + "，驱动 " + (detached ? "直接" : "临时实体") + "）");
    }

    /** /odc camera stop：立即恢复（不带出过渡——契约要求「立即」）。 */
    public List<String> stop() {
        List<String> out = new ArrayList<>();
        if (active == null) {
            out.add("§e[OpenDreamCore] §f当前没有运镜在播放。");
            return out;
        }
        String id = active.rule.id;
        restoreNow();
        out.add("§e[OpenDreamCore] §f运镜已停止，视角/HUD/输入已恢复：" + id);
        LOGGER.info("运镜停止（手动）：" + id);
        return out;
    }

    /** /odc camera status。 */
    public List<String> status() {
        List<String> out = new ArrayList<>();
        CameraActive a = active;
        if (a == null) {
            out.add("§e[OpenDreamCore] §f当前没有运镜在播放（/odc camera list 看规则）。");
            return out;
        }
        double total = a.inSeconds + a.pathSeconds;
        out.add("§e[OpenDreamCore] §f正在播放 §b" + a.rule.id + "§f（" + a.rule.typeLabel() + "）"
                + " 已播 " + fmt(a.elapsed) + "s"
                + (a.autoStop ? " / 共 " + fmt(total) + "s" : "（手动结束）")
                + (a.outro ? " §7收尾中" : ""));
        out.add("§7  锁输入=" + a.rule.lockInput + " 假人=" + a.hideBody
                + " 时间源=" + (useWallClock ? "挂钟" : "帧时钟")
                + " 驱动=" + (a.detached ? "直接" : "临时实体"));
        return out;
    }

    /** 诊断单行（Diagnostics 用）。 */
    public String statusLine() {
        CameraActive a = active;
        if (a == null) {
            return "§7空闲";
        }
        return "§a播放 " + a.rule.id + " §7" + fmt(a.elapsed) + "s"
                + (a.autoStop ? "/" + fmt(a.inSeconds + a.pathSeconds) + "s" : "")
                + (a.detached ? " §7直驱" : " §7实体");
    }

    // ------------------------------------------------------------------ 每 tick 推进

    /**
     * 每客户端 tick 推进一帧（ClientController.tickBindings 调用）。
     * 任何异常都不允许外泄——播放器炸了也必须把玩家相机还回去。
     */
    public void tick() {
        // 触发引擎轮询（按键/区域/事件三路；命令路在 OdcCommands）。必须在播放空检查之前——
        // 没在播放时才是触发器该干活的时机。内部全吞异常。
        com.opendreamcore.client.camera.CameraTriggers.tick();
        CameraActive a = active;
        if (a == null) {
            return;
        }
        try {
            tickActive(a);
        } catch (Throwable t) {
            LOGGER.warning("运镜推进异常，已强制恢复相机: " + t);
            try {
                restoreNow();
            } catch (Throwable ignored) {
                active = null;
                stack.clear();
            }
        }
    }

    private void tickActive(CameraActive a) {
        Minecraft mc = Minecraft.getInstance();
        if (handleInterruptions(mc, a)) {
            return;
        }
        a.elapsed += deltaSeconds();
        tickMotion(mc, a);
    }

    /**
     * 中断检测（M1.2 契约：不能把玩家锁死）：
     * 世界卸载/切换/断线 → MC 已自己重置相机，写回旧对象才是事故，只清状态；
     * 规则被热重载删掉 → 立刻收干净并恢复，别让镜头挂在一条不存在的运镜上。
     *
     * @return true = 本 tick 已被中断处理，调用方直接结束
     */
    private boolean handleInterruptions(Minecraft mc, CameraActive a) {
        if (mc == null) {
            dropState();
            return true;
        }
        // 世界卸载/切换/断线：MC 已经自己重置了相机，写回旧对象才是事故，只清状态
        if (mc.level == null || mc.player == null || mc.level != a.level) {
            LOGGER.info("运镜中断：世界已切换或退出（" + a.rule.id + "）");
            dropState();
            return true;
        }
        // 规则被热重载删掉了：立刻收干净，别让镜头挂在一个不存在的运镜上
        if (CameraStore.get().byId(a.rule.id) == null) {
            restoreNow();
            CameraMcBridge.chat("§e[OpenDreamCore] §f运镜 " + a.rule.id + " 已被重载移除，已恢复相机。");
            return true;
        }
        return false;
    }

    /** 时钟推进 + 出过渡/硬上限判定 + 两条驱动路线的分发。 */
    private void tickMotion(Minecraft mc, CameraActive a) {

        if (a.outro) {
            a.outroElapsed += SECONDS_PER_TICK;
            if (a.outroElapsed >= a.outSeconds) {
                finish(a, "路径播放完毕");
                return;
            }
        } else if (a.autoStop && a.elapsed >= a.inSeconds + a.pathSeconds) {
            if (a.outSeconds <= 0.0) {
                finish(a, "路径播放完毕");
                return;
            }
            a.outro = true;
            a.outroElapsed = 0.0;
            a.outroFromPos = new double[3];
            a.outroFromRot = new double[2];
            computePose(a, a.outroFromPos, a.outroFromRot);
        }

        if (a.elapsed > HARD_CAP_SECONDS) {
            finish(a, "超过 " + (int) HARD_CAP_SECONDS + "s 硬上限，强制恢复");
            return;
        }

        if (a.detached) {
            tickDetached(mc, a);
            return;
        }

        double[] pos = new double[3];
        double[] rot = new double[2];
        computePose(a, pos, rot);
        CameraFx.applyShake(a.rule, a.elapsed, pos, rot);
        CameraFx.publishFov(mc, a);
        CameraLive.applyPose(a, pos, rot);
        CameraEventFire.tickFired(mc, a, a.elapsed - SECONDS_PER_TICK, a.elapsed);
        Entity body = a.body;
        // 顺序要紧：先把「上一 tick 的姿态」写进旧值字段，再写新姿态。实体没加入世界、不会被 tick，
        // 原版的这两步（含 yHeadRotO，setOldPosAndRot 管不到）都得我们自己来——
        // 否则渲染插值从 0 起步，20tick 的更新在 144fps 下就是一顿一顿的（见 CameraMcBridge 注释）。
        body.setOldPosAndRot();
        CameraMcBridge.syncOldHeadRot(body);
        CameraPoseIo.applyPose(body, pos, rot);

        if (a.pinPlayer) {
            CameraPoseIo.pinPlayer(mc, a);
        }
    }

    /**
     * 直接驱动 tick：不动实体，只把这一 tick 的姿态采样好，等渲染帧按 partialTick 插值写相机。
     * 顺序与临时实体路线同构：先把上一 tick 的姿态挪到 prev，再算这一 tick 的 cur。
     */
    private void tickDetached(Minecraft mc, CameraActive a) {
        a.prevPos = a.curPos;
        a.prevRot = a.curRot;
        double[] nPos = new double[3];
        double[] nRot = new double[2];
        computePose(a, nPos, nRot);
        CameraFx.applyShake(a.rule, a.elapsed, nPos, nRot);
        a.curPos = nPos;
        a.curRot = nRot;
        CameraFx.publishFov(mc, a);
        CameraLive.applyPose(a, nPos, nRot);
        CameraEventFire.tickFired(mc, a, a.elapsed - SECONDS_PER_TICK, a.elapsed);
        if (a.pinPlayer) {
            CameraPoseIo.pinPlayer(mc, a);
        }
    }

    /** 帧时钟：每 tick 固定步长。挂钟开关打开时用单调时钟求真实 delta（下限 1ms，防除零）。 */
    private double deltaSeconds() {
        if (!useWallClock) {
            return SECONDS_PER_TICK;
        }
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            return SECONDS_PER_TICK;
        }
        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (dt <= 0.0 || dt > 1.0) {
            return SECONDS_PER_TICK;
        }
        return dt;
    }

    // ------------------------------------------------------------------ 姿态计算

    /**
     * 出姿态：进过渡（当前视角 → 目标起点）→ 出过渡（末帧 → 播放前视角）→ 主时间轴。
     * 主时间轴的路径时间 = elapsed - 进过渡时长，保证过渡不占用 duration。
     */
    private void computePose(CameraActive a, double[] outPos, double[] outRot) {
        if (a.outro && a.outroFromPos != null) {
            a.curFov = null;
            double u = a.outSeconds <= 0 ? 1.0 : clamp01(a.outroElapsed / a.outSeconds);
            u = Easing.apply(Easing.Type.SINE_IN_OUT, u);
            for (int i = 0; i < 3; i++) {
                outPos[i] = lerp(a.outroFromPos[i], a.fromPos[i], u);
            }
            for (int i = 0; i < 2; i++) {
                outRot[i] = lerpAngle(a.outroFromRot[i], a.fromRot[i], u);
            }
            return;
        }

        CameraTimeline.Pose target = CameraTimeline.sample(a.rule, Math.max(0.0, a.elapsed - a.inSeconds));
        if (target == null) {
            a.curFov = null;
            System.arraycopy(a.fromPos, 0, outPos, 0, 3);
            System.arraycopy(a.fromRot, 0, outRot, 0, 2);
            return;
        }
        a.curFov = target.fov;
        double[] tPos = target.hasPos() ? target.pos : a.fromPos;
        double[] tRot = target.hasRot() ? target.rot : a.fromRot;

        if (a.inSeconds > 0 && a.elapsed < a.inSeconds) {
            double u = Easing.apply(Easing.Type.SINE_IN_OUT, clamp01(a.elapsed / a.inSeconds));
            for (int i = 0; i < 3; i++) {
                outPos[i] = lerp(a.fromPos[i], tPos[i], u);
            }
            for (int i = 0; i < 2; i++) {
                outRot[i] = lerpAngle(a.fromRot[i], tRot[i], u);
            }
            return;
        }
        System.arraycopy(tPos, 0, outPos, 0, 3);
        System.arraycopy(tRot, 0, outRot, 0, 2);
    }

        // ------------------------------------------------------------------ 备份 / 恢复

    /**
     * 弹栈写回（备份/写回的细节都在 {@link CameraStateStack}）：世界已换时只清状态，
     * 结不把旧世界的对象写回去；写回失败也只记日志，不阻塞后续播放。
     */
    private void restoreNow() {
        CameraActive a = active;
        active = null;
        resetClock();
        // M1.4：直接驱动必须先放手——把 Camera.detached 复位，否则退出播放后原版会一直
        // 以为「相机是自由的」，玩家视角再也跟不上身体。没接管时这是空操作。
        OdcCameraDriver.get().release();
        CameraFx.reset();
        CameraStateStack.Result r = stack.restore(Minecraft.getInstance(), a == null ? null : a.level);
        if (r == CameraStateStack.Result.SKIPPED_WORLD_CHANGED && a != null) {
            LOGGER.info("运镜恢复跳过写回：世界已切换（" + a.rule.id + "）");
        }
    }

    /** 自动结束/硬上限：恢复 + 聊天栏 + 日志。 */
    private void finish(CameraActive a, String why) {
        String id = a.rule.id;
        restoreNow();
        CameraMcBridge.chat("§e[OpenDreamCore] §f运镜结束（" + why + "）：" + id);
        LOGGER.info("运镜结束（" + why + "）：" + id);
    }

    /** 只清运行态，不触碰任何 MC 对象（世界已换或 MC 不可用时的唯一安全出口）。 */
    private void dropState() {
        active = null;
        stack.clear();
        resetClock();
        // 世界已卸载：相机壳子可能已失效，release 内部吞异常，不会把这里搞崩
        OdcCameraDriver.get().release();
        CameraFx.reset();
    }

    private void resetClock() {
        lastNanos = 0L;
    }

    // ------------------------------------------------------------------ M1.4 直接驱动

    /**
     * 直接驱动：本渲染帧该给相机的姿态。
     *
     * <p>tick 侧每次推进都把「上一 tick 的姿态」与「这一 tick 的姿态」留在 CameraActive 里，
     * 这里按渲染帧的 partialTick 在两者之间插值——与临时实体路线把旧值写进
     * {@code setOldPosAndRot}/{@code yHeadRotO} 是同一件事的两种实现。
     *
     * @return {x,y,z,yRot,xRot}；null = 本帧不该我们驱动（驱动会原样跳过，一个字段都不写）
     */
    private double[] framePose(float partialTick) {
        CameraActive a = active;
        if (a == null || !a.detached || a.curPos == null) {
            return null;
        }
        double u = partialTick < 0.0f ? 0.0 : (partialTick > 1.0f ? 1.0 : partialTick);
        double[] p = new double[5];
        for (int i = 0; i < 3; i++) {
            p[i] = lerp(a.prevPos[i], a.curPos[i], u);
        }
        for (int i = 0; i < 2; i++) {
            p[3 + i] = lerpAngle(a.prevRot[i], a.curRot[i], u);
        }
        return p;
    }

    /** 取原版主相机的写入面（M1.4）。混入没生效、或 Camera 被别的模组换掉时返回 null。 */
    private static CameraAccess mainCameraAccess(Minecraft mc) {
        try {
            Object cam = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
            return cam instanceof CameraAccess ca ? ca : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 杂项

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double lerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    /** 过渡用最短角插值（与 CameraTimeline 内一致）。 */
    private static double lerpAngle(double a, double b, double u) {
        double d = ((b - a) % 360.0 + 540.0) % 360.0 - 180.0;
        return a + d * u;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
