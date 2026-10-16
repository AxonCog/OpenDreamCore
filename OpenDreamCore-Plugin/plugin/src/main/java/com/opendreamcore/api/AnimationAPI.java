package com.opendreamcore.api;

import com.opendreamcore.model.anim.AnimMessages;
import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 动画 API：给任意实体的骨骼动画点火。
 *
 * <p>配套的控制器写在服务端 Animation/ 下的 yml 里（原生写法或龙核写法都行），
 * triggers 条目就是这里的触发名：
 * <pre>
 * AnimationAPI.anim().trigger(entity, "cast");      // 点火 cast 触发（起手>循环 那串）
 * AnimationAPI.anim().play(entity, "walk");         // 直接播一段
 * AnimationAPI.anim().play(entity, "buff", "特效");  // 撂在「特效」层上
 * AnimationAPI.anim().stop(entity);                 // 停触发面
 * </pre>
 * <p>实体维度：只有「能被至少一个玩家看见」的实体值得播——按 48 格半径找观看者下发，
 * 没人看就白扔（返回 0）；玩家自己用 {@link #trigger(Player, String)} 系列重载。
 */
public final class AnimationAPI {

    static final AnimationAPI INSTANCE = new AnimationAPI();

    private static final double VIEW_RADIUS = 48.0;

    private AnimationAPI() {
    }

    public static AnimationAPI anim() {
        return INSTANCE;
    }

    /** 给实体点火控制器触发。 */
    public int trigger(Entity entity, String triggerName) {
        return broadcastAround(entity, AnimMessages.trigger(entityId(entity), triggerName));
    }

    /** 给玩家自己点火触发（身边没别人也保证本人收到）。 */
    public boolean trigger(Player player, String triggerName) {
        com.opendreamcore.plugin.server.visual.AnimVelocity.apply(player, triggerName);
        return send(player, AnimMessages.trigger(-1, triggerName));
    }

    /** 给玩家直接播（本人客户端播自己，同 play(Entity) 的 -1 语义）。 */
    public boolean play(Player player, String animName) {
        com.opendreamcore.plugin.server.visual.AnimVelocity.apply(player, animName);
        return send(player, AnimMessages.play(-1, animName, null));
    }

    /** 停玩家自己的触发面/直控。 */
    public boolean stop(Player player) {
        return send(player, AnimMessages.stop(-1));
    }

    /** 给实体直接播一段动画（不经过控制器，走 base 层）。 */
    public int play(Entity entity, String animName) {
        return play(entity, animName, null);
    }

    /** 直接播并指定挂哪层（层名对应控制器 layers 节；没有就 base）。 */
    public int play(Entity entity, String animName, String layer) {
        // 玩家目标发 -1（客户端=自己）：服务端/客户端实体编号在部分环境对不上，
        // 曾导致 play 命中了引擎表却永远查空（miss 日志实锢）。玩家动画的语义
        // 本就是「该玩家自己的客户端播自己」，不需要编号跨端。
        if (entity instanceof Player) {
            return send((Player) entity, AnimMessages.play(-1, animName, layer)) ? 1 : 0;
        }
        return broadcastAround(entity, AnimMessages.play(entityId(entity), animName, layer));
    }

    /** 停掉实体的触发面（base 状态判定不受影响）。 */
    public int stop(Entity entity) {
        if (entity instanceof Player) {
            return send((Player) entity, AnimMessages.stop(-1)) ? 1 : 0;
        }
        return broadcastAround(entity, AnimMessages.stop(entityId(entity)));
    }

    // 内部

    private boolean send(Player p, String payload) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || p == null || !p.isOnline()) {
            return false;
        }
        com.opendreamcore.plugin.network.CustomPacketRegistry.send(plugin, p, AnimMessages.CHANNEL, payload);
        return true;
    }

    private int broadcastAround(Entity entity, String payload) {
        if (entity == null || payload == null) {
            return 0;
        }
        if (entity instanceof Player) {
            return send((Player) entity, payload) ? 1 : 0;
        }
        Location loc = entity.getLocation();
        int sent = 0;
        for (Player p : viewers(loc)) {
            if (send(p, payload)) {
                sent++;
            }
        }
        return sent;
    }

    private long entityId(Entity entity) {
        return entity == null ? -1 : entity.getEntityId();
    }

    private List<Player> viewers(Location loc) {
        return loc == null || loc.getWorld() == null
                ? java.util.Collections.emptyList()
                : loc.getWorld().getNearbyEntities(loc, VIEW_RADIUS, VIEW_RADIUS, VIEW_RADIUS)
                        .stream()
                        .filter(e -> e instanceof Player && e.getEntityId() != 0)
                        .map(e -> (Player) e)
                        .collect(java.util.stream.Collectors.toList());
    }

    @SuppressWarnings("unused")
    private static Player online(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }
}
