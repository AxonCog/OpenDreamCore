package com.opendreamcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * 世界全息渲染：display: world 页面在世界里画 billboard 文本/图片。
 * 页面锚点默认玩家前方 3 格（options.world.offsetX/Y/Z 可调），
 * 元素 hologram: {x, y, z, scale} 相对锚点。
 * 目前渲染 text；其余类型跳过（后续补矩形/图片）。
 */
public final class WorldHologram {

    private WorldHologram() {
    }

    /**
     * RenderLevelStageEvent 里调用。camera 用于对齐视角（billboard）。
     * partialTick 未用（全息不插值）。
     */
    public static void render(List<RenderNode> nodes, Map<String, Object> options,
                              net.minecraft.client.Camera camera, float partialTick) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 页面锚点：相对玩家偏移
        Vec3 anchor = anchor(mc, options);

        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            for (RenderNode node : nodes) {
                renderNode(pose, buffers, node);
            }
            buffers.endBatch();
        } catch (Exception ignored) {
            // 全息渲染出错不拖垮帧
        }
    }

    private static Vec3 anchor(Minecraft mc, Map<String, Object> options) {
        Object world = options == null ? null : options.get("world");
        double ox = 0, oy = 1.6, oz = 3;
        if (world instanceof Map<?, ?> w) {
            ox = num(w.get("offsetX"), ox);
            oy = num(w.get("offsetY"), oy);
            oz = num(w.get("offsetZ"), oz);
        }
        var player = mc.player;
        double yaw = Math.toRadians(player.getYRot());
        double dx = -Math.sin(yaw) * oz;
        double dz = Math.cos(yaw) * oz;
        return player.position().add(dx + ox, oy, dz + oz);
    }

    private static void renderNode(PoseStack pose, MultiBufferSource buffers, RenderNode node) {
        if (!node.visible()) {
            return;
        }
        if ("text".equals(node.type())) {
            renderText(pose, buffers, node);
        } else if ("entity".equals(node.type())) {
            renderEntity(pose, buffers, node);
        }
        for (RenderNode child : node.children()) {
            renderNode(pose, buffers, child);
        }
    }

    /** 世界内实体渲染（entity 组件）：按实体类型创建临时实体（不进世界），全息位置展示。 */
    private static final Map<String, net.minecraft.world.entity.Entity> ENTITY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void renderEntity(PoseStack pose, MultiBufferSource buffers, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "entity");
        String typeId = UiRenderer.str(spec.get("type"));
        if (typeId == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        net.minecraft.world.entity.Entity entity = ENTITY_CACHE.get(node.id());
        if (entity == null || !typeId.equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                    net.minecraft.resources.ResourceLocation.tryParse(typeId));
            if (type == null) {
                return;
            }
            entity = type.create(mc.level);
            if (entity == null) {
                return;
            }
            ENTITY_CACHE.put(node.id(), entity);
        }
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        double x = num(holo.get("x"), 0);
        double y = num(holo.get("y"), 0);
        double z = num(holo.get("z"), 0);
        double scale = num(holo.get("scale"), 1.0);
        double yaw = num(holo.get("yaw"), (double) (mc.level.getGameTime() % 360));

        entity.setPos(x, y, z);
        entity.setYRot((float) yaw);
        entity.setXRot(0);

        pose.pushPose();
        pose.scale((float) scale, (float) scale, (float) scale);
        mc.getEntityRenderDispatcher().render(entity, x, y, z,
                entity.getYRot(), 1.0F, pose, buffers, 0xF000F0);
        pose.popPose();
    }

    private static void renderText(PoseStack pose, MultiBufferSource buffers, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "text");
        String content = UiRenderer.interpolate(node, UiRenderer.str(spec.get("content")), null);
        if (content == null || content.isEmpty()) {
            return;
        }
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        double x = num(holo.get("x"), 0);
        double y = num(holo.get("y"), 0);
        double z = num(holo.get("z"), 0);
        double scale = num(holo.get("scale"), 0.025);
        int color = UiStyle.color(spec.get("color"), 0xFFFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x, y, z);
        // 对齐相机（billboard）+ 固定文本尺寸
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale((float) scale, (float) -scale, (float) scale);
        float w = mc.font.width(content);
        mc.font.drawInBatch(content, -w / 2.0F, 0, color, false,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                0, 0xF000F0);
        pose.popPose();
    }

    private static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
