package nl.requios.effortlessbuilding.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.item.RandomizerToolData;

import java.util.ArrayList;
import java.util.List;

/** Replaces the server-side palette stored on the randomizer in the player's main hand. */
public record UpdateRandomizerC2SPacket(List<Item> items) implements CustomPacketPayload {
    public static final Type<UpdateRandomizerC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "update_randomizer"));

    public static final StreamCodec<FriendlyByteBuf, UpdateRandomizerC2SPacket> STREAM_CODEC = StreamCodec.of(
            UpdateRandomizerC2SPacket::encode, UpdateRandomizerC2SPacket::decode);

    public UpdateRandomizerC2SPacket {
        items = List.copyOf(items);
    }

    private static void encode(FriendlyByteBuf buf, UpdateRandomizerC2SPacket packet) {
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            Item item = i < packet.items.size() ? packet.items.get(i) : net.minecraft.world.item.Items.AIR;
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(item));
        }
    }

    private static UpdateRandomizerC2SPacket decode(FriendlyByteBuf buf) {
        List<Item> items = new ArrayList<>(RandomizerToolData.SLOT_COUNT);
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            items.add(BuiltInRegistries.ITEM.get(buf.readResourceLocation()));
        }
        return new UpdateRandomizerC2SPacket(items);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
