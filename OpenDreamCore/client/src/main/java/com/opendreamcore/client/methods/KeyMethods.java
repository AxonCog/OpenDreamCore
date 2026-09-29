package com.opendreamcore.client.methods;

import com.mojang.blaze3d.platform.InputConstants;
import com.opendreamcore.client.AnimationEngine;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.FfmpegVideoPlayer;
import com.opendreamcore.client.LegacyText;
import com.opendreamcore.client.MusicPlayer;
import com.opendreamcore.client.OdcScreen;
import com.opendreamcore.client.SoundStore;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.UiStyle;
import com.opendreamcore.client.visual.ClientKeyConfigTrigger;
import com.opendreamcore.client.visual.ClientVisualStore;
import com.opendreamcore.page.Page;
import com.opendreamcore.script.Easing;
import com.opendreamcore.script.NamespaceRegistry;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.KeyMapping;
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
 * C1 拆分自 ClientMethods。// Key 命名空间
 */
public final class KeyMethods {

    private KeyMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Key", args -> {
            // Key.是否按下("key.keyboard.w")：绑定别名走 KeyMapping（尊重改键）；
            // 其余任意键回退 InputConstants 原始状态查询
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var mapping = ClientMethodSupport.keyMapping(String.valueOf(args[0]));
            if (mapping != null) {
                return mapping.isDown();
            }
            int code = ClientMethodSupport.keyCodeOf(String.valueOf(args[0]));
            return code > 0 && ClientMethodSupport.rawKeyDown(
                    Minecraft.getInstance().getWindow().getWindow(), code);
        }, "是否按下", "isKeyDown", "is_down", "按下");
        NamespaceRegistry.register("Key", args -> {
            // Key.按键名("key.keyboard.w") → 当前绑定键的名字
            if (args.length < 1 || args[0] == null) {
                return "";
            }
            var mapping = ClientMethodSupport.keyMapping(String.valueOf(args[0]));
            if (mapping != null) {
                return mapping.getName();
            }
            int code = ClientMethodSupport.keyCodeOf(String.valueOf(args[0]));
            if (code <= 0) {
                return "";
            }
            String n = InputConstants.Type.KEYSYM.getOrCreate(code).getName();
            return n == null || n.isBlank() ? "KEY_" + code : n.toUpperCase(java.util.Locale.ROOT);
        }, "按键名", "getKeyName", "get_key_name");
        NamespaceRegistry.register("Key", args -> {
            // Key.模拟按下(...)：DragonCore「按键指令」语义——参数先按 KeyConfig 规则 ID 匹配，
            // 命中即触发该规则绑定的组合（等效玩家按下）；否则回退物理键模拟
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            String name = String.valueOf(args[0]).trim();
            if (name.isEmpty()) {
                return false;
            }
            String combo = ClientKeyConfigTrigger.firstComboOfRule(
                    ClientVisualStore.get().rulesOf("KeyConfig"), name);
            if (combo != null) {
                var mc = Minecraft.getInstance();
                UiSession sess = mc.screen instanceof OdcScreen s
                        ? s.session() : OdcScreen.lastActiveSession();
                ClientController.get().sendVisualKeyTrigger(sess, "keyconfig:" + combo);
                return true;
            }
            int code = ClientMethodSupport.keyCodeOf(name);
            if (code <= 0) {
                return false;
            }
            // 驱动原版绑定（click 当拍生效），并同步 ODC 页面 keyPress / KeyConfig 检测管线
            KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(code));
            if (Minecraft.getInstance().screen instanceof OdcScreen s) {
                s.simulateKeyPress(code);
            }
            return true;
        }, "模拟按下", "simulatePress", "pressKey", "simulate_key_press");
        NamespaceRegistry.register("Key", args ->
                OdcScreen.currentLegacyPressedKey(), "当前按下键", "get_current_pressed_key");
    }
}
