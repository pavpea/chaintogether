package com.evailcodes.chaintogether.network;

import com.evailcodes.chaintogether.ChainTogether;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = ChainTogether.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ChainPacketHandler {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                UpdateChainLengthPacket.TYPE,
                UpdateChainLengthPacket.STREAM_CODEC,
                UpdateChainLengthPacket::handle
        );

        registrar.playToClient(
                SyncBoundStatusPacket.TYPE,
                SyncBoundStatusPacket.STREAM_CODEC,
                SyncBoundStatusPacket::handle
        );
    }

    public static void register(FMLCommonSetupEvent event) {
        // Obsolete in NeoForge, payload registered via RegisterPayloadHandlersEvent
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToServer(MSG message) {
        PacketDistributor.sendToServer(message);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToAll(MSG message) {
        PacketDistributor.sendToAllPlayers(message);
    }
}