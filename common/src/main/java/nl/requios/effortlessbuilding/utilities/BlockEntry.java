package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntry {
    public final BlockPos blockPos;
    public boolean mirrorX;
    public boolean mirrorY;
    public boolean mirrorZ;
    public Rotation rotation = Rotation.NONE;
    public BlockState blockState;
    public Item item;
    private BlockStatus status = BlockStatus.VALID;

    public BlockEntry(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public BlockEntry(BlockPos blockPos, BlockState blockState, Item item) {
        this.blockPos = blockPos;
        this.blockState = blockState;
        this.item = item;
    }

    public void copyRotationSettingsFrom(BlockEntry blockEntry) {
        mirrorX = blockEntry.mirrorX;
        mirrorY = blockEntry.mirrorY;
        mirrorZ = blockEntry.mirrorZ;
        rotation = blockEntry.rotation;
    }
    
    /**
     * Applies the mirror and rotation transforms stored on this entry to the given
     * {@link BlockState}.  Used by the server placement handler and potentially by
     * client preview rendering.
     *
     * <p>Order: mirrorX → mirrorZ → mirrorY → rotation.
     */
    public BlockState applyTransforms(BlockState state) {
        // FRONT_BACK flips East↔West (X axis), LEFT_RIGHT flips North↔South (Z axis).
        if (mirrorX) state = state.mirror(Mirror.FRONT_BACK);
        if (mirrorZ) state = state.mirror(Mirror.LEFT_RIGHT);
        if (mirrorY) state = BlockUtilities.applyVerticalMirror(state);
        if (rotation != Rotation.NONE) state = state.rotate(rotation);
        return state;
    }

    /**
     * Marks this entry as rejected with the given reason.
     * Only the first rejection sticks (constraint stages run in order; first failure is the cause).
     */
    public void markRejected(BlockStatus reason) {
        if (status == BlockStatus.VALID) {
            this.status = reason;
        }
    }

    public BlockStatus getStatus() {
        return status;
    }

    public boolean isValid() {
        return status.isValid();
    }

    public void resetStatus() {
        this.status = BlockStatus.VALID;
    }
}
