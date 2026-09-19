package com.opendreamcore.legacy;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
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
 * 托管根目录常驻包（1.12.2）：resourcepacks/OpenDreamCore 本身当一张包。
 *
 * 两种布局都认：
 * 
 *   标准：assets/opendreamcore/gui/hp.png → opendreamcore:gui/hp.png
 *   直放：根下丢 gui/hp.png → opendreamcore:gui/hp.png（DreamEngine 同款体验）
 * 
 *
 * 元数据虚拟返回，根目录不强制放 pack.mcmeta。minecraft: 命名空间只走
 * 标准布局（assets/minecraft/...），不开放根目录直读——不然一张
 * textures/blocks/stone.png 就把原版石头整个顶掉了。
 */
public final class ManagedPack1212 implements IResourcePack {

    /** 托管根目录。 */
    private final File base;
    /** 虚拟元数据里报的包格式（1.12.2 = 3）。 */
    private final int packFormat;

    public ManagedPack1212(File base, int packFormat) {
        this.base = base;
        this.packFormat = packFormat;
    }

    /** 按 opendreamcore 语义解析：先标准布局，再根目录直放。 */
    private File fileFor(ResourceLocation location) {
        File standard = new File(base, "assets/" + location.getNamespace()
                + "/" + location.getPath());
        if (standard.isFile()) {
            return standard;
        }
        if ("opendreamcore".equals(location.getNamespace())) {
            File direct = new File(base, location.getPath());
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
    public Set<String> getResourceDomains() {
        return Collections.singleton("opendreamcore");
    }

    /** 虚拟元数据：不读 pack.mcmeta，直接编一个回去。 */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section)
            throws java.io.IOException {
        if ("pack".equals(section)) {
            return (T) new PackMetadataSection(
                    new net.minecraft.util.text.TextComponentString("OpenDreamCore 托管材质包"),
                    packFormat);
        }
        return null;
    }

    /** 包图标：从根目录 pack.png 读，没有就 null。 */
    @Override
    public BufferedImage getPackImage() throws java.io.IOException {
        File logo = new File(base, "pack.png");
        return logo.isFile() ? ImageIO.read(logo) : null;
    }

    @Override
    public String getPackName() {
        return "OpenDreamCore/托管根";
    }
}
