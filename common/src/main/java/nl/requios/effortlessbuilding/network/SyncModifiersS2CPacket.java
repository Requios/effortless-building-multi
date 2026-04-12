package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.requios.effortlessbuilding.Constants;

/**
 * Sent from server to client to synchronise a player's modifier list.
 * Dispatched on login and after the server processes an {@link UpdateModifiersC2SPacket}.
 *
 * <p>Payload is the full modifier list serialized as a JSON string.
 */
public record SyncModifiersS2CPacket(String json) implements CustomPacketPayload {

    public static final Type<SyncModifiersS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_modifiers"));

    public static final StreamCodec<FriendlyByteBuf, SyncModifiersS2CPacket> STREAM_CODEC = StreamCodec.of(
            SyncModifiersS2CPacket::encode,
            SyncModifiersS2CPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, SyncModifiersS2CPacket p) {
        buf.writeUtf(p.json);
    }

    private static SyncModifiersS2CPacket decode(FriendlyByteBuf buf) {
        return new SyncModifiersS2CPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

