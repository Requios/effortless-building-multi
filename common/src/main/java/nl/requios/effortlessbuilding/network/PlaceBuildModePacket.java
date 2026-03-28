package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import org.jetbrains.annotations.Nullable;

/**
 * Sent from client to server when the player completes a build-mode placement.
 * The server uses the supplied positions to recalculate block coordinates and place them.
 *
 * <p>Position semantics:
 * <ul>
 *   <li>{@link #firstPos} — first click position (always present)</li>
 *   <li>{@link #secondPos} — second click position (always present for non-disabled modes)</li>
 *   <li>{@link #thirdPos} — third click position; null for two-click modes</li>
 * </ul>
 */
public record PlaceBuildModePacket(
        BuildModeEnum buildMode,
        BlockPos firstPos,
        BlockPos secondPos,
        @Nullable BlockPos thirdPos,
        ModeOptions.ActionEnum fill,
        ModeOptions.ActionEnum cubeFill,
        ModeOptions.ActionEnum raisedEdge,
        ModeOptions.ActionEnum circleStart
) implements CustomPacketPayload {

    public static final Type<PlaceBuildModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "place_build_mode"));

    public static final StreamCodec<FriendlyByteBuf, PlaceBuildModePacket> STREAM_CODEC = StreamCodec.of(
            PlaceBuildModePacket::encode,
            PlaceBuildModePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, PlaceBuildModePacket p) {
        buf.writeVarInt(p.buildMode.ordinal());
        buf.writeLong(p.firstPos.asLong());
        buf.writeLong(p.secondPos.asLong());
        buf.writeBoolean(p.thirdPos != null);
        if (p.thirdPos != null) buf.writeLong(p.thirdPos.asLong());
        buf.writeVarInt(p.fill.ordinal());
        buf.writeVarInt(p.cubeFill.ordinal());
        buf.writeVarInt(p.raisedEdge.ordinal());
        buf.writeVarInt(p.circleStart.ordinal());
    }

    private static PlaceBuildModePacket decode(FriendlyByteBuf buf) {
        BuildModeEnum buildMode = BuildModeEnum.values()[buf.readVarInt()];
        BlockPos firstPos = BlockPos.of(buf.readLong());
        BlockPos secondPos = BlockPos.of(buf.readLong());
        BlockPos thirdPos = buf.readBoolean() ? BlockPos.of(buf.readLong()) : null;
        ModeOptions.ActionEnum fill = ModeOptions.ActionEnum.values()[buf.readVarInt()];
        ModeOptions.ActionEnum cubeFill = ModeOptions.ActionEnum.values()[buf.readVarInt()];
        ModeOptions.ActionEnum raisedEdge = ModeOptions.ActionEnum.values()[buf.readVarInt()];
        ModeOptions.ActionEnum circleStart = ModeOptions.ActionEnum.values()[buf.readVarInt()];
        return new PlaceBuildModePacket(buildMode, firstPos, secondPos, thirdPos, fill, cubeFill, raisedEdge, circleStart);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
