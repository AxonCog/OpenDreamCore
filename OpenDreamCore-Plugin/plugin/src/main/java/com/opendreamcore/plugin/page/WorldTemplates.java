package com.opendreamcore.plugin.page;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 世界面板预设布局模板（/odc world template &lt;页面id&gt; &lt;board|menu|shop&gt;）：
 * 一键生成扁平语法世界页面（match: world 进服自动打开，射线交互开箱即用），
 * 生成后可用 /odc edit world 进入 WYSIWYG 微调。
 */
public final class WorldTemplates {

    private WorldTemplates() {
    }

    /** 生成模板 YAML；未知模板返回 null。 */
    public static String build(String pageId, String type) {
        return switch (type) {
            case "board" -> board(pageId);
            case "menu" -> menu(pageId);
            case "shop" -> shop(pageId);
            default -> null;
        };
    }

    /** 写入 UI/&lt;页面&gt;.yaml（已存在不覆盖），返回是否成功。 */
    public static boolean write(Path uiDir, String pageId, String type) {
        String yaml = build(pageId, type);
        if (yaml == null) {
            return false;
        }
        try {
            Path file = uiDir.resolve(pageId + ".yaml");
            if (Files.isRegularFile(file)) {
                return false;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, yaml, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String board(String pageId) {
        return """
                # 世界公告板模板（/odc world template %s board）
                # 特性：进服自动打开 / 射线交互 / 全元素可拖（多玩家同步+持久化）/ 页签 / 物品展示
                match: world
                display: world
                title: 公告板
                options:
                  world:
                    offsetX: 0
                    offsetY: 1.8
                    offsetZ: 3
                    interact: true
                    drag: true
                    offScreenArrows: true
                    background: {color: "#10151FCC", radius: 0.15, border: "#3A4A66"}
                variables:
                  title_text: 服务器公告板
                  body_text: "欢迎来到服务器！\\n左键点击按钮，拖拽面板试试"
                elements:
                  - id: tab_bar
                    type: tabs
                    hologram: {x: 0, y: 0.45, z: 0, width: 3, height: 0.22}
                    tabs:
                      options: ["概览", "商店"]
                      active: "概览"
                      color: "#2A3A52"
                      activeColor: "#42A5F5"
                    actions:
                      input: |-
                        Chat.发送消息("§a[公告板] §f页签: " + vars.input, player.name)
                  - id: title_el
                    type: text
                    hologram: {x: 0, y: 0, z: 0, scale: 0.03, width: 3, height: 0.3}
                    text: {content: "{vars.title_text}", color: "#FFD700"}
                  - id: body_el
                    type: text
                    hologram: {x: 0, y: -0.4, z: 0, scale: 0.02, width: 4, height: 0.5, wrap: 4}
                    text: {content: "{vars.body_text}", color: "#FFFFFF"}
                  - id: move_btn
                    type: text
                    hologram: {x: 0, y: -1.0, z: 0, scale: 0.02, width: 2, height: 0.3, draggable: true}
                    text: {content: "◈ 可拖拽按钮 ◈", color: "#4FC3F7"}
                    actions:
                      click: |-
                        Chat.发送消息("§a[公告板] §f你点击了按钮！", player.name)
                  - id: lamp_toggle
                    type: toggle
                    hologram: {x: -0.5, y: -1.5, z: 0, width: 1.6, height: 0.18}
                    toggle: {value: false}
                    actions:
                      input: |-
                        Chat.发送消息("§a[公告板] §f世界开关：" + vars.input, player.name)
                  - id: lamp_label
                    type: text
                    hologram: {x: 0.6, y: -1.5, z: 0, scale: 0.015}
                    text: {content: "世界开关（点击）", color: "#90CAF9"}
                  - id: item_demo
                    type: item_slot
                    hologram: {x: -0.7, y: -2.1, z: 0, height: 0.35, group: demo_group}
                    item_slot: {item: "minecraft:diamond", count: 64}
                  - id: item_label
                    type: text
                    hologram: {x: 0.6, y: -2.1, z: 0, scale: 0.015, group: demo_group}
                    text: {content: "物品展示（拖物品标签跟随）", color: "#90CAF9"}
                  - id: shop_title
                    type: text
                    tab: "商店"
                    hologram: {x: 0, y: -0.6, z: 0, scale: 0.02}
                    text: {content: "商店页签：只在这里显示", color: "#FFD54F"}
                  - id: shop_item
                    type: item_slot
                    tab: "商店"
                    hologram: {x: -0.7, y: -1.1, z: 0, height: 0.35}
                    item_slot: {item: "minecraft:emerald", count: 32}
                  - id: shop_label
                    type: text
                    tab: "商店"
                    hologram: {x: 0.6, y: -1.1, z: 0, scale: 0.015}
                    text: {content: "商店物品（仅商店页签可见）", color: "#90CAF9"}
                """.formatted(pageId);
    }

    private static String menu(String pageId) {
        return """
                # 世界菜单模板（/odc world template %s menu）
                # 特性：进服自动打开 / 射线交互 / 按钮点击打开服务端页面
                match: world
                display: world
                title: 世界菜单
                options:
                  world:
                    offsetY: 1.8
                    offsetZ: 3
                    interact: true
                variables:
                  menu_title: 服务器菜单
                elements:
                  - id: title_el
                    type: text
                    hologram: {x: 0, y: 0, z: 0, scale: 0.03, width: 3, height: 0.3}
                    text: {content: "{vars.menu_title}", color: "#FFD700"}
                  - id: btn_shop
                    type: text
                    hologram: {x: 0, y: -0.6, z: 0, scale: 0.02, width: 2, height: 0.3}
                    text: {content: "◈ 商店 ◈", color: "#4FC3F7"}
                    actions:
                      click: |-
                        Screen.打开页面("shop", player.name)
                  - id: btn_mail
                    type: text
                    hologram: {x: 0, y: -1.1, z: 0, scale: 0.02, width: 2, height: 0.3}
                    text: {content: "◈ 邮件 ◈", color: "#66BB6A"}
                    actions:
                      click: |-
                        Chat.发送消息("§a[菜单] §f邮件功能开发中……", player.name)
                  - id: btn_home
                    type: text
                    hologram: {x: 0, y: -1.6, z: 0, scale: 0.02, width: 2, height: 0.3}
                    text: {content: "◈ 返回主城 ◈", color: "#FFA726"}
                    actions:
                      click: |-
                        Chat.发送消息("§a[菜单] §f传送主城（示例）", player.name)
                  - id: btn_close
                    type: text
                    hologram: {x: 0, y: -2.1, z: 0, scale: 0.015, width: 2, height: 0.2}
                    text: {content: "关闭面板", color: "#E57373"}
                    actions:
                      click: |-
                        Screen.关闭页面(player.name)
                """.formatted(pageId);
    }

    private static String shop(String pageId) {
        return """
                # 世界商店模板（/odc world template %s shop）
                # 特性：进服自动打开 / 物品展示（NBT 组件）/ 购买按钮 / 余额显示
                match: world
                display: world
                title: 世界商店
                options:
                  world:
                    offsetY: 1.8
                    offsetZ: 3
                    interact: true
                    drag: true
                variables:
                  shop_title: 世界商店
                  coins: 0
                elements:
                  - id: title_el
                    type: text
                    hologram: {x: 0, y: 0, z: 0, scale: 0.03, width: 3, height: 0.3}
                    text: {content: "{vars.shop_title}", color: "#FFD700"}
                  - id: item_sword
                    type: item_slot
                    hologram: {x: -0.7, y: -0.7, z: 0, height: 0.35}
                    item_slot:
                      item: "minecraft:diamond_sword"
                      count: 1
                      nbt: "{minecraft:custom_name:'\\"§b§l传说之刃\\"',minecraft:enchantments:{levels:{minecraft:sharpness:5}}}"
                  - id: sword_label
                    type: text
                    hologram: {x: 0.6, y: -0.7, z: 0, scale: 0.015}
                    text: {content: "传说之刃（锋利 V）", color: "#90CAF9"}
                  - id: coins_text
                    type: text
                    hologram: {x: 0, y: -1.3, z: 0, scale: 0.015}
                    text: {content: "余额: {vars.coins} 金币", color: "#FFD54F"}
                  - id: btn_buy
                    type: text
                    hologram: {x: 0, y: -1.8, z: 0, scale: 0.02, width: 2, height: 0.3}
                    text: {content: "◈ 购买（示例）◈", color: "#4FC3F7"}
                    actions:
                      click: |-
                        Chat.发送消息("§a[商店] §f购买成功（示例，接入经济系统后扣款）", player.name)
                  - id: btn_close
                    type: text
                    hologram: {x: 0, y: -2.3, z: 0, scale: 0.015, width: 2, height: 0.2}
                    text: {content: "关闭面板", color: "#E57373"}
                    actions:
                      click: |-
                        Screen.关闭页面(player.name)
                """.formatted(pageId);
    }
}
