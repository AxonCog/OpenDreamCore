package com.opendreamcore.client.methods;

import com.opendreamcore.script.NamespaceRegistry;
import com.opendreamcore.model.anim.ModelAnimState;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.format.FormatSources;
import com.opendreamcore.core.addons.AddonRegistry;
import com.opendreamcore.client.model.ClientAddons;
import com.opendreamcore.client.model.ModelRuleStore;

import java.util.ArrayList;
import java.util.List;

/**
 * C1 拆分自 ClientMethods。// Model 命名空间（P6）
 *
 * <p>动作（可在按键/区域/事件触发与相机时间轴 script 事件点里调用）：
 * 播放动画(id) / 停止动画()；查询：规则数() / 已加载(模型id) / 当前动画() / 格式列表()。
 * 相机 model_anim 事件点 = 相机时间轴 事件(type script) 里调 Model.播放动画 —— 无需专用事件类型。
 */
public final class ModelMethods {

    private ModelMethods() {
    }

    public static void register() {
        // ---- 动作 ----
        NamespaceRegistry.register("Model", args -> {
            // Model.播放动画(动画id) → 1.0=已写入意图，0.0=参数缺失
            if (args.length < 1 || args[0] == null) {
                return 0.0;
            }
            ModelAnimState.get().set(String.valueOf(args[0]));
            return 1.0;
        }, "播放动画", "playAnim", "play_anim");
        NamespaceRegistry.register("Model", args -> {
            ModelAnimState.get().clear();
            return 1.0;
        }, "停止动画", "stopAnim", "stop_anim");
        // ---- 查询 ----
        NamespaceRegistry.register("Model", args ->
                (double) ModelRuleStore.get().size(), "规则数", "ruleCount", "rule_count");
        NamespaceRegistry.register("Model", args -> {
            // Model.已加载(模型id) → 1.0/0.0
            if (args.length < 1 || args[0] == null) {
                return 0.0;
            }
            var models = ModelAssetStore.get().byModelId(String.valueOf(args[0]));
            return models != null && !models.isEmpty() ? 1.0 : 0.0;
        }, "已加载", "loaded", "loaded");
        NamespaceRegistry.register("Model", args -> {
            String cur = ModelAnimState.get().current();
            return cur == null ? 0.0 : 1.0;
        }, "当前动画", "currentAnim", "current_anim");
        NamespaceRegistry.register("Model", args -> {
            // Model.格式列表() → 内建 + addons 格式 id 数
            return (double) FormatSources.all(ClientAddons.registry()).size();
        }, "格式列表", "formats", "formats");
    }

    /** 给 /odc model formats 用的可读列表（id + 来源）。 */
    public static List<String> formatsLines() {
        List<String> out = new ArrayList<String>();
        AddonRegistry reg = ClientAddons.registry();
        for (var c : FormatSources.all(reg)) {
            String source = "内建";
            for (AddonRegistry.Handle h : reg.byType("model_format")) {
                if (h.addon instanceof com.opendreamcore.model.format.IModelFormatCodec
                        && ((com.opendreamcore.model.format.IModelFormatCodec) h.addon).id().equals(c.id())) {
                    source = "addon:" + h.id;
                    break;
                }
            }
            out.add("§f" + c.id() + " §7(" + source + ")");
        }
        return out;
    }
}
