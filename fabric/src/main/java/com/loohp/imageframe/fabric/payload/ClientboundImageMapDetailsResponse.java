package com.loohp.imageframe.fabric.payload;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundImageMapDetailsResponse(int index, int width, int height, IntList mapIds) implements CustomPacketPayload {

    public static final Type<ClientboundImageMapDetailsResponse> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "clientbound_imagemap_details"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundImageMapDetailsResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ClientboundImageMapDetailsResponse::index,
            ByteBufCodecs.INT, ClientboundImageMapDetailsResponse::width,
            ByteBufCodecs.INT, ClientboundImageMapDetailsResponse::height,
            ByteBufCodecs.collection(IntArrayList::new, ByteBufCodecs.INT), ClientboundImageMapDetailsResponse::mapIds,
            ClientboundImageMapDetailsResponse::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
