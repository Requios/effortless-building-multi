package nl.requios.effortlessbuilding.buildmode.buildmodes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildmode.BaseBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;

public class Single extends BaseBuildMode {

	private BlockPos pos;

	@Override
	public void initialize() {
		super.initialize();
		pos = null;
	}

	@Override
	public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
		pos = clickedPos;
		return true;
	}

	@Override
	public void findCoordinates(BlockSet blocks, Player player) {
		if (pos != null) {
			blocks.setStartPos(new BlockEntry(pos));
		}
	}
}
