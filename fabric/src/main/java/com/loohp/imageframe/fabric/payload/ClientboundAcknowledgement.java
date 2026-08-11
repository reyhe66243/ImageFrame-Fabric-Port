package com.loohp.imageframe.fabric.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundAcknowledgement(long id) implements CustomPacketPayload {

    public static final Type<ClientboundAcknowledgement> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "clientbound_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAcknowledgement> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, ClientboundAcknowledgement::id,
            ClientboundAcknowledgement::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
