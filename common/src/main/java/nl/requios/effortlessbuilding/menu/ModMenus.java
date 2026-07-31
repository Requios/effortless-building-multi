package nl.requios.effortlessbuilding.menu;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Shared menu instances; each loader registers these in its menu registry. */
public final class ModMenus {
    public static final MenuType<RandomizerMenu> RANDOMIZER =
            new MenuType<>(RandomizerMenu::new, FeatureFlags.VANILLA_SET);

    private ModMenus() {}
}
