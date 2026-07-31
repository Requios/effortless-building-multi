package nl.requios.effortlessbuilding.platform.services;

import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;
import nl.requios.effortlessbuilding.network.SyncServerConfigS2CPacket;
import nl.requios.effortlessbuilding.network.QueryAE2CountC2SPacket;
import nl.requios.effortlessbuilding.network.SyncAE2CountS2CPacket;
import nl.requios.effortlessbuilding.network.BuildModeHintC2SPacket;

public interface INetworkHelper {

    void sendToServer(PlaceBuildModePacket packet);

    void sendToServer(BreakBuildModePacket packet);

    void sendToServer(UndoPacket packet);

    void sendToServer(RedoPacket packet);

    void sendToServer(UpdateModifiersC2SPacket packet);

    void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet);

    void sendToServer(UpdateServerConfigC2SPacket packet);

    void sendToClient(ServerPlayer player, SyncServerConfigS2CPacket packet);

    void sendToServer(QueryAE2CountC2SPacket packet);

    void sendToServer(BuildModeHintC2SPacket packet);

    void sendToClient(ServerPlayer player, SyncAE2CountS2CPacket packet);

}
