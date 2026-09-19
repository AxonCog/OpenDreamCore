package com.opendreamcore.client.render;

import com.opendreamcore.page.Element;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;
import com.opendreamcore.page.Page;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 页面渲染器入口：把 common 的 Page/Element 模型翻译成 SPI 调用。
 * 与版本解耦——同一套逻辑跑遍 1.6.4~1.16.5，四个壳只提供画笔。
 *
 * 两条通路在这里分流：
 * 世界页（display: world 或带 world 段）→ WorldPageRenderer，
 *   面板整块投屏幕中央，坐标乘 PPU 换算；
 * 屏幕页（screen/hud）：元素 x/y/width/height 按逻辑像素画，原点左上。
 *
 * 屏幕元素类型：rect / text / image / tabs / toggle / slider / item_slot /
 * 其他（container/button 等有底色画底色）。children 递归，后画的盖前面的。
 * 文本落笔前过占位符替换（{player.*}/{vars.*}），动画每帧推进套在变换上。
 */
public final class PageRenderer {

    private final LegacyRenderer r;

    /** 视图窗口：屏幕页表达式环境的 window 段（现代端同义：guiScaled 画布，1:1 布局）。 */
    private double viewW;
    private double viewH;

    public PageRenderer(LegacyRenderer renderer) {
        this.r = renderer;
    }

    /** 渲染整页：世界页交给 WorldPageRenderer，屏幕页自己画。 */
    public void render(Page page) {
        if (page == null || page.elements() == null) {
            return;
        }
        if (WorldPageRenderer.isWorldPage(page)) {
            new WorldPageRenderer(r).render(page);
            return;
        }
        // 画布口径：页面声明了 design:{width,height} 就按设计稿等比缩放适配窗口，
        // 物理尺寸与 MC 的 GUI 缩放档位无关（guiScaled 空间里乘的补偿系数
        // 正好把 guiScale 抵消：物理 = min(realW/dw, realH/dh) × 设计稿）；
        // 没声明保持 1:1 guiScaled（现代端同口径，多大排多大）。
        int sw = r.screenWidth();
        int sh = r.screenHeight();
        xform = designTransform(page, sw, sh);
        if (xform != null) {
            viewW = xform.w;
            viewH = xform.h;
            r.pushPose();
            r.translate(xform.ox, xform.oy, 0);
            r.scale(xform.s);
        } else {
            viewW = sw;
            viewH = sh;
        }
        List<Element> sorted = new java.util.ArrayList<>(page.elements());
        sorted.sort(java.util.Comparator.comparingInt(PageRenderer::zOf));
        try {
            for (Element e : sorted) {
                drawScreenElement(page, e, 0, 0, viewW, viewH, 1.0);
            }
        } finally {
            if (xform != null) {
                r.popPose();
            }
            xform = null;
        }
    }

    /** 设计画布变换：null = 未声明，按 1:1 guiScaled 直画。 */
    private Xform xform;

