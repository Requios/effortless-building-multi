package nl.requios.effortlessbuilding.platform.services;

import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;

public interface INetworkHelper {

    void sendToServer(PlaceBuildModePacket packet);

    void sendToServer(BreakBuildModePacket packet);

    void sendToServer(UndoPacket packet);

    void sendToServer(RedoPacket packet);

    void sendToServer(UpdateModifiersC2SPacket packet);

    void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet);
}
