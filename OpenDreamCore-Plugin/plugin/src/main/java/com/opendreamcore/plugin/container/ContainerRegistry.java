package com.opendreamcore.plugin.container;

import com.opendreamcore.protocol.message.ContainerSync;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器注册表：容器 UI 会话 ↔ 真实 Inventory 的绑定。
 * 玩家打开箱子时（match 命中）由 MatchListener 绑定；
 * 脚本 Container.xxx 方法按会话 id 操作真实容器；关闭/退出时解绑。
 */
public final class ContainerRegistry {

    /** 绑定：会话 → 玩家 + 真实容器。 */
    public record Binding(String sessionId, Player player, Inventory inventory,
                          String pageId, String type, String title, long boundAt) {
    }

    private final Map<String, Binding> bySession = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerSession = new ConcurrentHashMap<>();

    /** 当前容器绑定数。 */
    public int size() {
        return bySession.size();
    }

    public void bind(String sessionId, Player player, Inventory inventory,
                     String pageId, String type, String title) {
        unbindPlayer(player); // 同一玩家只保留一个容器会话
        Binding binding = new Binding(sessionId, player, inventory, pageId, type, title,
                System.currentTimeMillis());
        bySession.put(sessionId, binding);
        playerSession.put(player.getUniqueId(), sessionId);
    }

    public Binding get(String sessionId) {
        return sessionId == null ? null : bySession.get(sessionId);
    }

    /** 玩家当前容器会话（无则 null）。 */
    public Binding ofPlayer(Player player) {
        String sessionId = playerSession.get(player.getUniqueId());
        return sessionId == null ? null : bySession.get(sessionId);
    }

    /** 看同一个容器的所有绑定（其他玩家在原版界面改动时重同步用）。 */
    public List<Binding> ofInventory(Inventory inventory) {
        List<Binding> out = new ArrayList<>();
        for (Binding binding : bySession.values()) {
            if (binding.inventory() == inventory) {
                out.add(binding);
            }
        }
        return out;
    }

    public void unbind(String sessionId) {
        Binding binding = bySession.remove(sessionId);
        if (binding != null) {
            playerSession.remove(binding.player().getUniqueId());
        }
    }

    public void unbindPlayer(Player player) {
        Binding binding = ofPlayer(player);
        if (binding != null) {
            unbind(binding.sessionId());
        }
    }

    /** 玩家退出：清掉其容器会话与光标。 */
    public void unbindAll(Player player) {
        unbindPlayer(player);
        clearCursor(player);
    }

    /** 构建容器内容快照（全量槽位 + 玩家光标物品，空槽跳过）。 */
    public ContainerSync snapshot(String sessionId) {
        Binding binding = bySession.get(sessionId);
        if (binding == null) {
            return null;
        }
        return snapshot(binding);
    }

    /** 构建容器内容快照。 */
    public ContainerSync snapshot(Binding binding) {
        Inventory inventory = binding.inventory();
        List<ContainerSync.Slot> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                slots.add(new ContainerSync.Slot(i, item.getType().getKey().toString(), item.getAmount()));
            }
        }
        ItemStack cursor = cursor(binding.player());
        return new ContainerSync(binding.sessionId(), binding.type(), binding.title(),
                inventory.getSize(), slots,
                cursor == null ? null : cursor.getType().getKey().toString(),
                cursor == null ? 0 : cursor.getAmount());
    }

    // ---------- 槽位拖放光标（服务端权威） ----------

    private final Map<UUID, org.bukkit.inventory.ItemStack> cursors = new ConcurrentHashMap<>();

    /** 玩家光标物品（null = 无）。 */
    public org.bukkit.inventory.ItemStack cursor(Player player) {
        org.bukkit.inventory.ItemStack c = cursors.get(player.getUniqueId());
        return c == null || c.getType().isAir() ? null : c;
    }

    public void setCursor(Player player, org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            cursors.remove(player.getUniqueId());
        } else {
            cursors.put(player.getUniqueId(), stack.clone());
        }
    }

    public void clearCursor(Player player) {
        cursors.remove(player.getUniqueId());
    }
}
