package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class TwoClicksBuildMode extends BaseBuildMode {

	protected BlockEntry firstBlockEntry;

	@Override
	public void initialize() {
		super.initialize();
		firstBlockEntry = null;
	}

	@Override
	public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
		super.onClick(blocks, clickedPos, player);

		if (clicks == 1) {
			// First click — remember starting position.
			firstBlockEntry = new BlockEntry(clickedPos);
		} else {
			// Second click — signal placement. Do NOT reset clicks here;
			// findCoordinates still needs the valid state.
			// BuildModes.handlePlace() calls initialize() after extracting positions.
			return true;
		}
		return false;
	}

	@Override
	public void findCoordinates(BlockSet blocks, Player player) {
		if (clicks == 0) return;

		var firstPos = firstBlockEntry.blockPos;
		var secondPos = findSecondPos(player, firstBlockEntry.blockPos, true);
		if (secondPos == null) return;

		int axisLimit = ServerConfig.INSTANCE.getMaxBlocksPerAxis(player);

		int x1 = firstPos.getX(), x2 = secondPos.getX();
		int y1 = firstPos.getY(), y2 = secondPos.getY();
		int z1 = firstPos.getZ(), z2 = secondPos.getZ();

		if (x2 - x1 >= axisLimit) x2 = x1 + axisLimit - 1;
		if (x1 - x2 >= axisLimit) x2 = x1 - axisLimit + 1;
		if (y2 - y1 >= axisLimit) y2 = y1 + axisLimit - 1;
		if (y1 - y2 >= axisLimit) y2 = y1 - axisLimit + 1;
		if (z2 - z1 >= axisLimit) z2 = z1 + axisLimit - 1;
		if (z1 - z2 >= axisLimit) z2 = z1 - axisLimit + 1;

		blocks.clear();
		for (BlockPos pos : getAllBlocks(player, x1, y1, z1, x2, y2, z2)) {
			if (blocks.containsKey(pos)) continue;
			blocks.add(new BlockEntry(pos));
		}
		blocks.firstPos = firstPos;
		blocks.lastPos = secondPos;
	}

	@Override
	public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos) {
		int axisLimit = ServerConfig.INSTANCE.getMaxBlocksPerAxis(player);

		int x1 = firstPos.getX(), x2 = secondPos.getX();
		int y1 = firstPos.getY(), y2 = secondPos.getY();
		int z1 = firstPos.getZ(), z2 = secondPos.getZ();

		if (x2 - x1 >= axisLimit) x2 = x1 + axisLimit - 1;
		if (x1 - x2 >= axisLimit) x2 = x1 - axisLimit + 1;
		if (y2 - y1 >= axisLimit) y2 = y1 + axisLimit - 1;
		if (y1 - y2 >= axisLimit) y2 = y1 - axisLimit + 1;
		if (z2 - z1 >= axisLimit) z2 = z1 + axisLimit - 1;
		if (z1 - z2 >= axisLimit) z2 = z1 - axisLimit + 1;

		return getAllBlocks(player, x1, y1, z1, x2, y2, z2);
	}

	protected abstract BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace);

	protected abstract List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2);
}
