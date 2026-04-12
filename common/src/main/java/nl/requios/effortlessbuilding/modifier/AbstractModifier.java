package nl.requios.effortlessbuilding.modifier;

import net.minecraft.network.chat.Component;

/**
 * Base class for all modifier types. Holds the shared {@code enabled} and
 * {@code dimension} fields so concrete subclasses only need to provide
 * {@link #getDisplayName()} and {@link #processBlocks}.
 */
public abstract class AbstractModifier implements IModifier {

    private boolean enabled = true;
    private String dimension = "";

    @Override
    public abstract Component getDisplayName();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getDimension() {
        return dimension;
    }

    @Override
    public void setDimension(String dimension) {
        this.dimension = dimension != null ? dimension : "";
    }
}

