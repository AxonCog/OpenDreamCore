// OpenDreamCore 附属插件示例
// 展示全部 API 用法：页面/容器/元素/HUD/事件/脚本注册
//
// 依赖配置（build.gradle）：
//   repositories { flatDir { dirs 'libs' } }
//   dependencies {
//       compileOnly 'com.opendreamcore:opendreamcore-plugin:0.1.0'
//   }
//
// plugin.yml 加： depend: [OpenDreamCore]

package com.example.myaddon;

import com.opendreamcore.api.*;
import com.opendreamcore.plugin.event.OdcEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class MyAddonExample extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // 注册自定义脚本方法（页面 YAML 里直接调用）
        ScriptAPI.register("经济", "取余额", args -> {
            Player p = (Player) args[0];
            return getBalance(p);
        });
        ScriptAPI.register("经济", "扣款", args -> {
            Player p = (Player) args[0];
            double amount = ((Number) args[1]).doubleValue();
            withdraw(p, amount);
            return true;
        });

        // 监听 GUI 事件
        Bukkit.getPluginManager().registerEvents(this, this);

        // 打开商店页面（命令触发）
        getCommand("shop").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) {
                openShop(p);
            }
            return true;
        });
    }

    /** 打开商店页面 + 推送变量。 */
    private void openShop(Player player) {
        var gui = OpenDreamCoreAPI.gui();
        gui.setVariable(player, "gold", getBalance(player));
        gui.setVariable(player, "shop_name", "钻石商店");
        gui.open(player, "shop");
    }

    /** 容器操作示例。 */
    private void openChest(Player player, org.bukkit.inventory.Inventory chestInv) {
        var gui = OpenDreamCoreAPI.gui();
        String sessionId = gui.openContainer(player, "container", chestInv,
                "minecraft:chest", "我的箱子");

        // 读写容器槽位
        var api = OpenDreamCoreAPI.container();
        var item = api.getItem(player, 0);
        api.setItem(player, 5, new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.DIAMOND, 1));
        api.resync(player); // 推送到客户端刷新 UI
    }

    /** 元素控制示例。 */
    private void updatePage(Player player) {
        var el = OpenDreamCoreAPI.element();
        el.show(player, "vip_section");
        el.hide(player, "normal_section");
        el.setProp(player, "title", "text.content", "VIP 商店");
    }

    /** HUD 操作示例。 */
    private void setupHud(Player player) {
        var hud = OpenDreamCoreAPI.hud();
        hud.mountHud(player, "hud");
        hud.showBossBar(player, "boss1", "末影龙", 75, "#E53935");
        hud.setNameTag(player.getName(), "§6VIP", "#FFD700");
    }

    // 事件监听

    @EventHandler
    public void onButtonClick(OdcEvents.ButtonEvent e) {
        // e.getPlayer(), e.getPageId(), e.getElementId()
        if ("buy_diamond".equals(e.getElementId())) {
            handleBuy(e.getPlayer());
        }
    }

    @EventHandler
    public void onInput(OdcEvents.InputEvent e) {
        // e.getValue() 获取用户输入内容
        getLogger().info(e.getPlayer().getName() + " 输入了: " + e.getValue());
    }

    @EventHandler
    public void onSlotClick(OdcEvents.SlotEvent e) {
        // 容器槽位点击（服务端裁决前）
    }

    @EventHandler
    public void onPageOpen(OdcEvents.OpenEvent e) {
        getLogger().info(e.getPlayer().getName() + " 打开了页面 " + e.getPageId());
    }

    // 业务逻辑（替换为你的实现）

    private double getBalance(Player p) { return 100.0; }
    private void withdraw(Player p, double amount) { }

    private void handleBuy(Player p) {
        p.sendMessage("§a购买成功！");
    }
}
