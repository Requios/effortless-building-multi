package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.requios.effortlessbuilding.Constants;

/**
 * Sent from client to server when an operator updates the server config.
 */
public record UpdateServerConfigC2SPacket(int buildModeReach, int maxBlocksPerAxis) implements CustomPacketPayload {

    public static final Type<UpdateServerConfigC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "update_server_config"));

    public static final StreamCodec<FriendlyByteBuf, UpdateServerConfigC2SPacket> STREAM_CODEC = StreamCodec.of(
            UpdateServerConfigC2SPacket::encode,
            UpdateServerConfigC2SPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, UpdateServerConfigC2SPacket p) {
        buf.writeVarInt(p.buildModeReach);
        buf.writeVarInt(p.maxBlocksPerAxis);
    }

    private static UpdateServerConfigC2SPacket decode(FriendlyByteBuf buf) {
        return new UpdateServerConfigC2SPacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

