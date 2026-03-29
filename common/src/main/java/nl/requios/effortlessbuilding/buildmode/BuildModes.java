package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.utilities.BlockSet;

public class BuildModes {

	// Client-side singleton — build modes are purely client-side during preview.
	// When placement is confirmed the client sends a PlaceBuildModePacket to the server.
	public static final BuildModes CLIENT = new BuildModes();

	// Placeholder constants until a power/permission system is wired up.
	public static final int BUILD_MODE_REACH = 32;
	public static final int MAX_BLOCKS_PER_AXIS = 20;

	private BuildModeEnum buildMode = BuildModeEnum.DISABLED;
	private BuildModeEnum previousBuildMode = BuildModeEnum.DISABLED;
	private BuildModeEnum beforeDisabledBuildMode = BuildModeEnum.SINGLE;

	// Called from MixinMinecraft — handles right-click with extended reach.
	// Client-side only.
	public static void handleRightClick(Minecraft mc) {
		BuildModeEnum mode = CLIENT.buildMode;
		Player player = mc.player;
		if (player == null || mc.level == null) return;

		BlockPos clickedPos;
		if (mode.instance.isFirstClick()) {
			// First click must hit a real block (extended reach).
			Vec3 start = player.getEyePosition();
			Vec3 end = start.add(player.getLookAngle().scale(BUILD_MODE_REACH));
			ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
			BlockHitResult hit = mc.level.clip(ctx);
			if (hit.getType() != HitResult.Type.BLOCK) return;
			clickedPos = hit.getBlockPos();
		} else {
			// Subsequent clicks may be in the air; use player block position as placeholder.
			// The mode's findCoordinates() will compute the real positions from look direction.
			clickedPos = player.blockPosition();
		}

		BlockSet blocks = new BlockSet();
		boolean shouldPlace = mode.instance.onClick(blocks, clickedPos, player);

		if (shouldPlace) {
			mode.instance.findCoordinates(blocks, player);

			if (blocks.firstPos != null && blocks.lastPos != null) {
				BlockPos intermediate = mode.instance.getIntermediatePos();
				BlockPos secondPos = intermediate != null ? intermediate : blocks.lastPos;
				BlockPos thirdPos  = intermediate != null ? blocks.lastPos : null;

				PacketHandler.sendToServer(new PlaceBuildModePacket(
						mode,
						blocks.firstPos,
						secondPos,
						thirdPos,
						ModeOptions.getFill(),
						ModeOptions.getCubeFill(),
						ModeOptions.getRaisedEdge(),
						ModeOptions.getCircleStart()
				));
			} else {
				Constants.LOG.warn("[EffortlessBuilding] Build mode {} produced no block positions", mode);
			}

			mode.instance.initialize();
		}
	}

	public void findCoordinates(BlockSet blocks, Player player) {
		buildMode.instance.findCoordinates(blocks, player);
	}

	public BuildModeEnum getBuildMode() {
		return buildMode;
	}

	public void setBuildMode(BuildModeEnum buildMode) {
		this.buildMode = buildMode;

		// TODO: send IsUsingBuildModePacket to server when packet system is ready

		Constants.LOG.info("[EffortlessBuilding] Build mode: {}", buildMode.getNameKey());
	}

	public void activatePreviousBuildMode() {
		var temp = buildMode;
		setBuildMode(previousBuildMode);
		previousBuildMode = temp;
	}

	public void activateDisableBuildModeToggle() {
		if (buildMode == BuildModeEnum.DISABLED) {
			setBuildMode(beforeDisabledBuildMode);
		} else {
			beforeDisabledBuildMode = buildMode;
			setBuildMode(BuildModeEnum.DISABLED);
		}
	}

	public void onCancel() {
		getBuildMode().instance.initialize();
	}

	// Find coordinates on a line bound by a plane.

	public static Vec3 findXBound(double x, Vec3 start, Vec3 look) {
		double y = (x - start.x) / look.x * look.y + start.y;
		double z = (x - start.x) / look.x * look.z + start.z;
		return new Vec3(x, y, z);
	}

	public static Vec3 findYBound(double y, Vec3 start, Vec3 look) {
		double x = (y - start.y) / look.y * look.x + start.x;
		double z = (y - start.y) / look.y * look.z + start.z;
		return new Vec3(x, y, z);
	}

	public static Vec3 findZBound(double z, Vec3 start, Vec3 look) {
		double x = (z - start.z) / look.z * look.x + start.x;
		double y = (z - start.z) / look.z * look.y + start.y;
		return new Vec3(x, y, z);
	}

	// Use this instead of player.getLookAngle() in any build-modes code.
	// Keeps components away from exactly 0 or ±1 to avoid division-by-zero in findXBound etc.
	public static Vec3 getPlayerLookVec(Player player) {
		Vec3 lookVec = player.getLookAngle();
		double x = lookVec.x;
		double y = lookVec.y;
		double z = lookVec.z;

		if (Math.abs(x) < 0.0001) x = 0.0001;
		if (Math.abs(x - 1.0) < 0.0001) x = 0.9999;
		if (Math.abs(x + 1.0) < 0.0001) x = -0.9999;

		if (Math.abs(y) < 0.0001) y = 0.0001;
		if (Math.abs(y - 1.0) < 0.0001) y = 0.9999;
		if (Math.abs(y + 1.0) < 0.0001) y = -0.9999;

		if (Math.abs(z) < 0.0001) z = 0.0001;
		if (Math.abs(z - 1.0) < 0.0001) z = 0.9999;
		if (Math.abs(z + 1.0) < 0.0001) z = -0.9999;

		return new Vec3(x, y, z);
	}

	public static boolean isCriteriaValid(Vec3 start, Vec3 look, int reach, Player player, boolean skipRaytrace, Vec3 lineBound, Vec3 planeBound, double distToPlayerSq) {
		boolean intersects = false;
		if (!skipRaytrace) {
			ClipContext rayTraceContext = new ClipContext(start, lineBound, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
			HitResult rayTraceResult = player.level().clip(rayTraceContext);
			intersects = rayTraceResult != null && rayTraceResult.getType() == HitResult.Type.BLOCK &&
				planeBound.subtract(rayTraceResult.getLocation()).lengthSqr() > 4;
		}

		return planeBound.subtract(start).dot(look) > 0 &&
			distToPlayerSq > 2 && distToPlayerSq < reach * reach &&
			!intersects;
	}
}