    private static Xform designTransform(Page page, int sw, int sh) {
        Map<String, Object> design = page.options() == null ? null
                : Painters.mapOf(page.options().get("design"));
        if (design == null || design.isEmpty()) {
            return null;
        }
        double dw = Painters.num(design.get("width"), 0);
        double dh = Painters.num(design.get("height"), 0);
        if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) {
            return null;
        }
        // scale 倍率：充满度微调（1 = 适配满屏；1.25 = 比适配大一母；0 关不掉 design，
        // 想关就把 design 段删掉回 1:1 guiScaled）。基准是适配后的尺寸再乘。
        double boost = Painters.num(design.get("scale"), 1.0);
        double s = Math.min(sw / dw, sh / dh) * (boost > 0 ? boost : 1.0);
        if (!(s > 0) || !Double.isFinite(s)) {
            return null;
        }
        return new Xform(dw, dh, s, (sw - dw * s) / 2.0, (sh - dh * s) / 2.0);
    }

    /** 设计画布：偏移（guiScaled 系）+ 缩放系数 + 设计稿尺寸。 */
    private static final class Xform {
        final double w;
        final double h;
        final double s;
        final double ox;
        final double oy;

        Xform(double w, double h, double s, double ox, double oy) {
            this.w = w;
            this.h = h;
            this.s = s;
            this.ox = ox;
            this.oy = oy;
        }
    }

    //     // 屏幕页
    // 
    private void drawScreenElement(Page page, Element e, double parentX, double parentY, double parentW, double parentH, double parentAlpha) {
        Map<String, Object> p = e.props();
        if (!Painters.visible(p)) {
            return;
        }
        // x/y/宽高支持表达式（window.width/2、parent.width-30、vars.xxx），
        // 环境和高版本 LayoutEngine 同款：window/parent/this/vars 平铺进作用域。
        // 取值必须走 e.layout()——PageSchema 把 x/y/width/height 当公共键从
        // props 剥进了 Layout（isCommonKey），读 props 永远拿到 null，
        // 全页元素就会一齐塌到 (0,0)（实机踩过）
        com.opendreamcore.page.Layout lay = e.layout();
        Map<String, Object> env = layoutEnv(page, e, parentX, parentY, parentW, parentH, viewW, viewH);
        // 表达式逐帧重算太浪费（DreamLang 每次都重新解析求值），按现代端
        // layoutHash 同义做缓存：环境没变就直接复用上帧的盒子
        double[] box = layoutBox(page, e, lay, env);
        double x = parentX + box[0];
        double y = parentY + box[1];
        double w = box[2];
        double h = box[3];

        long now = System.currentTimeMillis();
        PageAnimations.State fx = PageAnimations.tick(page.id(), e.id(),
                page.options() == null ? null : page.options().get("animations"), now);
        double alpha = parentAlpha * fx.alphaMul;
        if (alpha <= 0.01) {
            return;
        }

        String type = Painters.normType(e.type());
        // 静态 opacity/scale/rotation（props 表达式位，动画叠加在外层 fx 里）
        double staticOpacity = Painters.num(p.get("opacity"), 1.0);
        double staticScale = Painters.num(p.get("scale"), 1.0);
        double staticRotation = Painters.num(p.get("rotation"), 0.0);
        alpha *= staticOpacity;
        // 命中/hover 用屏幕系坐标：设计画布开着时渲染走矩阵，鼠标没矩阵可乘，
        // 手工把页面系盒子映射回屏幕系（鼠标喂的也是屏幕系）
        double hitX = xform == null ? x : xform.ox + x * xform.s;
        double hitY = xform == null ? y : xform.oy + y * xform.s;
        double hitW = xform == null ? w : w * xform.s;
        double hitH = xform == null ? h : h * xform.s;
        if (Interactions.interactive(type)) {
            Interactions.add(page, e, hitX, hitY, hitW, hitH);
        }
        final boolean hovered = MouseState.hover(hitX, hitY, hitW, hitH);
        final boolean pressed = MouseState.press(hitX, hitY, hitW, hitH);
        r.pushPose();
        r.translate(x + fx.dx, y + fx.dy, 0);
        if (fx.rotZ != 0) {
            r.rotateZ(fx.rotZ);
        }
        if (fx.scaleMul != 1.0) {
            r.scale(fx.scaleMul);
        }
        if (staticRotation != 0 || staticScale != 1.0) {
            // 绕元素中心摆姿势，和高版本那套 translate(c)→rot→scale→回Translate 同构
            r.translate(w / 2, h / 2, 0);
            if (staticRotation != 0) {
                r.rotateZ(staticRotation);
            }
            if (staticScale != 1.0) {
                r.scale(staticScale);
            }
            r.translate(-w / 2, -h / 2, 0);
        }
        try {
            // 垫底三件套：阴影→发光→背景，内容最后落笔（现代端同序）
            Elements.shadow(r, p, 0, 0, w, h, alpha);
            Elements.glow(r, p, 0, 0, w, h, alpha);
            // 附属自注的渲染器优先（类型名规范化后比对），没命中才走内置那串分支
            com.opendreamcore.client.api.LegacyRendererRegistry.Renderer custom =
                    com.opendreamcore.client.api.LegacyRendererRegistry.get(type);
            if (custom != null) {
                custom.render(r, page, e, p, w, h, alpha, hovered, pressed);
            } else
            switch (type) {
                case "rect":
                    Painters.rect(r, p, 0, 0, w, h, alpha);
                    break;
                case "text":
                    drawTextShape(page, p, 0, 0, w, h, alpha);
                    break;
                case "image":
                    // 嵌套 image.src 优先（WorldTexture 转页同形态），顶层 texture/src 兕底
                    Map<String, Object> im = Painters.mapOf(p.get("image"));
                    String tex = Painters.str(im.get("src"));
                    if (tex.isEmpty()) {
                        tex = Painters.str(p.containsKey("texture") ? p.get("texture") : p.get("src"));
                    }
                    if (!tex.isEmpty()) {
                        r.drawImage(tex, 0, 0, w, h, 0, 0, 1, 1, Painters.mulAlpha(
                                Painters.argb(im.containsKey("tint") ? im.get("tint") : p.get("tint"),
                                        0xFFFFFFFF), alpha));
                    }
                    break;
                case "tabs":
                    Painters.tabs(r, p, 0, 0, w, h, alpha, 1.0, page);
                    break;
                case "toggle":
                    Painters.toggle(r, p, 0, 0, w, h, alpha, 1.0);
                    break;
                case "slider":
                    Painters.slider(r, p, 0, 0, w, h, alpha, 1.0);
                    break;
                case "itemslot":
                    Painters.itemSlot(r, p, 0, 0, w <= 0 ? 18 : w, h <= 0 ? 18 : h, alpha);
                    break;
                case "button":
                    Elements.button(r, p, page, 0, 0, w, h, alpha, hovered, pressed);
                    break;
                case "progress":
                    Elements.progress(r, p, page, 0, 0, w, h, alpha);
                    break;
                case "checkbox":
                    Elements.checkbox(r, p, page, 0, 0, w, h, alpha, hovered);
                    break;
                case "bar":
                    Elements.bar(r, p, page, 0, 0, w, h, alpha);
                    break;
                case "line":
                    Elements.line(r, p, 0, 0, w, h, alpha);
                    break;
                case "circle":
                    Elements.circle(r, p, 0, 0, w, h, alpha, hovered);
                    break;
                case "triangle":
                    r.fillTriangle(w / 2, 0, w, h, 0, h,
                            Painters.mulAlpha(Painters.argb(Painters.mapOf(p.get("triangle")).get("color"),
                                    Painters.argb(p.get("color"), 0xFFFFD54F)), alpha));
                    break;
                case "gradient":
                    Elements.gradient(r, p, 0, 0, w, h, alpha);
                    break;
                case "gauge":
                    Elements.gauge(r, p, page, 0, 0, w, h, alpha);
                    break;
                case "dropdown":
                case "suggestion":
                    Elements.dropdown(r, p, page, 0, 0, w, h, alpha, hovered);
                    break;
                case "input":
                case "areainput":
                    Elements.input(r, p, page, 0, 0, w, h, alpha, hovered, hovered);
                    break;
                case "chatdisplay":
                    Elements.chat(r, p, page, 0, 0, w, h, alpha);
                    break;
                case "bossbar":
                    Elements.bossBar(r, p, page, 0, 0, w, h, alpha);
                    break;
                case "compass":
                    Elements.compass(r, p, 0, 0, w, h, alpha);
                    break;
                case "direction":
                    Elements.direction(r, p, 0, 0, w, h, alpha);
                    break;
                case "hotslot":
                    // 快捷栏真物品（ItemPainter SPI）；画不了退回格底占位
                    int slot = (int) Painters.num(p.get("index") != null ? p.get("index") : p.get("slot"), 0);
                    double hs = w <= 0 ? 18 : w;
                    double hh = h <= 0 ? 18 : h;
                    if (!com.opendreamcore.client.spi.ItemPainter.Host.current()
                            .renderHotbar(slot, 0, 0, Math.min(hs, hh), alpha)) {
                        Painters.itemSlot(r, p, 0, 0, hs, hh, alpha);
                    }
                    break;
                case "entity":
                case "model":
                    drawLegacyEntity(p, type, x, y, w, h);
                    break;
                case "item_model":
                case "item_3d":
                    drawLegacyItemModel(p, x, y, w, h);
                    break;
                case "layout":
                case "container":
                case "foreach":
                case "scroll":
                case "canvas":
                    Elements.background(r, p, 0, 0, w, h, alpha, hovered, false);
                    Elements.drawBrushes(r, p, page, w, h, alpha);
                    break;
                default: {
                    // 画不了就摆在明面上：占位框带类型名，不再静默丢
                    Elements.placeholder(r, 0, 0, w, h, alpha, e.type(), page.id(), e.id());
                    break;
                }
            }
            Elements.badge(r, p.get("badge"), 0, 0, w, h, alpha);
            Elements.statusIcon(r, p.get("statusIcon"), 0, 0, alpha);
            // hover 元素且服务端注册了 tooltip → 自管画提示框（原版容器槽位是老版本
            // 平台边界，元素 tooltip 这条全版本都有）
            if (hovered) {
                drawHoverTooltip(r, e, alpha);
            }
        } finally {
            r.popPose();
        }

        if (e.children() != null) {
            for (Element child : e.children()) {
                drawScreenElement(page, child, x, y, w, h, alpha);
            }
        }
    }

    /** 占位符替换要带页：{vars.xxx}/{menu_name} 这类页面变量从 page.variables() 取，传 null 就解析不出来。 */
    private void drawTextShape(Page page, Map<String, Object> p, double x, double y, double w, double h, double alpha) {
        String text = Placeholders.apply(Painters.textOf(p), page);
        if (text.isEmpty()) {
            return;
        }
        Map<String, Object> spec = Painters.mapOf(p.get("text"));
        boolean shadow = Painters.bool(spec.get("shadow"));
        int argb = Painters.mulAlpha(Painters.argb(spec.get("color"), 0xFFFFFFFF), alpha);
        String align = Painters.str(spec.get("align"));
        double scale = Painters.num(spec.get("scale"), 1.0);
        // fontSize：现代端语义 = 字号/默认字高(9)，不写就不动
        double fs = Painters.num(spec.get("fontSize"), 0);
        if (fs > 0) {
            scale *= fs / 9.0;
        }
        double lh = Painters.num(spec.get("lineHeight"), 9.0);
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            double lx = x;
            if (!align.isEmpty() && w > 0) {
                double tw = r.textWidth(line) * scale;
                if ("center".equals(align)) {
                    lx = x + (w - tw) / 2;
                } else if ("right".equals(align)) {
                    lx = x + w - tw;
                }
            }
            if (scale != 1.0) {
                r.pushPose();
                r.translate(lx, y + i * lh, 0);
                r.scale(scale);
                drawLineChars(line, 0, 0, argb, shadow);
                r.popPose();
            } else {
                drawLineChars(line, lx, y + i * lh, argb, shadow);
            }
        }
    }

    /**
     * 单行文本逐字符绘制：FontConfig 命中字符贴图顶替（复用 image 通道 drawImage），
     * 未命中回退字体 drawText；无替换规则时整行直通，零额外开销。
     */
    private void drawLineChars(String line, double x, double y, int argb, boolean shadow) {
        if (!com.opendreamcore.client.visual.LegacyFontReplace.hasAny()) {
            r.drawText(line, x, y, argb, shadow);
            return;
        }
        double cx = x;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            com.opendreamcore.client.visual.LegacyFontReplace.Glyph g =
                    com.opendreamcore.client.visual.LegacyFontReplace.glyphFor(c);
            if (g != null) {
                // 整图模式：u/v 定位到子帧；uv 按纹理整图比例给
                r.drawImage(g.texture, cx, y, g.frameW, g.frameH,
                        (double) g.u, g.v, g.u + g.frameW, g.v + g.frameH, argb);
                cx += g.fontWidth;
            } else {
                cx += r.drawText(String.valueOf(c), cx, y, argb, shadow);
            }
        }
    }

    /** hover 元素的自定义 tooltip：服务端注册优先，画在鼠标右下。 */
    private void drawHoverTooltip(com.opendreamcore.client.render.LegacyRenderer r,
                                  Element e, double alpha) {
        com.opendreamcore.client.LegacyTooltipStore.Entry entry =
                com.opendreamcore.client.LegacyTooltipStore.get(e.id());
        if (entry == null) {
            return;
        }
        String[] lines = String.valueOf(entry.text).split("\n", -1);
        double tw = 0;
        for (String l : lines) {
            tw = Math.max(tw, r.textWidth(l));
        }
        double pad = 4;
        double lh = 10;
        double bx = MouseState.mouseX + 8;
        double by = MouseState.mouseY + 8;
        double bw = tw + pad * 2;
        double bh = lines.length * lh + pad * 2;
        r.fillRect(bx, by, bw, bh, Painters.mulAlpha(
                Painters.argb(entry.background, 0xCC101018), alpha));
        int fg = Painters.argb(entry.color, 0xFFFFFFFF);
        for (int i = 0; i < lines.length; i++) {
            r.drawText(lines[i], bx + pad, by + pad + i * lh,
                    Painters.mulAlpha(fg, alpha), false);
        }
    }

    //     // 表达式布局环境（对齐高版本 LayoutEngine：window/parent/this/vars）
    // 
    /**
     * 布局缓存：键=页面/元素/父盒+表达式+环境指纹，命中直接复用上帧盒子。
     * 服务端 global_state 或变量一变指纹就变，自然失效——对齐现代端
     * layoutCache 的复用语义，把逐帧表达式解析的开销摊平。
     */
    private static final java.util.HashMap<String, double[]> LAYOUT_CACHE =
            new java.util.HashMap<>();

    private static double[] layoutBox(Page page, Element e, com.opendreamcore.page.Layout lay,
                                      Map<String, Object> env) {
        StringBuilder key = new StringBuilder(96);
        key.append(page == null ? "-" : page.id()).append('|').append(e.id())
                .append('|').append(lay == null ? "-" : String.valueOf(lay.x()))
                .append('|').append(lay == null ? "-" : String.valueOf(lay.y()))
                .append('|').append(lay == null ? "-" : String.valueOf(lay.width()))
                .append('|').append(lay == null ? "-" : String.valueOf(lay.height()))
                .append('|').append(env);
        String k = key.toString();
        double[] hit = LAYOUT_CACHE.get(k);
        if (hit != null) {
            return hit;
        }
        double[] box = new double[]{
                evalNum(lay == null ? null : lay.x(), env, 0),
                evalNum(lay == null ? null : lay.y(), env, 0),
                evalNum(lay == null ? null : lay.width(), env, 100),
                evalNum(lay == null ? null : lay.height(), env, 20),
        };
        // 缓存设上限：页面翻飞重载时旧键换新键，超了整体清一把防张普
        if (LAYOUT_CACHE.size() > 4096) {
            LAYOUT_CACHE.clear();
        }
        LAYOUT_CACHE.put(k, box);
        return box;
    }

    private static Map<String, Object> layoutEnv(Page page, Element e, double px, double py,
                                                 double pw, double ph, double winW, double winH) {
        Map<String, Object> env = new java.util.LinkedHashMap<>();
        Map<String, Object> window = new java.util.LinkedHashMap<>();
        window.put("width", winW);
        window.put("height", winH);
        env.put("window", window);
        env.put("w", winW);
        env.put("h", winH);
        if (pw > 0 || ph > 0) {
            Map<String, Object> parent = new java.util.LinkedHashMap<>();
            parent.put("width", pw);
            parent.put("height", ph);
            env.put("parent", parent);
        }
        if (page != null && page.variables() != null) {
            env.putAll(page.variables());
        }
        // 服务端全局变量平铺进作用域（现代端 scopeOf=vars+globals 同款），
        // 表达式里直接写 {online}/{tps} 就能拿到，和 {{global.online}} 等价
        env.putAll(com.opendreamcore.client.MessageDispatcher.globalsView());
        return env;
    }

    private static double evalNum(Object v, Map<String, Object> env, double fallback) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            if (s.matches("-?\\d+(\\.\\d+)?")) {
                return Double.parseDouble(s);
            }
            // window/parent/this 走局部命名空间（成员链 window.width 才通），
            // 其余进 vars（裸名/vars.xxx 都能引用）——和高版本 LayoutEngine.scopeOf 一字同款
            Scope scope = new Scope();
            for (Map.Entry<String, Object> en : env.entrySet()) {
                String k = en.getKey();
                if ("window".equals(k) || "parent".equals(k) || "this".equals(k)) {
                    scope.assign(k, en.getValue());
                } else {
                    scope.assignVar(k, en.getValue());
                }
            }
            Object out = DreamLang.evaluate(s, scope);
            if (out instanceof Number) {
                return ((Number) out).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /** 元素 z 序（props.z，缺省 0）：大者后画盖上面。 */
    private static int zOf(Element e) {
        Object z = e.props().get("z");
        if (z instanceof Number) {
            return ((Number) z).intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(z));
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** GUI 内实体组件（type: entity/model）：取实体 → 求值缩放/旋转 → 版本桥画。 */
    private void drawLegacyEntity(Map<String, Object> p, String type, double x, double y, double w, double h) {
        com.opendreamcore.client.spi.LegacyEntityRenderBridge b =
                com.opendreamcore.client.spi.LegacyEntityRenderBridge.Host.current();
        if (b == null) {
            return;
        }
        try {
            com.opendreamcore.ui.EntityViewSpec spec = com.opendreamcore.ui.EntityViewSpec.parse(type, p);
            Object ent = spec.kind == com.opendreamcore.ui.EntityViewSpec.Kind.ENTITY
                    ? b.resolveEntity(spec.entity)
                    : b.dummyFor(spec.model == null || spec.model.isEmpty() ? "player" : spec.model);
            if (ent == null) {
                return;
            }
            double cx = x + w / 2.0;
            double cy = y + h / 2.0;
            double scale = evalNum(spec.scale, 1.0);
            double ry = evalNum(spec.rotateY, 0.0);
            double rx = evalNum(spec.rotateX, 0.0);
            float yaw;
            float pitch;
            if (spec.followMouse) {
                float dx = (float) (MouseState.mouseX - cx);
                float dy = (float) (MouseState.mouseY - cy);
                yaw = (float) ry + 180.0f + (float) (Math.atan(dx / 40.0) * 40.0);
                pitch = (float) rx + (float) (Math.atan(dy / 40.0) * 20.0);
            } else {
                yaw = (float) ry;
                pitch = (float) rx;
            }
            b.drawEntity((int) cx, (int) cy, (float) scale, yaw, pitch, ent);
        } catch (Throwable t) {
            // 实体组件出岔子不炸页面
        }
    }

    /** GUI 内物品模型组件（type: item_model）：解析 → 求值 → 版本桥画。 */
    private void drawLegacyItemModel(Map<String, Object> p, double x, double y, double w, double h) {
        com.opendreamcore.client.spi.LegacyItemRenderBridge b =
                com.opendreamcore.client.spi.LegacyItemRenderBridge.Host.current();
        if (b == null) {
            return;
        }
        try {
            com.opendreamcore.ui.ItemModelSpec spec = com.opendreamcore.ui.ItemModelSpec.parse(p);
            if (spec.item == null || spec.item.isEmpty()) {
                return;
            }
            double cx = x + w / 2.0;
            double cy = y + h / 2.0;
            double scale = evalNum(spec.scale, 1.0);
            double ry = evalNum(spec.rotateY, 0.0);
            double rx = evalNum(spec.rotateX, 0.0);
            float yaw;
            float pitch;
            if (spec.followMouse) {
                float dx = (float) (MouseState.mouseX - cx);
                float dy = (float) (MouseState.mouseY - cy);
                yaw = (float) ry + 180.0f + (float) (Math.atan(dx / 40.0) * 40.0);
                pitch = (float) rx + (float) (Math.atan(dy / 40.0) * 20.0);
            } else {
                yaw = (float) ry;
                pitch = (float) rx;
            }
            b.drawItemModel((int) cx, (int) cy, (float) scale, yaw, pitch, spec.item);
        } catch (Throwable t) {
            // 物品组件出岔子不炸页面
        }
    }

    /** 字段求值：数字直读，字符串先按数字试，再丢给 DreamLang 表达式。 */
    private double evalNum(Object raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception ignored) {
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            Object r = com.opendreamcore.script.DreamLang.evaluate(s, scope);
            return r instanceof Number ? ((Number) r).doubleValue() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
