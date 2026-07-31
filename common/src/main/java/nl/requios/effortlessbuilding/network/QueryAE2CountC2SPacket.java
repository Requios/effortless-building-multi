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
 * C2S packet: client asks the server how many of {@code item} are available
 * on the player's AE2 ME network. The server replies with {@link SyncAE2CountS2CPacket}.
 */
public record QueryAE2CountC2SPacket(Item item) implements CustomPacketPayload {

    public static final Type<QueryAE2CountC2SPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "query_ae2_count"));

    public static final StreamCodec<FriendlyByteBuf, QueryAE2CountC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(p.item)),
            buf -> {
                Item item = BuiltInRegistries.ITEM.get(buf.readIdentifier())
                        .map(reference -> reference.value())
                        .orElse(Items.AIR);
                return new QueryAE2CountC2SPacket(item);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
