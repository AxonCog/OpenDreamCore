package com.opendreamcore.client;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * JavaCV（ffmpeg）反射解码静态图片（webp/bmp/tiff 等 ImageIO 不认的格式）。
 * javacv jar 丢进 mods 即可启用；jar 不在时返回 null（调用方按解码失败处理）。
 * 全反射调用，编译期零依赖 javacv。
 */
public final class JavaCvImage {

    private JavaCvImage() {
    }

    /** ffmpeg 解码图片文件 → BufferedImage（webp/bmp/gif 等全部支持）；失败返回 null。 */
    public static BufferedImage decode(Path file) {
        Object grabber = null;
        try {
            Class<?> grabberCls = Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber");
            grabber = grabberCls.getConstructor(String.class).newInstance(file.toString());
            grabberCls.getMethod("start").invoke(grabber);
            try {
                Object frame = grabberCls.getMethod("grabImage").invoke(grabber);
                if (frame != null) {
                    Class<?> converterCls = Class.forName("org.bytedeco.javacv.Java2DFrameConverter");
                    Object converter = converterCls.getConstructor().newInstance();
                    Object img = converterCls.getMethod("convert", frame.getClass()).invoke(converter, frame);
                    if (img instanceof BufferedImage bi) {
                        return bi;
                    }
                }
            } finally {
                try {
                    grabberCls.getMethod("stop").invoke(grabber);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
