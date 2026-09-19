package com.opendreamcore.client.render;

import com.opendreamcore.client.spi.EntityPainter;
import com.opendreamcore.page.DisplayMode;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * 世界页渲染器：把 world 面板（display: world 或带 world 段）整块投到屏幕
 * 中央画，元素坐标乘 PPU（每世界单位像素数）换算。
 *
 * 3D 相机那条路（billboard 朝向/纵深排序）这版先不走——四版老壳共享同一套
 * SPI，先把「面板看得见、布局对、动画活」做实，空间感后面有需要再升级。
 *
 * 面板锚点语义：hologram.x/y 是相对面板中心的偏移（世界单位），y 向上为正；
 * world.offsetX/Y/Z 由服务器整页配置时已经折进元素坐标，这里不再二次偏移。
 */
public final class WorldPageRenderer {

    /** 世界面板缩放：屏幕短边的两成三换一个世界单位。全息面板在 3 格距离
     * 大约占掉半个屏高，这个系数落出来的字差不多就是这个观感。 */
    public static final double WORLD_PPU_RATIO = 0.23;
    /** 世界页打开的淡入时长，和页签过渡同一个节奏。 */
    private static final long FADE_MS = 260;

    private final LegacyRenderer r;

    public WorldPageRenderer(LegacyRenderer renderer) {
        this.r = renderer;
    }

    /** 世界页判定：display 明写 world，或页面带 world 段（两种写法都认）。 */
    public static boolean isWorldPage(Page page) {
        if (page.displayMode() == DisplayMode.WORLD) {
            return true;
        }
        return page.options() != null && page.options().containsKey("world");
    }

