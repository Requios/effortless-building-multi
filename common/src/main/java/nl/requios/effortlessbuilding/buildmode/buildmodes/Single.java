package nl.requios.effortlessbuilding.buildmode.buildmodes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildmode.BaseBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockSet;

public class Single extends BaseBuildMode {

	@Override
	public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
		return true;
	}
}
