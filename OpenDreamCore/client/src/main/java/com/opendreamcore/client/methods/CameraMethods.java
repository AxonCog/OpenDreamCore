package com.opendreamcore.client.methods;

import com.opendreamcore.client.camera.CameraLive;
import com.opendreamcore.client.camera.CameraPlayback;
import com.opendreamcore.client.AnimationEngine;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.FfmpegVideoPlayer;
import com.opendreamcore.client.LegacyText;
import com.opendreamcore.client.MusicPlayer;
import com.opendreamcore.client.SoundStore;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.UiStyle;
import com.opendreamcore.page.Page;
import com.opendreamcore.script.Easing;
import com.opendreamcore.script.NamespaceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * C1 拆分自 ClientMethods。// Camera 命名空间
 */
public final class CameraMethods {

    private CameraMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.x(), "获取X", "getX", "get_x");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.y(), "获取Y", "getY", "get_y");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.z(), "获取Z", "getZ", "get_z");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.yaw(), "获取偏航", "getYaw", "get_yaw");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.pitch(), "获取俯仰", "getPitch", "get_pitch");
        registerPlayback();
    }

    /** M1.6 播放控制：四路触发里的「脚本/命令」路都从这里过。 */
    private static void registerPlayback() {
        // ---- 播放 / 停止 / 状态 / 实时改写 ----
        NamespaceRegistry.register("Camera", args -> {
            // Camera.播放(id) → 1.0=已发起，0.0=没发（参数缺失/规则不存在）
            if (args.length < 1 || args[0] == null) {
                return 0.0;
            }
            var out = CameraPlayback.get().play(String.valueOf(args[0]));
            return out.stream().noneMatch(s -> s.startsWith("§c")) ? 1.0 : 0.0;
        }, "播放", "play", "play");
        NamespaceRegistry.register("Camera", args -> {
            CameraPlayback.get().stop();
            return 1.0;
        }, "停止", "stop", "stop");
        NamespaceRegistry.register("Camera", args -> CameraPlayback.get().isPlaying() ? 1.0 : 0.0,
                "播放中", "isPlaying", "is_playing");
        NamespaceRegistry.register("Camera", args -> {
            // Camera.设置位置(x,y,z)：绝对覆盖机位（播放中立即生效，未播放预埋下一次）
            if (args.length < 3) {
                return 0.0;
            }
            CameraLive.setLivePos(ClientMethodSupport.num(args[0]),
                    ClientMethodSupport.num(args[1]), ClientMethodSupport.num(args[2]));
            return 1.0;
        }, "设置位置", "setPos", "set_pos");
        NamespaceRegistry.register("Camera", args -> {
            // Camera.看向(x,y,z)：朝向指向世界点（用当前机位算 yaw/pitch）
            if (args.length < 3) {
                return 0.0;
            }
            CameraLive.setLiveLook(ClientMethodSupport.num(args[0]),
                    ClientMethodSupport.num(args[1]), ClientMethodSupport.num(args[2]));
            return 1.0;
        }, "看向", "lookAt", "look_at");
        NamespaceRegistry.register("Camera", args -> {
            // Camera.设置视场(度)：绝对 FOV；0/负数 = 还原跟随规则
            if (args.length < 1) {
                return 0.0;
            }
            double deg = ClientMethodSupport.num(args[0]);
            CameraLive.setLiveFov(deg > 0.0 ? Double.valueOf(deg) : null);
            return 1.0;
        }, "设置视场", "setFov", "set_fov");
        NamespaceRegistry.register("Camera", args -> {
            // Camera.震屏(强度, tick)：脚本震屏，独立于规则震屏，按真实时间衰减
            if (args.length < 2) {
                return 0.0;
            }
            CameraLive.burst(ClientMethodSupport.num(args[0]), (int) ClientMethodSupport.num(args[1]));
            return 1.0;
        }, "震屏", "shake", "shake");
        NamespaceRegistry.register("Camera", args -> {
            // Camera.过渡(进秒, 出秒)：预埋下一次播放的进/出过渡时长
            if (args.length < 2) {
                return 0.0;
            }
            CameraLive.setNextTransition(ClientMethodSupport.num(args[0]), ClientMethodSupport.num(args[1]));
            return 1.0;
        }, "过渡", "transition", "transition");
    }
}