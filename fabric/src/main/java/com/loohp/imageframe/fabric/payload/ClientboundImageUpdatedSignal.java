package com.loohp.imageframe.fabric.payload;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundImageUpdatedSignal(IntSet indexes, IntSet mapIds, int frameIndex) implements CustomPacketPayload {

    public ClientboundImageUpdatedSignal(IntSet indexes, IntSet mapIds) {
        this(indexes, mapIds, -1);
    }

    public static final Type<ClientboundImageUpdatedSignal> ID = new Type<>(Identifier.fromNamespaceAndPath("imageframe", "clientbound_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundImageUpdatedSignal> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(IntOpenHashSet::new, ByteBufCodecs.INT), ClientboundImageUpdatedSignal::indexes,
            ByteBufCodecs.collection(IntOpenHashSet::new, ByteBufCodecs.INT), ClientboundImageUpdatedSignal::mapIds,
            ByteBufCodecs.INT, ClientboundImageUpdatedSignal::frameIndex,
            ClientboundImageUpdatedSignal::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
