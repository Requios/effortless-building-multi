package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ThreeClicksBuildMode extends BaseBuildMode {

	protected BlockEntry firstBlockEntry;
	protected BlockEntry secondBlockEntry;

	@Override
	public void initialize() {
		super.initialize();
		firstBlockEntry = null;
		secondBlockEntry = null;
	}

	@Override
	public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
		super.onClick(blocks, clickedPos, player);

		if (clicks == 1) {
			// First click — remember starting position.
			firstBlockEntry = new BlockEntry(clickedPos);

		} else if (clicks == 2) {
			// Second click — find second position.
			var secondPos = findSecondPos(player, firstBlockEntry.blockPos, true);
			if (secondPos == null) {
				clicks = 1;
				return false;
			}
			secondBlockEntry = new BlockEntry(secondPos);

		} else {
			// Third click — signal placement. Do NOT reset clicks here;
			// findCoordinates still needs the valid state.
			// BuildModes.handlePlace() calls initialize() after extracting positions.
			return true;
		}
		return false;
	}

	@Override
	public void findCoordinates(BlockSet blocks, Player player) {
		if (clicks == 0) return;

		int axisLimit = BuildModes.MAX_BLOCKS_PER_AXIS;

		if (clicks == 1) {
			var firstPos = firstBlockEntry.blockPos;
			var secondPos = findSecondPos(player, firstBlockEntry.blockPos, true);
			if (secondPos == null) return;

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
			for (BlockPos pos : getIntermediateBlocks(player, x1, y1, z1, x2, y2, z2)) {
				if (blocks.containsKey(pos)) continue;
				blocks.add(new BlockEntry(pos));
			}
			blocks.firstPos = firstPos;
			blocks.lastPos = secondPos;
		} else {
			// clicks >= 2: compute final blocks using firstBlockEntry, secondBlockEntry, and findThirdPos.
			BlockPos firstPos = firstBlockEntry.blockPos;
			BlockPos secondPos = secondBlockEntry.blockPos;
			BlockPos thirdPos = findThirdPos(player, firstPos, secondPos, true);
			if (thirdPos == null) return;

			int x1 = firstPos.getX(), x2 = secondPos.getX(), x3 = thirdPos.getX();
			int y1 = firstPos.getY(), y2 = secondPos.getY(), y3 = thirdPos.getY();
			int z1 = firstPos.getZ(), z2 = secondPos.getZ(), z3 = thirdPos.getZ();

			if (x2 - x1 >= axisLimit) x2 = x1 + axisLimit - 1;
			if (x1 - x2 >= axisLimit) x2 = x1 - axisLimit + 1;
			if (y2 - y1 >= axisLimit) y2 = y1 + axisLimit - 1;
			if (y1 - y2 >= axisLimit) y2 = y1 - axisLimit + 1;
			if (z2 - z1 >= axisLimit) z2 = z1 + axisLimit - 1;
			if (z1 - z2 >= axisLimit) z2 = z1 - axisLimit + 1;

			if (x3 - x1 >= axisLimit) x3 = x1 + axisLimit - 1;
			if (x1 - x3 >= axisLimit) x3 = x1 - axisLimit + 1;
			if (y3 - y1 >= axisLimit) y3 = y1 + axisLimit - 1;
			if (y1 - y3 >= axisLimit) y3 = y1 - axisLimit + 1;
			if (z3 - z1 >= axisLimit) z3 = z1 + axisLimit - 1;
			if (z1 - z3 >= axisLimit) z3 = z1 - axisLimit + 1;

			blocks.clear();
			for (BlockPos pos : getFinalBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3)) {
				if (blocks.containsKey(pos)) continue;
				blocks.add(new BlockEntry(pos));
			}
			blocks.firstPos = firstPos;
			blocks.lastPos = thirdPos;
		}
	}

	@Override
	public @Nullable BlockPos getIntermediatePos() {
		return secondBlockEntry != null ? secondBlockEntry.blockPos : null;
	}

	@Override
	public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos) {
		if (thirdPos == null) return List.of();

		int axisLimit = BuildModes.MAX_BLOCKS_PER_AXIS;

		int x1 = firstPos.getX(), x2 = secondPos.getX(), x3 = thirdPos.getX();
		int y1 = firstPos.getY(), y2 = secondPos.getY(), y3 = thirdPos.getY();
		int z1 = firstPos.getZ(), z2 = secondPos.getZ(), z3 = thirdPos.getZ();

		if (x2 - x1 >= axisLimit) x2 = x1 + axisLimit - 1;
		if (x1 - x2 >= axisLimit) x2 = x1 - axisLimit + 1;
		if (y2 - y1 >= axisLimit) y2 = y1 + axisLimit - 1;
		if (y1 - y2 >= axisLimit) y2 = y1 - axisLimit + 1;
		if (z2 - z1 >= axisLimit) z2 = z1 + axisLimit - 1;
		if (z1 - z2 >= axisLimit) z2 = z1 - axisLimit + 1;

		if (x3 - x1 >= axisLimit) x3 = x1 + axisLimit - 1;
		if (x1 - x3 >= axisLimit) x3 = x1 - axisLimit + 1;
		if (y3 - y1 >= axisLimit) y3 = y1 + axisLimit - 1;
		if (y1 - y3 >= axisLimit) y3 = y1 - axisLimit + 1;
		if (z3 - z1 >= axisLimit) z3 = z1 + axisLimit - 1;
		if (z1 - z3 >= axisLimit) z3 = z1 - axisLimit + 1;

		return getFinalBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
	}

	public static BlockPos findHeight(Player player, BlockPos secondPos, boolean skipRaytrace) {
		Vec3 look = BuildChain.getPlayerLookVec(player);
		Vec3 start = new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());

		List<HeightCriteria> criteriaList = new ArrayList<>(2);

		Vec3 xBound = BuildModes.findXBound(secondPos.getX(), start, look);
		criteriaList.add(new HeightCriteria(xBound, secondPos, start));

		Vec3 zBound = BuildModes.findZBound(secondPos.getZ(), start, look);
		criteriaList.add(new HeightCriteria(zBound, secondPos, start));

		int reach = BuildModes.BUILD_MODE_REACH;
		criteriaList.removeIf(criteria -> !criteria.isValid(start, look, reach, player, skipRaytrace));

		if (criteriaList.isEmpty()) return null;

		HeightCriteria selected = criteriaList.get(0);

		if (criteriaList.size() > 1) {
			for (int i = 1; i < criteriaList.size(); i++) {
				HeightCriteria criteria = criteriaList.get(i);
				if (criteria.distToLineSq < 2.0 && selected.distToLineSq < 2.0) {
					if (criteria.distToPlayerSq < selected.distToPlayerSq)
						selected = criteria;
				} else {
					if (criteria.distToLineSq < selected.distToLineSq)
						selected = criteria;
				}
			}
		}
		return BlockPos.containing(selected.lineBound);
	}

	protected abstract BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace);

	protected abstract BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace);

	protected abstract List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2);

	protected abstract List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3);

	static class HeightCriteria {
		Vec3 planeBound;
		Vec3 lineBound;
		double distToLineSq;
		double distToPlayerSq;

		HeightCriteria(Vec3 planeBound, BlockPos secondPos, Vec3 start) {
			this.planeBound = planeBound;
			this.lineBound = toLongestLine(this.planeBound, secondPos);
			this.distToLineSq = this.lineBound.subtract(this.planeBound).lengthSqr();
			this.distToPlayerSq = this.planeBound.subtract(start).lengthSqr();
		}

		private Vec3 toLongestLine(Vec3 boundVec, BlockPos secondPos) {
			BlockPos bound = BlockPos.containing(boundVec);
			return new Vec3(secondPos.getX(), bound.getY(), secondPos.getZ());
		}

		public boolean isValid(Vec3 start, Vec3 look, int reach, Player player, boolean skipRaytrace) {
			return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, lineBound, planeBound, distToPlayerSq);
		}
	}
}
