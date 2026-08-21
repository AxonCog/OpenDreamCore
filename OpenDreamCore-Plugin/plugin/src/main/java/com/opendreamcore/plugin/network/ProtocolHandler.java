package com.opendreamcore.plugin.network;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.event.OdcEvents;
import com.opendreamcore.plugin.page.ServerPageManager;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议业务处理：客户端事件的裁决。
 * 事件 → 会话 → 页面元素 → actions 脚本（服务端执行，命名空间方法走 ServerMethods）。
 */
public final class ProtocolHandler {

    /** 会话记录：客户端上报事件时用 sessionId 找回页面。 */
    public record Session(String pageId, long openedAt) {
    }

    private final OpenDreamCorePlugin plugin;
    private final ServerPageManager pages;
    /** 按会话去重（不再按玩家：HUD 和 World 面板同时发事件时各自独立计数）。 */
    private final Map<String, Long> eventSequence = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Player, String> playerSessions = new ConcurrentHashMap<>();
    /** 会话归属（sessionId → 玩家；多面板同屏：一个玩家可持多个世界面板会话）。 */
    private final Map<String, Player> sessionOwners = new ConcurrentHashMap<>();

    // 性能统计（/odc stats）
    private final java.util.concurrent.atomic.AtomicLong eventsProcessed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong scriptsRun = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong scriptMillis = new java.util.concurrent.atomic.AtomicLong();

    public ProtocolHandler(OpenDreamCorePlugin plugin, ServerPageManager pages) {
        this.plugin = plugin;
        this.pages = pages;
    }

