package nl.requios.effortlessbuilding.modifier;

import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildchain.IBuildSystem;
import nl.requios.effortlessbuilding.utilities.BlockSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@link IBuildSystem} that holds an ordered list of {@link IModifier}s.
 * Each enabled modifier is applied in sequence.
 *
 * <p>Register {@link #CLIENT} with {@link BuildChain#CLIENT} once during client init.
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

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildChain.BuildState action) {
        for (IModifier modifier : modifiers) {
            if (modifier.isEnabled()) {
                modifier.processBlocks(blocks, player, action);
            }
        }
    }
}
