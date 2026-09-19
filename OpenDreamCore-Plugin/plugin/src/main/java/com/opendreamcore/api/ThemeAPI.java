package com.opendreamcore.api;

import com.opendreamcore.ui.theme.ThemeLibrary;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 主题 API：附属插件自带皮肤的注册、查询与热更新入口。
 *
 * 主题文件平时放在客户端 OpenDreamCore/themes/ 目录，玩家自己管。
 * 但附属插件经常想"自带一套皮肤"——比如一个 RPG 附属附上暗金色主题，
 * 一个服务器管理工具想让玩家一键切节日风。这个 API 就是干这个的：
 * 插件把主题 YAML 注册进来，页面声明 theme: 名字 即可命中。
 *
 * <pre>
 * // 从插件自带资源读一份主题（jar 里放 themes/gold.yml）
 * String yaml = new String(getResource("themes/gold.yml").readAllBytes());
 *
 * // 一行注册（同名覆盖，改版升级直接重注册）
 * ThemeAPI.get().register("gold", yaml);
 *
 * // 完事：任何页面写 theme: gold 就用这套皮肤；
 * // 只想给某页用就在那页头部声明，别的不受影响。
 * </pre>
 *
 * 分发模型（先看再设计）：主题在客户端构建页面时生效，所以——
 *   单机 / 本地主题：放 OpenDreamCore/themes/ 即可，无需本 API；
 *   服务器想全员统一皮肤：推荐把 theme.yml 放进资源云下发到玩家客户端目录，
 *       或让玩家手动安装；服务端注册的主题用于服务端自身的页面编译校验场景。
 * 这个 API 管的是服务端这一侧的主题库，跨端分发走资源云，各司其职。
 */
public final class ThemeAPI {

    static final ThemeAPI INSTANCE = new ThemeAPI();

    private ThemeAPI() {
    }

    /** 取实例（等价于 {@link OpenDreamCoreAPI#theme()}）。 */
    public static ThemeAPI get() {
        return INSTANCE;
    }

    /**
     * 从 YAML 文本注册主题。同名覆盖——附属升级时直接重注册即可。
     *
     * name：主题名（页面里 theme: 名字 引用；建议全小写避免踩坑）
     * yamlText：主题 YAML 全文（结构见《YAML语法.md》§12）
     * 返回：成功 true；YAML 坏了返回 false（日志里有原因，不会炸你的插件）
     */
    public boolean register(String name, String yamlText) {
        return ThemeLibrary.get().registerYaml(name, yamlText);
    }

    /** 注销一个主题。正在引用它的页面会回退 default，不炸。 */
    public boolean unregister(String name) {
        if (ThemeLibrary.get().names().contains(name)) {
            // clear + 重灌太粗鲁，直接从底层摘掉这一个
            ThemeLibrary.get().remove(name);
            return true;
        }
        return false;
    }

    /** 已注册的主题名清单。 */
    public Set<String> names() {
        return ThemeLibrary.get().names();
    }

    /** 某主题是否已注册。 */
    public boolean exists(String name) {
        return ThemeLibrary.get().names().contains(name);
    }

    /**
     * 从目录批量加载主题（递归，.yml/.yaml/.json），返回成功数量。
     * 适合"插件 dataFolder/themes/ 里用户丢文件"的自管理模式。
     */
    public int loadFromDir(Path dir) {
        return ThemeLibrary.get().loadFromDir(dir);
    }

    /**
     * 重载插件数据目录下的 themes/（dataFolder/themes/*.yml）。
     * 用户改完主题文件后调这个，或者干脆监听文件变化自动调。
     *
     * 返回：这次加载成功的数量；插件未就绪返回 -1
     */
    public int reloadFromPluginDir(Plugin addon) {
        Path dir = addon.getDataFolder().toPath().resolve("themes");
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
                return 0; // 空目录不算错，第一次嘛
            } catch (Exception e) {
                return -1;
            }
        }
        int n;
        try (Stream<Path> ignored = Files.list(dir)) {
            n = ThemeLibrary.get().loadFromDir(dir);
        } catch (Exception e) {
            return -1;
        }
        return n;
    }

    /** 当前主题链解析结果（调试用）：看某个 theme 声明实际会命中哪些主题。 */
    public List<String> resolveChain(String pageThemeName) {
        return ThemeLibrary.get().resolveFor(pageThemeName)
                .stream().map(t -> t.name()).collect(java.util.stream.Collectors.toList());
    }
}
