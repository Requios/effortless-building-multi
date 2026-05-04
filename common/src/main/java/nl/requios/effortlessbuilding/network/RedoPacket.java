package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import nl.requios.effortlessbuilding.Constants;

/**
 * Empty C2S packet requesting the server to redo the player's last undone operation.
 */
public record RedoPacket() implements CustomPacketPayload {

    public static final Type<RedoPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "redo"));

    public static final StreamCodec<FriendlyByteBuf, RedoPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { /* nothing to write */ },
            buf -> new RedoPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

