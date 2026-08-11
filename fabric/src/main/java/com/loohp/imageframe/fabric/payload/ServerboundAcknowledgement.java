package com.loohp.imageframe.fabric.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundAcknowledgement(long id) implements CustomPacketPayload {

    public static final Type<ServerboundAcknowledgement> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "serverbound_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAcknowledgement> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, ServerboundAcknowledgement::id,
            ServerboundAcknowledgement::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
