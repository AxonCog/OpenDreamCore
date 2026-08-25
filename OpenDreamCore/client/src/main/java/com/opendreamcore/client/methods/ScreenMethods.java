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
 * C1 拆分自 ClientMethods。// Screen 命名空间
 */
public final class ScreenMethods {

    private ScreenMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            ClientController.get().open(ClientController.get().localPages().get(String.valueOf(args[0])));
            return true;
        }, "打开页面", "openPage", "open_page", "打开", "open");
        NamespaceRegistry.register("Screen", args -> {
            ClientController.get().close();
            return null;
        }, "关闭页面", "closePage", "close_page", "关闭", "close");
        NamespaceRegistry.register("Screen", args -> {
            ClientController.get().autoMountHud();
            return null;
        }, "挂载HUD", "mountHud", "mount_hud");
        NamespaceRegistry.register("Screen", args -> {
            ClientController.get().closeHud();
            return null;
        }, "卸载HUD", "unmountHud", "unmount_hud");
        NamespaceRegistry.register("Screen", args -> ClientController.get().isOpen(), "是否打开", "isOpen", "is_open");
        NamespaceRegistry.register("Screen", args -> ClientController.get().isHudOpen(), "HUD是否打开", "isHudOpen", "is_hud_open");
        // 组件方法：动态改元素属性（文本/颜色/显隐/位置等）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置元素(元素id, 路径, 值) → 如 ("title_text", "text.content", "新标题")
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            var controller = ClientController.get();
            Page page = controller.currentPage();
            if (page == null) {
                return false;
            }
            boolean ok = controller.setElementProp(page,
                    String.valueOf(args[0]), String.valueOf(args[1]), args[2]);
            if (ok) {
                controller.refreshCurrent();
            }
            return ok;
        }, "设置元素", "setElement", "set_element", "设置元素属性", "setElementProp", "set_element_prop");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.获取元素(元素id, 路径)
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return null;
            }
            var controller = ClientController.get();
            Page page = controller.currentPage();
            if (page == null) {
                return null;
            }
            return controller.getElementProp(page,
                    String.valueOf(args[0]), String.valueOf(args[1]));
        }, "获取元素", "getElement", "get_element", "获取元素属性", "getElementProp", "get_element_prop");
        // 组件动态操作：显隐/存在/悬停/创建
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().hideElement(String.valueOf(args[0]));
        }, "隐藏元素", "hideElement", "hide_element", "隐藏");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().showElement(String.valueOf(args[0]));
        }, "显示元素", "showElement", "show_element", "显示");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().elementExists(String.valueOf(args[0]));
        }, "元素存在", "elementExists", "element_exists", "存在");
        NamespaceRegistry.register("Screen", args -> {
            String hovered = ClientController.get().hoveredElement();
            return hovered == null ? "" : hovered;
        }, "获取悬停元素", "getHoveredElement", "get_hovered_element", "悬停元素", "hovered");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.创建元素(id, type, x, y, width?, height?)
            if (args.length < 4 || args[0] == null || args[1] == null) {
                return false;
            }
            double x = ClientMethodSupport.num(args.length > 2 ? args[2] : null);
            double y = ClientMethodSupport.num(args.length > 3 ? args[3] : null);
            double w = args.length > 4 && args[4] instanceof Number n ? n.doubleValue() : Double.NaN;
            double h = args.length > 5 && args[5] instanceof Number n2 ? n2.doubleValue() : Double.NaN;
            return ClientController.get().createElement(
                    String.valueOf(args[0]), String.valueOf(args[1]), x, y, w, h);
        }, "创建元素", "createElement", "create_element", "创建");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return false;
            String id = String.valueOf(args[0]);
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return false;
            if (c.findElement(page, id) == null && !c.elementExists(id)) return false;
            String pageId = page.id() == null ? "page" : page.id();
            c.elementEdits().markDeleted(pageId, id);
            c.refreshCurrent();
            return true;
        }, "删除元素", "removeElement", "remove_element", "移除元素", "deleteElement", "删除");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return null;
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return null;
            var el = c.findElement(page, String.valueOf(args[0]));
            if (el == null) return null;
            var layout = el.layout();
            return java.util.List.of(
                    layout == null || layout.x() == null ? 0 : layout.x(),
                    layout == null || layout.y() == null ? 0 : layout.y(),
                    layout == null || layout.width() == null ? 0 : layout.width(),
                    layout == null || layout.height() == null ? 0 : layout.height());
        }, "获取元素位置", "getElementPosition", "get_element_position", "元素位置");
        NamespaceRegistry.register("Screen", args -> {
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return List.of();
            List<String> out = new java.util.ArrayList<>();
            for (var e : page.elements()) ClientMethodSupport.collectElementIds(e, out);
            for (var e : c.elementEdits().copies(page.id() == null ? "page" : page.id())) ClientMethodSupport.collectElementIds(e, out);
            return out;
        }, "获取全部元素", "getAllElements", "get_all_elements", "全部元素");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return false;
            String id = String.valueOf(args[0]);
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return false;
            String pageId = page.id() == null ? "page" : page.id();
            boolean vis = c.elementExists(id) && !c.elementEdits().isHidden(pageId, id);
            if (vis) c.hideElement(id); else c.showElement(id);
            return !vis;
        }, "切换元素", "toggleElement", "toggle_element", "切换");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return List.of();
            String q = String.valueOf(args[0]).toLowerCase(java.util.Locale.ROOT);
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return List.of();
            List<String> all = new java.util.ArrayList<>();
            for (var e : page.elements()) ClientMethodSupport.collectElementIds(e, all);
            for (var e : c.elementEdits().copies(page.id() == null ? "page" : page.id())) ClientMethodSupport.collectElementIds(e, all);
            boolean byType = q.startsWith("type:");
            String filter = byType ? q.substring(5) : q;
            List<String> out = new java.util.ArrayList<>();
            for (String id : all) {
                if (byType) {
                    var el = c.findElement(page, id);
                    if (el != null && el.type().toLowerCase(java.util.Locale.ROOT).contains(filter)) out.add(id);
                } else {
                    String mode = args.length > 1 ? String.valueOf(args[1]).toLowerCase(java.util.Locale.ROOT) : "contains";
                    if ("prefix".equals(mode) ? id.toLowerCase(java.util.Locale.ROOT).startsWith(filter)
                            : "suffix".equals(mode) ? id.toLowerCase(java.util.Locale.ROOT).endsWith(filter)
                            : id.toLowerCase(java.util.Locale.ROOT).contains(filter)) out.add(id);
                }
            }
            return out;
        }, "获取元素按条件", "getElementsBy", "get_elements_by", "按条件获取元素");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 2 || args[0] == null || args[1] == null) return false;
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return false;
            var el = c.findElement(page, String.valueOf(args[0]));
            if (el == null) return false;
            Map<String, Object> target = el.props();
            String path = String.valueOf(args[1]);
            String[] parts = path.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = target.get(parts[i]);
                if (!(next instanceof Map<?, ?> m)) { Map<String, Object> fresh = new java.util.LinkedHashMap<>(); target.put(parts[i], fresh); target = fresh; }
                else target = (Map<String, Object>) (Map<?, ?>) m;
            }
            target.put(parts[parts.length - 1], args[2]);
            c.refreshCurrent();
            return true;
        }, "批量设置属性", "batchSetProperty", "batch_set_property", "批量设置");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return Map.of();
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return Map.of();
            String id = String.valueOf(args[0]);
            var el = c.findElement(page, id);
            if (el == null) return Map.of();
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("id", el.id());
            out.put("type", el.type());
            out.put("x", el.layout() == null ? 0 : el.layout().x());
            out.put("y", el.layout() == null ? 0 : el.layout().y());
            out.put("width", el.layout() == null ? 0 : el.layout().width());
            out.put("height", el.layout() == null ? 0 : el.layout().height());
            return out;
        }, "获取组件位置", "getComponentPosition", "get_component_position", "组件位置");
        // 延迟变量（基于脚本定时器体系：到期写页面变量并刷新对应展示形态）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.延迟设置变量(变量名, 值, 毫秒) → 同名挂起先取消；返回任务 id（-1 失败）
            if (args.length < 3 || args[0] == null) {
                return -1.0;
            }
            return (double) ClientController.get().delaySetPageVar(
                    String.valueOf(args[0]), args[1], (long) ClientMethodSupport.num(args[2]));
        }, "延迟设置变量", "delaySetVar", "delay_set_var", "setDelayedVariable", "set_delayed_variable");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.取消延迟变量(变量名)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().cancelDelayedVar(
                    ClientController.get().currentPageId(), String.valueOf(args[0]));
        }, "取消延迟变量", "cancelDelayedVar", "cancel_delayed_var", "clearDelayedVariable");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.延迟剩余(变量名) → 剩余毫秒；无挂起任务 -1
            if (args.length < 1 || args[0] == null) {
                return -1.0;
            }
            return ClientController.get().delayedVarRemaining(
                    ClientController.get().currentPageId(), String.valueOf(args[0]));
        }, "延迟剩余", "delayedRemaining", "delayed_remaining", "getDelayedRemaining");
        // 视频控制（按元素 id，FfmpegVideoPlayer 注册表）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频暂停(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.pause();
            return true;
        }, "视频暂停", "pauseVideo", "pause_video", "暂停视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频继续(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.resume();
            return true;
        }, "视频继续", "resumeVideo", "resume_video", "继续视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频停止(元素id) — 停止并清空画面
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.stop();
            return true;
        }, "视频停止", "stopVideo", "stop_video", "停止视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频重播(元素id) — 从头播放
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.restart();
            return true;
        }, "视频重播", "restartVideo", "restart_video", "重播视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频是否播放(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            return video != null && video.isPlaying();
        }, "视频是否播放", "isVideoPlaying", "is_video_playing", "视频播放中");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频跳转(元素id, 秒) — seek 到指定时间
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.seek(ClientMethodSupport.num(args[1]));
            return true;
        }, "视频跳转", "seekVideo", "seek_video", "跳转视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.隐藏原版界面(true/false) — 隐藏准星/物品栏/聊天等全部原版 HUD（F1 同款）
            boolean hide = args.length > 0 && Boolean.parseBoolean(String.valueOf(args[0]));
            Minecraft.getInstance().options.hideGui = hide;
            return true;
        }, "隐藏原版界面", "hideVanilla", "hide_vanilla", "隐藏原版HUD", "hideVanillaHud");
        // 世界元素位置（脚本化控制，配合拖拽）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置世界元素位置(元素id, x, y, z)
            if (args.length < 4 || args[0] == null) {
                return false;
            }
            return ClientController.get().setWorldElementPos(String.valueOf(args[0]),
                    ClientMethodSupport.num(args[1]), ClientMethodSupport.num(args[2]), ClientMethodSupport.num(args[3]));
        }, "设置世界元素位置", "setWorldElementPos", "set_world_element_pos", "世界元素位置");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.获取世界元素位置(元素id) → {x, y, z}
            if (args.length < 1 || args[0] == null) {
                return null;
            }
            return ClientController.get().getWorldElementPos(String.valueOf(args[0]));
        }, "获取世界元素位置", "getWorldElementPos", "get_world_element_pos", "世界元素坐标");
        // 动画方法
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().play(String.valueOf(args[0]));
            return true;
        }, "播放动画", "playAnimation", "play_animation", "播放");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().stop(String.valueOf(args[0]));
            return true;
        }, "停止动画", "stopAnimation", "stop_animation", "停止");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().pause(String.valueOf(args[0]));
            return true;
        }, "暂停动画", "pauseAnimation", "pause_animation", "暂停");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().resume(String.valueOf(args[0]));
            return true;
        }, "恢复动画", "resumeAnimation", "resume_animation", "恢复");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.播放动画序列("a", "b", "c")
            if (args.length < 1) {
                return false;
            }
            String[] names = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                names[i] = args[i] == null ? "" : String.valueOf(args[i]);
            }
            AnimationEngine.get().playSequence(names);
            return true;
        }, "播放动画序列", "playSequence", "play_sequence", "播放序列");
        // 屏幕特效
        NamespaceRegistry.register("Screen", args -> {
            // Screen.屏幕震动(强度, 时长)
            double strength = args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 5;
            int duration = args.length > 1 && args[1] instanceof Number n2 ? n2.intValue() : 300;
            ClientController.get().shake(strength, duration);
            return true;
        }, "屏幕震动", "shakeScreen", "shake_screen", "震动");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.闪屏("red" 或 "#FF0000", 时长)
            String color = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "#FFFFFF";
            int duration = args.length > 1 && args[1] instanceof Number n ? n.intValue() : 200;
            int argb = UiStyle.color(color, 0xFFFFFFFF);
            ClientController.get().flash(argb, duration);
            return true;
        }, "闪屏", "flashScreen", "flash_screen", "闪屏");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.过渡(颜色?, 时长ms) — 全屏淡入淡出遮罩（单机直调；
            // 与服务端 UiEffect TRANSITION / ClientController.transition 同一条渲染路径）
            String color = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "#000000";
            int duration = args.length > 1 && args[1] instanceof Number n ? n.intValue() : 400;
            ClientController.get().transition(UiStyle.color(color, 0xFF000000), Math.max(1, duration));
            return true;
        }, "过渡", "transition");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置变量(名, 值) — 写当前页面变量并刷新对应展示形态
            // （文档/示例的惯用写法；与 Var.设置 同走 setPageVarAny：屏幕 → HUD → 世界聚焦面板）
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            return ClientController.get().setPageVarAny(String.valueOf(args[0]), args[1]);
        }, "设置变量", "setVariable", "set_variable", "写入变量");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.获取变量(名) — 读当前页面变量（无页/未定义返回 null）
            var page = ClientController.get().anyCurrentPage();
            return page == null || args.length < 1 || args[0] == null
                    ? null : page.variables().get(String.valueOf(args[0]));
        }, "获取变量", "getVariable", "get_variable", "读取变量");
    }
}