package nl.requios.effortlessbuilding.modifier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildpipeline.IBuildSystem;

/**
 * A single transform stage that can be toggled on/off independently.
 * Multiple modifiers of any type can coexist in a {@link ModifierSystem}.
 *
 * <p>Each modifier is tagged with a dimension string (e.g. {@code "minecraft:overworld"}).
 * Only modifiers matching the player's current dimension are applied during
 * {@link ModifierSystem#processBlocks}. An empty dimension means "all dimensions".
 */
public interface IModifier extends IBuildSystem {
    Component getDisplayName();
    boolean isEnabled();
    void setEnabled(boolean enabled);

    /** Dimension this modifier belongs to, e.g. {@code "minecraft:overworld"}. Empty = all dimensions. */
    String getDimension();
    void setDimension(String dimension);

    /** Returns {@code true} if this modifier should apply in the given player's dimension. */
    default boolean matchesDimension(Player player) {
        String dim = getDimension();
        if (dim == null || dim.isEmpty()) return true; // global
        return dim.equals(player.level().dimension().location().toString());
    }
}
