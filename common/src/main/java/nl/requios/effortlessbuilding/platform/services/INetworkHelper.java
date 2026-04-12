package nl.requios.effortlessbuilding.platform.services;

import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;

public interface INetworkHelper {

    void sendToServer(PlaceBuildModePacket packet);

    void sendToServer(BreakBuildModePacket packet);

    void sendToServer(UndoPacket packet);

    void sendToServer(RedoPacket packet);
}
