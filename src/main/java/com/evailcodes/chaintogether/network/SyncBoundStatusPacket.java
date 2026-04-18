package com.evailcodes.chaintogether.network;

import com.evailcodes.chaintogether.ChainTogether;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SyncBoundStatusPacket(UUID player1, UUID player2, boolean bound) implements CustomPacketPayload {
    public static final Type<SyncBoundStatusPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChainTogether.MODID, "sync_bound_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBoundStatusPacket> STREAM_CODEC = StreamCodec.of(
            SyncBoundStatusPacket::encode,
            SyncBoundStatusPacket::decode
    );

    public static void encode(RegistryFriendlyByteBuf buf, SyncBoundStatusPacket packet) {
        buf.writeUUID(packet.player1());
        buf.writeUUID(packet.player2());
        buf.writeBoolean(packet.bound());
    }

    public static SyncBoundStatusPacket decode(RegistryFriendlyByteBuf buf) {
        return new SyncBoundStatusPacket(buf.readUUID(), buf.readUUID(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            com.evailcodes.chaintogether.client.ChainRenderer.syncBoundStatus(
                    player1, player2, bound);
        });
    }
}
