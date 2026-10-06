package com.opendreamcore.client.model;

import com.opendreamcore.model.anim.ModelAnimState;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.format.FormatSources;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * P6：/odc model 命令组（客户端本地，不经服务器）。
 * list / status / reload / bench / formats / preview &lt;id|off&gt; / anim &lt;id|off&gt;
 */
public final class ModelCommands {

    private ModelCommands() {
    }

    public static List<String> run(String args) {
        String p = args == null ? "" : args.trim();
        String[] parts = p.isEmpty() ? new String[0] : p.split("\\s+");
        try {
            if (parts.length == 0 || parts[0].equalsIgnoreCase("status")) {
                return status();
            }
            String sub = parts[0].toLowerCase(java.util.Locale.ROOT);
            switch (sub) {
                case "list":
                    return list();
                case "reload":
                    return reload();
                case "bench":
                    return bench();
                case "formats":
                    return formats();
                case "preview":
                    return preview(parts);
                case "anim":
                    return anim(parts);
                default:
                    return single("§e用法: §f/odc model list|status|reload|bench|formats|preview <id|off>|anim <id|off>");
            }
        } catch (Throwable t) {
            return single("§c/odc model 执行失败: " + t);
        }
    }

    private static List<String> status() {
        List<String> out = new ArrayList<String>();
        out.add("§e=== 模型模块状态 ===");
        out.add("实体规则: §f" + ModelRuleStore.get().entitySize()
                + " §7| 物品规则: §f" + ModelRuleStore.get().itemSize()
                + " §7| 已装载模型: §f" + ModelAssetStore.get().modelIds().size()
                + " §7| 格式: §f" + FormatSources.all(ClientAddons.registry()).size()
                + " §7| addons: §f" + ClientAddons.lastLoaded());
        String anim = ModelAnimState.get().current();
        out.add("动画意图: §f" + (anim == null ? "（默认）" : anim)
                + " §7(v" + ModelAnimState.get().version() + ")");
        String pv = ModelPreviewHolo.previewing();
        out.add("全息预览: §f" + (pv == null ? "关" : pv));
        return out;
    }

    private static List<String> list() {
        List<String> out = new ArrayList<String>();
        out.add("§e=== 实体规则（EntityModel.yml）===");
        for (ModelRuleStore.Rule r : ModelRuleStore.get().all()) {
            out.add("§f" + r.id + " §7→ 模型 §f" + r.model
                    + (r.texture == null || r.texture.isEmpty() ? "" : " §7/ 贴图 §f" + r.texture));
        }
        out.add("§e=== 物品规则（ItemModel.yml）===");
        for (ModelRuleStore.Rule r : ModelRuleStore.get().allItems()) {
            out.add("§f" + r.id + " §7→ 模型 §f" + r.model
                    + (r.texture == null || r.texture.isEmpty() ? "" : " §7/ 贴图 §f" + r.texture));
        }
        out.add("§e=== 已装载模型 ===");
        var ids = new ArrayList<String>(ModelAssetStore.get().modelIds());
        java.util.Collections.sort(ids);
        for (String id : ids) {
            int tris = 0;
            int quads = 0;
            for (BakedModel bm : ModelAssetStore.get().byModelId(id)) {
                tris += bm.tris.length / 24;
                quads += bm.quads.length / 32;
            }
            out.add("§f" + id + " §7(三角 " + tris + " / 四边 " + quads + ")");
        }
        return out;
    }

    private static List<String> reload() {
        ModelAssetStore.get().reload();
        int addons = ClientAddons.reload();
        return single("§a已重扫模型资产与 addons（本次 addons 装载 " + addons + " 项）");
    }

    private static List<String> bench() {
        long t0 = System.nanoTime();
        ModelAssetStore.get().reload();
        long loadMs = (System.nanoTime() - t0) / 1000000L;
        int tris = 0;
        int quads = 0;
        for (String id : ModelAssetStore.get().modelIds()) {
            for (BakedModel bm : ModelAssetStore.get().byModelId(id)) {
                tris += bm.tris.length / 24;
                quads += bm.quads.length / 32;
            }
        }
        return single("§a烘焙基准: §f" + ModelAssetStore.get().modelIds().size() + " 模型 / "
                + quads + " 四边 / " + tris + " 三角 §7重扫耗时 " + loadMs + "ms");
    }

    private static List<String> formats() {
        List<String> out = new ArrayList<String>();
        out.add("§e=== 模型格式 ===");
        out.addAll(com.opendreamcore.client.methods.ModelMethods.formatsLines());
        return out;
    }

    private static List<String> preview(String[] parts) {
        if (parts.length < 2 || "off".equalsIgnoreCase(parts[1])) {
            ModelPreviewHolo.stop();
            return single("§a模型预览已关闭");
        }
        return single(ModelPreviewHolo.start(parts[1]));
    }

    private static List<String> anim(String[] parts) {
        if (parts.length < 2 || "off".equalsIgnoreCase(parts[1])) {
            ModelAnimState.get().clear();
            return single("§a动画意图已清除（回到默认）");
        }
        long v = ModelAnimState.get().set(parts[1]);
        return single("§a动画意图 → §f" + parts[1] + " §7(v" + v + ")");
    }

    private static List<String> single(String s) {
        List<String> out = new ArrayList<String>();
        out.add(s);
        return out;
    }
}
