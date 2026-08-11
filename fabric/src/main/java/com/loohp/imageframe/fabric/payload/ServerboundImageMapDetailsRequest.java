package com.loohp.imageframe.fabric.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundImageMapDetailsRequest(int index) implements CustomPacketPayload {

    public static final Type<ServerboundImageMapDetailsRequest> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "serverbound_imagemap_details"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundImageMapDetailsRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ServerboundImageMapDetailsRequest::index,
            ServerboundImageMapDetailsRequest::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
