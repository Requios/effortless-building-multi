package nl.requios.effortlessbuilding.platform.services;

import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;

public interface INetworkHelper {

    /**
     * Send a packet from the client to the server.
     * Must only be called from the client thread.
     */
    void sendToServer(PlaceBuildModePacket packet);
}