    /** 服务端分配会话并记录（openPage 时调用）。 */
    public String openSession(Player player, String pageId) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new Session(pageId, System.currentTimeMillis()));
        playerSessions.put(player, sessionId);
        sessionOwners.put(sessionId, player);
        plugin.getLogger().info("会话开启 " + player.getName() + " -> " + pageId + " (" + sessionId + ")");
        return sessionId;
    }

    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
        sessionOwners.remove(sessionId);
        eventSequence.remove(sessionId);
        playerSessions.entrySet().removeIf(e -> e.getValue().equals(sessionId));
    }

    /** 玩家当前会话 id（无则 null）。 */
    public String sessionOf(Player player) {
        return playerSessions.get(player);
    }

    /** 玩家所有打开页面的 id 集合（多面板同屏：世界面板各自独立会话；广播过滤用）。 */
    public java.util.Set<String> openPageIds(Player player) {
        java.util.Set<String> out = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, Player> e : sessionOwners.entrySet()) {
            if (e.getValue() == player) {
                Session s = sessions.get(e.getKey());
                if (s != null) {
                    out.add(s.pageId());
                }
            }
        }
        return out;
    }

    /** 分配 HUD 会话（不占用屏幕会话；HUD 事件路由用）。 */
    public String openHudSession(Player player, String pageId) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new Session(pageId, System.currentTimeMillis()));
        sessionOwners.put(sessionId, player);
        return sessionId;
    }

    /** 会话信息（关闭事件广播用；已关闭返回 null）。 */
    public Session sessionInfo(String sessionId) {
        return sessions.get(sessionId);
    }

    /** 客户端 UI 事件（C→S）：主线程调度后裁决。 */
    public void onUiEvent(Player player, byte[] bytes) {
        plugin.getServer().getScheduler().runTask(plugin, () -> handle(player, bytes));
    }

    private void handle(Player player, byte[] bytes) {
        try {
            UiEvent event = UiEvent.decode(new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            eventsProcessed.incrementAndGet();
            Long last = eventSequence.get(event.sessionId());
            if (last != null && event.sequence() <= last) {
                plugin.getLogger().warning("重复/乱序 ui_event: " + player.getName() + " seq=" + event.sequence() + " 会话=" + event.sessionId());
                return;
            }
            eventSequence.put(event.sessionId(), event.sequence());

            Session session = sessions.get(event.sessionId());
            if (session == null) {
                plugin.getLogger().warning("未知会话 ui_event: " + player.getName() + " " + event.sessionId()
                        + "（当前会话数=" + sessions.size() + "，触发=" + event.trigger() + "，元素=" + event.elementId() + "）");
                return;
            }
            Page page = pages.get(session.pageId());
            if (page == null) {
                plugin.getLogger().warning("会话指向的页面不存在: " + session.pageId());
                return;
            }
            Element element = findElement(page, event.elementId());
            int generatedSlot = -1; // 自动生成的容器槽位（grid_13 → 容器 grid + 箱子槽 13）
            boolean generatedHot = false; // grid_inv13 → 玩家主背包 13；grid_hot5 → 快捷栏 5
            if (element == null && event.trigger() != UiEvent.Trigger.KEY) {
                String eid = event.elementId();
                int us = eid.lastIndexOf('_');
                if (us > 0) {
                    Element parentEl = findElement(page, eid.substring(0, us));
                    if (parentEl != null && "container".equals(parentEl.type())) {
                        String suffix = eid.substring(us + 1);
                        try {
                            if (suffix.startsWith("inv")) {
                                generatedSlot = Integer.parseInt(suffix.substring(3));
                                generatedHot = true;
                            } else if (suffix.startsWith("hot")) {
                                generatedSlot = Integer.parseInt(suffix.substring(3));
                                generatedHot = true;
                            } else {
                                generatedSlot = Integer.parseInt(suffix);
                            }
                            element = parentEl; // 以容器元素承载事件（actions/脚本/槽位裁决）
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            if (element == null && event.trigger() != UiEvent.Trigger.KEY) {
                plugin.getLogger().warning("页面里没有元素 " + event.elementId() + " (页面 " + session.pageId() + ")");
                return;
            }
            String script = element == null ? null : element.actions().get(triggerName(event.trigger()));
            // 世界面板拖拽落点：INPUT "drag:x,y,z" → 更新共享页面 hologram 坐标 → 同页玩家重发（多玩家同步）
            if (event.trigger() == UiEvent.Trigger.INPUT && event.data() != null
                    && event.data().startsWith("drag:")) {
                syncWorldDrag(session.pageId(), element, event.data().substring(5));
            }
            // 容器槽位物品交互：chest_slot / hot_slot 点击 "slot:action"（L 拿起/放置、R 半组/放一、Q 快捷移动）
            // container 组件自动生成的槽位（grid_13）也走此管线；服务端权威执行，执行后重同步槽位 + 光标
            if (event.trigger() == UiEvent.Trigger.CLICK && element != null
                    && ("chest_slot".equals(element.type()) || "hot_slot".equals(element.type())
                            || ("container".equals(element.type()) && generatedSlot >= 0))
                    && event.data() != null) {
                com.opendreamcore.plugin.container.ContainerRegistry.Binding binding =
                        plugin.containerRegistry().get(event.sessionId());
                if (binding == null) {
                    plugin.getLogger().warning("容器槽位点击但会话未绑定真实容器（需打开真实箱子触发替换）: "
                            + player.getName() + " 会话=" + event.sessionId());
                } else if (handleSlotClick(player, binding, element, event.data(), generatedSlot, generatedHot)) {
                    plugin.networkLayer().sendContainerSync(binding.player(),
                            plugin.containerRegistry().snapshot(binding));
                }
            }
            // Bukkit 事件广播（第三方插件监听）；Button 可取消（取消后不执行 actions 脚本）
            if (!fireEvent(player, page, event.sessionId(), event, element)) {
                return;
            }
            if (script == null || script.isBlank()) {
                return;
            }
            // 容器会话：槽位点击注入 slot / container 变量（脚本里用 vars.slot 区分槽位）
            com.opendreamcore.plugin.container.ContainerRegistry.Binding binding =
                    plugin.containerRegistry().get(event.sessionId());
            long start = System.nanoTime();
            scriptsRun.incrementAndGet();
            execute(player, page, script, binding, event.data());
            scriptMillis.addAndGet((System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            plugin.getLogger().warning("ui_event 处理失败 (" + player.getName() + "): " + e);
        }
    }

    /** 性能统计（/odc stats）。 */
    public String stats() {
        long runs = scriptsRun.get();
        long ms = scriptMillis.get();
        return "会话 " + sessions.size()
                + " | ui_event " + eventsProcessed.get()
                + " | 脚本执行 " + runs + " 次"
                + (runs > 0 ? "（平均 " + String.format("%.2f", ms / (double) runs) + " ms）" : "");
    }

    // ---------- 容器槽位物品交互（服务端权威：光标 + 槽位操作） ----------

    /** 槽位点击执行：data = "slot:action"（L 拿起/放置、R 半组/放一、Q 快捷移动；旧纯数字 = L）。
     *  chest_slot → 绑定容器槽位；hot_slot → 玩家背包 0..35（快捷移动 = 背包 ↔ 容器）；
     *  container 自动生成槽位（generatedSlot >= 0）→ generatedHot 决定玩家背包/容器。
     *  返回 true = 发生变更（调用方重同步槽位 + 光标）。 */
    private boolean handleSlotClick(Player player,
                                    com.opendreamcore.plugin.container.ContainerRegistry.Binding binding,
                                    Element element, String data, int generatedSlot, boolean generatedHot) {
        String[] parts = data.split(":");
        int slot;
        char action = 'L';
        try {
            slot = generatedSlot >= 0 ? generatedSlot : Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            action = Character.toUpperCase(parts[1].trim().charAt(0));
        }
        boolean isHot = "hot_slot".equals(element.type()) || generatedHot;
        org.bukkit.inventory.Inventory target = isHot ? player.getInventory() : binding.inventory();
        org.bukkit.inventory.Inventory other = isHot ? binding.inventory() : player.getInventory();
        int max = isHot ? 36 : target.getSize(); // 玩家背包全量: 0..8 快捷栏 + 9..35 主背包
        if (slot < 0 || slot >= max) {
            return false;
        }
        var registry = plugin.containerRegistry();
        switch (action) {
            case 'L' -> leftClick(target, player, slot, registry);
            case 'R' -> rightClick(target, player, slot, registry);
            case 'Q' -> quickMove(target, other, slot, registry);
            case 'D' -> swapClick(target, player, slot, registry);    // 双击交换
            case 'A' -> takeAll(target, player, slot, registry);       // 整组拿取
            case 'S' -> distribute(target, player, slot, registry);   // 分发光标
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 左键：空光标拿起整组；有光标 → 放置（同类型合并）/ 交换。 */
    private static void leftClick(org.bukkit.inventory.Inventory target, Player player, int slot,
                                  com.opendreamcore.plugin.container.ContainerRegistry registry) {
        org.bukkit.inventory.ItemStack cursor = registry.cursor(player);
        org.bukkit.inventory.ItemStack inSlot = target.getItem(slot);
        if (cursor == null) {
            if (inSlot != null && !inSlot.getType().isAir()) {
                registry.setCursor(player, inSlot);
                target.setItem(slot, null);
            }
            return;
        }
        if (inSlot == null || inSlot.getType().isAir()) {
            target.setItem(slot, cursor);
            registry.setCursor(player, null);
        } else if (inSlot.isSimilar(cursor)) {
            int space = inSlot.getMaxStackSize() - inSlot.getAmount();
            if (space > 0) {
                int move = Math.min(space, cursor.getAmount());
                inSlot.setAmount(inSlot.getAmount() + move);
                target.setItem(slot, inSlot);
                if (cursor.getAmount() - move <= 0) {
                    registry.setCursor(player, null);
                } else {
                    cursor.setAmount(cursor.getAmount() - move);
                    registry.setCursor(player, cursor);
                }
            }
        } else {
            target.setItem(slot, cursor);
            registry.setCursor(player, inSlot);
        }
    }

    /** 右键：空光标拿起半组（向上取整）；有光标放 1 个（空槽或同类型合并）。 */
    private static void rightClick(org.bukkit.inventory.Inventory target, Player player, int slot,
                                   com.opendreamcore.plugin.container.ContainerRegistry registry) {
        org.bukkit.inventory.ItemStack cursor = registry.cursor(player);
        org.bukkit.inventory.ItemStack inSlot = target.getItem(slot);
        if (cursor == null) {
            if (inSlot != null && !inSlot.getType().isAir() && inSlot.getAmount() > 1) {
                int half = (inSlot.getAmount() + 1) / 2;
                org.bukkit.inventory.ItemStack picked = inSlot.clone();
                picked.setAmount(half);
                inSlot.setAmount(inSlot.getAmount() - half);
                target.setItem(slot, inSlot);
                registry.setCursor(player, picked);
            }
            return;
        }
        if (inSlot == null || inSlot.getType().isAir()) {
            org.bukkit.inventory.ItemStack one = cursor.clone();
            one.setAmount(1);
            target.setItem(slot, one);
            if (cursor.getAmount() - 1 <= 0) {
                registry.setCursor(player, null);
            } else {
                cursor.setAmount(cursor.getAmount() - 1);
                registry.setCursor(player, cursor);
            }
        } else if (inSlot.isSimilar(cursor) && inSlot.getAmount() < inSlot.getMaxStackSize()) {
            inSlot.setAmount(inSlot.getAmount() + 1);
            target.setItem(slot, inSlot);
            if (cursor.getAmount() - 1 <= 0) {
                registry.setCursor(player, null);
            } else {
                cursor.setAmount(cursor.getAmount() - 1);
                registry.setCursor(player, cursor);
            }
        }
    }

    /** Shift+左键：快捷移动（槽位所在方 → 对侧：容器 ↔ 背包/快捷栏；优先合并同类型，其次空槽）。 */
    private static void quickMove(org.bukkit.inventory.Inventory from,
                                  org.bukkit.inventory.Inventory to, int slot,
                                  com.opendreamcore.plugin.container.ContainerRegistry registry) {
        if (from == to) {
            return;
        }
        org.bukkit.inventory.ItemStack item = from.getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        int moved = 0;
        for (int i = 0; i < to.getSize() && item.getAmount() > 0; i++) {
            org.bukkit.inventory.ItemStack existing = to.getItem(i);
            if (existing != null && existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                int move = Math.min(space, item.getAmount());
                existing.setAmount(existing.getAmount() + move);
                item.setAmount(item.getAmount() - move);
                moved += move;
            }
        }
        for (int i = 0; i < to.getSize() && item.getAmount() > 0; i++) {
            org.bukkit.inventory.ItemStack existing = to.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                to.setItem(i, item);
                moved += item.getAmount();
                item.setAmount(0);
            }
        }
        if (moved > 0) {
            from.setItem(slot, item.getAmount() > 0 ? item : null);
        }
    }

    /** 双击交换：光标物品与槽位物品直接交换（不论类型是否相同）。 */
    private static void swapClick(org.bukkit.inventory.Inventory target, Player player, int slot,
                                   com.opendreamcore.plugin.container.ContainerRegistry registry) {
        org.bukkit.inventory.ItemStack cursor = registry.cursor(player);
        org.bukkit.inventory.ItemStack inSlot = target.getItem(slot);
        if (cursor == null && (inSlot == null || inSlot.getType().isAir())) {
            return; // 两边都空，无操作
        }
        if (cursor == null) {
            registry.setCursor(player, inSlot);
            target.setItem(slot, null);
        } else if (inSlot == null || inSlot.getType().isAir()) {
            target.setItem(slot, cursor);
            registry.setCursor(player, null);
        } else {
            // 直接交换
            target.setItem(slot, cursor);
            registry.setCursor(player, inSlot);
        }
    }

    /** 整组拿取：将槽位全部物品拿到光标（如已有光标则先放回槽位再拿取）。 */
    private static void takeAll(org.bukkit.inventory.Inventory target, Player player, int slot,
                                 com.opendreamcore.plugin.container.ContainerRegistry registry) {
        org.bukkit.inventory.ItemStack cursor = registry.cursor(player);
        org.bukkit.inventory.ItemStack inSlot = target.getItem(slot);
        if (inSlot == null || inSlot.getType().isAir()) {
            return; // 空槽位无操作
        }
        if (cursor != null) {
            // 已有光标：先放回槽位（交换）
            target.setItem(slot, cursor);
            registry.setCursor(player, inSlot);
        } else {
            registry.setCursor(player, inSlot);
            target.setItem(slot, null);
        }
    }

    /** 分发：将光标物品均匀分发给目标容器所有空槽位（每个槽 1 个）。 */
    private static void distribute(org.bukkit.inventory.Inventory target, Player player, int slot,
                                    com.opendreamcore.plugin.container.ContainerRegistry registry) {
        org.bukkit.inventory.ItemStack cursor = registry.cursor(player);
        if (cursor == null) {
            return; // 无光标物品
        }
        int remaining = cursor.getAmount();
        for (int i = 0; i < target.getSize() && remaining > 0; i++) {
            if (i == slot) continue; // 跳过当前槽
            org.bukkit.inventory.ItemStack existing = target.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                org.bukkit.inventory.ItemStack one = cursor.clone();
                one.setAmount(1);
                target.setItem(i, one);
                remaining--;
            }
        }
        if (remaining <= 0) {
            registry.setCursor(player, null);
        } else {
            cursor.setAmount(remaining);
            registry.setCursor(player, cursor);
        }
    }

    /** 广播对应 Bukkit 事件；返回 false 表示 Button 被插件取消（跳过脚本）。 */
    private boolean fireEvent(Player player, Page page, String sessionId, UiEvent event, Element element) {
        String pageId = page.id() == null ? sessionId : page.id();
        String elementId = event.elementId();
        String data = event.data();
        switch (event.trigger()) {
            case CLICK -> {
                Integer slot = parseInt(data);
                if (slot == null && data != null && data.contains(":")) {
                    slot = parseInt(data.split(":")[0]); // "12:L" 槽位交互格式
                }
                if (element != null && slot != null
                        && ("chest_slot".equals(element.type()) || "hot_slot".equals(element.type()))) {
                    safeCallEvent(
                            OdcEvents.slot(player, pageId, sessionId, elementId, slot));
                    return true;
                }
                var buttonEvent = OdcEvents.button(player, pageId, sessionId, elementId);
                safeCallEvent(buttonEvent);
                return !buttonEvent.isCancelled();
            }
            case INPUT -> {
                if (element != null && "chat_input".equals(element.type())) {
                    safeCallEvent(
                            OdcEvents.chat(player, pageId, sessionId, elementId, data));
                } else {
                    safeCallEvent(
                            OdcEvents.input(player, pageId, sessionId, elementId, data));
                }
                return true;
            }
            case HOVER -> {
                safeCallEvent(
                        OdcEvents.hover(player, pageId, sessionId, elementId));
                return true;
            }
            case PRESS -> {
                safeCallEvent(
                        OdcEvents.press(player, pageId, sessionId, elementId, data));
                return true;
            }
            case SCROLL -> {
                Integer amount = parseInt(data);
                safeCallEvent(
                        OdcEvents.scroll(player, pageId, sessionId, elementId, amount == null ? 0 : amount));
                return true;
            }
            case KEY -> {
                if (data != null && data.startsWith("mouse:")) {
                    String[] parts = data.split(":");
                    String name = parts.length > 1 ? parts[1] : "";
                    int button = parts.length > 2 ? parseInt(parts[2]) == null ? 0 : parseInt(parts[2]) : 0;
                    safeCallEvent(
                            OdcEvents.mouse(player, pageId, sessionId, name, button));
                } else {
                    String key = data != null && data.startsWith("key:") ? data.substring(4) : data;
                    safeCallEvent(
                            OdcEvents.key(player, pageId, sessionId, key == null ? "" : key));
                }
                return true;
            }
        }
        return true;
    }

    private static Integer parseInt(String data) {
        if (data == null) {
            return null;
        }
        try {
            return Integer.parseInt(data.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 执行元素动作脚本：页面变量 + 当前玩家绑定 + 可选容器上下文 + 事件数据。 */
    private void execute(Player player, Page page, String script,
                         com.opendreamcore.plugin.container.ContainerRegistry.Binding binding, String slotData) {
        try {
            Scope scope = new Scope();
            page.variables().forEach(scope::assignVar);
            scope.assignPlayer("name", player.getName());
            scope.assignPlayer("uuid", player.getUniqueId().toString());
            if (slotData != null) {
                // 事件数据：vars.input / vars.event（滑块/输入框/开关等 INPUT/CLICK 数据）
                scope.assignVar("input", slotData);
                scope.assignVar("event", slotData);
            }
            if (binding != null) {
                int slot = -1;
                if (slotData != null) {
                    try {
                        slot = Integer.parseInt(slotData.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                scope.assignVar("slot", (double) slot);
                var container = new java.util.LinkedHashMap<String, Object>();
                container.put("sessionId", binding.sessionId());
                container.put("size", (double) binding.inventory().getSize());
                container.put("type", binding.type());
                container.put("title", binding.title());
                scope.assignVar("container", container);
            }
            DreamLang.execute(script, scope);
        } catch (Exception e) {
            plugin.getLogger().warning("动作脚本执行失败 (" + player.getName() + "): " + e);
        }
    }

    /** 世界面板拖拽落点同步：坐标写回共享页面元素 hologram（持久化）→ 打开同页面的玩家重发（位置一致）。 */
    private void syncWorldDrag(String pageId, Element element, String data) {
        String[] parts = data.split(",");
        if (parts.length < 3) {
            return;
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            Object raw = element.props().get("hologram");
            java.util.Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                    raw instanceof java.util.Map<?, ?> m ? (java.util.Map<?, ?>) m : java.util.Map.of());
            holo.put("x", x);
            holo.put("y", y);
            holo.put("z", z);
            element.props().put("hologram", holo);
            // 持久化：world_positions.json（重启后拖拽结果仍在）
            plugin.pageManager().saveWorldPosition(pageId, element.id(), x, y, z);
        } catch (NumberFormatException e) {
            return;
        }
        for (java.util.Map.Entry<Player, String> e : playerSessions.entrySet()) {
            Session s = sessions.get(e.getValue());
            if (s != null && pageId.equals(s.pageId()) && e.getKey().isOnline()) {
                plugin.openPage(e.getKey(), pageId);
            }
        }
        plugin.getLogger().info("世界面板拖拽同步 " + pageId + " -> " + data);
    }

    /** 递归找元素（按 id）。 */
    private static Element findElement(Page page, String id) {
        for (Element element : page.elements()) {
            Element found = findElement(element, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Element findElement(Element element, String id) {
        if (element.id().equals(id)) {
            return element;
        }
        for (Element child : element.children()) {
            Element found = findElement(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String triggerName(UiEvent.Trigger trigger) {
        return switch (trigger) {
            case CLICK -> "click";
            case HOVER -> "hover";
            case PRESS -> "press";
            case INPUT -> "input";
            case SCROLL -> "scroll";
            case KEY -> "key";
        };
    }

    /** 安全触发 Bukkit 事件（Arclight 在调度器任务中调 callEvent 会报异常，try-catch 跳过）。 */
    private void safeCallEvent(org.bukkit.event.Event event) {
        try {
            plugin.getServer().getPluginManager().callEvent(event);
        } catch (IllegalStateException e) {
            plugin.getLogger().fine("事件触发跳过（Arclight 限制）: " + event.getEventName());
        }
    }
}