    /** 渲染整页（世界路）：背景底衬先画，可见元素按声明序叠上去。 */
    public void render(Page page) {
        if (page == null || page.elements() == null) {
            return;
        }
        int sw = r.screenWidth();
        int sh = r.screenHeight();
        if (sw <= 0 || sh <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        PageAnimations.resetPageIfAbsent(page.id(), now);
        double fade = Math.min(1.0, (now - PageAnimations.pageOpen(page.id())) / (double) FADE_MS);
        String activeTab = WorldPanels.activeTab(page);

        Object world = page.options() == null ? null : page.options().get("world");
        Map<?, ?> worldMap = world instanceof Map ? (Map<?, ?>) world : null;
        Object bg = worldMap == null ? null : worldMap.get("background");

        // 先把可见元素连同各自动画状态收拢，背景包围盒要靠它们算
        List<Draw> draws = new ArrayList<>();
        for (Element e : page.elements()) {
            if (!Painters.visible(e.props()) || !tabVisible(e, activeTab)) {
                continue;
            }
            PageAnimations.State fx = PageAnimations.tick(page.id(), e.id(),
                    page.options() == null ? null : page.options().get("animations"), now);
            draws.add(new Draw(e, fx, fade));
        }
        if (draws.isEmpty()) {
            return;
        }
        // 海报语义：按 hologram.z 升序画（远→近），同 z 保声明序（List.sort 稳定）。
        // 跟现代端 WorldHologram.render 的排序一个口径，元素前后关系不靠上帝视角
        draws.sort((a, b) -> Double.compare(zOf(a), zOf(b)));

        // 包围盒一份算：背景、居中、缩放全吃这份数据，别各算各的。
        double[] bounds = contentBounds(draws, page);
        double ppu = fitScale(sw, sh, bounds);
        double cx = sw / 2.0 - (bounds[0] + bounds[2]) / 2 * ppu;
        double cy = sh / 2.0 + (bounds[1] + bounds[3]) / 2 * ppu;

        if (bg != null) {
            drawBackground(bg, bounds, cx, cy, ppu, fade);
        }
        for (Draw d : draws) {
            // 屏幕投影路不参与距离淡出（没有“距离”可淡），固定 1.0
            drawWorldElement(page, d, cx, cy, ppu, true, 1.0);
        }
    }

    /**
     * 世界相位渲染：锚点即 hologram 原点（hologram x/y 直接当面板平面像素用，
     * 1px = 0.025 格），不做屏幕适配——面板是真实方块尺寸，远了小近了大。
     * distFade 是页级距离淡出乘子（PageDirector 按现代端 fadeDistance/fadeRange
     * 公式算好传入），元素自己的淡入动画另乘。
     * 屏幕射线拾取还没接，这版不登记命中区（交互元素照画，点了暂时不响）。
     */
    public void renderWorldSpace(Page page, double ppu, double distFade) {
        if (page == null || page.elements() == null || distFade <= 0.0) {
            return;
        }
        long now = System.currentTimeMillis();
        PageAnimations.resetPageIfAbsent(page.id(), now);
        double fade = Math.min(1.0, (now - PageAnimations.pageOpen(page.id())) / (double) FADE_MS);
        String activeTab = WorldPanels.activeTab(page);

        Object world = page.options() == null ? null : page.options().get("world");
        Map<?, ?> worldMap = world instanceof Map ? (Map<?, ?>) world : null;
        Object bg = worldMap == null ? null : worldMap.get("background");

        List<Draw> draws = new ArrayList<>();
        for (Element e : page.elements()) {
            if (!Painters.visible(e.props()) || !tabVisible(e, activeTab)) {
                continue;
            }
            PageAnimations.State fx = PageAnimations.tick(page.id(), e.id(),
                    page.options() == null ? null : page.options().get("animations"), now);
            draws.add(new Draw(e, fx, fade));
        }
        if (draws.isEmpty()) {
            return;
        }
        // 海报语义同屏幕路：z 升序远→近，同 z 保声明序
        draws.sort((a, b) -> Double.compare(zOf(a), zOf(b)));
        if (bg != null) {
            drawBackground(bg, contentBounds(draws, page), 0, 0, ppu, fade * distFade);
        }
        for (Draw d : draws) {
            drawWorldElement(page, d, 0, 0, ppu, false, distFade);
        }
    }

    /** 元素的 hologram.z（海报排序键，缺省 0）。 */
    private static double zOf(Draw d) {
        return holoNum(holo(d.element), "z", 0.0);
    }

    /**
     * holo 段数值读取：数字直取；表达式（如血条宽度
     * "(entity.health_ratio*190)/50"）走 DreamLang 逐帧求值，实体变量
     * 由导演在渲染堆栈里压好。世界页元素就那么几个，逐帧求值的开销
     * 可忽略——等真出现几百元素的页再谈缓存，先别上复杂度。
     */
    private static double holoNum(Map<String, Object> holo, String key, double def) {
        Object v = holo.get(key);
        double d = Painters.num(v, Double.NaN);
        if (!Double.isNaN(d)) {
            return d;
        }
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty()) {
            return def;
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            Map<String, Object> ent = Placeholders.entityScopeMap();
            if (ent != null) {
                scope.assign("entity", ent);
            }
            Object out = com.opendreamcore.script.DreamLang.evaluate(s, scope);
            if (out instanceof Number) {
                return ((Number) out).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    /** 世界元素的一次绘制任务（元素本体 + 动画状态 + 页面淡入）。 */
    private static final class Draw {
        final Element element;
        final PageAnimations.State fx;
        final double fade;

        Draw(Element element, PageAnimations.State fx, double fade) {
            this.element = element;
            this.fx = fx;
            this.fade = fade;
        }
    }

    /** 可见元素的世界坐标包围盒：minX/minY/maxX/maxY，动画摆动幅度算进去。 */
    private double[] contentBounds(List<Draw> draws, Page page) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Draw d : draws) {
            Map<String, Object> holo = holo(d.element);
            double hx = holoNum(holo, "x", 0.0);
            double hy = holoNum(holo, "y", 0.0);
            double halfW = worldHalfWidth(d.element, holo, page) + Math.abs(d.fx.dx);
            double halfH = worldHalfHeight(d.element, holo, page) + Math.abs(d.fx.dy);
            minX = Math.min(minX, hx - halfW);
            maxX = Math.max(maxX, hx + halfW);
            minY = Math.min(minY, hy - halfH);
            maxY = Math.max(maxY, hy + halfH);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * 面板缩放：自然比例优先，装不下就整体缩到屏内（只缩不放），
     * 包围盒中心对准屏幕中心——老截图里偏右下还被裁边就是这里没管。
     */
    private static double fitScale(int sw, int sh, double[] bounds) {
        double ppu = Math.min(sw, sh) * WORLD_PPU_RATIO;
        double bw = bounds[2] - bounds[0];
        double bh = bounds[3] - bounds[1];
        if (bw > 0.01) {
            ppu = Math.min(ppu, sw * 0.9 / bw);
        }
        if (bh > 0.01) {
            ppu = Math.min(ppu, sh * 0.82 / bh);
        }
        return ppu;
    }

    /**
     * 世界面板命中投影：指针屏开着（真鼠标可用）时，把交互元素的面板盒
     * 投影回屏幕坐标登记命中。面板是 billboard——平面跟屏幕平行，
     * holo.x/y 沿屏幕 x/上展开；屏幕缩放 = 焦距/深度（真实方块尺寸的透视缩放）。
     * 命中盒跟 drawWorldElement 同源：worldBox 同参重算，中心对齐 holo.x/y。
     */
    public void registerProjectionHits(Page page, double camX, double camY, double camZ,
                                       double ax, double ay, double az,
                                       double yawDeg, double pitchDeg, double fovDeg) {
        if (page == null || page.elements() == null || !(fovDeg > 0.0)) {
            return; // fov 非 NaN/非正都拒绝（老签名委托传 NaN 安全跳过）
        }
        int sw = r.screenWidth();
        int sh = r.screenHeight();
        if (sw <= 0 || sh <= 0) {
            return;
        }
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        // 相机世界基：forward / right / up（up = right × forward，右手系）
        double fX = -Math.sin(yaw) * Math.cos(pitch);
        double fY = -Math.sin(pitch);
        double fZ = Math.cos(yaw) * Math.cos(pitch);
        double rX = -Math.cos(yaw);
        double rZ = -Math.sin(yaw);
        double uX = -rZ * fY;
        double uY = rZ * fX - rX * fZ;
        double uZ = rX * fY;
        double vX = ax - camX;
        double vY = ay - camY;
        double vZ = az - camZ;
        double depth = vX * fX + vY * fY + vZ * fZ;
        if (!(depth > 0.2)) {
            return; // 面板在背后/贴脸，投影不稳
        }
        double focal = (sh / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
        double axScreen = sw / 2.0 + focal * (vX * rX + vZ * rZ) / depth;
        double ayScreen = sh / 2.0 - focal * (vX * uX + vY * uY + vZ * uZ) / depth;
        double kw = focal / depth;                    // 屏幕 px / 世界单位
        double kp = kw * PageDirector.BLOCKS_PER_PX;  // 屏幕 px / 面板平面 px

        String activeTab = WorldPanels.activeTab(page);
        for (Element e : page.elements()) {
            if (!Painters.visible(e.props()) || !tabVisible(e, activeTab)) {
                continue;
            }
            String type = Painters.normType(e.type());
            if (!Interactions.interactive(type)) {
                continue;
            }
            Map<String, Object> holo = holo(e);
            double hx = holoNum(holo, "x", 0.0);
            double hy = holoNum(holo, "y", 0.0);
            double[] box = worldBox(type, holo, PageDirector.WORLD_PPU);
            double cx = axScreen + hx * kw;
            double cy = ayScreen - hy * kw;
            Interactions.add(page, e,
                    cx - box[0] * kp / 2, cy - box[1] * kp / 2, box[0] * kp, box[1] * kp);
        }
    }

    private void drawBackground(Object bg, double[] bounds, double cx, double cy, double ppu, double fade) {
        double minX = bounds[0];
        double minY = bounds[1];
        double maxX = bounds[2];
        double maxY = bounds[3];
        if (minX > maxX) {
            return;
        }
        Map<?, ?> bgMap = bg instanceof Map ? (Map<?, ?>) bg : null;
        double pad = bgMap != null ? Painters.num(bgMap.get("padding"), 0.25) : 0.25;
        int color = bgMap != null ? Painters.argb(bgMap.get("color"), 0xCC10151F) : Painters.argb(bg, 0xCC10151F);
        int gradient = bgMap != null ? Painters.argb(bgMap.get("gradient"), 0) : 0;
        int border = bgMap != null ? Painters.argb(bgMap.get("border"), 0) : 0;

        double x0 = cx + (minX - pad) * ppu;
        double y0 = cy - (maxY + pad) * ppu;
        double bw = (maxX - minX + pad * 2) * ppu;
        double bh = (maxY - minY + pad * 2) * ppu;
        int a = Painters.mulAlpha(color, fade);
        int borderAlpha = Painters.mulAlpha(border, fade);
        double radius = bgMap != null ? Painters.num(bgMap.get("radius"), 0) * ppu : 0;
        double borderW = bgMap != null ? Painters.num(bgMap.get("borderWidth"), 1) : 1;
        // 现代端 drawRoundedRect 同款：先铺一圈 border 色的圆角底，再内缩画填充，
        // 圆角描边自然成型（outlineRect 直角描边和圆角底衬不搭，实机已踩）
        if (border != 0 && (borderAlpha >>> 24) != 0) {
            r.fillRounded(x0, y0, bw, bh, radius, borderAlpha);
        }
        double inset = (border != 0 && (borderAlpha >>> 24) != 0) ? borderW : 0;
        if (inset * 2 >= bw || inset * 2 >= bh) {
            return;
        }
        if (a >>> 24 != 0) {
            if (gradient != 0) {
                // 渐变底衬：颜色→gradient 线性过渡，圆角扇形内同样按 y 插值
                r.fillRoundedGradient(x0 + inset, y0 + inset, bw - inset * 2, bh - inset * 2,
                        Math.max(0, radius - inset),
                        Painters.mulAlpha(color, fade), Painters.mulAlpha(gradient, fade));
            } else {
                r.fillRounded(x0 + inset, y0 + inset, bw - inset * 2, bh - inset * 2,
                        Math.max(0, radius - inset), a);
            }
        }
    }

    private void drawWorldElement(Page page, Draw d, double cx, double cy, double ppu,
                                  boolean registerHit, double distFade) {
        Map<String, Object> p = d.element.props();
        Map<String, Object> holo = holo(d.element);
        String type = Painters.normType(d.element.type());
        double hx = holoNum(holo, "x", 0.0);
        double hy = holoNum(holo, "y", 0.0);
        double fontUnit = holoNum(holo, "scale", "text".equals(type) ? 0.025 : 0.02) * ppu;

        double px = cx + (hx + d.fx.dx) * ppu;
        double py = cy - (hy + d.fx.dy) * ppu;
        double alpha = d.fx.alphaMul * d.fade * distFade;
        if (alpha <= 0.01) {
            return;
        }

        // 实体真身走绝对坐标直画：它有自己的模型矩阵（yaw/缩放），
        // 套进页面的 2D 变换里会被动画矩阵带偏，所以分流在通用变换外。
        // 世界相位和屏幕相位都画真身，只差 z 抬升用哪套尺度，
        // 画笔侧自己分（renderWorld/render）；建不起来仍画占位框
        if (type.equals("entity")) {
            drawEntity(d, p, holo, cx, cy, ppu, alpha, !registerHit);
            return;
        }

        if (registerHit && Interactions.interactive(type)) {
            double[] box = worldBox(type, holo, ppu);
            Interactions.add(page, d.element, px - box[0] / 2, py - box[1] / 2, box[0], box[1]);
        }
        r.pushPose();
        r.translate(px, py, 0);
        if (d.fx.rotZ != 0) {
            r.rotateZ(d.fx.rotZ);
        }
        if (d.fx.scaleMul != 1.0) {
            r.scale(d.fx.scaleMul);
        }
        try {
            switch (type) {
                case "text": {
                    String content = Placeholders.apply(Painters.textOf(p), page);
                    if (content.isEmpty()) {
                        break;
                    }
                    Map<String, Object> text = Painters.mapOf(p.get("text"));
                    int argb = Painters.mulAlpha(Painters.argb(text.get("color"), 0xFFFFFFFF), alpha);
                    boolean shadow = Boolean.parseBoolean(String.valueOf(
                            text.getOrDefault("shadow", "false")));
                    String[] lines = content.split("\n", -1);
                    // 元素点是文本块中心：先算最长行定基准宽，逐行居中
                    double refW = 0;
                    for (String line : lines) {
                        refW = Math.max(refW, r.textWidth(line));
                    }
                    r.pushPose();
                    r.scale(fontUnit);
                    double blockH = lines.length * 8.0;
                    for (int i = 0; i < lines.length; i++) {
                        double lw = r.textWidth(lines[i]);
                        r.drawText(lines[i], -lw / 2, -blockH / 2 + i * 8.0, argb, shadow);
                    }
                    r.popPose();
                    break;
                }
                case "rect": {
                    double w = Painters.num(holo.get("width"), 1.0) * ppu;
                    double h = Painters.num(holo.get("height"), 1.0) * ppu;
                    Painters.rect(r, p, -w / 2, -h / 2, w, h, alpha);
                    break;
                }
                case "image": {
                    // 贴图来源三层：嵌套 image.src（WorldTexture 转页的形态）→
                    // 顶层 texture → 顶层 src，跟现代端读法对齐
                    Map<String, Object> im = Painters.mapOf(p.get("image"));
                    String tex = Painters.str(im.get("src"));
                    if (tex.isEmpty()) {
                        tex = Painters.str(p.containsKey("texture") ? p.get("texture") : p.get("src"));
                    }
                    if (tex.isEmpty()) {
                        break;
                    }
                    double w = Painters.num(holo.get("width"), 1.0) * ppu;
                    double h = Painters.num(holo.get("height"), 1.0) * ppu;
                    r.drawImage(tex, -w / 2, -h / 2, w, h, 0, 0, 1, 1,
                            Painters.mulAlpha(Painters.argb(im.get("tint"), 0xFFFFFFFF), alpha));
                    break;
                }
                case "tabs": {
                    double w = Painters.num(holo.get("width"), 2.2) * ppu;
                    double h = Painters.num(holo.get("height"), 0.2) * ppu;
                    Painters.tabs(r, p, -w / 2, -h / 2, w, h, alpha, fontUnit, page);
                    break;
                }
                case "toggle": {
                    double w = Painters.num(holo.get("width"), 0.9) * ppu;
                    double h = Painters.num(holo.get("height"), 0.09) * ppu;
                    Painters.toggle(r, p, -w / 2, -h / 2, w, h, alpha, fontUnit);
                    break;
                }
                case "slider": {
                    double w = Painters.num(holo.get("width"), 1.5) * ppu;
                    double h = Painters.num(holo.get("height"), 0.09) * ppu;
                    Painters.slider(r, p, -w / 2, -h / 2, w, h, alpha, fontUnit);
                    break;
                }
                case "itemslot": {
                    double size = Painters.num(holo.get("height"), Painters.num(holo.get("width"), 0.42)) * ppu;
                    Painters.itemSlot(r, p, -size / 2, -size / 2, size, size, alpha);
                    break;
                }
                case "button": {
                    double w = Painters.num(holo.get("width"), 1.6) * ppu;
                    double h = Painters.num(holo.get("height"), 0.22) * ppu;
                    Elements.button(r, p, page, -w / 2, -h / 2, w, h, alpha, false, false);
                    break;
                }
                case "progress": {
                    double w = Painters.num(holo.get("width"), 1.6) * ppu;
                    double h = Painters.num(holo.get("height"), 0.14) * ppu;
                    Elements.progress(r, p, page, -w / 2, -h / 2, w, h, alpha);
                    break;
                }
                case "checkbox": {
                    double w = Painters.num(holo.get("width"), 1.6) * ppu;
                    double h = Painters.num(holo.get("height"), 0.14) * ppu;
                    Elements.checkbox(r, p, page, -w / 2, -h / 2, w, h, alpha, false);
                    break;
                }
                case "line": {
                    double w = Painters.num(holo.get("width"), 1.6) * ppu;
                    double h = Painters.num(holo.get("height"), 0.02) * ppu;
                    Elements.line(r, p, -w / 2, -h / 2, w, h, alpha);
                    break;
                }
                case "circle": {
                    double size = Painters.num(holo.get("width"), Painters.num(holo.get("height"), 0.4)) * ppu;
                    Elements.circle(r, p, -size / 2, -size / 2, size, size, alpha, false);
                    break;
                }
                default: {
                    // 世界页也摆明面：占位框按 hologram 尺寸画，别静默吞
                    double w = Painters.num(holo.get("width"), 1.0) * ppu;
                    double h = Painters.num(holo.get("height"), 0.2) * ppu;
                    Elements.placeholder(r, -w / 2, -h / 2, w, h, alpha, d.element.type(),
                            page.id(), d.element.id());
                    break;
                }
            }
        } finally {
            r.popPose();
        }
    }

    /**
     * entity 元素：临时实体（不进世界）画在面板锚点上。
     * 高版本同款规则：淡入过深时不画（实体无法调透明度，硬调会突兀跳变）。
     * worldPhase 为 true 时走世界画布入口（z 抬升换世界尺度），
     * 屏幕相位走原入口；真身画不上仍回占位框。
     */
    private void drawEntity(Draw d, Map<String, Object> p, Map<String, Object> holo,
                            double cx, double cy, double ppu, double alpha, boolean worldPhase) {
        EntityPainter.Spec spec = EntityPainter.Spec.of(p, holo);
        double anchorX = cx + (Painters.num(holo.get("x")) + d.fx.dx) * ppu;
        double anchorY = cy - (Painters.num(holo.get("y")) + d.fx.dy) * ppu;
        double phSize = Painters.num(holo.get("scale"), 0.85) * ppu * 0.5;
        if (spec.typeId.isEmpty() || alpha < 0.5) {
            placeholderAt(anchorX, anchorY, phSize, alpha);
            return;
        }
        boolean ok = false;
        try {
            // bob 偏移在共享层算好烘进 py，画笔只管落笔
            EntityPainter painter = EntityPainter.Host.current();
            if (worldPhase) {
                ok = painter.renderWorld(
                        spec.typeId, spec.nbt, anchorX, anchorY - spec.bobOff * ppu,
                        spec.scale * ppu, spec.yaw,
                        spec.orthographic, spec.lookAtPlayer,
                        spec.name, spec.nameVisible, spec.glowing);
            } else {
                ok = painter.render(
                        spec.typeId, spec.nbt, anchorX, anchorY - spec.bobOff * ppu,
                        spec.scale * ppu, spec.yaw,
                        spec.orthographic, spec.lookAtPlayer,
                        spec.name, spec.nameVisible, spec.glowing);
            }
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            placeholderAt(anchorX, anchorY, phSize, alpha);
        }
    }

    /** 占位框（实体建不起来时）：绝对坐标画，免得嵌进局部矩阵。 */
    private void placeholderAt(double x, double y, double size, double alpha) {
        r.pushPose();
        r.translate(x, y, 0);
        r.fillRect(-size / 2, -size / 2, size, size, Painters.mulAlpha(0x66222B3A, alpha));
        r.outlineRect(-size / 2, -size / 2, size, size, Painters.mulAlpha(0xFF4A6680, alpha));
        r.popPose();
    }

    /** hologram 段：世界元素的坐标/尺寸都住在这。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> holo(Element e) {
        Object h = e.props().get("hologram");
        return h instanceof Map ? (Map<String, Object>) h : java.util.Collections.emptyMap();
    }

    /** 页签过滤：没有 tab 属性的总显示；有的只认激活页签。 */
    private static boolean tabVisible(Element e, String activeTab) {
        Object tab = e.props().get("tab");
        if (tab == null) {
            return true;
        }
        return activeTab != null && activeTab.equals(String.valueOf(tab));
    }

    /** 交互元素的默认世界盒尺寸（与分发 case 里的兜底宽高一字不差）。 */
    private static double[] worldBox(String type, Map<String, Object> holo, double ppu) {
        if ("tabs".equals(type)) {
            return new double[]{holoNum(holo, "width", 2.2) * ppu, holoNum(holo, "height", 0.2) * ppu};
        }
        if ("toggle".equals(type)) {
            return new double[]{holoNum(holo, "width", 0.9) * ppu, holoNum(holo, "height", 0.09) * ppu};
        }
        if ("slider".equals(type)) {
            return new double[]{holoNum(holo, "width", 1.5) * ppu, holoNum(holo, "height", 0.09) * ppu};
        }
        if ("button".equals(type)) {
            return new double[]{holoNum(holo, "width", 1.6) * ppu, holoNum(holo, "height", 0.22) * ppu};
        }
        if ("progress".equals(type)) {
            return new double[]{holoNum(holo, "width", 1.6) * ppu, holoNum(holo, "height", 0.14) * ppu};
        }
        if ("checkbox".equals(type)) {
            return new double[]{holoNum(holo, "width", 1.6) * ppu, holoNum(holo, "height", 0.14) * ppu};
        }
        return new double[]{holoNum(holo, "width", 1.0) * ppu, holoNum(holo, "height", 0.2) * ppu};
    }

    /** 元素的世界半宽（背景包围盒用）：文字按内容宽，形状类按 width。 */
    private double worldHalfWidth(Element e, Map<String, Object> holo, Page page) {
        String type = Painters.normType(e.type());
        if ("text".equals(type)) {
            Map<String, Object> p = e.props();
            double scale = holoNum(holo, "scale", 0.025);
            String content = Placeholders.apply(Painters.textOf(p), page);
            double maxW = 0;
            for (String line : content.split("\n", -1)) {
                maxW = Math.max(maxW, r.textWidth(line) * scale);
            }
            return maxW / 2;
        }
        if ("entity".equals(type)) {
            return holoNum(holo, "scale", 0.85) * 0.25;
        }
        if ("itemslot".equals(type)) {
            return holoNum(holo, "height", holoNum(holo, "width", 0.42)) / 2;
        }
        return holoNum(holo, "width", 1.0) / 2;
    }

    private double worldHalfHeight(Element e, Map<String, Object> holo, Page page) {
        String type = Painters.normType(e.type());
        if ("text".equals(type)) {
            double scale = holoNum(holo, "scale", 0.025);
            String content = Placeholders.apply(Painters.textOf(e.props()), page);
            return content.split("\n", -1).length * 8.0 * scale / 2;
        }
        if ("entity".equals(type)) {
            return holoNum(holo, "scale", 0.85) * 0.25;
        }
        if ("itemslot".equals(type)) {
            return holoNum(holo, "height", holoNum(holo, "width", 0.42)) / 2;
        }
        return holoNum(holo, "height", 1.0) / 2;
    }
}
