package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.utilities.BlockSet;

public class BuildModes {

	// Client-side singleton — build modes are purely client-side during preview.
    // When placement is confirmed the client sends a packet to the server.
    public static final BuildModes CLIENT = new BuildModes();

    private BuildModeEnum buildMode = BuildModeEnum.DISABLED;
    private BuildModeEnum previousBuildMode = BuildModeEnum.DISABLED;
    private BuildModeEnum beforeDisabledBuildMode = BuildModeEnum.SINGLE;

	public void findCoordinates(BlockSet blocks, Player player) {
        buildMode.instance.findCoordinates(blocks, player);
    }

    public BuildModeEnum getBuildMode() {
        return buildMode;
    }

    public void setBuildMode(BuildModeEnum buildMode) {
        this.buildMode = buildMode;
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

    // -------------------------------------------------------------------------
    // Math utilities — used by IBuildMode implementations for plane-line intersection
    // -------------------------------------------------------------------------

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

	public static boolean isCriteriaValid(Vec3 start, Vec3 look, int reach, Player player, boolean skipRaytrace, Vec3 lineBound, Vec3 planeBound, double distToPlayerSq) {
        boolean intersects = false;
        if (!skipRaytrace) {
            ClipContext rayTraceContext = new ClipContext(start, lineBound, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
            BlockHitResult rayTraceResult = player.level().clip(rayTraceContext);
            intersects = rayTraceResult != null && rayTraceResult.getType() == HitResult.Type.BLOCK &&
                planeBound.subtract(rayTraceResult.getLocation()).lengthSqr() > 4;
        }

        return planeBound.subtract(start).dot(look) > 0 &&
            distToPlayerSq > 2 && distToPlayerSq < reach * reach &&
            !intersects;
    }
}
