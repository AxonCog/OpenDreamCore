package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public final class YamlLegacy {
    private YamlLegacy() { }

    public static void install() {
        LegacyMethods.register("取Yaml值", YamlLegacy::get);
        LegacyMethods.register("取yaml值", YamlLegacy::get);
        LegacyMethods.register("取yamlValue", YamlLegacy::get);
        LegacyMethods.register("取Yaml节点", YamlLegacy::get);
        LegacyMethods.register("取yaml节点", YamlLegacy::get);
        LegacyMethods.register("取Yaml全部节点", YamlLegacy::get);
        LegacyMethods.register("取yaml全部节点", YamlLegacy::get);
        LegacyMethods.register("Yaml文件存在", a -> {
            String f = LegacyMethods.argStr(a, 0);
            return f != null && java.nio.file.Files.isRegularFile(
                    Minecraft.getInstance().gameDirectory.toPath()
                            .resolve("OpenDreamCore").resolve(f + ".yaml"));
        });
    }

    private static Object get(Object[] a) {
        String file = LegacyMethods.argStr(a, 0);
        String key = LegacyMethods.argStr(a, 1);
        if (file == null || key == null) return null;
        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve(file + ".yaml");
            if (!java.nio.file.Files.isRegularFile(p)) return null;
            var data = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(p));
            Object cur = data;
            for (String part : key.split("\\.")) {
                if (!(cur instanceof java.util.Map)) return null;
                cur = ((java.util.Map<?, ?>) cur).get(part);
            }
            return cur;
        } catch (Exception e) { return null; }
    }
}
