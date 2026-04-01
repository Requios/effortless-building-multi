package nl.requios.effortlessbuilding.modifier;

import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.buildchain.IBuildSystem;

/**
 * A single transform stage that can be toggled on/off independently.
 * Multiple modifiers of any type can coexist in a {@link ModifierSystem}.
 */
public interface IModifier extends IBuildSystem {
    Component getDisplayName();
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
