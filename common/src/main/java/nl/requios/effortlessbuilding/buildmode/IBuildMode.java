package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IBuildMode {

	// Reset values here, start over.
	void initialize();

	// Returns true if we should place blocks now.
	// clickedPos is the block the player clicked; player is the local player (client-side only).
	boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player);

	// Populates blocks with the current preview/placement positions.
	// player is the local player (client-side only); must NOT be called after initialize().
	void findCoordinates(BlockSet blocks, Player player);

	// Server-side: recalculate and return the blocks to place from already-resolved positions.
	// firstPos/secondPos are always required; thirdPos is null for two-click modes.
	default List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos) {
		return List.of();
	}

	// Returns the intermediate (second) position stored after the second click of a three-click mode.
	// Returns null for all other modes.
	default @Nullable BlockPos getIntermediatePos() {
		return null;
	}
}
