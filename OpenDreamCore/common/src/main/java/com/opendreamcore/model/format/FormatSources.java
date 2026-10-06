package com.opendreamcore.model.format;

import com.opendreamcore.core.addons.AddonRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型格式 = addons 通用底座的第一个消费者：
 * 内建注册表（bbmodel/geo.json/obj + 运行期 register）+ addons jar 里 type="model_format"
 * 且实现 IModelFormatCodec 的扩展（按 id 去重，addon 不覆盖已注册同名）。
 */
public final class FormatSources {

    private FormatSources() {
    }

    /** 内建 + addon 合并（addon 按 id 去重后追加在尾部）。 */
    public static List<IModelFormatCodec> all(AddonRegistry registry) {
        List<IModelFormatCodec> out = new ArrayList<IModelFormatCodec>(FormatCodecs.all());
        if (registry != null) {
            for (AddonRegistry.Handle h : registry.byType("model_format")) {
                if (h.addon instanceof IModelFormatCodec) {
                    IModelFormatCodec c = (IModelFormatCodec) h.addon;
                    boolean dup = false;
                    for (IModelFormatCodec have : out) {
                        if (have.id().equals(c.id())) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }
}
