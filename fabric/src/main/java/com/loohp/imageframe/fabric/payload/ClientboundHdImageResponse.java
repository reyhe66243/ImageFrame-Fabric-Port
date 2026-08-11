package com.loohp.imageframe.fabric.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record ClientboundHdImageResponse(int mapId, boolean requestAccepted, byte[] data, Optional<Integer> multipart, int frameIndex, int totalFrames) implements CustomPacketPayload {

    public ClientboundHdImageResponse(int mapId, boolean requestAccepted, byte[] data, Optional<Integer> multipart) {
        this(mapId, requestAccepted, data, multipart, 0, 1);
    }

    public static final Type<ClientboundHdImageResponse> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "clientbound_hd_image"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundHdImageResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ClientboundHdImageResponse::mapId,
            ByteBufCodecs.BOOL, ClientboundHdImageResponse::requestAccepted,
            ByteBufCodecs.BYTE_ARRAY, ClientboundHdImageResponse::data,
            ByteBufCodecs.optional(ByteBufCodecs.INT), ClientboundHdImageResponse::multipart,
            ByteBufCodecs.INT, ClientboundHdImageResponse::frameIndex,
            ByteBufCodecs.INT, ClientboundHdImageResponse::totalFrames,
            ClientboundHdImageResponse::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
