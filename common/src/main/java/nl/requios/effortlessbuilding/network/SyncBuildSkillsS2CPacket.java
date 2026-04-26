package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.requios.effortlessbuilding.Constants;

/**
 * Sent from server to client to synchronise the player's effective build skills
 * (reach and max blocks per axis).
 */
public record SyncBuildSkillsS2CPacket(int effectiveReach, int effectiveAxis) implements CustomPacketPayload {

    public static final Type<SyncBuildSkillsS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_build_skills"));

    public static final StreamCodec<FriendlyByteBuf, SyncBuildSkillsS2CPacket> STREAM_CODEC = StreamCodec.of(
            SyncBuildSkillsS2CPacket::encode,
            SyncBuildSkillsS2CPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, SyncBuildSkillsS2CPacket p) {
        buf.writeVarInt(p.effectiveReach);
        buf.writeVarInt(p.effectiveAxis);
    }

    private static SyncBuildSkillsS2CPacket decode(FriendlyByteBuf buf) {
        return new SyncBuildSkillsS2CPacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

