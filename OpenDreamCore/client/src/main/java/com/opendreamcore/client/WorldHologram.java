package com.opendreamcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * 世界全息渲染：display: world 页面在世界里画 billboard 文本/图片。
 * 页面锚点默认玩家前方 3 格（options.world.offsetX/Y/Z 可调），
 * 元素 hologram: {x, y, z, scale} 相对锚点。
 * 目前渲染 text；其余类型跳过（后续补矩形/图片）。
 */
public final class WorldHologram {

    private WorldHologram() {
    }

    /**
     * RenderLevelStageEvent 里调用。camera 用于对齐视角（billboard）。
     * 距离淡出：options.world.fadeDistance（米，0 = 关）+ fadeRange（淡出带，默认 3 米）。
     */
    /** 深度写开关：部分版本 RenderSystem 不再暴露 depthMask，走反射兼容。 */
    private static void rsDepthMask(boolean write) {
        try {
            com.mojang.blaze3d.systems.RenderSystem.class
                    .getMethod("depthMask", boolean.class).invoke(null, write);
        } catch (Throwable ignored) {
        }
    }

/** 混合函数复位：同上走反射。 */
    private static void rsDefaultBlendFunc() {
        try {
            com.mojang.blaze3d.systems.RenderSystem.class
                    .getMethod("defaultBlendFunc").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    /** 无参 RenderSystem 状态调用反射版（1.21.5+ 状态 API 重构，方法可能不存在，静默跳过）。 */
    private static void rsCall(String method) {
        try {
            com.mojang.blaze3d.systems.RenderSystem.class.getMethod(method).invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick) {
        render(nodes, options, camera, partialTick, null, null, null, null);
    }

    /**
     * RenderLevelStageEvent 里调用。camera 用于对齐视角（billboard）。
     * hoverId 非空时该元素画内置高亮框（世界面板悬停反馈）。
     * scope = 页面 id：动画按页面作用域隔离；pageVars = 页面变量（{vars.xxx} 插值用）。
     * dragOffsets：元素 id → [dx, dy, dz]（世界拖拽中的实时偏移）。
     * selectionId 非空时该元素画选中框（WYSIWYG 编辑模式）。
     * 距离淡出：options.world.fadeDistance（米，0 = 关）+ fadeRange（淡出带，默认 3 米）。
     */
    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick, String hoverId,
                              String scope, java.util.Map<String, Object> pageVars,
                              Map<String, double[]> dragOffsets) {
        render(nodes, options, camera, partialTick, hoverId, scope, pageVars, dragOffsets, null, null, 1.0);
    }

    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick, String hoverId,
                              String scope, java.util.Map<String, Object> pageVars,
                              Map<String, double[]> dragOffsets, String selectionId) {
        render(nodes, options, camera, partialTick, hoverId, scope, pageVars, dragOffsets, selectionId, null, 1.0);
    }

    /**
     * 全量渲染入口。activeTab 非空时：带 tab 属性的元素只在匹配页签下渲染/可拾取
     * （世界面板多页签：`tab: "商店"` 的元素仅激活页签显示，`type: tabs` 页签栏始终显示）。
     * tabReveal（0~1，1 = 无过渡）：切页签后带 tab 的元素淡入进度（easeOutCubic 由调用方算好）。
     */
    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick, String hoverId,
                              String scope, java.util.Map<String, Object> pageVars,
                              Map<String, double[]> dragOffsets, String selectionId, String activeTab) {
        render(nodes, options, camera, partialTick, hoverId, scope, pageVars, dragOffsets, selectionId, activeTab, 1.0);
    }

    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick, String hoverId,
                              String scope, java.util.Map<String, Object> pageVars,
                              Map<String, double[]> dragOffsets, String selectionId, String activeTab,
                              double tabReveal) {
        render(nodes, options, camera, partialTick, hoverId, scope, pageVars, dragOffsets, selectionId, activeTab,
                tabReveal, null);
    }

    /** anchorOverride 非空时用调用方提供的锚点（平滑跟随/固定锚点模式），否则按 options 计算。 */
    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick, String hoverId,
                              String scope, java.util.Map<String, Object> pageVars,
                              Map<String, double[]> dragOffsets, String selectionId, String activeTab,
                              double tabReveal, Vec3 anchorOverride) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 页面锚点：相对玩家偏移（或绝对 anchor / 调用方平滑锚点）
        Vec3 anchor = anchorOverride != null ? anchorOverride : anchor(mc, options);

        // 距离淡出（世界面板离远了逐渐透明）+ 面板级透明度（world.alpha 0~1 乘算）
        double fade = 1.0;
        Object worldOpt = options == null ? null : options.get("world");
        // 深度模式：occluded（默认，被遮挡不显示）/ transparent（被遮挡半透明）/ always（始终穿透）
        String depthMode = "occluded";
        double occludedAlpha = 0.25; // transparent 模式下被遮挡时的透明度
        if (worldOpt instanceof Map<?, ?> w) {
            double panelAlpha = num(w.get("alpha"), 1.0);
            fade = Math.max(0, Math.min(1, panelAlpha));
            double fadeDistance = num(w.get("fadeDistance"), 0);
            if (fadeDistance > 0) {
                double dist = mc.player.position().distanceTo(anchor);
                double range = num(w.get("fadeRange"), 3);
                if (dist > fadeDistance) {
                    fade = Math.max(0, 1 - (dist - fadeDistance) / Math.max(range, 0.1));
                    if (fade <= 0) {
                        return; // 完全淡出不画
                    }
                }
            }
            // 深度模式解析
            Object dmRaw = w.get("depthMode");
            String dm = dmRaw == null ? null : String.valueOf(dmRaw);
            if (dm != null && !dm.isBlank() && !"null".equals(dm)) {
                depthMode = dm.trim().toLowerCase();
            }
            occludedAlpha = num(w.get("occludedAlpha"), 0.25);
        }
        currentDepthMode = depthMode;
        currentOccludedAlpha = occludedAlpha;

        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // 世界面板统一渲染状态：混合开启（半透明背景可见）、双面（背对不消失）、深度只测不写
        rsCall("enableBlend");
        rsDefaultBlendFunc();
        rsCall("disableCull");
        rsDepthMask(false);
        try {
            // 深度模式控制：
            // - occluded（默认）：启用深度测试，被遮挡的元素不显示
            // - always：禁用深度测试，元素始终穿透方块全亮显示
            // - transparent：两遍渲染——先深度测试画可见部分，再禁用深度测试以低透明度画被遮挡部分
            boolean transparentMode = "transparent".equals(depthMode);
            applyContentDepth();
            // 按下缩放反馈（元素按左键时 scale * 0.95；渲染线程单帧字段，按页键控）
            String pressedId = com.opendreamcore.client.ClientController.get().worldPressedId(scope);
            currentPressedKey = pressedId == null ? null
                    : com.opendreamcore.client.ClientController.wkey(scope, pressedId);
            // 悬停高亮颜色（options.world.hoverColor，缺省亮蓝；渲染线程单帧字段）
            Object hoverColor = worldOpt instanceof Map<?, ?> w ? w.get("hoverColor") : null;
            if (hoverColor != null) {
                currentHoverColor = UiStyle.color(hoverColor, currentHoverColor);
            }
            // 背景遮罩：options.world.background（颜色或 {color, padding, border}）；按住 U 时隐藏看全貌
            Object bg = worldOpt instanceof Map<?, ?> w ? w.get("background") : null;
            if (bg != null && !com.opendreamcore.client.ClientController.get().worldBackgroundHidden()) {
                // 背景淡入动画：配置签名变更后 300ms alpha 缓入（避免换色闪变）
                float bgFadeIn = 1.0F;
                String sig = String.valueOf(bg);
                long now = System.currentTimeMillis();
                String prevSig = backgroundFadeSig.get(scope);
                if (!sig.equals(prevSig)) {
                    backgroundFadeAt.put(scope, now);
                    backgroundFadeSig.put(scope, sig);
                    prevSig = sig;
                }
                Long at = backgroundFadeAt.get(scope);
                if (at != null) {
                    bgFadeIn = Math.max(0, Math.min(1, (now - at) / 300.0F));
                }
                renderBackground(pose, nodes, activeTab, fade, pageVars, bg, bgFadeIn);
            }
            // 海报语义：按 hologram.z 升序绘制（远→近画家算法），同 z 保持声明顺序——
            // 换视角时元素间不再因 3D 深度变化互相覆盖（墙面遮挡仍由深度测试保证）
            List<RenderNode> ordered = new java.util.ArrayList<>(nodes);
            ordered.sort(java.util.Comparator.comparingDouble(n -> holoNum(holo(n), "z", 0, pageVars)));
            // 主渲染循环
            for (RenderNode node : ordered) {
                renderNode(pose, buffers, node, fade, hoverId, selectionId, activeTab, tabReveal,
                        scope, pageVars, dragOffsets, null);
            }
            for (RenderNode node : nodes) {
                renderNode(pose, buffers, node, fade, hoverId, selectionId, activeTab, tabReveal,
                        scope, pageVars, dragOffsets, null);
            }
            buffers.endBatch();

            // transparent 模式：第二遍——禁用深度测试，用低透明度重画被遮挡的元素
            if (transparentMode) {
                noDepthPass = true;
                CompatRender.disableDepthTest();
                double occludedFade = fade * occludedAlpha;
                for (RenderNode node : ordered) {
                    renderNode(pose, buffers, node, occludedFade, hoverId, selectionId, activeTab, tabReveal,
                            scope, pageVars, dragOffsets, null);
                }
                buffers.endBatch();
                noDepthPass = false;
            }
        // 面板绘制结束：恢复全局渲染状态（blend/cull/深度写回）
        rsDepthMask(true);
        rsCall("enableCull");
        rsCall("disableBlend");
        } catch (Exception ignored) {
            // 全息渲染出错不拖垮帧
        }
    }

    /** 悬停高亮颜色（world.hoverColor 可配，缺省亮蓝；渲染线程单帧字段）。 */
    public static int currentHoverColor = 0xAA7A8BFF;
    /** 当前按下的元素（pageId/elementId 键；按下缩放反馈；渲染线程单帧字段）。 */
    private static String currentPressedKey;
    /** 深度模式（occluded/transparent/always；渲染线程单帧字段）。 */
    private static String currentDepthMode = "occluded";
    /** transparent 模式下被遮挡时的透明度（渲染线程单帧字段）。 */
    private static double currentOccludedAlpha = 0.25;
    /** 背景淡入动画状态（scope → 配置签名/变更时间；渲染线程字段）。 */
    private static final java.util.Map<String, String> backgroundFadeSig = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> backgroundFadeAt = new java.util.concurrent.ConcurrentHashMap<>();
    /** transparent 深度模式第二遍标记（渲染线程单帧；true = 内容强制无深度绘制被遮挡残影）。 */
    private static boolean noDepthPass;

    /**
     * 内容绘制深度状态（修复"全息穿墙"）：
     * <ul>
     *   <li>occluded（默认）→ 开启深度测试，被方块遮挡的内容不显示</li>
     *   <li>always → 关闭深度测试，内容穿透方块全亮显示</li>
     *   <li>transparent 第二遍（noDepthPass）→ 关闭深度测试画低透明残影</li>
     * </ul>
     * 编辑浮层（手柄/框选/参考线等）仍显式 disableDepthTest，保证隔墙可编辑。
     */
    public static void applyContentDepth() {
        if (noDepthPass || "always".equals(currentDepthMode)) {
            CompatRender.disableDepthTest();
        } else {
            CompatRender.enableDepthTest();
        }
    }

    /**
     * billboard 对齐 + 静态旋转（hologram.yaw，度，绕元素中心）+ 动画旋转叠加。
     * 全部世界元素共用：text/rect/image/video/toggle/slider/checkbox/dropdown/
     * progress/item_slot/tabs 等（entity 用 setYRot 走自己的旋转）。
     */
    public static void applyBillboardRotation(PoseStack pose, Map<?, ?> holo, double[] anim,
                                               java.util.Map<String, Object> pageVars) {
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        double yaw = holoNum(holo, "yaw", 0, pageVars);
        if (yaw != 0) {
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) yaw));
        }
        if (anim != null && anim[4] != 0) {
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) anim[4]));
        }
    }

    /** 编辑模式旋转手柄（委托到 WorldHoloEdit）。 */
    public static void renderRotateHandle(net.minecraft.client.Camera camera, Map<String, Object> options,
                                          double[] center, double[] handle) {
        WorldHoloEdit.renderRotateHandle(camera, options, center, handle);
    }
















    /** 页面锚点（公开：屏幕外箭头投影用）。 */
    public static Vec3 anchorFor(net.minecraft.client.Camera camera, Map<String, Object> options) {
        return anchor(Minecraft.getInstance(), options);
    }

    /**
     * 背景遮罩（options.world.background）：按当前可见元素（tab 过滤）的包围盒
     * 画 billboard 半透明底 + 可选边框，先于元素绘制（视觉垫底）。
     * 用法：background: "#10151FCC" 或 {color: "#10151FCC", padding: 0.25, border: "#3A4A66"}
     */
    private static void renderBackground(PoseStack pose, List<RenderNode> nodes, String activeTab, double fade,
                                         java.util.Map<String, Object> pageVars, Object bg, double bgFadeIn) {
        double[] bounds = collectBounds(nodes, activeTab, pageVars, null);
        if (bounds == null) {
            return;
        }
        double minX = bounds[0], minY = bounds[1], maxX = bounds[2], maxY = bounds[3];
        int color = 0xCC10151F;
        int border = 0;
        double padding = 0.25;
        double radius = 0;
        float borderWidth = 0.02F;
        int borderGlow = 0;
        double borderGlowSize = 0.06;
        int gradient = 0; // 0 = 纯色；非 0 = 上下渐变（顶 color → 底 gradient）
        if (bg instanceof Map<?, ?> m) {
            color = UiStyle.color(m.get("color"), color);
            padding = num(m.get("padding"), padding);
            border = UiStyle.color(m.get("border"), 0);
            radius = num(m.get("radius"), 0);
            borderWidth = (float) num(m.get("borderWidth"), 0.02);
            borderGlow = UiStyle.color(m.get("borderGlow"), 0);
            borderGlowSize = num(m.get("borderGlowSize"), 0.06);
            gradient = UiStyle.color(m.get("gradient"), 0);
        } else {
            color = UiStyle.color(bg, color);
        }
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        double hw = (maxX - minX) / 2 + padding;
        double hh = (maxY - minY) / 2 + padding;
        // 圆角不超过短边一半
        double r = Math.max(0, Math.min(radius, Math.min(hw, hh)));
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        // 圆角矩形用 TRIANGLES 批次（中央 + 四边 + 四角扇形），支持上下渐变
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        float a = ((color >>> 24) & 0xFF) / 255.0F * (float) fade * (float) bgFadeIn;
        if (a > 0) {
            if (gradient != 0) {
                boolean gradH = "horizontal".equals(String.valueOf(
                        bg instanceof Map<?, ?> m2 ? m2.get("gradientDir") : null));
                int midArgb = 0;
                float midPos = 0.5F;
                if (bg instanceof Map<?, ?> m3 && m3.get("gradientMid") != null) {
                    midArgb = UiStyle.color(m3.get("gradientMid"), 0);
                    midPos = (float) num(m3.get("gradientMidPos"), 0.5);
                }
                roundedQuadTrisGrad(builder, matrix, (float) -hw, (float) -hh, (float) hw, (float) hh,
                        (float) r, color, gradient, (float) -hh, (float) hh, (float) fade * (float) bgFadeIn, gradH,
                        midArgb, midPos);
            } else {
                roundedQuadTris(builder, matrix, (float) -hw, (float) -hh, (float) hw, (float) hh,
                        (float) r, ((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F, a);
            }
        }
        if (border != 0) {
            float ba = ((border >>> 24) & 0xFF) / 255.0F * (float) fade * (float) bgFadeIn;
            if (ba > 0) {
                float t = Math.max(0.005F, borderWidth); // 可配 borderWidth（world 单位）
                float br = ((border >> 16) & 0xFF) / 255.0F;
                float bg2 = ((border >> 8) & 0xFF) / 255.0F;
                float bb = (border & 0xFF) / 255.0F;
                quadTris(builder, matrix, (float) -hw, (float) -hh, (float) hw, (float) (-hh + t), br, bg2, bb, ba);
                quadTris(builder, matrix, (float) -hw, (float) (hh - t), (float) hw, (float) hh, br, bg2, bb, ba);
                quadTris(builder, matrix, (float) -hw, (float) -hh, (float) (-hw + t), (float) hh, br, bg2, bb, ba);
                quadTris(builder, matrix, (float) (hw - t), (float) -hh, (float) hw, (float) hh, br, bg2, bb, ba);
            }
        }
        // 边框发光（borderGlow 色 + borderGlowSize 扩散层，紧贴外沿向外 4 层衰减）
        if (borderGlow != 0) {
            float ga0 = ((borderGlow >>> 24) & 0xFF) / 255.0F * (float) fade * (float) bgFadeIn;
            if (ga0 > 0) {
                float gs = Math.max(0.01F, (float) borderGlowSize);
                float gr2 = ((borderGlow >> 16) & 0xFF) / 255.0F;
                float gg2 = ((borderGlow >> 8) & 0xFF) / 255.0F;
                float gb2 = (borderGlow & 0xFF) / 255.0F;
                int layers = 4;
                for (int i = 0; i < layers; i++) {
                    float off = gs * (i + 1) / layers;
                    float al = ga0 * (1 - (i + 1) / (float) (layers + 1)) * 0.5F;
                    quadTris(builder, matrix, (float) (-hw - off), (float) (-hh - off),
                            (float) (hw + off), (float) -hh, gr2, gg2, gb2, al);
                    quadTris(builder, matrix, (float) (-hw - off), (float) hh,
                            (float) (hw + off), (float) (hh + off), gr2, gg2, gb2, al);
                    quadTris(builder, matrix, (float) (-hw - off), (float) (-hh - off),
                            (float) -hw, (float) (hh + off), gr2, gg2, gb2, al);
                    quadTris(builder, matrix, (float) hw, (float) (-hh - off),
                            (float) (hw + off), (float) (hh + off), gr2, gg2, gb2, al);
                }
            }
        }
        drawSafe(builder);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 四边形（TRIANGLES 批次：2 个三角形）。 */
    public static void quadTris(CompatBuffer builder, org.joml.Matrix4f matrix,
                                 float x0, float y0, float x1, float y1,
                                 float red, float green, float blue, float alpha) {
        builder.addVertex(matrix, x0, y0, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x1, y0, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x1, y1, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x0, y0, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x1, y1, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, x0, y1, 0).setColor(red, green, blue, alpha);
    }

    /** 圆角矩形（TRIANGLES）：中央 + 四边 + 四角四分之一圆扇形（每角 6 段）。 */
    private static void roundedQuadTris(CompatBuffer builder, org.joml.Matrix4f matrix,
                                        float x0, float y0, float x1, float y1, float r,
                                        float red, float green, float blue, float alpha) {
        if (r <= 0.01F) {
            quadTris(builder, matrix, x0, y0, x1, y1, red, green, blue, alpha);
            return;
        }
        float cx0 = x0 + r, cy0 = y0 + r, cx1 = x1 - r, cy1 = y1 - r;
        quadTris(builder, matrix, cx0, cy0, cx1, cy1, red, green, blue, alpha); // 中央
        quadTris(builder, matrix, cx0, y0, cx1, cy0, red, green, blue, alpha); // 上
        quadTris(builder, matrix, cx0, cy1, cx1, y1, red, green, blue, alpha); // 下
        quadTris(builder, matrix, x0, cy0, cx0, cy1, red, green, blue, alpha); // 左
        quadTris(builder, matrix, cx1, cy0, x1, cy1, red, green, blue, alpha); // 右
        cornerFan(builder, matrix, cx1, cy1, r, 0, 90, red, green, blue, alpha);   // 右上
        cornerFan(builder, matrix, cx0, cy1, r, 90, 180, red, green, blue, alpha); // 左上
        cornerFan(builder, matrix, cx0, cy0, r, 180, 270, red, green, blue, alpha);// 左下
        cornerFan(builder, matrix, cx1, cy0, r, 270, 360, red, green, blue, alpha);// 右下
    }

    /** 四分之一圆扇形（角度制，逆时针，中心 + 弧段三角形）。 */
    private static void cornerFan(CompatBuffer builder, org.joml.Matrix4f matrix,
                                  float cx, float cy, float r, double a0, double a1,
                                  float red, float green, float blue, float alpha) {
        int seg = 6;
        float prevX = cx + (float) Math.cos(Math.toRadians(a0)) * r;
        float prevY = cy + (float) Math.sin(Math.toRadians(a0)) * r;
        for (int i = 1; i <= seg; i++) {
            float ang = (float) Math.toRadians(a0 + (a1 - a0) * i / seg);
            float nx = cx + (float) Math.cos(ang) * r;
            float ny = cy + (float) Math.sin(ang) * r;
            builder.addVertex(matrix, cx, cy, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, prevX, prevY, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, nx, ny, 0).setColor(red, green, blue, alpha);
            prevX = nx;
            prevY = ny;
        }
    }

    // ---------- 渐变版本（顶点颜色插值：vertical = 顶 color → 底 gradient；horizontal = 左 → 右） ----------

    private static void gradVertex(CompatBuffer builder, org.joml.Matrix4f matrix,
                                   float x, float y, int topArgb, int bottomArgb, float yTop, float yBot,
                                   float alphaMul, boolean horizontal, float xA, float xB,
                                   int midArgb, float midPos) {
        float t = horizontal
                ? (xB > xA ? (x - xA) / (xB - xA) : 0)
                : (yBot > yTop ? (y - yTop) / (yBot - yTop) : 0);
        t = Math.max(0, Math.min(1, t));
        int c;
        if (midArgb != 0 && midPos > 0 && midPos < 1) {
            c = t < midPos
                    ? lerpColor(topArgb, midArgb, t / midPos)
                    : lerpColor(midArgb, bottomArgb, (t - midPos) / (1 - midPos));
        } else {
            c = lerpColor(topArgb, bottomArgb, t);
        }
        float r = ((c >> 16) & 0xFF) / 255.0F;
        float g = ((c >> 8) & 0xFF) / 255.0F;
        float b = (c & 0xFF) / 255.0F;
        float a = ((c >>> 24) & 0xFF) / 255.0F * alphaMul;
        builder.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
    }

    /** ARGB 线性插值。 */
    public static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    private static void quadTrisGrad(CompatBuffer builder, org.joml.Matrix4f matrix,
                                     float x0, float y0, float x1, float y1,
                                     int topArgb, int bottomArgb, float yTop, float yBot, float alphaMul,
                                     boolean horizontal, int midArgb, float midPos) {
        gradVertex(builder, matrix, x0, y0, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        gradVertex(builder, matrix, x1, y0, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        gradVertex(builder, matrix, x1, y1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        gradVertex(builder, matrix, x0, y0, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        gradVertex(builder, matrix, x1, y1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        gradVertex(builder, matrix, x0, y1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
    }

    /** 圆角矩形（渐变）：中央 + 四边 + 四角扇形，顶点颜色按 y 插值。 */
    private static void roundedQuadTrisGrad(CompatBuffer builder, org.joml.Matrix4f matrix,
                                            float x0, float y0, float x1, float y1, float r,
                                            int topArgb, int bottomArgb, float yTop, float yBot, float alphaMul,
                                            boolean horizontal, int midArgb, float midPos) {
        if (r <= 0.01F) {
            quadTrisGrad(builder, matrix, x0, y0, x1, y1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, midArgb, midPos);
            return;
        }
        float cx0 = x0 + r, cy0 = y0 + r, cx1 = x1 - r, cy1 = y1 - r;
        quadTrisGrad(builder, matrix, cx0, cy0, cx1, cy1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, midArgb, midPos);
        quadTrisGrad(builder, matrix, cx0, y0, cx1, cy0, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, midArgb, midPos);
        quadTrisGrad(builder, matrix, cx0, cy1, cx1, y1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, midArgb, midPos);
        quadTrisGrad(builder, matrix, x0, cy0, cx0, cy1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, midArgb, midPos);
        quadTrisGrad(builder, matrix, cx1, cy0, x1, cy1, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, midArgb, midPos);
        cornerFanGrad(builder, matrix, cx1, cy1, r, 0, 90, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        cornerFanGrad(builder, matrix, cx0, cy1, r, 90, 180, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        cornerFanGrad(builder, matrix, cx0, cy0, r, 180, 270, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
        cornerFanGrad(builder, matrix, cx1, cy0, r, 270, 360, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, x0, x1, midArgb, midPos);
    }

    /** 四分之一圆扇形（渐变）。 */
    private static void cornerFanGrad(CompatBuffer builder, org.joml.Matrix4f matrix,
                                      float cx, float cy, float r, double a0, double a1,
                                      int topArgb, int bottomArgb, float yTop, float yBot, float alphaMul,
                                      boolean horizontal, float xA, float xB, int midArgb, float midPos) {
        int seg = 6;
        float prevX = cx + (float) Math.cos(Math.toRadians(a0)) * r;
        float prevY = cy + (float) Math.sin(Math.toRadians(a0)) * r;
        for (int i = 1; i <= seg; i++) {
            float ang = (float) Math.toRadians(a0 + (a1 - a0) * i / seg);
            float nx = cx + (float) Math.cos(ang) * r;
            float ny = cy + (float) Math.sin(ang) * r;
            gradVertex(builder, matrix, cx, cy, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, xA, xB, midArgb, midPos);
            gradVertex(builder, matrix, prevX, prevY, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, xA, xB, midArgb, midPos);
            gradVertex(builder, matrix, nx, ny, topArgb, bottomArgb, yTop, yBot, alphaMul, horizontal, xA, xB, midArgb, midPos);
            prevX = nx;
            prevY = ny;
        }
    }

    /** 可见元素包围盒（对齐工具用）：{minX, minY, maxX, maxY}，无元素返回 null。 */
    public static double[] visibleBounds(List<RenderNode> nodes, String activeTab,
                                         java.util.Map<String, Object> pageVars) {
        return collectBounds(nodes, activeTab, pageVars, null);
    }

    /** 可见元素（tab 过滤 + 有 hologram）包围盒 {minX, minY, maxX, maxY}，无元素返回 null。 */
    private static double[] collectBounds(List<RenderNode> nodes, String activeTab,
                                          java.util.Map<String, Object> pageVars, double[] parentOffset) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (RenderNode node : nodes) {
            if (!node.visible() || !tabVisible(node, activeTab)) {
                continue;
            }
            Map<?, ?> holo = holo(node);
            double baseX = parentOffset == null ? 0 : parentOffset[0];
            double baseY = parentOffset == null ? 0 : parentOffset[1];
            double[] childOffset = parentOffset;
            if (!holo.isEmpty()) {
                double x = baseX + holoNum(holo, "x", 0, pageVars);
                double y = baseY + holoNum(holo, "y", 0, pageVars);
                double w;
                double h;
                if ("text".equals(node.type())) {
                    double[] sz = textAutoSize(node, pageVars); // wrap 折行自适应
                    w = sz[0];
                    h = sz[1];
                } else {
                    w = holoNum(holo, "width", 1.0, pageVars);
                    h = holoNum(holo, "height", 1.0, pageVars);
                }
                minX = Math.min(minX, x - w / 2);
                maxX = Math.max(maxX, x + w / 2);
                minY = Math.min(minY, y - h / 2);
                maxY = Math.max(maxY, y + h / 2);
                any = true;
                childOffset = new double[]{x, y, parentOffset == null ? 0 : parentOffset[2] + holoNum(holo, "z", 0, pageVars)};
            }
            double[] sub = collectBounds(node.children(), activeTab, pageVars, childOffset);
            if (sub != null) {
                minX = Math.min(minX, sub[0]);
                minY = Math.min(minY, sub[1]);
                maxX = Math.max(maxX, sub[2]);
                maxY = Math.max(maxY, sub[3]);
            }
        }
        return any ? new double[]{minX, minY, maxX, maxY} : null;
    }

    static Vec3 anchor(Minecraft mc, Map<String, Object> options) {
        Object world = options == null ? null : options.get("world");
        double ox = 0, oy = 1.6, oz = 3;
        if (world instanceof Map<?, ?> w) {
            ox = anchorNum(w, "offsetX", ox);
            oy = anchorNum(w, "offsetY", oy);
            oz = anchorNum(w, "offsetZ", oz);
        }
        // 绝对锚点：world.anchor: {x, y, z} — 面板固定在世界坐标，不跟随玩家（offsetX/Y/Z 作为相对微调）
        if (world instanceof Map<?, ?> w && w.get("anchor") instanceof Map<?, ?> am) {
            double ax = anchorNum(am, "x", 0);
            double ay = anchorNum(am, "y", 0);
            double az = anchorNum(am, "z", 0);
            return new Vec3(ax + ox, ay + oy, az + oz);
        }
        var player = mc.player;
        double yaw = Math.toRadians(player.getYRot());
        double dx = -Math.sin(yaw) * oz;
        double dz = Math.cos(yaw) * oz;
        return player.position().add(dx + ox, oy, dz + oz);
    }

    /** 锚点偏移：数字 / 数字字符串 / 表达式（vars 驱动，世界面板整体移动）。 */
    private static double anchorNum(Map<?, ?> world, String key, double fallback) {
        Object v = world.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v).trim();
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(s);
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            worldVars().forEach(scope::assignVar);
            Object r = com.opendreamcore.script.DreamLang.evaluate(s, scope);
            if (r instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** 元素页签过滤：无 tab 属性始终可见；有 tab 属性仅激活页签下可见。 */
    public static boolean tabVisible(RenderNode node, String activeTab) {
        Object tab = node.props().get("tab");
        if (tab == null) {
            return true;
        }
        return activeTab != null && activeTab.equals(String.valueOf(tab));
    }

    private static void renderNode(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                   String hoverId, String selectionId, String activeTab, String scope,
                                   java.util.Map<String, Object> pageVars,
                                   Map<String, double[]> dragOffsets) {
        renderNode(pose, buffers, node, fade, hoverId, selectionId, activeTab, scope, pageVars, dragOffsets, null);
    }

    /**
     * parentOffset：父链上有 hologram 的元素位置累积（子元素相对父面板定位）。
     * 实现：并入 drag 通道传给各渲染方法（drag = 拖拽偏移 + 父偏移）。
     */
    private static void renderNode(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                   String hoverId, String selectionId, String activeTab, String scope,
                                   java.util.Map<String, Object> pageVars,
                                   Map<String, double[]> dragOffsets, double[] parentOffset) {
        renderNode(pose, buffers, node, fade, hoverId, selectionId, activeTab, 1.0, scope, pageVars,
                dragOffsets, parentOffset);
    }

    private static void renderNode(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                   String hoverId, String selectionId, String activeTab, double tabReveal,
                                   String scope, java.util.Map<String, Object> pageVars,
                                   Map<String, double[]> dragOffsets, double[] parentOffset) {
        if (!node.visible() || !tabVisible(node, activeTab)
                || !com.opendreamcore.client.ClientController.get().worldElementVisible(scope, node.id())) {
            return;
        }
        // 页签切换过渡：带 tab 属性的元素按淡入进度乘算透明度（公共区/页签栏不受影响）
        double effFade = node.props().get("tab") == null ? fade : fade * tabReveal;
        if (effFade <= 0) {
            return;
        }
        // 编辑模式 H 透视：选中元素半透明（露出下层元素）
        double ghost = com.opendreamcore.client.ClientController.get().worldElementGhostFade(scope, node.id());
        if (ghost < 1.0) {
            effFade *= ghost;
        }
        String nodeKey = com.opendreamcore.client.ClientController.wkey(scope, node.id());
        double[] drag = dragOffsets == null ? null : dragOffsets.get(nodeKey);
        boolean hovered = hoverId != null && hoverId.equals(node.id())
                || dragOffsets != null && dragOffsets.containsKey(nodeKey); // 拖拽中元素也高亮
        boolean selected = (selectionId != null && selectionId.equals(node.id()))
                || com.opendreamcore.client.ClientController.get().worldElementMultiSelected(scope, node.id());
        // 合成偏移：拖拽偏移 + 父链偏移
        double[] effective = mergeDrag(drag, parentOffset);
        // 子元素的父偏移 = 父链偏移 + 本元素 hologram 位置（本元素有 hologram 时子元素相对它定位）
        double[] childParent = parentOffset;
        Map<?, ?> ownHolo = holo(node);
        if (!ownHolo.isEmpty()) {
            double[] own = {holoNum(ownHolo, "x", 0, pageVars), holoNum(ownHolo, "y", 0, pageVars),
                    holoNum(ownHolo, "z", 0, pageVars)};
            childParent = childParent == null ? own
                    : new double[]{childParent[0] + own[0], childParent[1] + own[1], childParent[2] + own[2]};
        }
        if ("text".equals(node.type())) {
            com.opendreamcore.client.world.HoloTextRender.renderText(pose, buffers, node, effFade, scope, pageVars, effective);
            if (hovered) {
                com.opendreamcore.client.world.HoloDecor.renderHoverOutline(pose, node, effFade, false, pageVars);
            }
        } else if ("entity".equals(node.type())) {
            // 实体无法直接调透明度：淡出过深时整体不画（避免突兀跳变）
            if (effFade >= 0.5) {
                com.opendreamcore.client.world.HoloEntityRender.renderEntity(pose, buffers, node, effective, pageVars);
            }
        } else if ("rect".equals(node.type())) {
            renderRect(pose, buffers, node, effFade, scope, effective, pageVars);
            if (hovered) {
                com.opendreamcore.client.world.HoloDecor.renderHoverOutline(pose, node, effFade, true, pageVars);
            }
        } else if ("button".equals(node.type())) {
            // 世界内按钮：背景（hover/按下态变色）+ 居中标签；点击走通用 CLICK 管线
            renderButton(pose, buffers, node, effFade, scope, effective, pageVars, hovered);
            if (hovered) {
                com.opendreamcore.client.world.HoloDecor.renderHoverOutline(pose, node, effFade, true, pageVars);
            }
        } else if ("image".equals(node.type())) {
            String src = UiRenderer.str(UiRenderer.propsMap(node, "image").get("src"));
            if (src != null && src.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
                renderGif(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内 GIF 播放
            } else {
                renderImage(pose, buffers, node, effFade, scope, effective, pageVars);
            }
            if (hovered) {
                com.opendreamcore.client.world.HoloDecor.renderHoverOutline(pose, node, effFade, true, pageVars);
            }
        } else if ("video".equals(node.type())) {
            renderVideo(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内视频面板（FFmpeg）
        } else if ("canvas".equals(node.type())) {
            com.opendreamcore.client.world.HoloCanvas.renderCanvas(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内画布（笔刷系统）
        } else if ("progress".equals(node.type())) {
            renderProgress(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内进度条（billboard 血条）
        } else if ("toggle".equals(node.type())) {
            renderToggle(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内开关
        } else if ("slider".equals(node.type())) {
            renderSlider(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内滑块
        } else if ("checkbox".equals(node.type())) {
            renderCheckbox(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内复选框
        } else if ("dropdown".equals(node.type())) {
            renderDropdown(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内下拉（轮盘切换）
        } else if ("item_slot".equals(node.type()) || "item_display".equals(node.type())) {
            renderItemSlot(pose, buffers, node, effFade, scope, effective, pageVars); // 世界内物品展示
        } else if ("tabs".equals(node.type())) {
            renderTabs(pose, buffers, node, effFade, scope, effective, pageVars, activeTab); // 世界内页签栏
        }
        // 元素发光（hologram.glow：颜色或 {color, size}，内容之前绘制 = 垫底辉光）
        Object glowProp = ownHolo.get("glow");
        if (glowProp != null && !Boolean.FALSE.equals(glowProp)) {
            com.opendreamcore.client.world.HoloDecor.renderGlow(pose, node, effFade, pageVars, effective, glowProp);
        }
        // 元素倒影（hologram.shadow：颜色或 {color, offset, size}，内容之前绘制 = 垫底阴影）
        Object shadowProp = ownHolo.get("shadow");
        if (shadowProp != null && !Boolean.FALSE.equals(shadowProp)) {
            com.opendreamcore.client.world.HoloDecor.renderShadow(pose, node, effFade, pageVars, effective, shadowProp);
        }
        // 元素角标（hologram.badge：布尔红点 / 数字数量 / {count, color}，右上角 billboard）
        Object badgeProp = ownHolo.get("badge");
        if (badgeProp != null) {
            com.opendreamcore.client.world.HoloDecor.renderBadge(pose, buffers, node, effFade, pageVars, effective, badgeProp);
        }
        // 元素状态图标（hologram.statusIcon：文本或 {icon, color}，左上角 billboard）
        Object statusProp = ownHolo.get("statusIcon");
        if (statusProp != null) {
            com.opendreamcore.client.world.HoloDecor.renderStatusIcon(pose, buffers, node, effFade, pageVars, effective, statusProp);
        }
        // 元素边框（hologram.border：颜色字符串或 {color, width, flow, flowColor}，billboard 描边）
        Object borderProp = ownHolo.get("border");
        if (borderProp != null) {
            int bc = 0xFFFFD700;
            float bw = 0.02F;
            boolean flow = false;
            int flowColor = 0xFFFFFFFF;
            int flowColor2 = 0;
            long flowSpeedMs = 0;
            float flowSeg = 0;
            float flowPhase = 0;
            float flowPhase2 = 0;
            float borderAlpha = 1.0F;
            boolean flowGradient = false;
            boolean flowReverse = false;
            int flowSegments = 0;
            float flowSegGap = 0;
            boolean dash = false;
            float dashLen = 0.1F;
            boolean doubleLine = false;
            if (borderProp instanceof Map<?, ?> bm) {
                bc = UiStyle.color(bm.get("color"), bc);
                bw = (float) num(bm.get("width"), bw);
                flow = Boolean.parseBoolean(String.valueOf(bm.get("flow")));
                flowColor = UiStyle.color(bm.get("flowColor"), flowColor);
                flowColor2 = UiStyle.color(bm.get("flowColor2"), 0);
                if (bm.get("flowSpeed") instanceof Number n) {
                    flowSpeedMs = n.longValue();
                }
                flowSeg = (float) num(bm.get("flowSeg"), 0);
                flowPhase = (float) num(bm.get("flowPhase"), 0);
                flowPhase2 = (float) num(bm.get("flowPhase2"), 0);
                borderAlpha = (float) num(bm.get("alpha"), 1.0);
                flowGradient = Boolean.parseBoolean(String.valueOf(bm.get("flowGradient")));
                flowReverse = Boolean.parseBoolean(String.valueOf(bm.get("flowReverse")));
                if (bm.get("flowSegments") instanceof Number n) {
                    flowSegments = n.intValue();
                }
                flowSegGap = (float) num(bm.get("flowSegGap"), 0);
                dash = Boolean.parseBoolean(String.valueOf(bm.get("dash")));
                dashLen = (float) num(bm.get("dashLen"), 0.1);
                doubleLine = Boolean.parseBoolean(String.valueOf(bm.get("double")));
            } else {
                bc = UiStyle.color(borderProp, bc);
            }
            // 圆角矩形元素：描边跟随 rect.radius
            double borderRadius = "rect".equals(node.type())
                    ? UiRenderer.num(UiRenderer.propsMap(node, "rect").get("radius"), 0) : 0;
            com.opendreamcore.client.world.HoloDecor.renderBorderOutline(pose, node, effFade, pageVars, effective, bc, bw, flow, flowColor,
                    borderRadius, flowSpeedMs, dash, dashLen, doubleLine, flowColor2, flowSeg,
                    flowPhase, flowPhase2, borderAlpha, flowGradient, flowReverse, flowSegments,
                    flowSegGap, hovered);
        }
        // 编辑模式选中框（任何类型都画，含无 hover 框的实体/物品）
        if (selected) {
            com.opendreamcore.client.world.HoloDecor.renderSelectionOutline(pose, node, effFade, pageVars, effective);
        }
        for (RenderNode child : node.children()) {
            renderNode(pose, buffers, child, fade, hoverId, selectionId, activeTab, tabReveal, scope, pageVars,
                    dragOffsets, childParent);
        }
    }

    /** 世界内物品展示（item_slot/item_display）：billboard 物品图标 + 数量角标。 */
    private static void renderItemSlot(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                       double fade, String scope, double[] drag,
                                       java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = node.type().equals("item_display")
                ? UiRenderer.propsMap(node, "item_display") : UiRenderer.propsMap(node, "item_slot");
        String rawItem = UiRenderer.str(spec.get("item"));
        if (rawItem == null) {
            return;
        }
        String itemId = UiRenderer.interpolate(node, rawItem, pageVars).trim();
        int count = (int) UiRenderer.num(spec.get("count"), 1);
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.item.ItemStack stack = parseItemStack(mc, node, spec, itemId, count, pageVars);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double h = holoNum(holo, "height", 0.5, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        // 物品图标：FIXED 视角渲染（1 单位 ≈ 物品模型 16px），按世界高度缩放
        pose.scale((float) (h * 1.2), (float) (h * 1.2), (float) (h * 1.2));
        try {
            mc.getItemRenderer().renderStatic(stack,
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    0xF000F0, 0, pose, buffers, mc.level, 0);
        } catch (Exception ignored) {
            // 物品模型渲染失败跳过
        }
        pose.popPose();
        // 数量角标（右下）
        if (count > 1) {
            pose.pushPose();
            pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0])
                            + h * 0.5,
                    y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1])
                            - h * 0.45,
                    z + (drag == null ? 0 : drag[2]));
            pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
            pose.scale((float) (h * 0.5), (float) -(h * 0.5), (float) (h * 0.5));
            String label = String.valueOf(count);
            float tw = mc.font.width(label);
            mc.font.drawInBatch(label, -tw / 2.0F, 0,
                    withAlpha(0xFFFFFFFF, (float) fade * (float) (anim == null ? 1 : anim[3])), true,
                    pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    0, 0xF000F0);
            pose.popPose();
        }
    }

    /**
     * 解析物品展示的物品栈（支持 NBT/组件）：
     * 1) item 以 "{" 开头 → 完整 SNBT 物品标签（含 id/Count/组件），如
     *    "{id:'minecraft:diamond_sword',Count:1b,minecraft:enchantments:{levels:{minecraft:sharpness:5}}}"
     * 2) 纯 id + 可选 `nbt:` 属性（SNBT 组件，如 "{minecraft:custom_name:'\"§b附魔剑\"'}"，与 id/Count 合并）
     * 3) 纯 id + count（原行为）
     */
    private static net.minecraft.world.item.ItemStack parseItemStack(Minecraft mc, RenderNode node,
                                                                     Map<?, ?> spec, String itemId, int count,
                                                                     java.util.Map<String, Object> pageVars) {
        try {
            var items = mc.level.registryAccess();
            if (itemId.startsWith("{")) {
                // 完整 SNBT 物品
                var tag = net.minecraft.nbt.NbtUtils.snbtToStructure(itemId);
                return (net.minecraft.world.item.ItemStack) CompatRender.parseStack(items, tag);
            }
            net.minecraft.core.Registry<net.minecraft.world.item.Item> registry =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM;
            Object item = CompatRender.registryGet(registry, net.minecraft.resources.ResourceLocation.tryParse(itemId));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return null;
            }
            Object rawNbt = spec.get("nbt");
            if (rawNbt == null) {
                return new net.minecraft.world.item.ItemStack((net.minecraft.world.item.Item) item, Math.max(1, count));
            }
            String snbt = UiRenderer.interpolate(node, String.valueOf(rawNbt), pageVars).trim();
            if (snbt.isEmpty()) {
                return new net.minecraft.world.item.ItemStack((net.minecraft.world.item.Item) item, Math.max(1, count));
            }
            // id/Count 由 item/count 属性保证，nbt 只叠加组件
            var tag = net.minecraft.nbt.NbtUtils.snbtToStructure(snbt);
            var base = new net.minecraft.nbt.CompoundTag();
            base.putString("id", itemId);
            base.putByte("Count", (byte) Math.max(1, count));
            tag.merge(base);
            return (net.minecraft.world.item.ItemStack) CompatRender.parseStack(items, tag);
        } catch (Exception e) {
            return null; // 解析失败不渲染（非法 id/SNBT）
        }
    }

    /** 世界内页签栏（tabs）：横向页签 + 激活高亮下划线，点击切换（INPUT 上报选项值）。 */
    private static void renderTabs(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                   String scope, double[] drag, java.util.Map<String, Object> pageVars,
                                   String activeTab) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "tabs");
        List<?> options = spec.get("options") instanceof List<?> l ? l : List.of();
        if (options.isEmpty()) {
            return;
        }
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 3, pageVars);
        double h = holoNum(holo, "height", 0.25, pageVars);
        int bg = UiStyle.color(spec.get("color"), 0xFF2A3A52);
        int activeColor = UiStyle.color(spec.get("activeColor"), 0xFF42A5F5);
        int textColor = UiStyle.color(spec.get("textColor"), 0xFFE0E0E0);
        int textActive = UiStyle.color(spec.get("textActiveColor"), 0xFFFFFFFF);
        double[] anim = animOf(node, scope);
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        // 文本空间：16 单位 = 1 个 h（文字高 ≈ 0.5h）
        float ts = (float) (h / 16.0);
        pose.scale(ts, -ts, ts);
        var matrix = pose.last().pose();
        int n = options.size();
        float tw = (float) (16.0 * w / h);
        float hw = tw / 2;
        float pw = tw / n;
        float gap = pw * 0.05F;
        float fadeA = (float) fade * (float) (anim == null ? 1 : anim[3]);
        // 页签底色 + 激活下划线（批量一次提交）
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            boolean active = activeTab != null && activeTab.equals(String.valueOf(options.get(i)));
            float x0 = -hw + i * pw + gap;
            float x1 = -hw + (i + 1) * pw - gap;
            int c = active ? activeColor : bg;
            float a = ((c >>> 24) & 0xFF) / 255.0F * fadeA;
            if (a > 0) {
                com.opendreamcore.client.world.HoloDecor.edge(builder, matrix, x0, -7, x1, 7,
                        ((c >> 16) & 0xFF) / 255.0F, ((c >> 8) & 0xFF) / 255.0F, (c & 0xFF) / 255.0F, a);
            }
            if (active) {
                float u = ((activeColor >>> 24) & 0xFF) / 255.0F * fadeA;
                if (u > 0) {
                    com.opendreamcore.client.world.HoloDecor.edge(builder, matrix, x0, -7, x1, -5.5F,
                            ((activeColor >> 16) & 0xFF) / 255.0F, ((activeColor >> 8) & 0xFF) / 255.0F,
                            (activeColor & 0xFF) / 255.0F, u);
                }
            }
        }
        drawSafe(builder);
        applyContentDepth();
        CompatRender.disableBlend();
        // 页签文字（billboard，激活高亮）
        for (int i = 0; i < n; i++) {
            boolean active = activeTab != null && activeTab.equals(String.valueOf(options.get(i)));
            String label = UiRenderer.interpolate(node, String.valueOf(options.get(i)), pageVars);
            float lw = mc.font.width(label);
            int c = active ? textActive : textColor;
            int a = (int) (((c >>> 24) & 0xFF) * fadeA);
            c = (a << 24) | (c & 0xFFFFFF);
            mc.font.drawInBatch(label, -hw + i * pw + (pw - lw) / 2, -4.0F, c, true,
                    pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    0, 0xF000F0);
        }
        pose.popPose();
    }

    /** 世界内下拉（dropdown）：边框框体 + 当前选项文本，点击轮换（INPUT 上报选项值）。 */
    private static void renderDropdown(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                       double fade, String scope, double[] drag,
                                       java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "dropdown");
        List<?> options = spec.get("options") instanceof List<?> list ? list : List.of();
        if (options.isEmpty()) {
            return;
        }
        Integer index = ClientController.get().worldDropdownIndex(scope, node.id());
        int idx = index != null ? index : 0;
        String value = UiRenderer.str(spec.get("value"));
        if (value == null || index != null) {
            value = String.valueOf(options.get(idx));
        } else {
            idx = 0;
            for (int i = 0; i < options.size(); i++) {
                if (String.valueOf(options.get(i)).equals(value)) {
                    idx = i;
                    break;
                }
            }
        }
        String label = UiRenderer.interpolate(node, value, pageVars);
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1.2, pageVars);
        double h = holoNum(holo, "height", 0.18, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 框体
        quadV(builder, matrix, -w / 2, -h / 2, w / 2, h / 2, 0xFF20242C, alphaMul);
        quadV(builder, matrix, -w / 2, -h / 2, w / 2, -h / 2 + h * 0.08F, 0xFF505868, alphaMul);
        // 右箭头（▼）
        double ax = w / 2 - h * 0.35;
        double ay = 0;
        quadV(builder, matrix, ax - h * 0.12, ay - h * 0.1, ax + h * 0.12, ay - h * 0.1, 0xFF9AA3B2, alphaMul);
        quadV(builder, matrix, ax - h * 0.12, ay - h * 0.1, ax, ay + h * 0.14, 0xFF9AA3B2, alphaMul);
        quadV(builder, matrix, ax + h * 0.12, ay - h * 0.1, ax, ay + h * 0.14, 0xFF9AA3B2, alphaMul);
        drawSafe(builder);
        applyContentDepth();
        CompatRender.disableBlend();
        // 选项文本
        if (label != null && !label.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            pose.pushPose();
            pose.translate(0, 0, 0);
            pose.scale((float) h, (float) -h, (float) h);
            float tw = mc.font.width(label);
            float scale = (float) Math.min(1.0, (w / h - 0.9) / tw);
            pose.scale(scale, scale, scale);
            mc.font.drawInBatch(label, -tw / 2.0F, -4.0F,
                    withAlpha(0xFFFFFFFF, alphaMul), false,
                    pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    0, 0xF000F0);
            pose.popPose();
        }
        pose.popPose();
    }

    /** 颜色乘 alpha。 */
    public static int withAlpha(int color, float alphaMul) {
        int a = (int) (((color >>> 24) & 0xFF) * alphaMul);
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** 世界内复选框（checkbox）：方框 + 勾号，点击切换（INPUT true/false）。 */
    private static void renderCheckbox(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                       double fade, String scope, double[] drag,
                                       java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "checkbox");
        Boolean local = ClientController.get().worldToggleValue(scope, node.id());
        boolean checked = local != null ? local : UiRenderer.bool(spec.get("value"), false);
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 0.16, pageVars);
        double h = holoNum(holo, "height", 0.16, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        int accent = UiStyle.color(spec.get("color"), 0xFF7A8BFF);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 框（底色 + 四边）
        quadV(builder, matrix, -w / 2, -h / 2, w / 2, h / 2,
                checked ? 0xFF2A3355 : 0xFF20242C, alphaMul);
        float t = (float) Math.min(w, h) * 0.12F;
        quadV(builder, matrix, -w / 2, -h / 2, w / 2, -h / 2 + t, accent, alphaMul);
        quadV(builder, matrix, -w / 2, h / 2 - t, w / 2, h / 2, accent, alphaMul);
        quadV(builder, matrix, -w / 2, -h / 2, -w / 2 + t, h / 2, accent, alphaMul);
        quadV(builder, matrix, w / 2 - t, -h / 2, w / 2, h / 2, accent, alphaMul);
        if (checked) {
            // 勾号（三段斜线近似）
            double s = Math.min(w, h) / 2;
            quadV(builder, matrix, -s * 0.6, s * 0.15, -s * 0.35, s * 0.15, accent, alphaMul);
            quadV(builder, matrix, -s * 0.35, -s * 0.15, -s * 0.2, -s * 0.35, accent, alphaMul);
            quadV(builder, matrix, -s * 0.2, -s * 0.35, s * 0.55, -s * 0.6, accent, alphaMul);
        }
        drawSafe(builder);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 合并拖拽偏移与父链偏移（都为 null 返回 null）。 */
    private static double[] mergeDrag(double[] drag, double[] parent) {
        if (drag == null) {
            return parent;
        }
        if (parent == null) {
            return drag;
        }
        return new double[]{drag[0] + parent[0], drag[1] + parent[1], drag[2] + parent[2]};
    }

    /** 世界面板标题小字（billboard，面板顶部上方居中，随元素缩放基准），面板 id 隔离。 */
    public static void renderPanelTitle(net.minecraft.client.Camera camera, Map<String, Object> options,
                                        Vec3 anchor, double px, double py, double pz, double panelWidth,
                                        String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || title == null || title.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x + px,
                anchor.y - camera.getPosition().y + py,
                anchor.z - camera.getPosition().z + pz);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        // 字高 = 面板宽度的 4%（世界单位）；1px 字 → 字高/8 世界单位
        double pxPerWorld = Math.max(30, panelWidth * 12);
        pose.scale((float) (1.0 / pxPerWorld), (float) (-1.0 / pxPerWorld), (float) (1.0 / pxPerWorld));
        mc.font.drawInBatch(title, -mc.font.width(title) / 2.0F, 0, 0xDDFFFFFF, true,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0, 0xF000F0);
        buffers.endBatch();
    }

    /** 世界内滑块（slider）：轨道 + 填充 + 手柄，拖拽改值（INPUT 数值上报）。 */
    private static void renderSlider(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                     double fade, String scope, double[] drag,
                                     java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "slider");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        Double local = ClientController.get().worldSliderValue(scope, node.id());
        double value = local != null ? local : UiRenderer.num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1.5, pageVars);
        double h = holoNum(holo, "height", 0.15, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        double trackH = h * 0.4;
        boolean vertical = UiRenderer.bool(spec.get("vertical"), false);
        if (vertical) {
            // 竖轨：沿 y 轴，填充自下而上，手柄随值上下
            quadV(builder, matrix, -trackH / 2, -h / 2, trackH / 2, h / 2, 0xFF303540, alphaMul);
            double fillH = h * ratio;
            if (fillH > 0.005) {
                quadV(builder, matrix, -trackH / 2, -h / 2, trackH / 2, -h / 2 + fillH,
                        0xFF7A8BFF, alphaMul);
            }
            double knobY = -h / 2 + fillH;
            quadV(builder, matrix, -h * 0.25, knobY - trackH * 0.8, h * 0.25, knobY + trackH * 0.8,
                    0xFFE8EDFF, alphaMul);
        } else {
            // 轨道
            quadV(builder, matrix, -w / 2, -trackH / 2, w / 2, trackH / 2, 0xFF303540, alphaMul);
            // 填充
            double fillW = w * ratio;
            if (fillW > 0.005) {
                quadV(builder, matrix, -w / 2, -trackH / 2, -w / 2 + fillW, trackH / 2,
                        0xFF7A8BFF, alphaMul);
            }
            // 手柄
            double knobX = -w / 2 + fillW;
            quadV(builder, matrix, knobX - h * 0.2, -h / 2, knobX + h * 0.2, h / 2,
                    0xFFE8EDFF, alphaMul);
        }
        drawSafe(builder);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 世界内开关（toggle）：轨道 + 滑块，点击切换（INPUT true/false 上报）。 */
    private static void renderToggle(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                     double fade, String scope, double[] drag,
                                     java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "toggle");
        Boolean local = ClientController.get().worldToggleValue(scope, node.id());
        boolean on = local != null ? local : UiRenderer.bool(spec.get("value"), false);
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1, pageVars);
        double h = holoNum(holo, "height", 0.12, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 轨道
        quadV(builder, matrix, -w / 2, -h / 2, w / 2, h / 2,
                on ? 0xFF4CAF50 : 0xFF303540, alphaMul);
        // 滑块（右侧=开）
        double knobW = Math.max(w / 3, h);
        double knobX = on ? w / 2 - knobW : -w / 2;
        quadV(builder, matrix, knobX, -h / 2 + h * 0.1, knobX + knobW, h / 2 - h * 0.1,
                0xFFFFFFFF, alphaMul);
        drawSafe(builder);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 世界内进度条（progress）：矩形轨道 + 填充；shape=arc/circle 时用 billboard 弧形（TRIANGLES 扇环）。 */
    private static void renderProgress(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                       double fade, String scope, double[] drag,
                                       java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "progress");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        double value = UiRenderer.num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        String shape = UiRenderer.str(spec.get("shape"));
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        if (shape != null && ("arc".equalsIgnoreCase(shape) || "circle".equalsIgnoreCase(shape))) {
            double w = holoNum(holo, "width", 1, pageVars);
            double h = holoNum(holo, "height", 1, pageVars);
            double radius = UiRenderer.num(spec.get("radius"), Math.min(w, h) / 2 - 0.06);
            double thickness = UiRenderer.num(spec.get("thickness"), 0.14);
            double start = UiRenderer.num(spec.get("startAngle"), -90);
            double sweep = "circle".equalsIgnoreCase(shape) ? 360 : UiRenderer.num(spec.get("sweepAngle"), 270);
            int track = UiStyle.color(spec.get("trackColor"), 0xFF303540);
            int color = UiStyle.color(spec.get("color"), 0xFF4CAF50);
            var arcBuilder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            fillArcWorld(arcBuilder, matrix, 0, 0, radius, thickness, start, sweep, track, alphaMul);
            if (ratio > 0) {
                fillArcWorld(arcBuilder, matrix, 0, 0, radius, thickness, start, start + sweep * ratio - start, color, alphaMul);
            }
            drawSafe(arcBuilder);
        } else {
            double w = holoNum(holo, "width", 1, pageVars);
            double h = holoNum(holo, "height", 0.08, pageVars);
            var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            quadV(builder, matrix, -w / 2, -h / 2, w / 2, h / 2, 0xFF303540, alphaMul);
            double fillW = w * ratio;
            if (fillW > 0.005) {
                quadV(builder, matrix, -w / 2, -h / 2, -w / 2 + fillW, h / 2,
                        UiStyle.color(spec.get("color"), 0xFF4CAF50), alphaMul);
            }
            drawSafe(builder);
        }
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    private static void fillArcWorld(CompatBuffer builder, org.joml.Matrix4f matrix,
                                      double cx, double cy, double radius, double thickness,
                                      double startDeg, double sweepDeg, int color, float alphaMul) {
        if (sweepDeg == 0 || radius <= 0 || thickness <= 0) return;
        float[] c = rgba(color, alphaMul);
        if (c[3] <= 0) return;
        double rOut = radius;
        double rIn = Math.max(0, radius - thickness);
        int segments = Math.max(4, (int) Math.ceil(Math.abs(sweepDeg) / 8.0));
        for (int i = 0; i < segments; i++) {
            double a0 = Math.toRadians(startDeg + sweepDeg * i / segments);
            double a1 = Math.toRadians(startDeg + sweepDeg * (i + 1) / segments);
            double ox0 = cx + rOut * Math.cos(a0);
            double oy0 = cy + rOut * Math.sin(a0);
            double ox1 = cx + rOut * Math.cos(a1);
            double oy1 = cy + rOut * Math.sin(a1);
            double ix0 = cx + rIn * Math.cos(a0);
            double iy0 = cy + rIn * Math.sin(a0);
            double ix1 = cx + rIn * Math.cos(a1);
            double iy1 = cy + rIn * Math.sin(a1);
            builder.addVertex(matrix, (float) ox0, (float) oy0, 0).setColor(c[0], c[1], c[2], c[3]);
            builder.addVertex(matrix, (float) ox1, (float) oy1, 0).setColor(c[0], c[1], c[2], c[3]);
            builder.addVertex(matrix, (float) ix0, (float) iy0, 0).setColor(c[0], c[1], c[2], c[3]);
            builder.addVertex(matrix, (float) ox1, (float) oy1, 0).setColor(c[0], c[1], c[2], c[3]);
            builder.addVertex(matrix, (float) ix1, (float) iy1, 0).setColor(c[0], c[1], c[2], c[3]);
            builder.addVertex(matrix, (float) ix0, (float) iy0, 0).setColor(c[0], c[1], c[2], c[3]);
        }
    }

    // ========== 世界画布（canvas 笔刷 → billboard 平面绘制） ==========

    /**
     * 世界内画布：与屏幕 canvas 同一套笔刷（rect/circle/line/triangle/gradient/image/text），
     * 笔刷坐标 = 面板内像素（左上原点 y 向下），按面板宽高映射到世界单位。
     * hologram.width/height = 世界面板尺寸；canvas.width/height = 面板逻辑像素尺寸。
     */

    /** RGBA 分解（带 alpha 乘数）。 */
    public static float[] rgba(int color, float alphaMul) {
        float a = ((color >>> 24) & 0xFF) / 255.0F * alphaMul;
        return new float[]{((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, a};
    }

    public static void quadV(CompatBuffer builder, org.joml.Matrix4f matrix,
                              double x0, double y0, double x1, double y1, int color, float alphaMul) {
        float[] c = rgba(color, alphaMul);
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x1, (float) y0, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x1, (float) y1, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x1, (float) y1, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x0, (float) y1, 0).setColor(c[0], c[1], c[2], c[3]);
    }

    public static void quad4(CompatBuffer builder, org.joml.Matrix4f matrix,
                              double x0, double y0, double x1, double y1,
                              double x2, double y2, double x3, double y3, int color, float alphaMul) {
        float[] c = rgba(color, alphaMul);
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x1, (float) y1, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x2, (float) y2, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x2, (float) y2, 0).setColor(c[0], c[1], c[2], c[3]);
        builder.addVertex(matrix, (float) x3, (float) y3, 0).setColor(c[0], c[1], c[2], c[3]);
    }

    /** 世界内视频面板（FFmpeg 真视频贴图到 billboard 面板）。 */
    /** 世界画布渲染——实现移至 world/HoloCanvas（C3 第二波）。 */
    public static void renderCanvas(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade, String scope, double[] drag, java.util.Map<String, Object> pageVars) {
        com.opendreamcore.client.world.HoloCanvas.renderCanvas(pose, buffers, node, fade, scope, drag, pageVars);
    }

    private static void renderVideo(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade, String scope, double[] drag, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "video");
        String src = UiRenderer.str(spec.get("src"));
        if (src == null) {
            return;
        }
        String lower = src.toLowerCase(java.util.Locale.ROOT);
        boolean remote = lower.startsWith("https://") || lower.startsWith("http://");
        boolean videoFile = remote
                || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")
                || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".flv");
        net.minecraft.resources.ResourceLocation texture = null;
        int[] rect = null;
        if (videoFile && FfmpegVideoPlayer.available()) {
            Object loopRaw = spec.get("loop");
            boolean loop = loopRaw == null || Boolean.parseBoolean(String.valueOf(loopRaw));
            FfmpegVideoPlayer video = FfmpegVideoPlayer.of(src, loop, UiRenderer.str(spec.get("fit")));
            if (video != null) {
                FfmpegVideoPlayer.register(node.id(), video);
                texture = video.currentTexture();
                rect = video.drawRect(0, 0, holoNum(holo(node), "width", 1, pageVars), holoNum(holo(node), "height", 1, pageVars));
            }
        }
        if (texture == null) {
            return;
        }
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float hw = rect == null ? 0.5F : (float) rect[2] / 2;
        float hh = rect == null ? 0.3F : (float) rect[3] / 2;
        // fit: contain 时 rect 相对面板原点居中
        if (rect != null) {
            hw = (float) rect[2] / 2;
            hh = (float) rect[3] / 2;
            float ox = (float) (rect[0] + rect[2] / 2.0 - holoNum(holo, "width", 1, pageVars) / 2.0);
            float oy = (float) (rect[1] + rect[3] / 2.0 - holoNum(holo, "height", 1, pageVars) / 2.0);
            pose.translate(-ox, -oy, 0);
        }
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setShaderTexture(0, texture);
        CompatRender.setTextureShader();
        float alpha = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.shaderColor(1.0F, 1.0F, 1.0F, alpha);
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, -hw, -hh, 0).setUv(0, 0);
        builder.addVertex(matrix, hw, -hh, 0).setUv(1, 0);
        builder.addVertex(matrix, hw, hh, 0).setUv(1, 1);
        builder.addVertex(matrix, -hw, hh, 0).setUv(0, 1);
        drawSafe(builder);
        CompatRender.shaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 世界内 GIF 播放（GifPlayer 动态纹理贴到 billboard 面板）。 */
    private static void renderGif(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade, String scope, double[] drag, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "image");
        String src = UiRenderer.str(spec.get("src"));
        if (src == null) {
            return;
        }
        GifPlayer gif = GifPlayer.of(src);
        net.minecraft.resources.ResourceLocation texture = gif == null ? null : gif.currentTexture();
        if (texture == null) {
            return;
        }
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1, pageVars);
        double h = holoNum(holo, "height", 1, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setShaderTexture(0, texture);
        CompatRender.setTextureShader();
        float alpha = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.shaderColor(1.0F, 1.0F, 1.0F, alpha);
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, -hw, -hh, 0).setUv(0, 0);
        builder.addVertex(matrix, hw, -hh, 0).setUv(1, 0);
        builder.addVertex(matrix, hw, hh, 0).setUv(1, 1);
        builder.addVertex(matrix, -hw, hh, 0).setUv(0, 1);
        drawSafe(builder);
        CompatRender.shaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }















    /** 世界内色块（rect，billboard，可作特效背景/光柱）。 */
    private static void renderRect(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade, String scope, double[] drag, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "rect");
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1, pageVars);
        double h = holoNum(holo, "height", 1, pageVars);
        int color = UiStyle.color(spec.get("color"), 0xFFFFFFFF);
        double[] anim = animOf(node, scope);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >>> 24) & 0xFF) / 255.0F * (float) fade * (float) (anim == null ? 1 : anim[3]);
        if (a <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        // 圆角（rect.radius，世界单位，超短边一半自动钳制）
        double radius = UiRenderer.num(spec.get("radius"), 0);
        float rr = (float) Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        // 渐变（rect.gradient：顶 color → 底 gradient，圆角同步渐变）
        int gradient = UiStyle.color(spec.get("gradient"), 0);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        if (gradient != 0) {
            var round = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            roundedQuadTrisGrad(round, matrix, -hw, -hh, hw, hh, rr, color, gradient, -hh, hh, (float) fade, false, 0, 0.5F);
            drawSafe(round);
        } else if (rr > 0.01F) {
            var round = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            roundedQuadTris(round, matrix, -hw, -hh, hw, hh, rr, r, g, b, a);
            drawSafe(round);
        } else {
            var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(matrix, -hw, -hh, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, hw, -hh, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, hw, hh, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, -hw, hh, 0).setColor(r, g, b, a);
            drawSafe(builder);
        }
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /**
     * 世界内按钮（button，billboard）：背景色块（hover 高亮 / 按下变亮 + 0.95 缩放）+ 居中标签。
     * 点击/悬停走通用世界交互管线（CLICK 事件 / click actions）。
     */
    private static void renderButton(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade,
                                     String scope, double[] drag, java.util.Map<String, Object> pageVars,
                                     boolean hovered) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "button");
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1, pageVars);
        double h = holoNum(holo, "height", 0.25, pageVars);
        boolean pressed = currentPressedKey != null
                && currentPressedKey.equals(com.opendreamcore.client.ClientController.wkey(scope, node.id()));
        int bg;
        if (!node.enabled()) {
            bg = 0xFF20242C;
        } else if (pressed) {
            bg = UiStyle.color(spec.get("pressedColor"),
                    UiStyle.color(spec.get("hoverColor"),
                            UiStyle.color(spec.get("background"), UiStyle.color(spec.get("color"), 0xFF3A3F4A))));
        } else if (hovered) {
            bg = UiStyle.color(spec.get("hoverColor"),
                    UiStyle.color(spec.get("background"), UiStyle.color(spec.get("color"), 0xFF3A3F4A)));
        } else {
            bg = UiStyle.color(spec.get("background"), UiStyle.color(spec.get("color"), 0xFF2A2F3A));
        }
        double[] anim = animOf(node, scope);
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        float a = ((bg >>> 24) & 0xFF) / 255.0F * alphaMul;
        if (a <= 0) {
            return;
        }
        float r = ((bg >> 16) & 0xFF) / 255.0F;
        float g = ((bg >> 8) & 0xFF) / 255.0F;
        float b = (bg & 0xFF) / 255.0F;
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        if (pressed) {
            pose.scale(0.95F, 0.95F, 1.0F); // 按下缩放反馈（与屏幕端 hit 反馈一致）
        }
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        double radius = UiRenderer.num(spec.get("radius"), 0.05);
        float rr = (float) Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setColorShader();
        if (rr > 0.01F) {
            var round = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            roundedQuadTris(round, matrix, -hw, -hh, hw, hh, rr, r, g, b, a);
            drawSafe(round);
        } else {
            var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(matrix, -hw, -hh, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, hw, -hh, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, hw, hh, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, -hw, hh, 0).setColor(r, g, b, a);
            drawSafe(builder);
        }
        applyContentDepth();
        CompatRender.disableBlend();
        // 标签（billboard 文本：8px 字高 ≈ 0.5h；NORMAL 模式深度测试与背景一致）
        String label = UiRenderer.interpolate(node, UiRenderer.str(spec.get("label")), pageVars);
        if (label != null && !label.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            int labelColor = UiStyle.color(spec.get("textColor"), 0xFFFFFFFF);
            int la = (int) (((labelColor >>> 24) & 0xFF) * alphaMul);
            if (la > 0) {
                int lc = (la << 24) | (labelColor & 0xFFFFFF);
                float ts = (float) (h / 16.0);
                pose.scale(ts, -ts, ts);
                float lw = mc.font.width(label);
                mc.font.drawInBatch(label, -lw / 2.0F, -4.0F, lc, true,
                        pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                        0, 0xF000F0);
            }
        }
        pose.popPose();
    }

    /** 世界内图片（image，billboard 贴图）。 */
    private static void renderImage(PoseStack pose, MultiBufferSource buffers, RenderNode node, double fade, String scope, double[] drag, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "image");
        String src = UiRenderer.str(spec.get("src"));
        net.minecraft.resources.ResourceLocation texture = UiStyle.texture(src);
        if (texture == null) {
            return;
        }
        Map<?, ?> holo = holo(node);
        double x = holoNum(holo, "x", 0, pageVars);
        double y = holoNum(holo, "y", 0, pageVars);
        double z = holoNum(holo, "z", 0, pageVars);
        double w = holoNum(holo, "width", 1, pageVars);
        double h = holoNum(holo, "height", 1, pageVars);
        double[] anim = animOf(node, scope);
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        CompatRender.setShaderTexture(0, texture);
        CompatRender.setTextureShader();
        // 距离淡出 + 动画 alpha：全局着色器颜色乘 alpha
        float alpha = (float) fade * (float) (anim == null ? 1 : anim[3]);
        CompatRender.shaderColor(1.0F, 1.0F, 1.0F, alpha);
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, -hw, -hh, 0).setUv(0, 0);
        builder.addVertex(matrix, hw, -hh, 0).setUv(1, 0);
        builder.addVertex(matrix, hw, hh, 0).setUv(1, 1);
        builder.addVertex(matrix, -hw, hh, 0).setUv(0, 1);
        drawSafe(builder);
        CompatRender.shaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        applyContentDepth();
        CompatRender.disableBlend();
        pose.popPose();
    }

    /** 世界内名牌渲染：实体头顶 billboard 文本（服务端 name_tag 推送）。 */
    public static void renderNameTags(java.util.List<com.opendreamcore.client.WorldUiStore.NameTag> tags,
                                      net.minecraft.client.Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (tags.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            for (WorldUiStore.NameTag tag : tags) {
                net.minecraft.world.entity.Entity entity = mc.level.getEntity(tag.entityId());
                if (entity == null || !entity.isAlive()) {
                    continue;
                }
                pose.pushPose();
                pose.translate(entity.getX(), entity.getY() + entity.getBbHeight() + 0.55, entity.getZ());
                pose.mulPose(camera.rotation());
                pose.scale(0.025F, -0.025F, 0.025F);
                float w = mc.font.width(tag.text());
                mc.font.drawInBatch(tag.text(), -w / 2.0F, 0, tag.color(), true,
                        pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                        0, 0xF000F0);
                pose.popPose();
            }
            buffers.endBatch();
        } catch (Exception ignored) {
            // 名牌渲染出错不拖垮帧
        }
    }

    // ========== 世界面板射线交互（3D 面板拾取） ==========




    /** 最近一次拾取命中的世界偏移（父链累积，渲染线程；交互/拖拽基准用）。 */
    static volatile double[] lastPickOffset;


    /** 选中框圆角带宽（世界单位），UI 一致性。 */













    public static Map<?, ?> holo(RenderNode node) {
        return node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
    }

    /**
     * hologram 数值属性：数字 / 数字字符串 / **表达式**（"vars.offset_x"、"vars.angle / 10" 等，
     * 布局每帧求值，服务端 state_patch 改变量即可驱动世界面板位置/缩放）。
     */
    public static double holoNum(Map<?, ?> holo, String key, double fallback,
                                 java.util.Map<String, Object> vars) {
        Object v = holo == null ? null : holo.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v).trim();
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(s);
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            if (vars != null) {
                vars.forEach(scope::assignVar);
            }
            Object r = com.opendreamcore.script.DreamLang.evaluate(s, scope);
            if (r instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** 文本自动尺寸（世界单位）——实现移至 world/HoloTextRender（C3 第一波）。 */
    public static double[] textAutoSize(RenderNode node, java.util.Map<String, Object> pageVars) {
        return com.opendreamcore.client.world.HoloTextRender.textAutoSize(node, pageVars);
    }

    /** 世界页面变量（表达式求值用；无世界页返回空）。 */
    private static java.util.Map<String, Object> worldVars() {
        var controller = ClientController.get();
        return controller.isWorldOpen() ? controller.worldVariables() : java.util.Map.of();
    }



    /** 元素动画偏移（世界单位；rotation 为 billboard 平面内角度）：{dx, dy, scale, alpha, rot}。 */
    public static double[] animOf(RenderNode node, String scope) {
        double[] anim = AnimationEngine.get().offset(node.id(), scope);
        // 拖拽倾斜反馈：拖拽中的元素轻微旋转（4°，"拿起"感；按页键控）
        if (com.opendreamcore.client.ClientController.get().worldElementDragging(scope, node.id())) {
            double rot = 4;
            if (anim == null) {
                return new double[]{0, 0, 1, 1, rot};
            }
            double[] dragged = anim.clone();
            dragged[4] += rot;
            return dragged;
        }
        // 按下缩放反馈：按住左键时 scale * 0.95（与动画 scale 乘算；按页键控）
        String pressedKey = com.opendreamcore.client.ClientController.wkey(scope, node.id());
        if (currentPressedKey != null && currentPressedKey.equals(pressedKey)) {
            if (anim == null) {
                return new double[]{0, 0, 0.95, 1, 0};
            }
            double[] pressed = anim.clone();
            pressed[2] *= 0.95;
            return pressed;
        }
        // 点击弹跳：点击后 300ms 正弦回弹（0.88 谷值，与动画 scale 乘算；按页键控）
        double bounce = com.opendreamcore.client.ClientController.get().worldClickBounceScale(scope, node.id());
        if (bounce != 1) {
            if (anim == null) {
                return new double[]{0, 0, bounce, 1, 0};
            }
            double[] bounced = anim.clone();
            bounced[2] *= bounce;
            return bounced;
        }
        return anim;
    }

    public static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** 安全构建并绘制：空 buffer 时跳过绘制（防条件跳过全部顶点时 buildOrThrow 崩溃）。 */
    public static void drawSafe(CompatBuffer builder) {
        builder.buildAndDraw();
    }

    // ---- 兼容转发（实现移至 WorldPicking / WorldHoloEdit，round 5）----
    public static RenderNode raycast(java.util.List<RenderNode> nodes, java.util.Map<String, Object> options, net.minecraft.client.Camera camera, Minecraft mc) {
        return WorldPicking.raycast(nodes, options, camera, mc);
    }

    public static RenderNode raycast(java.util.List<RenderNode> nodes, java.util.Map<String, Object> options, net.minecraft.client.Camera camera, Minecraft mc, String activeTab, String pageId, java.util.Map<String, Object> pageVars) {
        return WorldPicking.raycast(nodes, options, camera, mc, activeTab, pageId, pageVars);
    }

    public static RenderNode raycast(java.util.List<RenderNode> nodes, java.util.Map<String, Object> options, net.minecraft.client.Camera camera, Minecraft mc, String activeTab, String pageId, java.util.Map<String, Object> pageVars, Vec3 anchorOverride) {
        return WorldPicking.raycast(nodes, options, camera, mc, activeTab, pageId, pageVars, anchorOverride);
    }

    public static double[] lastPickOffset() {
        return WorldPicking.lastPickOffset();
    }

    public static boolean locked(RenderNode node) {
        return WorldPicking.locked(node);
    }

    public static boolean draggable(RenderNode node, java.util.Map<String, Object> options) {
        return WorldPicking.draggable(node, options);
    }

    public static double[] mouseRayWorld(Minecraft mc, net.minecraft.client.Camera camera) {
        return WorldPicking.mouseRayWorld(mc, camera);
    }

    public static void renderResizeHandle(Camera camera, Map<String, Object> options, double[] center, double[] handle) {
        WorldHoloEdit.renderResizeHandle(camera, options, center, handle);
    }

    public static void renderBorderHandle(Camera camera, Map<String, Object> options, double[] center, double[] handle, int borderColor) {
        WorldHoloEdit.renderBorderHandle(camera, options, center, handle, borderColor);
    }

    public static void renderDragGhost(Camera camera, Map<String, Object> options, Vec3 base, double w, double h) {
        WorldHoloEdit.renderDragGhost(camera, options, base, w, h);
    }

    public static void renderMagnetRing(Camera camera, Map<String, Object> options, Vec3 anchor, double cx, double cy, double radius) {
        WorldHoloEdit.renderMagnetRing(camera, options, anchor, cx, cy, radius);
    }

    public static void renderFadeRange(Camera camera, Map<String, Object> options, Vec3 anchor, boolean dim) {
        WorldHoloEdit.renderFadeRange(camera, options, anchor, dim);
    }

    public static void renderAnchorMarker(Camera camera, Map<String, Object> options, Vec3 anchor) {
        WorldHoloEdit.renderAnchorMarker(camera, options, anchor);
    }

    public static void renderMirrorPreview(Camera camera, Map<String, Object> options, Vec3 anchor, List<double[]> boxes, double center, boolean horizontal) {
        WorldHoloEdit.renderMirrorPreview(camera, options, anchor, boxes, center, horizontal);
    }

    public static void renderGhostBoxes(Camera camera, Map<String, Object> options, Vec3 anchor, List<double[]> boxes) {
        WorldHoloEdit.renderGhostBoxes(camera, options, anchor, boxes);
    }

    public static void renderSelectionBounds(Camera camera, Map<String, Object> options, Vec3 anchor, double x0, double y0, double x1, double y1) {
        WorldHoloEdit.renderSelectionBounds(camera, options, anchor, x0, y0, x1, y1);
    }

    public static void renderLockMarker(Camera camera, Map<String, Object> options, Vec3 anchor, double cx, double cy, double w, double h) {
        WorldHoloEdit.renderLockMarker(camera, options, anchor, cx, cy, w, h);
    }

    public static void renderDragGuides(Camera camera, Map<String, Object> options, double[] guides) {
        WorldHoloEdit.renderDragGuides(camera, options, guides);
    }

    public static void renderEditGrid(Camera camera, Map<String, Object> options, Vec3 anchor, double step) {
        WorldHoloEdit.renderEditGrid(camera, options, anchor, step);
    }

    public static void renderRipples(Camera camera, Map<String, Object> options, List<double[]> ripples) {
        WorldHoloEdit.renderRipples(camera, options, ripples);
    }

    // ---- 拾取辅助转发 ----
    public static double defaultQuadH(RenderNode node) {
        return WorldPicking.defaultQuadH(node);
    }

    public static double defaultQuadW(RenderNode node) {
        return WorldPicking.defaultQuadW(node);
    }

}
