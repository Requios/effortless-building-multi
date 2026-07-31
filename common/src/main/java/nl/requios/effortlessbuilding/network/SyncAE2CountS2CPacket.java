package nl.requios.effortlessbuilding.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.requios.effortlessbuilding.Constants;

/**
 * S2C packet: server responds with how many of {@code item} the player has
 * on their AE2 ME network. Received by {@link nl.requios.effortlessbuilding.utilities.ItemUsageTracker}.
 */
public record SyncAE2CountS2CPacket(Item item, int count) implements CustomPacketPayload {

    public static final Type<SyncAE2CountS2CPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_ae2_count"));

    public static final StreamCodec<FriendlyByteBuf, SyncAE2CountS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(p.item));
                buf.writeVarInt(p.count);
            },
            buf -> {
                Item item = BuiltInRegistries.ITEM.get(buf.readIdentifier())
                        .map(reference -> reference.value())
                        .orElse(Items.AIR);
                int count = buf.readVarInt();
                return new SyncAE2CountS2CPacket(item, count);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
