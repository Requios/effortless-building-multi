package nl.requios.effortlessbuilding.modifier;

import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.IBuildSystem;
import nl.requios.effortlessbuilding.utilities.BlockSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@link IBuildSystem} that holds an ordered list of {@link IModifier}s.
 * Each enabled modifier is applied in sequence.
 *
 * <p>Register {@link #CLIENT} with {@link BuildPipeline#CLIENT} once during client init.
 */
public class ModifierSystem implements IBuildSystem {

    public static final ModifierSystem CLIENT = new ModifierSystem();

    private final List<IModifier> modifiers = new ArrayList<>();

    public List<IModifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    public void addModifier(IModifier modifier) {
        modifiers.add(modifier);
    }

    public void clearModifiers() {
        modifiers.clear();
    }

    public void removeModifier(int index) {
        if (index >= 0 && index < modifiers.size()) {
            modifiers.remove(index);
        }
    }

    /** Swaps the modifier at {@code index} with the one at {@code index + direction}. */
    public void moveModifier(int index, int direction) {
        int target = index + direction;
        if (index < 0 || index >= modifiers.size() || target < 0 || target >= modifiers.size()) return;
        Collections.swap(modifiers, index, target);
    }

    /**
     * Returns {@code true} if at least one modifier is enabled.
     * Used to determine whether DISABLED mode should intercept vanilla clicks.
     */
    public boolean hasActiveModifiers() {
        for (IModifier modifier : modifiers) {
            if (modifier.isEnabled()) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if at least one modifier is enabled and matches the player's dimension.
     */
    public boolean hasActiveModifiers(Player player) {
        for (IModifier modifier : modifiers) {
            if (modifier.isEnabled() && modifier.matchesDimension(player)) return true;
        }
        return false;
    }

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        for (IModifier modifier : modifiers) {
            if (modifier.isEnabled() && modifier.matchesDimension(player)) {
                modifier.processBlocks(blocks, player, action);
            }
        }
    }
}
