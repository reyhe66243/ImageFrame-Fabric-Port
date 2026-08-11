package com.loohp.imageframe.fabric.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundHdImageRequest(int mapId, int frameIndex) implements CustomPacketPayload {

    public ServerboundHdImageRequest(int mapId) {
        this(mapId, 0);
    }

    public static final Type<ServerboundHdImageRequest> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "serverbound_hd_image"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundHdImageRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ServerboundHdImageRequest::mapId,
            ByteBufCodecs.INT, ServerboundHdImageRequest::frameIndex,
            ServerboundHdImageRequest::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
