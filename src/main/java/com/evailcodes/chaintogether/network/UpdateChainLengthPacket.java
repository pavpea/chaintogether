package com.evailcodes.chaintogether.network;

import com.evailcodes.chaintogether.ChainTogether;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateChainLengthPacket(Vec3 player1Pos, Vec3 player2Pos) implements CustomPacketPayload {
    public static final Type<UpdateChainLengthPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChainTogether.MODID, "update_chain_length"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateChainLengthPacket> STREAM_CODEC = StreamCodec.of(
            UpdateChainLengthPacket::encode,
            UpdateChainLengthPacket::decode
    );

    public static void encode(RegistryFriendlyByteBuf buf, UpdateChainLengthPacket packet) {
        buf.writeDouble(packet.player1Pos().x);
        buf.writeDouble(packet.player1Pos().y);
        buf.writeDouble(packet.player1Pos().z);
        buf.writeDouble(packet.player2Pos().x);
        buf.writeDouble(packet.player2Pos().y);
        buf.writeDouble(packet.player2Pos().z);
    }

    public static UpdateChainLengthPacket decode(RegistryFriendlyByteBuf buf) {
        return new UpdateChainLengthPacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 客户端处理逻辑
        });
    }
}
