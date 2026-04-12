package nl.requios.effortlessbuilding.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.platform.services.INetworkHelper;

public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public void sendToServer(PlaceBuildModePacket packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public void sendToServer(BreakBuildModePacket packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public void sendToServer(UndoPacket packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public void sendToServer(RedoPacket packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public void sendToServer(UpdateModifiersC2SPacket packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }
}
