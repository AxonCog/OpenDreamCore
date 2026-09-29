package com.opendreamcore.client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

/**
 * 动图播放器（远古版）：把一张 gif 解成一整张横向帧表交给纹理系统，播放时按时间
 * 落帧，绘制端只负责拿当前帧号切 uv。这样四个远古版本各自的纹理管线完全不用懂
 * gif ——它们眼里只有一张普通的宽图，会动的部分全收在这里。
 *
 * 为什么拼帧表而不是一帧一张纹理：远古两代的 TextureManager 都是"一次注册一张图"
 * 最省事，逐帧注册要么撞名、要么得维护一堆 ResourceLocation 与卸载时机；帧表只有
 * 一张，切 uv 就换帧，跟原版图集是同一个思路，也顺带把资源生命周期压到一条。
 *
 * 帧合成按 gif 规范处理局部帧与处置方式，不处理会拖残影：
 *   0/1 保留上一帧画面，只把本帧那一小块贴上去；
 *   2   本帧画完后，先把这块清成透明再画下一帧；
 *   3   画下一帧之前，把整个画面还原成本帧之前的样子。
 *
 * 节奏取每帧自带的延时（gif 里单位是 1/100 秒），延时为 0 的帧按 100 毫秒兜底
 * （不少导出工具会把 0 写进去，真按 0 走就变成单帧闪现）。配置里的 fps 若给了
 * 正数，则整段统一按 1000/fps 走，忽略自带延时。
 *
 * 全程只用 JDK 自带的 ImageIO，不引任何第三方解码库。
 */
public final class LegacyGifPlayer {

    /** 帧表：所有帧横向排成一行，第 n 帧占 [n*frameW, (n+1)*frameW)。 */
    private final BufferedImage sheet;
    private final int frameW;
    private final int frameH;
    private final int frames;
    /** 每帧停留毫秒数，长度与 frames 对齐。 */
    private final int[] delays;
    private final long totalMs;
    private final File source;
    private final long startedAt = System.currentTimeMillis();

    private LegacyGifPlayer(BufferedImage sheet, int frameW, int frameH, int frames,
                            int[] delays, File source) {
        this.sheet = sheet;
        this.frameW = frameW;
        this.frameH = frameH;
        this.frames = frames;
        this.delays = delays;
        this.source = source;
        long sum = 0L;
        for (int i = 0; i < delays.length; i++) {
            sum += delays[i];
        }
        this.totalMs = sum > 0L ? sum : 1L;
    }

    public BufferedImage sheet() {
        return sheet;
    }

    public int frameW() {
        return frameW;
    }

    public int frameH() {
        return frameH;
    }

    public int frames() {
        return frames;
    }

    public File source() {
        return source;
    }

    /** 当前帧号（按启动时刻推算），永远落在 [0, frames)。 */
    public int currentFrame() {
        return currentFrame(System.currentTimeMillis());
    }

    /** 当前帧号；now 传进来只为好测，正常走无参那个。 */
    public int currentFrame(long now) {
        if (frames <= 1) {
            return 0;
        }
        long t = (now - startedAt) % totalMs;
        if (t < 0L) {
            t += totalMs;
        }
        long acc = 0L;
        for (int i = 0; i < frames; i++) {
            acc += delays[i];
            if (t < acc) {
                return i;
            }
        }
        // 理论上到不了这（t < totalMs），浮点式边界兜一下
        return frames - 1;
    }

