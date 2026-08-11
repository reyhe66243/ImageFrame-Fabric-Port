package com.loohp.imageframe.fabric.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundHdImageMultipartResponse(int mapId, int multipart, int index, byte[] data, boolean end) implements CustomPacketPayload {

    public static final Type<ClientboundHdImageMultipartResponse> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "clientbound_hd_image_multi"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundHdImageMultipartResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ClientboundHdImageMultipartResponse::mapId,
            ByteBufCodecs.INT, ClientboundHdImageMultipartResponse::multipart,
            ByteBufCodecs.INT, ClientboundHdImageMultipartResponse::index,
            ByteBufCodecs.BYTE_ARRAY, ClientboundHdImageMultipartResponse::data,
            ByteBufCodecs.BOOL, ClientboundHdImageMultipartResponse::end,
            ClientboundHdImageMultipartResponse::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
