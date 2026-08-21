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
 * 元素级编辑记忆（P3-18 扩展）：
 * - 位置覆盖：页面 id → 元素 id → {x, y}（拖动/属性面板改坐标，持久化）
 * - 删除：页面 id → 被删元素 id 集合（持久化；布局时过滤）
 * - 复制：页面 id → 追加元素列表（仅会话内，不持久化）
 * - 隐藏：页面 id → 运行时隐藏元素 id 集合（仅会话内；Screen.隐藏元素/显示元素 用）
 */
public final class ElementEditStore {

    private final Map<String, Map<String, double[]>> edits = new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<String>> deleted = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<com.opendreamcore.page.Element>> copies = new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<String>> hidden = new ConcurrentHashMap<>();

    /** 页面的元素位置覆盖（无则 null）。 */
    public Map<String, double[]> forPage(String pageId) {
        return edits.get(pageId);
    }

    public void set(String pageId, String elementId, double x, double y) {
        edits.computeIfAbsent(pageId, k -> new ConcurrentHashMap<>()).put(elementId, new double[]{x, y});
    }

    public void clear(String pageId) {
        edits.remove(pageId);
        deleted.remove(pageId);
        copies.remove(pageId);
        hidden.remove(pageId);
    }

    // ---- 删除 ----

    /** 标记元素删除（含子元素由布局过滤递归处理）。 */
    public void markDeleted(String pageId, String elementId) {
        deleted.computeIfAbsent(pageId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(elementId);
    }

    /** 被删元素 id（无则 null）。 */
    public java.util.Set<String> deleted(String pageId) {
        return deleted.get(pageId);
    }

    // ---- 隐藏（会话内，运行时显隐） ----

    public void markHidden(String pageId, String elementId) {
        hidden.computeIfAbsent(pageId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(elementId);
    }

    public void unmarkHidden(String pageId, String elementId) {
        java.util.Set<String> set = hidden.get(pageId);
        if (set != null) {
            set.remove(elementId);
        }
    }

    public boolean isHidden(String pageId, String elementId) {
        java.util.Set<String> set = hidden.get(pageId);
        return set != null && set.contains(elementId);
    }

    /** 页面隐藏集合（无则 null）。 */
    public java.util.Set<String> hidden(String pageId) {
        return hidden.get(pageId);
    }

    /** 页面是否有隐藏标记（布局可跳过整页重建）。 */
    public boolean hasHidden(String pageId) {
        java.util.Set<String> set = hidden.get(pageId);
        return set != null && !set.isEmpty();
    }

    // ---- 复制（会话内） ----

    public void addCopy(String pageId, com.opendreamcore.page.Element element) {
        copies.computeIfAbsent(pageId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(element);
    }

    /** 追加的复制元素（无则空列表）。 */
    public java.util.List<com.opendreamcore.page.Element> copies(String pageId) {
        return copies.getOrDefault(pageId, java.util.List.of());
    }

    /** 持久化到 OpenDreamCore/edits.json（位置 + 删除标记）。 */
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
            if (!deleted.isEmpty()) {
                var deletedObj = new JsonObject();
                deleted.forEach((pageId, ids) -> {
                    var arr = new com.google.gson.JsonArray();
                    ids.forEach(arr::add);
                    deletedObj.add(pageId, arr);
                });
                out.add("_deleted", deletedObj);
            }
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (Exception e) {
            ClientController.LOGGER.debug("编辑记忆保存失败: {}", e.toString());
        }
    }

    public String snapshot(String pageId) {
        try {
            var out = new JsonObject();
            Map<String, double[]> m = edits.get(pageId);
            if (m != null) {
                var pageObj = new JsonObject();
                m.forEach((k, v) -> { var o = new JsonObject(); o.addProperty("x", v[0]); o.addProperty("y", v[1]); pageObj.add(k, o); });
                out.add(pageId, pageObj);
            }
            java.util.Set<String> del = deleted.get(pageId);
            if (del != null && !del.isEmpty()) {
                var arr = new com.google.gson.JsonArray();
                del.forEach(arr::add);
                var delObj = new JsonObject();
                delObj.add(pageId, arr);
                out.add("_deleted", delObj);
            }
            return out.toString();
        } catch (Exception e) { return "{}"; }
    }

    public void restore(String pageId, String json) {
        try {
            edits.remove(pageId);
            deleted.remove(pageId);
            if (json == null || json.isBlank() || "{}".equals(json.trim())) return;
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(pageId)) {
                Map<String, double[]> pageMap = new ConcurrentHashMap<>();
                root.getAsJsonObject(pageId).entrySet().forEach(e -> {
                    var pos = e.getValue().getAsJsonObject();
                    pageMap.put(e.getKey(), new double[]{pos.get("x").getAsDouble(), pos.get("y").getAsDouble()});
                });
                if (!pageMap.isEmpty()) edits.put(pageId, pageMap);
            }
            if (root.has("_deleted")) {
                var delObj = root.getAsJsonObject("_deleted");
                if (delObj.has(pageId)) {
                    java.util.Set<String> set = ConcurrentHashMap.newKeySet();
                    delObj.getAsJsonArray(pageId).forEach(id -> set.add(id.getAsString()));
                    if (!set.isEmpty()) deleted.put(pageId, set);
                }
            }
        } catch (Exception ignored) {}
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
                String pageId = pageEntry.getKey();
                if ("_deleted".equals(pageId)) {
                    pageEntry.getValue().getAsJsonObject().entrySet().forEach(deletedEntry ->
                            deletedEntry.getValue().getAsJsonArray().forEach(id ->
                                    deleted.computeIfAbsent(deletedEntry.getKey(),
                                            k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                                            .add(id.getAsString())));
                    return;
                }
                Map<String, double[]> pageMap = new LinkedHashMap<>();
                pageEntry.getValue().getAsJsonObject().entrySet().forEach(elementEntry -> {
                    var pos = elementEntry.getValue().getAsJsonObject();
                    pageMap.put(elementEntry.getKey(),
                            new double[]{pos.get("x").getAsDouble(), pos.get("y").getAsDouble()});
                });
                edits.put(pageId, pageMap);
            });
        } catch (Exception e) {
            ClientController.LOGGER.debug("编辑记忆加载失败: {}", e.toString());
        }
    }
}
