package com.opendreamcore.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元素级位置编辑记忆：页面 id → 元素 id → {x, y}。
 * 编辑模式下拖动元素后保存到这里，打开页面时按覆盖应用（不重写 YAML）。
 */
public final class ElementEditStore {

    private final Map<String, Map<String, double[]>> edits = new ConcurrentHashMap<>();

    /** 页面的元素位置覆盖（无则 null）。 */
    public Map<String, double[]> forPage(String pageId) {
        return edits.get(pageId);
    }

    public void set(String pageId, String elementId, double x, double y) {
        edits.computeIfAbsent(pageId, k -> new ConcurrentHashMap<>()).put(elementId, new double[]{x, y});
    }

    public void clear(String pageId) {
        edits.remove(pageId);
    }

    /** 持久化到 OpenDreamCore/edits.json。 */
    public void save() {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("edits.json");
            Files.createDirectories(file.getParent());
            var out = new JsonObject();
            edits.forEach((pageId, map) -> {
                var pageObj = new JsonObject();
                map.forEach((elementId, pos) -> {
                    var posObj = new JsonObject();
                    posObj.addProperty("x", pos[0]);
                    posObj.addProperty("y", pos[1]);
                    pageObj.add(elementId, posObj);
                });
                out.add(pageId, pageObj);
            });
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (Exception e) {
            ClientController.LOGGER.debug("编辑记忆保存失败: {}", e.toString());
        }
    }

    /** 进服时加载。 */
    public void load() {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("edits.json");
            if (!Files.isRegularFile(file)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            root.entrySet().forEach(pageEntry -> {
                Map<String, double[]> pageMap = new LinkedHashMap<>();
                pageEntry.getValue().getAsJsonObject().entrySet().forEach(elementEntry -> {
                    var pos = elementEntry.getValue().getAsJsonObject();
                    pageMap.put(elementEntry.getKey(),
                            new double[]{pos.get("x").getAsDouble(), pos.get("y").getAsDouble()});
                });
                edits.put(pageEntry.getKey(), pageMap);
            });
        } catch (Exception e) {
            ClientController.LOGGER.debug("编辑记忆加载失败: {}", e.toString());
        }
    }
}
