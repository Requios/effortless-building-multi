package nl.requios.effortlessbuilding.buildpipeline;

import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.utilities.BlockSet;

/**
 * Server-side modifier stage that applies the per-player modifiers from
 * {@link ModifierServerStorage}. Registered in the server pipeline so
 * PacketHandler doesn't need to call it manually.
 */
public class ModifierSystemServer implements IBuildSystem {

    public static final ModifierSystemServer INSTANCE = new ModifierSystemServer();

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        ModifierServerStorage.getModifiers(player.getUUID())
                .processBlocks(blocks, player, action);
    }
}

