package nl.requios.effortlessbuilding.platform;

import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.ForgeChannel;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.platform.services.INetworkHelper;

public class ForgeNetworkHelper implements INetworkHelper {

    @Override
    public void sendToServer(PlaceBuildModePacket packet) {
        ForgeChannel.sendToServer(packet);
    }

    @Override
    public void sendToServer(BreakBuildModePacket packet) {
        ForgeChannel.sendToServer(packet);
    }
}