    /**
     * 解码入口。不是动图（读不出两帧以上、或整段读失败）返回 null，调用方按静态图处理。
     * fpsOverride &gt; 0 时整段统一帧率。
     */
    public static LegacyGifPlayer decode(File file, double fpsOverride) {
        if (file == null || !file.isFile()) {
            return null;
        }
        ImageReader reader = null;
        ImageInputStream in = null;
        Graphics2D sg = null;
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return null;
            }
            reader = readers.next();
            in = ImageIO.createImageInputStream(file);
            if (in == null) {
                return null;
            }
            // 第二参 false：不预读整段（大图省内存）；第三参 false：允许乱序取帧
            reader.setInput(in, false, false);
            int n = reader.getNumImages(true);
            if (n <= 1) {
                return null;
            }
            BufferedImage first = reader.read(0);
            if (first == null) {
                return null;
            }
            // 画布尺寸以第一帧为准，后续帧越界的部分裁掉——gif 每帧尺寸可以不一样
            int w = first.getWidth();
            int h = first.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            BufferedImage sheet = new BufferedImage(w * n, h, BufferedImage.TYPE_INT_ARGB);
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] delays = new int[n];
            sg = sheet.createGraphics();
            sg.setComposite(AlphaComposite.Src);
            Graphics2D cg = canvas.createGraphics();
            BufferedImage backup = null;
            int prevDisposal = 0;
            int prevX = 0;
            int prevY = 0;
            int prevW = 0;
            int prevH = 0;
            for (int i = 0; i < n; i++) {
                IIOMetadata meta = reader.getImageMetadata(i);
                int[] geo = geometry(meta);
                int fx = geo[0];
                int fy = geo[1];
                int delayCenti = geo[2];
                int disposal = geo[3];
                // 先把上一帧要求的处置补上，再画本帧，否则本帧会叠在被丢弃的内容上
                if (prevDisposal == 2 && prevW > 0 && prevH > 0) {
                    cg.setComposite(AlphaComposite.Clear);
                    cg.fillRect(prevX, prevY, prevW, prevH);
                    cg.setComposite(AlphaComposite.SrcOver);
                } else if (prevDisposal == 3 && backup != null) {
                    cg.setComposite(AlphaComposite.Src);
                    cg.drawImage(backup, 0, 0, null);
                    cg.setComposite(AlphaComposite.SrcOver);
                }
                if (disposal == 3) {
                    backup = copyOf(canvas);
                }
                BufferedImage frame = i == 0 ? first : reader.read(i);
                if (frame != null) {
                    cg.setComposite(AlphaComposite.SrcOver);
                    cg.drawImage(frame, fx, fy, null);
                    prevX = fx;
                    prevY = fy;
                    prevW = frame.getWidth();
                    prevH = frame.getHeight();
                }
                sg.drawImage(canvas, i * w, 0, null);
                delays[i] = millis(delayCenti, fpsOverride);
                prevDisposal = disposal;
            }
            cg.dispose();
            return new LegacyGifPlayer(sheet, w, h, n, delays, file);
        } catch (Throwable ignored) {
            // 读坏一张 gif 不该把整批散装贴图带崩，交给静态图路径兜底
            return null;
        } finally {
            if (sg != null) {
                sg.dispose();
            }
            if (reader != null) {
                reader.dispose();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 帧延时换算：gif 单位 1/100 秒；缺省与 0 都按 100 毫秒；fps 覆盖优先。 */
    private static int millis(int delayCenti, double fpsOverride) {
        if (fpsOverride > 0.0D) {
            int ms = (int) Math.round(1000.0D / fpsOverride);
            return ms > 0 ? ms : 1;
        }
        if (delayCenti <= 0) {
            return 100;
        }
        return delayCenti * 10;
    }

    /** 取帧几何与处置：{x, y, 延时(1/100秒), 处置方式}。 */
    private static int[] geometry(IIOMetadata meta) {
        int x = 0;
        int y = 0;
        int delay = 0;
        int disposal = 0;
        if (meta == null) {
            return new int[]{x, y, delay, disposal};
        }
        try {
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
            // 图像描述符：本帧画在画布的哪个位置
            IIOMetadataNode desc = child(root, "ImageDescriptor");
            if (desc != null) {
                x = attrInt(desc, "imageLeftPosition", 0);
                y = attrInt(desc, "imageTopPosition", 0);
            }
            // 图形控制扩展：停留时间与处置方式
            IIOMetadataNode gce = child(root, "GraphicControlExtension");
            if (gce != null) {
                delay = attrInt(gce, "delayTime", 0);
                disposal = disposalOf(gce);
            }
        } catch (Throwable ignored) {
            // 元数据读不出来就按"整帧不动"处理，画面至少是对的
        }
        return new int[]{x, y, delay, disposal};
    }

    /** 处置方式：规范里是枚举名，个别写入器给数字，两种都认。 */
    private static int disposalOf(IIOMetadataNode gce) {
        String v = gce.getAttribute("disposalMethod");
        if (v == null) {
            return 0;
        }
        v = v.trim();
        if (v.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(v);
        } catch (Throwable ignored) {
        }
        if ("restoreToBackgroundColor".equalsIgnoreCase(v)) {
            return 2;
        }
        if ("restoreToPrevious".equalsIgnoreCase(v)) {
            return 3;
        }
        return 0;
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        if (parent == null) {
            return null;
        }
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) {
                return (IIOMetadataNode) n;
            }
        }
        return null;
    }

    private static int attrInt(IIOMetadataNode node, String attr, int fallback) {
        try {
            String v = node.getAttribute(attr);
            if (v == null || v.trim().length() == 0) {
                return fallback;
            }
            return Integer.parseInt(v.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static BufferedImage copyOf(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }
}
