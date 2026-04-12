package nl.requios.effortlessbuilding.platform;

import net.neoforged.neoforge.network.PacketDistributor;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
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
}
