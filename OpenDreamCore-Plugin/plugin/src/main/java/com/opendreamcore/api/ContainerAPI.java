package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 容器 API：给附属插件操作已绑定的容器（读写槽位物品、光标管理）。
 * 需要先通过 GUIAPI.openContainer 绑定真实容器。
 */
public final class ContainerAPI {

    static final ContainerAPI INSTANCE = new ContainerAPI();

    private ContainerAPI() {
    }

    /** 读取容器指定槽位的物品（未绑定或越界返回 null）。 */
    public ItemStack getItem(Player player, int slot) {
        var binding = binding(player);
        return binding == null ? null : binding.inventory().getItem(slot);
    }

    /** 设置容器指定槽位的物品（自动推送 container_sync 给客户端）。 */
    public boolean setItem(Player player, int slot, ItemStack stack) {
        var binding = binding(player);
        if (binding == null || slot < 0 || slot >= binding.inventory().getSize()) {
            return false;
        }
        binding.inventory().setItem(slot, stack);
        resync(player, binding);
        return true;
    }

    /** 清空容器指定槽位。 */
    public boolean clearSlot(Player player, int slot) {
        return setItem(player, slot, null);
    }

    /** 获取玩家光标上的物品（容器 UI 中拿起但未放下的）。 */
    public ItemStack getCursor(Player player) {
        var registry = registry();
        return registry == null ? null : registry.cursor(player);
    }

    /** 设置玩家光标上的物品。 */
    public boolean setCursor(Player player, ItemStack stack) {
        var registry = registry();
        if (registry == null) {
            return false;
        }
        registry.setCursor(player, stack);
        return true;
    }

    /** 推送容器快照到客户端（手动修改容器内容后调用）。 */
    public boolean resync(Player player) {
        var binding = binding(player);
        if (binding == null) {
            return false;
        }
        resync(player, binding);
        return true;
    }

    private com.opendreamcore.plugin.container.ContainerRegistry.Binding binding(Player player) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        return plugin == null ? null : plugin.containerRegistry().ofPlayer(player);
    }

    private com.opendreamcore.plugin.container.ContainerRegistry registry() {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        return plugin == null ? null : plugin.containerRegistry();
    }

    private void resync(Player player, com.opendreamcore.plugin.container.ContainerRegistry.Binding binding) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin != null) {
            plugin.networkLayer().sendContainerSync(binding.player(),
                    plugin.containerRegistry().snapshot(binding));
        }
    }
}
