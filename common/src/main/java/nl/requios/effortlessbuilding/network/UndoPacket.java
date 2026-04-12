package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.requios.effortlessbuilding.Constants;

/**
 * Empty C2S packet requesting the server to undo the player's last operation.
 */
public record UndoPacket() implements CustomPacketPayload {

    public static final Type<UndoPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "undo"));

    public static final StreamCodec<FriendlyByteBuf, UndoPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { /* nothing to write */ },
            buf -> new UndoPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

