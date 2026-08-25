package com.opendreamcore.client.methods;

import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.SoundStore;
import com.opendreamcore.client.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * C1：原 ClientMethods 共享助手集中地（各命名空间类静态引用）。
 * 纯移动：方法体与原实现逐字一致，可见性 private → public。
 */
public final class ClientMethodSupport {
    private ClientMethodSupport() {}

    public static double[] lookDirection() {
        var p = player();
        if (p == null) {
            return new double[]{0, 0, 0};
        }
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        return new double[]{dx, dy, dz};
    }

    public static LookBlock lookAtBlock() {
        var p = player();
        if (p == null || p.level() == null) {
            return null;
        }
        try {
            var hit = p.pick(4.5, 0.0F, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var pos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                var state = p.level().getBlockState(pos);
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                return new LookBlock(id, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static String name() {
        return player() != null ? player().getName().getString() : "";
    }

    public static double health() {
        return player() != null ? player().getHealth() : 0;
    }

    public static double maxHealth() {
        return player() != null ? player().getMaxHealth() : 0;
    }

    public static double hunger() {
        return player() != null ? player().getFoodData().getFoodLevel() : 0;
    }

    public static double exp() {
        return player() != null ? player().experienceProgress : 0;
    }

    public static double level() {
        return player() != null ? player().experienceLevel : 0;
    }

    public static double x() {
        return player() != null ? player().getX() : 0;
    }

    public static double y() {
        return player() != null ? player().getY() : 0;
    }

    public static double z() {
        return player() != null ? player().getZ() : 0;
    }

    public static double yaw() {
        return player() != null ? player().getYRot() : 0;
    }

    public static double pitch() {
        return player() != null ? player().getXRot() : 0;
    }

    public static String gamemode() {
        return Minecraft.getInstance().gameMode != null
                ? Minecraft.getInstance().gameMode.getPlayerMode().getName() : "";
    }

    public static String biome() {
        if (player() == null || player().level() == null) {
            return "";
        }
        return CompatRender.holderRegisteredName(player().level().getBiome(player().blockPosition()));
    }

    public static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    public static double onlineTime() {
        return ClientController.get().onlineSeconds();
    }

    public static double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static net.minecraft.client.KeyMapping keyMapping(String name) {
        var options = Minecraft.getInstance().options;
        return switch (name) {
            case "key.keyboard.w", "key.up" -> options.keyUp;
            case "key.keyboard.a", "key.left" -> options.keyLeft;
            case "key.keyboard.s", "key.down" -> options.keyDown;
            case "key.keyboard.d", "key.right" -> options.keyRight;
            case "key.keyboard.space", "key.jump" -> options.keyJump;
            case "key.keyboard.left.shift", "key.sneak" -> options.keyShift;
            case "key.keyboard.left.control", "key.sprint" -> options.keySprint;
            case "key.keyboard.e", "key.inventory" -> options.keyInventory;
            case "key.keyboard.q", "key.drop" -> options.keyDrop;
            case "key.keyboard.f", "key.swapOffhand" -> options.keySwapOffhand;
            case "key.keyboard.attack", "key.attack" -> options.keyAttack;
            case "key.keyboard.use", "key.use" -> options.keyUse;
            case "key.keyboard.f5", "key.togglePerspective" -> options.keyTogglePerspective;
            default -> null;
        };
    }

    public static void collectElementIds(com.opendreamcore.page.Element el, List<String> out) {
        out.add(el.id());
        for (var child : el.children()) collectElementIds(child, out);
    }

    public static void sendChat(String text) {
        if (player() != null) {
            player().displayClientMessage(Component.literal(text), false);
        }
    }

    public static void openVanillaChat(String initialText) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen previous = mc.screen;
        mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(initialText == null ? "" : initialText) {
            // removed()：屏幕因任何原因关闭（发送/ESC）都会调用，且全版本签名稳定
            @Override
            public void removed() {
                super.removed();
                if (previous != null) {
                    mc.setScreen(previous);
                }
            }
        });
    }

    public static void sendActionBar(String text) {
        if (player() != null) {
            player().displayClientMessage(Component.literal(text), true);
        }
    }

    public static SoundEvent soundEvent(String soundName) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(soundName);
        return id == null ? null : UiRenderer.soundEvent(id);
    }

    public static void playSound(String soundName, double volume, double pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundName);
        if (id == null || player() == null) {
            return;
        }
        SoundEvent event = UiRenderer.soundEvent(id);
        if (event == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(event, (float) pitch, (float) volume));
    }

    /** 视线指向的方块（玩家拾取，4.5 格）：方块注册名 + 坐标。 */
    public static record LookBlock(String block, double x, double y, double z) {
    }
}
