package nl.requios.effortlessbuilding.network;

import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import nl.requios.effortlessbuilding.Constants;

public class ForgeChannel {

    public static SimpleChannel INSTANCE;

    public static void init() {
        INSTANCE = ChannelBuilder
                .named(Constants.MOD_ID + ":main")
                .networkProtocolVersion(1)
                .simpleChannel();

        INSTANCE.messageBuilder(PlaceBuildModePacket.class)
                .encoder((msg, buf) -> PlaceBuildModePacket.STREAM_CODEC.encode(buf, msg))
                .decoder(buf -> PlaceBuildModePacket.STREAM_CODEC.decode(buf))
                .consumerNetworkThread((msg, ctx) -> {
                    ctx.enqueueWork(() -> PacketHandler.handlePlaceBuildMode(msg, ctx.getSender()));
                    ctx.setPacketHandled(true);
                })
                .add();
    }

    public static void sendToServer(PlaceBuildModePacket packet) {
        INSTANCE.send(packet, PacketDistributor.SERVER.noArg());
    }
}
