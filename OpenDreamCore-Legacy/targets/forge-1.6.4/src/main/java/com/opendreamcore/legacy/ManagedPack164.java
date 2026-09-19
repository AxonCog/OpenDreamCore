package com.opendreamcore.legacy;

import net.minecraft.client.resources.ResourcePack;
import net.minecraft.client.resources.data.MetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * 托管根目录常驻包（1.6.4）：resourcepacks/OpenDreamCore 本身当一张包。
 *
 * 两种布局都认：标准 assets/opendreamcore/gui/hp.png 和直接在根下丢
 * gui/hp.png（DreamEngine 同款体验）。元数据虚拟返回，根目录不强制放
 * pack.mcmeta。minecraft: 命名空间只走标准布局，防止一张贴图顶掉原版资源。
 */
public final class ManagedPack164 implements ResourcePack {

    /** 托管根目录。 */
    private final File base;
    /** 虚拟元数据里报的包格式（1.6.4 = 1）。 */
    private final int packFormat;

    public ManagedPack164(File base, int packFormat) {
        this.base = base;
        this.packFormat = packFormat;
    }

    /** 按 opendreamcore 语义解析：先标准布局，再根目录直放。 */
    private File fileFor(ResourceLocation location) {
        File standard = new File(base, "assets/" + location.getResourceDomain()
                + "/" + location.getResourcePath());
        if (standard.isFile()) {
            return standard;
        }
        if ("opendreamcore".equals(location.getResourceDomain())) {
            File direct = new File(base, location.getResourcePath());
            if (direct.isFile()) {
                return direct;
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws java.io.IOException {
        File f = fileFor(location);
        if (f == null) {
            throw new java.io.FileNotFoundException(location.toString());
        }
        return new FileInputStream(f);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return fileFor(location) != null;
    }

    @Override
    public Set getResourceDomains() {
        return Collections.singleton("opendreamcore");
    }

    /** 虚拟元数据：不读 pack.mcmeta，直接编一个回去（1.6.4 的描述是 String）。 */
    @Override
    public MetadataSection getPackMetadata(MetadataSerializer serializer, String section) {
        if ("pack".equals(section)) {
            return new PackMetadataSection("OpenDreamCore 托管材质包", packFormat);
        }
        return null;
    }

    /** 包图标：从根目录 pack.png 读，没有就 null。 */
    @Override
    public BufferedImage getPackImage() {
        File logo = new File(base, "pack.png");
        if (!logo.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(logo);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getPackName() {
        return "OpenDreamCore/托管根";
    }
}
