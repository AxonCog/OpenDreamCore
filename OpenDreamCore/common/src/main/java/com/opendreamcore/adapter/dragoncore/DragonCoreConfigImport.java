package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.config.YamlParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 龙核 config.yml 适配（住在 dragoncore 文件夹里的财产交接员）。
 *
 * 老服主在龙核 config.yml 里配过的三样东西，跟他搬家的时候得带走：
 *   - Password：资源缓存的加密密码；
 *   - ResourcePack：他在哪放资源的（默认 plugins/DragonCoreResource）；
 *   - syncResource：资源同步开没开。
 *
 * 放进 ODC 之后直接成为我们资源云的对应配置（resource-password /
 * resource-pack / resource-sync）。服主保留原有龙核配置，换个内核
 * 设置原地继承，不用重新面试一遍 config。
 *
 * 只读不写：改的是对方家的东西，拿走值就行，不碰原文件。
 */
public final class DragonCoreConfigImport {

    /** 搬过来的三样家当。 */
    public static final class Settings {
        public final String password;
        public final String resourcePack;
        public final boolean syncResource;

        Settings(String password, String resourcePack, boolean syncResource) {
            this.password = password;
            this.resourcePack = resourcePack;
            this.syncResource = syncResource;
        }
    }

    private DragonCoreConfigImport() {
    }

    /** 从龙核插件目录读 config.yml（没有龙核/文件不在/读不懂 → null）。 */
    public static Settings read(Path dragonCoreRoot) {
        if (dragonCoreRoot == null) {
            return null;
        }
        Path file = dragonCoreRoot.resolve("config.yml");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Map<String, Object> raw = YamlParser.lenient().parse(text);
            if (raw == null) {
                return null;
            }
            String password = str(raw.get("Password"));
            String resourcePack = str(raw.get("ResourcePack"));
            boolean sync = Boolean.TRUE.equals(raw.get("syncResource"));
            return new Settings(password, resourcePack, sync);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.trim().isEmpty() ? null : s.trim();
    }
}