package com.opendreamcore.client.elements;

import net.minecraft.resources.ResourceLocation;

import com.opendreamcore.client.ContainerStore;

import com.opendreamcore.client.AnimationEngine;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.FfmpegVideoPlayer;
import com.opendreamcore.client.GifPlayer;
import com.opendreamcore.client.VideoPlayer;
import com.opendreamcore.client.CustomFonts;
import com.opendreamcore.client.SoundStore;
import com.opendreamcore.client.TtfRenderer;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.UiStyle;
import com.opendreamcore.page.Element;
import com.opendreamcore.script.RichText;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C2 拆分自 ScreenElements（MediaItemDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class MediaItemDraws {
    /** C2：视频进度条命中区域（元素id → 屏幕 rect），handleVideoSeekClick 用。 */
    public static final java.util.Map<String, int[]> VIDEO_SEEK_BARS = new java.util.concurrent.ConcurrentHashMap<>();
    private MediaItemDraws() {}

    /**
     * 状态贴图绘制（button 四态 / input.normal+focus 通用）：
     * 字符串 = 整图路径；对象 = {path, x, y, width, height(或 sourceHeight), textureWidth, textureHeight} 源区域。
     * 源区域会拉伸铺满元素；返回 false 表示未提供贴图（调用方回退纯色绘制）。
     */
    public static boolean drawStateTexture(GuiGraphics g, Object raw, RenderNode node) {
        if (raw == null) {
            return false;
        }
        net.minecraft.resources.ResourceLocation tex;
        int sx = 0;
        int sy = 0;
        int sw = 0;
        int sh = 0;
        int tw = 256;
        int th = 256;
        if (raw instanceof String s) {
            tex = UiStyle.texture(UiRenderer.str(s));
            if (tex == null) {
                return false;
            }
        } else if (raw instanceof java.util.Map<?, ?> tm) {
            tex = UiStyle.texture(UiRenderer.str(tm.get("path")));
            if (tex == null) {
                return false;
            }
            sx = (int) UiRenderer.num(tm.get("x"), 0);
            sy = (int) UiRenderer.num(tm.get("y"), 0);
            sw = (int) UiRenderer.num(tm.get("width"), 0);
            sh = (int) UiRenderer.num(tm.get("height"), UiRenderer.num(tm.get("sourceHeight"), 0));
            tw = (int) UiRenderer.num(tm.get("textureWidth"), 256);
            th = (int) UiRenderer.num(tm.get("textureHeight"), 256);
        } else {
            return false;
        }
        if (sw > 0 || sh > 0) {
            if (sw <= 0) {
                sw = Math.max(sh, 1);
            }
            if (sh <= 0) {
                sh = Math.max(sw, 1);
            }
            CompatRender.blit(g, tex, (int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(),
                    sx, sy, sw, sh, tw, th);
        } else {
            CompatRender.blit(g, tex, (int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(),
                    0.0F, 0.0F, (int) node.width(), (int) node.height(),
                    (int) node.width(), (int) node.height());
        }
        return true;
    }

    /**
     * 视频组件：src 指向真视频文件（mp4/webm/mov/mkv...，需 JavaCV 丢进 mods）或帧序列目录
     * （frame_0000.png...，无 JavaCV 回退）。fps 帧序列速度；loop 循环；fit: contain 按原比例居中。
     */
    public static void drawVideo(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "video");
        String src = UiRenderer.str(spec.get("src"));
        double fps = UiRenderer.num(spec.get("fps"), 24);
        Object loopRaw = spec.get("loop");
        boolean loop = loopRaw == null || Boolean.parseBoolean(String.valueOf(loopRaw));
        String fit = UiRenderer.str(spec.get("fit"));
        ResourceLocation texture = null;
        int x = (int) node.x();
        int y = (int) node.y();
        int w = (int) node.width();
        int h = (int) node.height();
        boolean videoFile = false;
        if (src != null) {
            String lower = src.toLowerCase(java.util.Locale.ROOT);
            boolean remote = lower.startsWith("https://") || lower.startsWith("http://");
            videoFile = remote
                    || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")
                    || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".flv");
            if (videoFile && FfmpegVideoPlayer.available()) {
                FfmpegVideoPlayer video = FfmpegVideoPlayer.of(src, loop, fit);
                if (video != null) {
                    FfmpegVideoPlayer.register(node.id(), video); // 脚本按元素 id 控制
                    texture = video.currentTexture();
                    int[] r = video.drawRect(node.x(), node.y(), node.width(), node.height());
                    x = r[0];
                    y = r[1];
                    w = r[2];
                    h = r[3];
                }
            }
            if (texture == null) {
                // 回退帧序列（帧目录）
                VideoPlayer video = VideoPlayer.of(src);
                if (video != null) {
                    texture = video.currentTexture(fps);
                }
            }
        }
        if (texture == null) {
            UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF101318));
            // 播放器已就绪但首帧未出 → 加载中；否则占位
            FfmpegVideoPlayer vp = videoFile && FfmpegVideoPlayer.available()
                    ? FfmpegVideoPlayer.byElement(node.id()) : null;
            if (vp != null && !vp.hasFrame() && !vp.isFailed()) {
                g.drawString(font, "加载中…", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
            } else {
                g.drawString(font, "[video]", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
            }
            return;
        }
        CompatRender.blit(g, texture, x, y, w, h, 0.0F, 0.0F, w, h, w, h);
        // seek 条（video.seekable: true 且播放器有时间轴）：底部进度条，点击跳转
        if (UiRenderer.bool(spec.get("seekable"), false) && videoFile && FfmpegVideoPlayer.available()) {
            FfmpegVideoPlayer vp = FfmpegVideoPlayer.byElement(node.id());
            if (vp != null) {
                double cur = vp.currentSeconds();
                double dur = vp.durationSeconds();
                if (dur > 0 && cur >= 0) {
                    int barH = 4;
                    int bx = x;
                    int by = y + h - barH;
                    int bw = w;
                    g.fill(bx, by, bx + bw, by + barH, 0x90000000);
                    int fillW = (int) Math.max(0, Math.min(bw, bw * (cur / dur)));
                    g.fill(bx, by, bx + fillW, by + barH, 0xFF7A8BFF);
                    // 记录条区域（OdcScreen 点击跳转用）
                    VIDEO_SEEK_BARS.put(node.id(), new int[]{bx, by, bx + bw, by + barH,
                            (int) Math.round(dur * 100)});
                }
            }
        }
    }

    /**
     * video 元素点击：落在 seek 条上 → 跳转播放位置并返回 true（已消费）。
     */
    public static boolean handleVideoSeekClick(String elementId, double mouseX, double mouseY) {
        int[] bar = VIDEO_SEEK_BARS.get(elementId);
        if (bar == null) {
            return false;
        }
        if (mouseX < bar[0] || mouseX > bar[2] || mouseY < bar[1] - 2 || mouseY > bar[3] + 2) {
            return false;
        }
        FfmpegVideoPlayer vp = FfmpegVideoPlayer.byElement(elementId);
        if (vp == null) {
            return false;
        }
        double ratio = (mouseX - bar[0]) / Math.max(1, bar[2] - bar[0]);
        ratio = Math.max(0, Math.min(1, ratio));
        vp.seek(ratio * (bar[4] / 100.0));
        return true;
    }

    public static void drawImage(GuiGraphics g, Font font, RenderNode node) {
        drawImage(g, font, node, false);
    }

    public static void drawImage(GuiGraphics g, Font font, RenderNode node, boolean hovered) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "image");
        String src = UiRenderer.str(spec.get("src"));
        // 悬停换图（hoverSrc）：旧版 textureHovered 的等价能力
        if (hovered) {
            String hoverSrc = UiRenderer.str(spec.get("hoverSrc"));
            if (hoverSrc != null && !hoverSrc.isBlank()) {
                src = hoverSrc;
            }
        }
        if (src == null && spec.get("images") instanceof List<?> imgs && !imgs.isEmpty()) {
            long interval = (long) UiRenderer.num(spec.get("interval"), 1200);
            if (interval <= 0) interval = 1200;
            int idx = (int) ((System.currentTimeMillis() / interval) % imgs.size());
            Object pick = imgs.get(idx);
            src = pick == null ? null : String.valueOf(pick);
        }
        ResourceLocation texture;
        if (src != null && src.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
            GifPlayer gif = GifPlayer.of(src);
            texture = gif == null ? null : gif.currentTexture();
        } else {
            texture = UiStyle.texture(src);
        }
        if (texture == null) {
            UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF20242C));
            return;
        }
        int w = (int) node.width();
        int h = (int) node.height();
        Map<?, ?> srcR = spec.get("source") instanceof Map<?, ?> sm ? sm : null;
        int su = srcR == null ? 0 : (int) UiRenderer.num(srcR.get("x"), 0);
        int sv = srcR == null ? 0 : (int) UiRenderer.num(srcR.get("y"), 0);
        int sw = srcR == null ? w : (int) UiRenderer.num(srcR.get("w"), w);
        int sh = srcR == null ? h : (int) UiRenderer.num(srcR.get("h"), h);
        Map<?, ?> sheet = spec.get("sheet") instanceof Map<?, ?> sm2 ? sm2 : null;
        int texW = sheet == null ? 256 : (int) UiRenderer.num(sheet.get("w"), 256);
        int texH = sheet == null ? 256 : (int) UiRenderer.num(sheet.get("h"), 256);
        double rot = UiRenderer.num(spec.get("rotation"), 0);
        Object mirror = spec.get("mirror");
        boolean mx = Boolean.TRUE.equals(mirror)
                || mirror instanceof Map<?, ?> mm && UiRenderer.bool(mm.get("x"), false);
        boolean my = mirror instanceof Map<?, ?> mm2 && UiRenderer.bool(mm2.get("y"), false);
        Map<?, ?> ns = spec.get("nineSlice") instanceof Map<?, ?> nm ? nm : null;
        boolean tile = UiRenderer.bool(spec.get("tile"), UiRenderer.bool(spec.get("repeat"), false));
        var pose = g.pose();
        CompatRender.posePush(pose);
        if (mx || my || rot != 0) {
            double cx = node.x() + w / 2.0;
            double cy = node.y() + h / 2.0;
            CompatRender.poseTranslate(pose, cx, cy);
            if (mx) CompatRender.poseScale(pose, -1.0, 1.0);
            if (my) CompatRender.poseScale(pose, 1.0, -1.0);
            if (rot != 0) CompatRender.poseRotateZDegrees(pose, rot);
            CompatRender.poseTranslate(pose, -cx, -cy);
        }
        if (tile) {
            int tw = Math.max(1, sw);
            int th = Math.max(1, sh);
            for (int yy = 0; yy < h; yy += th) {
                for (int xx = 0; xx < w; xx += tw) {
                    int cw = Math.min(tw, w - xx);
                    int ch = Math.min(th, h - yy);
                    CompatRender.blit(g, texture, (int) (node.x() + xx), (int) (node.y() + yy), cw, ch, su, sv, Math.min(tw, cw), Math.min(th, ch), texW, texH);
                }
            }
        } else if (ns != null) {
            int left = (int) UiRenderer.num(ns.get("left"), 0);
            int right = (int) UiRenderer.num(ns.get("right"), 0);
            int top = (int) UiRenderer.num(ns.get("top"), 0);
            int bottom = (int) UiRenderer.num(ns.get("bottom"), 0);
            int midW = Math.max(0, sw - left - right);
            int midH = Math.max(0, sh - top - bottom);
            if (midW > 0 && midH > 0) {
                CompatRender.blit(g, texture, (int) (node.x() + left), (int) (node.y() + top),
                        (int) (w - left - right), (int) (h - top - bottom),
                        su + left, sv + top, midW, midH, texW, texH);
            }
            CompatRender.blit(g, texture, (int) node.x(), (int) node.y(), left, top, su, sv, left, top, texW, texH);
            CompatRender.blit(g, texture, (int) (node.x() + w - right), (int) node.y(), right, top, su + sw - right, sv, right, top, texW, texH);
            CompatRender.blit(g, texture, (int) node.x(), (int) (node.y() + h - bottom), left, bottom, su, sv + sh - bottom, left, bottom, texW, texH);
            CompatRender.blit(g, texture, (int) (node.x() + w - right), (int) (node.y() + h - bottom), right, bottom, su + sw - right, sv + sh - bottom, right, bottom, texW, texH);
            if (midW > 0) {
                CompatRender.blit(g, texture, (int) (node.x() + left), (int) node.y(), (int) (w - left - right), top, su + left, sv, midW, top, texW, texH);
                CompatRender.blit(g, texture, (int) (node.x() + left), (int) (node.y() + h - bottom), (int) (w - left - right), bottom, su + left, sv + sh - bottom, midW, bottom, texW, texH);
            }
            if (midH > 0) {
                CompatRender.blit(g, texture, (int) node.x(), (int) (node.y() + top), left, (int) (h - top - bottom), su, sv + top, left, midH, texW, texH);
                CompatRender.blit(g, texture, (int) (node.x() + w - right), (int) (node.y() + top), right, (int) (h - top - bottom), su + sw - right, sv + top, right, midH, texW, texH);
            }
        } else {
            CompatRender.blit(g, texture, (int) node.x(), (int) node.y(), w, h, su, sv, sw, sh, texW, texH);
        }
        CompatRender.posePop(pose);
    }

    /** 快捷栏：渲染玩家 9 格物品 + 当前选中高亮，点击切换（样式可配）。 */
    public static void drawHotSlot(GuiGraphics g, Font font, RenderNode node) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Map<?, ?> spec = UiRenderer.propsMap(node, "hot_slot");
        int slots = Math.max(1, (int) UiRenderer.num(spec.get("slots"), 9));
        int selectedColor = UiStyle.color(spec.get("selectedColor"), 0xFF3A4254);
        int slotColor = UiStyle.color(spec.get("slotColor"), 0xFF181C24);
        int accent = UiStyle.color(spec.get("accent"), 0xFF7A8BFF);
        int topBorder = UiStyle.color(spec.get("borderColor"), 0xFF505868);
        int bottomDim = UiStyle.color(spec.get("borderColor"), 0xFF20242C);
        double cellRadius = UiRenderer.num(spec.get("radius"), 0);
        double cellW = node.width() / slots;
        int selected = CompatRender.invSelectedIndex(player.getInventory());
        var inventory = (java.util.List<?>) CompatRender.invItems(player.getInventory());
        for (int i = 0; i < slots; i++) {
            int x = (int) (node.x() + i * cellW);
            int y = (int) node.y();
            int w = (int) cellW;
            int h = (int) node.height();
            boolean sel = i == selected;
            int bg = sel ? selectedColor : slotColor;
            if (cellRadius > 0) {
                double r = Math.min(cellRadius, Math.min(w, h) / 2);
                if (sel) {
                    UiRenderer.fillRounded(g, x, y, w, h, r, accent); // 选中：accent 外圈
                    UiRenderer.fillRounded(g, x + 2, y + 2, w - 4, h - 4, Math.max(0, r - 2), bg);
                } else {
                    UiRenderer.fillRounded(g, x, y, w, h, r, bg);
                }
            } else {
                g.fill(x, y, x + w, y + h, bg);
                g.fill(x, y, x + w, y + 1, sel ? accent : topBorder);
                g.fill(x, y + h - 1, x + w, y + h, sel ? accent : bottomDim);
            }
            if (i < inventory.size()) {
                var stack = (net.minecraft.world.item.ItemStack) inventory.get(i);
                if (!stack.isEmpty()) {
                    int icon = Math.min(16, (int) Math.min(cellW - 4, h - 4));
                    int ix = x + (w - icon) / 2;
                    int iy = y + (h - icon) / 2;
                    var pose = g.pose();
                    CompatRender.posePush(pose);
                    CompatRender.poseTranslate(pose, ix, iy);
                    CompatRender.poseScale(pose, icon / 16.0F, icon / 16.0F);
                    g.renderItem(stack, 0, 0);
                    if (stack.getCount() > 1) {
                        g.renderItemDecorations(font, stack, 0, 0);
                    }
                    CompatRender.posePop(pose);
                }
            }
        }
    }

    /** 聊天输入框：同 input（placeholder/圆角/描边/光标），回车发送聊天。 */
    public static void drawChatInput(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "chat_input");
        String text = state == null ? "" : state.inputText(node.id());
        if (text == null) {
            text = "";
        }
        boolean focused = state != null && state.focused(node.id());
        InputDraws.drawInputBox(g, node, spec, focused);
        String prefix = UiRenderer.str(spec.get("prefix"));
        String shown = (prefix == null ? "" : prefix) + text;
        int textColor = UiStyle.color(spec.get("textColor"), 0xFFFFFFFF);
        if (shown.isEmpty()) {
            String ph = UiRenderer.interpolate(node, UiRenderer.str(spec.get("placeholder")), null);
            if (ph != null && !ph.isEmpty()) {
                g.drawString(font, ph, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2),
                        UiRenderer.alphaColor(0xFF707880));
            }
        } else {
            g.drawString(font, shown, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2),
                    UiRenderer.alphaColor(textColor));
        }
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = (int) node.x() + 4 + font.width(shown);
            g.fill(cx, (int) node.y() + 4, cx + 1, (int) (node.y() + node.height() - 4),
                    UiRenderer.alphaColor(textColor));
        }
    }

    /** 聊天显示区：底部对齐显示最近聊天（RichText 渲染，支持行内多色；channel 过滤通道消息）。 */
    public static void drawChatDisplay(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "chat_display");
        // 背景（可选）
        Object bg = spec.get("background");
        if (bg != null) {
            int color = UiStyle.color(bg, 0);
            if (color != 0) {
                UiRenderer.fillRect(g, node, UiRenderer.alphaColor(color));
            }
        }
        String channel = UiRenderer.str(spec.get("channel"));
        java.util.List<String> messages;
        if (channel != null && !channel.isBlank() && !"all".equalsIgnoreCase(channel)) {
            messages = ClientController.get().chatStore().messages(channel); // 服务端通道
        } else {
            messages = ClientController.get().latestChat(200); // 全局聊天缓存
        }
        if (messages.isEmpty()) {
            return;
        }
        double lineH = UiRenderer.num(spec.get("lineHeight"), 9);
        int maxLines = Math.max(1, (int) (node.height() / lineH));
        int start = Math.max(0, messages.size() - maxLines);
        double y = node.y() + node.height() - lineH;
        for (int i = messages.size() - 1; i >= start; i--) {
            // 占位符插值（{player.*}/{system.*}/{color.*} 等按接收者解析）后画富文本
            String line = UiRenderer.interpolate(node, messages.get(i), null);
            drawRichLine(g, font, line, (int) node.x() + 2, (int) y);
            y -= lineH;
        }
    }

    /** 画一行富文本（按 RichText 片段累进 x，行内多色）。 */
    public static void drawRichLine(GuiGraphics g, Font font, String legacy, int x, int y) {
        int cx = x;
        for (com.opendreamcore.script.RichText.Segment seg : com.opendreamcore.script.RichText.parse(legacy)) {
            if (seg.text().isEmpty()) {
                continue;
            }
            g.drawString(font, seg.text(), cx, y, UiRenderer.alphaColor(0xFF000000 | seg.color()));
            cx += font.width(seg.text());
        }
    }

    public static void drawItemSlot(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF101318));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
        UiRenderer.drawItemIcon(g, font, node, node.props().get("item"), false, pageVars);
    }

    public static void drawItemDisplay(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "item_display");
        Map<?, ?> rot = spec.get("rotation") instanceof Map<?, ?> rm ? rm
                : spec.get("rotate") instanceof Map<?, ?> rm2 ? rm2 : null;
        if (rot != null) {
            double size = Math.max(16, node.width() * 0.7);
            double ix = node.x() + (node.width() - size) / 2;
            double iy = node.y() + (node.height() - size) / 2;
            UiRenderer.drawItemAtRot(g, font, ix, iy, size, spec.get("item"), pageVars, rot);
        } else {
            UiRenderer.drawItemIcon(g, font, node, spec.get("item"), true, pageVars);
        }
    }

    /** 容器槽位：显示服务端 container_sync 推送的真实容器物品；点击事件带槽位号。 */
    public static void drawChestSlot(GuiGraphics g, Font font, RenderNode node) {
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF101318));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
        Map<?, ?> spec = UiRenderer.propsMap(node, "chest_slot");
        int slot = spec.get("slot") instanceof Number n ? n.intValue() : 0;
        ContainerStore.ContainerData data = ClientController.get().containerStore()
                .get(ClientController.get().currentSessionId());
        if (data != null) {
            ContainerStore.SlotData slotData = data.slot(slot);
            if (slotData != null && slotData.itemId() != null && !slotData.itemId().isEmpty()) {
                double size = Math.min(16, Math.min(node.width(), node.height()));
                double ix = node.x() + (node.width() - size) / 2;
                double iy = node.y() + (node.height() - size) / 2;
                UiRenderer.drawItemAt(g, font, ix, iy, size, slotData.itemId() + " x" + slotData.count(), null);
            }
        }
        if (UiRenderer.bool(spec.get("showSlot"), false)) {
            g.drawString(font, String.valueOf(slot), (int) node.x() + 2, (int) node.y() + 1, UiRenderer.alphaColor(0xFF6B7280));
        }
    }
}
