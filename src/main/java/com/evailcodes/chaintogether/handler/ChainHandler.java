package com.evailcodes.chaintogether.handler;

import com.evailcodes.chaintogether.ChainTogether;
import com.evailcodes.chaintogether.config.ChainConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;

public class ChainHandler {
    private static final Map<UUID, UUID> CHAINED_PLAYERS = new HashMap<>();
    private static final Map<UUID, ServerLevel> DIMENSION_CHANGE_MAP = new HashMap<>();
    // 用于跟踪死亡后未复活的玩家
    private static final Set<UUID> DEAD_PLAYERS = new HashSet<>();

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 设置游戏规则
        // 移除自动复活设置，让玩家手动选择复活
        // event.getServer().getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, event.getServer());
        // event.getServer().getGameRules().getRule(GameRules.KEEP_INVENTORY).set(true, event.getServer());

        ChainTogether.LOGGER.info("ChainTogether game rules configured!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.evailcodes.chaintogether.ChainCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerPlayer partner = getPartner(player);
            if (partner != null) {
                // 只有当两个玩家在同一维度时才执行链条逻辑
                if (player.level() == partner.level()) {
                    updateChain(player, partner);
                    enforceChainRules(player, partner);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 将死亡玩家添加到死亡集合中
            DEAD_PLAYERS.add(player.getUUID());
            // 不移除绑定关系，保持绑定状态
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 从死亡集合中移除
            DEAD_PLAYERS.remove(player.getUUID());
            
            ServerPlayer partner = getPartner(player);
            if (partner != null) {
                if (DEAD_PLAYERS.contains(partner.getUUID())) {
                    // 伙伴还未复活，等待伙伴复活
                } else if (partner.isAlive()) {
                    // 伙伴已复活，将当前玩家传送到伙伴身边
                    teleportPlayerToPartner(player, partner, (ServerLevel) partner.level());
                    // 不需要重新绑定，因为死亡时没有移除绑定关系
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getTarget() instanceof ServerPlayer targetPlayer && event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getItemStack();

            if (stack.getItem() == Items.LEAD && !arePlayersChained(player, targetPlayer)) {
                bindPlayers(player, targetPlayer);
                event.setCanceled(true);

                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRightClickEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getTarget() instanceof ServerPlayer targetPlayer && event.getEntity() instanceof ServerPlayer player) {
            // 防止右键取下拴绳
            if (arePlayersChained(player, targetPlayer) || isChained(player) || isChained(targetPlayer)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getItemStack();
            // 防止使用拴绳右键取下
            if (stack.getItem() == Items.LEAD && isChained(player)) {
                event.setCanceled(true);
            }
        }
    }

    private boolean isChained(ServerPlayer player) {
        return getPartner(player) != null;
    }

    @SubscribeEvent
    public void onLivingAttack(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker &&
            event.getEntity() instanceof ServerPlayer victim) {
            // 防止攻击被拴住的玩家
            if (isChained(attacker) || isChained(victim)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTeleport(EntityTeleportEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && isChained(player)) {
            // 检查是否有维度变化记录
            ServerLevel targetLevel = DIMENSION_CHANGE_MAP.get(player.getUUID());
            
            if (targetLevel == null) {
                // 禁止被拴住的玩家使用任何传送方式（包括tp指令、末影珍珠等）
                // 但允许通过传送门传送（因为会触发PlayerChangedDimensionEvent）
                // 注意：传送门传送不会触发此事件，而是直接触发PlayerChangedDimensionEvent
                event.setCanceled(true);
            }
        }
    }

    // 移除共享重生点机制，防止游戏崩溃
    // @SubscribeEvent
    // public void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
    //     if (event.getEntity().level().isClientSide) {
    //         return;
    //     }
    //     if (event.getEntity() instanceof ServerPlayer player && isChained(player)) {
    //         ServerPlayer partner = getPartner(player);
    //         if (partner != null && ChainConfig.SHARED_RESPAWN.get()) {
    //             // 更新共享重生点
    //             BlockPos newSpawnPos = player.getRespawnPosition();
    //             if (newSpawnPos != null) {
    //                 SHARED_RESPAWN_POINTS.put(player.getUUID(), newSpawnPos);
    //                 SHARED_RESPAWN_POINTS.put(partner.getUUID(), newSpawnPos);
    //                 
    //                 // 同时更新伙伴的重生点
    //                 partner.setRespawnPosition(player.level().dimension(), newSpawnPos, player.getRespawnAngle(),
    //                         player.isRespawnForced(), player.isRespawnForced());
    //             }
    //         }
    //     }
    // }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 玩家登出时的处理
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家是否仍然被绑定
            ServerPlayer partner = getPartner(player);
            if (partner != null && partner.isAlive()) {
                // 玩家登录时的处理
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家是否被绑定
            ServerPlayer partner = getPartner(player);
            
            if (partner != null) {
                // 检查玩家是否是因为伙伴已经通过传送门而跟随传送
                ServerLevel targetLevel = DIMENSION_CHANGE_MAP.get(player.getUUID());
                
                if (targetLevel != null) {
                    // 伙伴已经在另一个维度，将当前玩家传送到伙伴附近
                    teleportPlayerToPartner(player, partner, targetLevel);
                    
                    // 清除维度变化记录
                    DIMENSION_CHANGE_MAP.remove(player.getUUID());
                } else {
                    // 记录当前玩家的维度变化，以便伙伴跟随
                    DIMENSION_CHANGE_MAP.put(partner.getUUID(), (ServerLevel) player.level());
                    
                    ChainTogether.LOGGER.info("Player {} changed dimension to {}", 
                            player.getName().getString(), player.level().dimension().location());
                }
            }
        }
    }
    
    private void teleportPlayerToPartner(ServerPlayer player, ServerPlayer partner, ServerLevel targetLevel) {
        // 直接传送到伙伴的位置，避免复杂的位置计算导致无限循环
        BlockPos partnerPos = partner.blockPosition();
        
        // 传送到伙伴附近的安全位置
        player.teleportTo(targetLevel, 
                partnerPos.getX() + 0.5, 
                partnerPos.getY(), 
                partnerPos.getZ() + 0.5,
                player.getYRot(), 
                player.getXRot());
        
        ChainTogether.LOGGER.info("Player {} teleported to {} near partner {}", 
                player.getName().getString(), 
                targetLevel.dimension().location(), 
                partner.getName().getString());
    }

    public static void bindPlayers(ServerPlayer player1, ServerPlayer player2) {
        CHAINED_PLAYERS.put(player1.getUUID(), player2.getUUID());
        CHAINED_PLAYERS.put(player2.getUUID(), player1.getUUID());

        // 发送网络包同步绑定状态到客户端
        com.evailcodes.chaintogether.network.ChainPacketHandler.sendToPlayer(
                new com.evailcodes.chaintogether.network.SyncBoundStatusPacket(
                        player1.getUUID(), player2.getUUID(), true), player1);
        com.evailcodes.chaintogether.network.ChainPacketHandler.sendToPlayer(
                new com.evailcodes.chaintogether.network.SyncBoundStatusPacket(
                        player1.getUUID(), player2.getUUID(), true), player2);

        // 发送绑定成功提示
        player1.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chaintogether.bound.success", player2.getName().getString()));
        player2.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chaintogether.bound.success", player1.getName().getString()));

        ChainTogether.LOGGER.info("Bound players: {} and {}",
                player1.getName().getString(), player2.getName().getString());
    }

    public static void unbindPlayer(ServerPlayer player) {
        UUID partnerId = CHAINED_PLAYERS.remove(player.getUUID());
        if (partnerId != null) {
            CHAINED_PLAYERS.remove(partnerId);

            // 发送网络包同步解绑状态到客户端
            ServerPlayer partner = player.getServer().getPlayerList().getPlayer(partnerId);
            if (partner != null) {
                com.evailcodes.chaintogether.network.ChainPacketHandler.sendToPlayer(
                        new com.evailcodes.chaintogether.network.SyncBoundStatusPacket(
                                player.getUUID(), partnerId, false), player);
                com.evailcodes.chaintogether.network.ChainPacketHandler.sendToPlayer(
                        new com.evailcodes.chaintogether.network.SyncBoundStatusPacket(
                                player.getUUID(), partnerId, false), partner);
                
                // 发送解绑成功提示
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chaintogether.unbound.success", partner.getName().getString()));
                partner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chaintogether.unbound.success", player.getName().getString()));
            } else {
                // 如果伙伴不在线，只通知当前玩家
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chaintogether.unbound.success.alone"));
            }

            ChainTogether.LOGGER.info("Unbound player: {}", player.getName().getString());
        }
    }

    public static ServerPlayer getPartner(ServerPlayer player) {
        UUID partnerId = CHAINED_PLAYERS.get(player.getUUID());
        if (partnerId != null) {
            return player.getServer().getPlayerList().getPlayer(partnerId);
        }
        return null;
    }

    public static boolean arePlayersChained(ServerPlayer player1, ServerPlayer player2) {
        return Objects.equals(CHAINED_PLAYERS.get(player1.getUUID()), player2.getUUID());
    }

    public static Collection<ServerPlayer> getChainedPlayers() {
        List<ServerPlayer> players = new ArrayList<>();
        // 使用线程安全的副本进行迭代
        Set<Map.Entry<UUID, UUID>> entries = new HashSet<>(CHAINED_PLAYERS.entrySet());
        Set<UUID> processedPlayers = new HashSet<>();
        
        // 遍历所有绑定关系
        for (Map.Entry<UUID, UUID> entry : entries) {
            UUID playerId = entry.getKey();
            UUID partnerId = entry.getValue();
            
            // 只添加一个方向的映射以避免重复
            if (playerId.hashCode() < partnerId.hashCode() && !processedPlayers.contains(playerId)) {
                processedPlayers.add(playerId);
                processedPlayers.add(partnerId);
            }
        }
        return players;
    }

    /**
     * 获取所有绑定关系，返回玩家名对列表
     */
    public static List<String> getChainedPlayerPairs(net.minecraft.server.MinecraftServer server) {
        List<String> pairs = new ArrayList<>();
        // 使用线程安全的副本进行迭代
        Set<Map.Entry<UUID, UUID>> entries = new HashSet<>(CHAINED_PLAYERS.entrySet());
        Set<UUID> processedPairs = new HashSet<>();
        
        // 遍历所有绑定关系
        for (Map.Entry<UUID, UUID> entry : entries) {
            UUID playerId = entry.getKey();
            UUID partnerId = entry.getValue();
            
            // 只处理一个方向的映射以避免重复
            if (playerId.hashCode() < partnerId.hashCode() && !processedPairs.contains(playerId)) {
                // 标记为已处理
                processedPairs.add(playerId);
                processedPairs.add(partnerId);
                
                // 获取玩家实例
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                
                if (player != null && partner != null) {
                    // 添加到列表中
                    pairs.add(player.getName().getString() + " 与 " + partner.getName().getString() + " 已绑定");
                }
            }
        }
        return pairs;
    }

    private static void updateChain(ServerPlayer player1, ServerPlayer player2) {
        // 计算最大距离：10.0是原版拴绳长度，乘以配置的倍数
        double maxDistance = 10.0 * ChainConfig.CHAIN_LENGTH.get();
        double minDistance = 4.0;
        double distance = player1.distanceTo(player2);

        // 只在距离超过阈值且差异足够大时才应用物理效果
        // 这样可以减少不必要的速度修改，避免虚假脚步声
        if (distance > maxDistance + 0.5) {
            // 将玩家拉向彼此，确保拴绳不会断开
            Vec3 direction = player2.position().subtract(player1.position()).normalize();
            double pullStrength = 0.3;
            
            // 仅在玩家没有主动移动时应用拉力（避免干扰正常移动）
            if (player1.getDeltaMovement().lengthSqr() < 0.001) {
                player1.setDeltaMovement(direction.scale(pullStrength * 0.5));
            } else {
                player1.setDeltaMovement(player1.getDeltaMovement().add(direction.scale(pullStrength * 0.25)));
            }
            
            if (player2.getDeltaMovement().lengthSqr() < 0.001) {
                player2.setDeltaMovement(direction.scale(-pullStrength * 0.5));
            } else {
                player2.setDeltaMovement(player2.getDeltaMovement().subtract(direction.scale(pullStrength * 0.25)));
            }

            player1.hurtMarked = true;
            player2.hurtMarked = true;
        } else if (distance < minDistance - 1.5) {
            // 防止玩家太靠近，保持适当距离
            // 增加了1.5的缓冲，只有距离小于2.5时才触发推力
            Vec3 direction = player2.position().subtract(player1.position()).normalize();
            double pushStrength = 0.05;
            
            // 仅在玩家完全静止时才应用微弱的推力
            if (player1.getDeltaMovement().lengthSqr() < 0.0001) {
                player1.setDeltaMovement(direction.scale(-pushStrength));
            }
            
            if (player2.getDeltaMovement().lengthSqr() < 0.0001) {
                player2.setDeltaMovement(direction.scale(pushStrength));
            }
        }
    }

    private static void enforceChainRules(ServerPlayer player1, ServerPlayer player2) {
        // 防止传送
        if (ChainConfig.PREVENT_TELEPORT.get()) {
            if (player1.getLastDeathLocation().isPresent() ||
                    player2.getLastDeathLocation().isPresent()) {
                // 重置死亡位置
                player1.setLastDeathLocation(Optional.empty());
                player2.setLastDeathLocation(Optional.empty());
            }
        }
    }

    private static void rebindPlayers(ServerPlayer player1, ServerPlayer player2) {
        unbindPlayer(player1);
        bindPlayers(player1, player2);
    }

    private static void scheduleRespawn(ServerPlayer player, BlockPos pos, ServerLevel level) {
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
    }
}