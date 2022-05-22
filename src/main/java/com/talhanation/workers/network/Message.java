package com.talhanation.workers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

public interface Message<T extends Message> {
    Dist getExecutingSide();

    default void executeServerSide(NetworkEvent.Context context) {}

    default void executeClientSide(NetworkEvent.Context context) {}

    T fromBytes(FriendlyByteBuf paramPacketBuffer);

    void toBytes(FriendlyByteBuf paramPacketBuffer);
}
