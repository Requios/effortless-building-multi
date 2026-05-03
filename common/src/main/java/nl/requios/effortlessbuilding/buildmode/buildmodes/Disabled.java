package nl.requios.effortlessbuilding.buildmode.buildmodes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildmode.BaseBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "Normal" (disabled) build mode: produces exactly one block at the clicked position.
 * When modifiers are active, this single block is multiplied by them.
 * When no modifiers are active, the mod does not intercept and vanilla handles everything.
 */
public class Disabled extends BaseBuildMode {

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

	@Override
	public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos) {
		return List.of(firstPos);
	}
}
