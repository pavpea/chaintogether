package com.evailcodes.chaintogether.client;

import com.evailcodes.chaintogether.config.ChainConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT, modid = "chaintogether")
public class ChainRenderer {
    // 客户端绑定状态缓存
    private static final Map<java.util.UUID, java.util.UUID> CLIENT_BOUND_PLAYERS = new HashMap<>();
    
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer localPlayer = mc.player;
            
            if (localPlayer == null || mc.level == null) {
                return;
            }
            
            renderWires(event.getPoseStack().last().pose(), localPlayer, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }
    
    // 检查两个玩家是否绑定
    private static boolean isPlayersBound(Player player1, Player player2) {
        // 检查是否在客户端缓存中绑定
        return CLIENT_BOUND_PLAYERS.containsKey(player1.getUUID()) && 
               CLIENT_BOUND_PLAYERS.get(player1.getUUID()).equals(player2.getUUID());
    }
    
    // 同步绑定状态（实际应该通过网络包调用）
    public static void syncBoundStatus(java.util.UUID player1, java.util.UUID player2, boolean bound) {
        if (bound) {
            CLIENT_BOUND_PLAYERS.put(player1, player2);
            CLIENT_BOUND_PLAYERS.put(player2, player1);
        } else {
            CLIENT_BOUND_PLAYERS.remove(player1);
            CLIENT_BOUND_PLAYERS.remove(player2);
        }
    }
    

    private static void renderWires(Matrix4f pose, LocalPlayer localPlayer, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        
        if (localPlayer == null || mc.level == null) {
            return;
        }
        
        List<PlayerWireData> wires = new ArrayList<>();
        
        for (Player otherPlayer : mc.level.players()) {
            if (otherPlayer != localPlayer) {
                if (isPlayersBound(localPlayer, otherPlayer)) {
                    double maxDistance = ChainConfig.CHAIN_LENGTH.get() * 10.0;
                    wires.add(new PlayerWireData(localPlayer.getUUID(), otherPlayer.getUUID(), maxDistance, true));
                }
            }
        }
        
        if (wires.isEmpty()) {
            return;
        }
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);
        
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        for (PlayerWireData wire : wires) {
            if (!wire.isActive()) continue;
            
            Player player1 = mc.level.getPlayerByUUID(wire.getPlayer1());
            Player player2 = mc.level.getPlayerByUUID(wire.getPlayer2());
            
            if (player1 == null || player2 == null) continue;
            
            Vec3 pos1 = player1.getPosition(partialTicks);
            Vec3 pos2 = player2.getPosition(partialTicks);
            
            double distance = pos1.distanceTo(pos2);
            double maxDistance = wire.getMaxDistance();
            
            if (distance > maxDistance * 2.0) continue;
            
            Color color = calculateColor(distance, maxDistance);
            
            renderLineMatrix(pose, vertexConsumer, cameraPos, pos1, pos2, color);
        }
        
        bufferSource.endBatch(RenderType.LINES);
    }

    private static void renderLineMatrix(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 cameraPos, Vec3 start, Vec3 end, Color color) {
        float alpha = ChainConfig.getTransparencyAsFloat();
        int a = (int)(alpha * 255);
        
        Vec3 startRelative = start.subtract(cameraPos);
        Vec3 endRelative = end.subtract(cameraPos);
        
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        
        vertexConsumer.addVertex(pose, (float) startRelative.x, (float) startRelative.y + 1.0f, (float) startRelative.z)
                .setColor(r, g, b, a)
                .setNormal(0, 1, 0);
        
        vertexConsumer.addVertex(pose, (float) endRelative.x, (float) endRelative.y + 1.0f, (float) endRelative.z)
                .setColor(r, g, b, a)
                .setNormal(0, 1, 0);
    }
    
    private static Color calculateColor(double distance, double maxDistance) {
        double percentage = distance / maxDistance;
        
        if (percentage <= 0.5) {
            // 绿色
            return Color.GREEN;
        } else if (percentage <= 1.0) {
            // 从绿色渐变到红色
            float factor = (float) ((percentage - 0.5) / 0.5);
            int red = Math.min(255, (int) (255 * factor));
            int green = Math.min(255, 255 - red);
            return new Color(red, green, 0);
        } else {
            // 红色
            return Color.RED;
        }
    }
    

    
    // 玩家绑定线数据类
    private static class PlayerWireData {
        private final java.util.UUID player1;
        private final java.util.UUID player2;
        private final double maxDistance;
        private final boolean active;
        
        public PlayerWireData(java.util.UUID player1, java.util.UUID player2, double maxDistance, boolean active) {
            this.player1 = player1;
            this.player2 = player2;
            this.maxDistance = maxDistance;
            this.active = active;
        }
        
        public java.util.UUID getPlayer1() {
            return player1;
        }
        
        public java.util.UUID getPlayer2() {
            return player2;
        }
        
        public double getMaxDistance() {
            return maxDistance;
        }
        
        public boolean isActive() {
            return active;
        }
    }
}