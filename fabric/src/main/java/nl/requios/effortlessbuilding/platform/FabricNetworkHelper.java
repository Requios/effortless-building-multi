package nl.requios.effortlessbuilding.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
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
}
