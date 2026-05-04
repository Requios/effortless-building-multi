package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import nl.requios.effortlessbuilding.Constants;

/**
 * Sent from client to server when the player changes their modifier list
 * (e.g. closing the Modifiers screen).
 *
 * <p>Payload is the full modifier list serialized as a JSON string.
 */
public record UpdateModifiersC2SPacket(String json) implements CustomPacketPayload {

    public static final Type<UpdateModifiersC2SPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "update_modifiers"));

    public static final StreamCodec<FriendlyByteBuf, UpdateModifiersC2SPacket> STREAM_CODEC = StreamCodec.of(
            UpdateModifiersC2SPacket::encode,
            UpdateModifiersC2SPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, UpdateModifiersC2SPacket p) {
        buf.writeUtf(p.json);
    }

    private static UpdateModifiersC2SPacket decode(FriendlyByteBuf buf) {
        return new UpdateModifiersC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

