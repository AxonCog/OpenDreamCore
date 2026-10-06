package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.logging.Logger;

/**
 * 时间轴事件点执行（M1.6）：sound / particle / script 按 (prev, now] 窗口触发，
 * t≤0 的条目在首 tick 一次性补发；animation 解析入库但只记日志（接口预留，M1.x 后续接）。
 * 单条失败只记日志，绝不拖垮播放循环。
 */
final class CameraEventFire {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore");

    private CameraEventFire() {
    }

    /** tickMotion 每 tick 调一次；prev = 上一 tick 已推进到的时间。 */
    static void tickFired(Minecraft mc, CameraActive a, double prev, double now) {
        if (!a.startupFired) {
            a.startupFired = true;
            for (CameraEvent e : a.rule.events) {
                if (e.t <= 0.0) {
                    fire(mc, a, e);
                }
            }
        }
        for (CameraEvent e : CameraEvent.due(a.rule.events, prev, now)) {
            fire(mc, a, e);
        }
    }

    private static void fire(Minecraft mc, CameraActive a, CameraEvent e) {
        try {
            switch (e.kind) {
                case SOUND:
                    Object so = regGet(BuiltInRegistries.SOUND_EVENT, rl(e.value));
                    SoundEvent se = so instanceof SoundEvent snd ? snd : null;
                    if (se != null) {
                        mc.getSoundManager().play(
                                SimpleSoundInstance.forUI(se, (float) e.pitch, (float) e.volume));
                    } else {
                        LOGGER.info("[运镜事件] 没这个音效：" + e.value);
                    }
                    break;
                case PARTICLE:
                    if (mc.level != null && a.curPos != null) {
                        // 原版把简单粒子（flame/heart…）直接以 SimpleParticleType 注册，
                        // 它同时就是 ParticleOptions，可以直接下；带参粒子（dust/item…）
                        // 光凭名字构不出参数，记日志跳过（后续按需扩）。
                        net.minecraft.core.particles.ParticleOptions po =
                                regGet(BuiltInRegistries.PARTICLE_TYPE, rl(e.value))
                                        instanceof net.minecraft.core.particles.SimpleParticleType spt
                                        ? spt : null;
                        if (po != null) {
                            double[] off = e.offset != null ? e.offset : new double[]{0.0, 0.0, 0.0};
                            mc.level.addParticle(po,
                                    a.curPos[0] + off[0], a.curPos[1] + off[1], a.curPos[2] + off[2],
                                    0.0, 0.0, 0.0);
                        } else {
                            LOGGER.info("[运镜事件] 粒子不存在或需要参数（暂不支持）：" + e.value);
                        }
                    }
                    break;
                case SCRIPT:
                    com.opendreamcore.client.ClientController cc = com.opendreamcore.client.ClientController.get();
                    com.opendreamcore.page.Page page = cc.currentPage();
                    if (page != null) {
                        cc.runLocalAction(page, e.value);
                    } else {
                        LOGGER.info("[运镜事件] 脚本被跳过（无页面作用域）：" + e.value);
                    }
                    break;
                case ANIMATION:
                default:
                    LOGGER.info("[运镜事件] animation 接口预留（本次只记录）：" + e.value);
                    break;
            }
        } catch (Throwable t) {
            LOGGER.info("[运镜事件] 执行失败（已吞）：" + e.value + " - " + t);
        }
    }

    private static ResourceLocation rl(String s) {
        String src = s != null && s.contains(":") ? s : "minecraft:" + s;
        return ResourceLocation.tryParse(src);
    }

    /**
     * Registry.get 的返回在 1.21.4 起从 T 变成 Optional<Reference<T>>（同名签名漂移）。
     * 这里用反射直调同名方法并统一解包两形态；这是版本漂移 shim（同 CompatRender 的
     * 形状兜底），不是按 MC 名的注册表查找反射。
     */
    private static Object regGet(Object registry, ResourceLocation rl) {
        try {
            java.lang.reflect.Method m = registry.getClass().getMethod("get", ResourceLocation.class);
            Object v = m.invoke(registry, rl);
            if (v instanceof java.util.Optional<?> opt) {
                Object inner = opt.orElse(null);
                if (inner instanceof net.minecraft.core.Holder.Reference<?> ref) {
                    return ref.value();
                }
                return inner;
            }
            return v;
        } catch (Throwable t) {
            return null;
        }
    }
}
