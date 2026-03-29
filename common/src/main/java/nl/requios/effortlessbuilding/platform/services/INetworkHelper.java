package nl.requios.effortlessbuilding.platform.services;

import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;

public interface INetworkHelper {

    void sendToServer(PlaceBuildModePacket packet);

    void sendToServer(BreakBuildModePacket packet);
}
