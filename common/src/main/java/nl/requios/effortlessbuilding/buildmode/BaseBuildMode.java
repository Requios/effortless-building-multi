package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.utilities.BlockSet;

public abstract class BaseBuildMode implements IBuildMode {

	protected int clicks;

	@Override
	public void initialize() {
		clicks = 0;
	}

	@Override
	public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
		clicks++;
		return false;
	}

	@Override
	public void findCoordinates(BlockSet blocks, Player player) {
		// No-op for modes that don't need coordinate calculation (Disabled, Single).
	}
}
