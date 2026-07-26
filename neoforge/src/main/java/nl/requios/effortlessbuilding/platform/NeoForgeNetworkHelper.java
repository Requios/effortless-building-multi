package nl.requios.effortlessbuilding.platform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
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
import nl.requios.effortlessbuilding.platform.services.INetworkHelper;

public class NeoForgeNetworkHelper implements INetworkHelper {

    @Override
    public void sendToServer(PlaceBuildModePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToServer(BreakBuildModePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToServer(UndoPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToServer(RedoPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToServer(UpdateModifiersC2SPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public void sendToServer(UpdateServerConfigC2SPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncServerConfigS2CPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public void sendToServer(QueryAE2CountC2SPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncAE2CountS2CPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

}
