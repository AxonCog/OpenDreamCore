package com.opendreamcore.client.methods;

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
 * C1 拆分自 ClientMethods。// Var 命名空间
 */
public final class VarMethods {

    private VarMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            return page != null && args.length > 0 && args[0] != null
                    && page.variables().containsKey(String.valueOf(args[0]));
        }, "是否存在", "hasVariable", "has_variable", "has");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            return page == null || args.length < 1 || args[0] == null
                    ? null : page.variables().get(String.valueOf(args[0]));
        }, "获取", "getVariable", "get_variable", "get", "读取变量");
        NamespaceRegistry.register("Var", args -> {
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            return ClientController.get().setPageVarAny(String.valueOf(args[0]), args[1]);
        }, "设置", "setVariable", "set_variable", "set", "写入变量");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            return page == null ? 0.0 : (double) page.variables().size();
        }, "数量", "getVariableCount", "get_variable_count", "count");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            page.variables().keySet().forEach(out::add);
            return out;
        }, "名称列表", "getVariableNames", "get_variable_names", "names");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return 0.0;
            }
            int n = page.variables().size();
            page.variables().clear();
            return (double) n;
        }, "清空", "clearVariables", "clear_variables", "clear");
        NamespaceRegistry.register("Var", args -> {
            // Var.递增(变量名, 增量=1)：缺省从 0 起；非数值返回 -1
            if (args.length < 1 || args[0] == null) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            double step = args.length > 1 && args[1] instanceof Number s ? s.doubleValue() : 1.0;
            Object cur = page.variables().get(name);
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double nv = base + step;
            page.variables().put(name, nv);
            return nv;
        }, "递增", "incrementVariable", "increment_variable", "increment", "加一");
        NamespaceRegistry.register("Var", args -> {
            if (args.length < 1 || args[0] == null) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            double step = args.length > 1 && args[1] instanceof Number s ? s.doubleValue() : 1.0;
            Object cur = page.variables().get(name);
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            double nv = base - step;
            page.variables().put(name, nv);
            return nv;
        }, "递减", "decrementVariable", "decrement_variable", "decrement", "减一");
        NamespaceRegistry.register("Var", args -> {
            // Var.增加(变量名, 数值)：数值加；非数值目标返回 -1
            if (args.length < 2 || args[0] == null || !(args[1] instanceof Number add)) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            Object cur = page.variables().get(name);
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            double nv = base + add.doubleValue();
            page.variables().put(name, nv);
            return nv;
        }, "增加", "addToVariable", "add_to_variable", "addTo", "加");
        NamespaceRegistry.register("Var", args -> {
            if (args.length < 2 || args[0] == null || !(args[1] instanceof Number sub)) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            Object cur = page.variables().get(name);
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            double nv = base - sub.doubleValue();
            page.variables().put(name, nv);
            return nv;
        }, "减少", "subtractFromVariable", "subtract_from_variable", "subtractFrom", "减");
        // 动画变量（数值补间：每帧写入页面变量，HUD/世界直接显示动画；屏幕刷新）
        NamespaceRegistry.register("Var", args -> {
            // Var.动画值(变量名) → 当前补间值（无补间读页面变量；不存在 0）
            if (args.length < 1 || args[0] == null) {
                return 0.0;
            }
            return ClientController.get().getAnimateValue(String.valueOf(args[0]));
        }, "动画值", "getAnimateValue", "get_animate_value", "animateValue");
        NamespaceRegistry.register("Var", args -> {
            // Var.设置动画值(变量名, 值) → 立即写入（清除补间）
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            return ClientController.get().setAnimateValue(String.valueOf(args[0]), args[1]);
        }, "设置动画值", "setAnimationVariable", "set_animation_variable", "setAnimateValue");
        NamespaceRegistry.register("Var", args -> {
            // Var.动画到(变量名, 目标值, 毫秒, 缓动?) → 启动补间（缓动: linear/quad_out/cubic_in_out/
            // sine_out/elastic_out/bounce_out 等，缺省 linear）
            if (args.length < 3 || args[0] == null) {
                return false;
            }
            double to = ClientMethodSupport.num(args[1]);
            long ms = (long) ClientMethodSupport.num(args[2]);
            com.opendreamcore.script.Easing.Type easing = com.opendreamcore.script.Easing.Type.LINEAR;
            if (args.length > 3 && args[3] != null) {
                String e = String.valueOf(args[3]).toUpperCase(java.util.Locale.ROOT).replace('-', '_');
                try {
                    easing = com.opendreamcore.script.Easing.Type.valueOf(e);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return ClientController.get().animateValueTo(String.valueOf(args[0]), to, ms, easing);
        }, "动画到", "animateValueTo", "animate_value_to", "tweenTo", "tween_to");
    }
}