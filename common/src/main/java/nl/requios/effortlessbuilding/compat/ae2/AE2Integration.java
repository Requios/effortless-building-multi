package nl.requios.effortlessbuilding.compat.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.platform.Services;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Safe bridge into Applied Energistics 2 (AE2) for pulling building materials
 * from the player's ME network.
 *
 * <p>All public methods guard against AE2 being absent — call {@link #isAvailable()}
 * first, or just call the methods directly (they return 0 / no-op when AE2 is missing).
 *
 * <p>The actual AE2 class references live in the private {@link Internal} class so
 * that {@code NoClassDefFoundError} is impossible unless AE2 is actually installed.
 */
public class AE2Integration {

    private static final String AE2_MOD_ID = "ae2";
    private static boolean initialized = false;
    private static boolean available = false;
    @Nullable private static Internal bridge = null;

    /**
     * Call once during common setup (e.g. constructor of the mod entry point).
     * Safe to call multiple times.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!Services.PLATFORM.isModLoaded(AE2_MOD_ID)) {
            Constants.LOG.info("[AE2] Applied Energistics 2 not detected — AE2 integration disabled.");
            return;
        }

        try {
            bridge = new Internal();
            available = true;
            Constants.LOG.info("[AE2] Applied Energistics 2 detected — integration enabled.");
        } catch (Throwable t) {
            Constants.LOG.warn("[AE2] Failed to initialize AE2 integration", t);
            available = false;
        }
    }

    /** Returns {@code true} if AE2 is loaded and the integration is usable. */
    public static boolean isAvailable() {
        return available;
    }

    // -------------------------------------------------------------------------
    // Public API — all safe to call even when AE2 is absent
    // -------------------------------------------------------------------------

    /**
     * Counts how many of {@code item} are present on the ME network accessible
     * via the player's wireless terminal.
     *
     * @return item count, or 0 if AE2 is not available or no terminal is found.
     */
    public static int countOnNetwork(Player player, Item item) {
        if (!available || bridge == null) return 0;
        try {
            return bridge.countItems(player, item);
        } catch (Exception e) {
            Constants.LOG.warn("[AE2] countOnNetwork failed", e);
            return 0;
        }
    }

    /**
     * Extracts up to {@code count} of {@code item} from the player's ME network.
     * The items are removed from the digital storage — no physical ItemStack is
     * created (caller is placing blocks, not receiving items).
     *
     * @return number of items actually extracted.
     */
    public static int extractFromNetwork(Player player, Item item, int count) {
        if (!available || bridge == null || count <= 0) return 0;
        try {
            return bridge.extractItems(player, item, count);
        } catch (Exception e) {
            Constants.LOG.warn("[AE2] extractFromNetwork failed", e);
            return 0;
        }
    }

    /**
     * Refills the player's main-hand stack from the ME network up to its max stack size.
     * Items are physically added to the held stack.
     *
     * @return number of items restocked.
     */
    public static int restockMainHand(Player player) {
        if (!available || bridge == null) return 0;
        try {
            return bridge.restockMainHand(player);
        } catch (Exception e) {
            Constants.LOG.warn("[AE2] restockMainHand failed", e);
            return 0;
        }
    }

    /**
     * Returns a human-readable status string about the AE2 connection for display
     * in the radial menu. Safe to call from the client.
     *
     * @return one of: "AE2 ✓" (connected), "AE2 (no terminal)" (mod loaded but no terminal found),
     *         or empty string if AE2 is not installed.
     */
    public static String getStatusString(Player player) {
        if (!available || bridge == null) return "";
        try {
            if (bridge.playerHasWirelessTerminal(player)) {
                return "AE2 \u2713"; // ✓ checkmark
            }
            return "AE2 (no terminal)";
        } catch (Exception e) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Internal — AE2 classes loaded ONLY when AE2 is present
    // -------------------------------------------------------------------------

    /**
     * This class is loaded lazily by the JVM only when {@link #init()} succeeds.
     * If AE2 is not installed, this class is never loaded and therefore no
     * {@link NoClassDefFoundError} is thrown.
     */
    private static class Internal {

        private final appeng.items.tools.powered.WirelessTerminalItem terminalMarker;

        Internal() {
            // Just to prove this class loads — store a reference to any AE2 class.
            terminalMarker = null;
        }

        // ---- helpers --------------------------------------------------------

        /** Scans the player's inventory for a linked, in-range wireless terminal. */
        @Nullable
        private appeng.api.networking.IGrid findGrid(Player player) {
            // 1. Scan vanilla inventory
            appeng.api.networking.IGrid grid = scanVanillaInventory(player);
            if (grid != null) return grid;

            // 2. Scan Curios slots (only if Curios mod is installed — cached check)
            if (curiosAvailable) {
                grid = scanCuriosSlotsCached(player);
            }
            return grid;
        }

        /** Scans the player's vanilla inventory slots for a wireless terminal. */
        @Nullable
        private appeng.api.networking.IGrid scanVanillaInventory(Player player) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                appeng.api.networking.IGrid grid = gridFromStack(player, stack);
                if (grid != null) return grid;
            }
            return null;
        }

        /** If the stack is a linked wireless terminal, returns its grid, else null. */
        @Nullable
        private appeng.api.networking.IGrid gridFromStack(Player player, ItemStack stack) {
            if (stack.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem terminal) {
                return terminal.getLinkedGrid(stack, player.level(), null);
            }
            return null;
        }

        /**
         * Scans Curios slots via cached reflection handles — called only when
         * {@link #curiosAvailable} is true.
         */
        @Nullable
        private appeng.api.networking.IGrid scanCuriosSlotsCached(Player player) {
            try {
                Object optional = curiosGetInventory.invoke(null, player);
                if (optional instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object handler = opt.get();
                    Method findFirst = findCurioMethod(handler);
                    if (findFirst == null) return null;

                    Predicate<ItemStack> isTerminal = stack -> stack.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem;
                    Object resultOpt = findFirst.invoke(handler, isTerminal);

                    if (resultOpt instanceof java.util.Optional<?> slotOpt && slotOpt.isPresent()) {
                        Object slotResult = slotOpt.get();
                        Method stackM = stackMethod(slotResult);
                        if (stackM == null) return null;
                        ItemStack stack = (ItemStack) stackM.invoke(slotResult);
                        return gridFromStack(player, stack);
                    }
                }
            } catch (Throwable t) {
                curiosAvailable = false;
            }
            return null;
        }

        /**
         * Checks all Curios slots for a wireless terminal via cached reflection.
         * Returns true only if a terminal item is found (regardless of link status).
         */
        private boolean curiosHasTerminal(Player player) {
            if (!curiosAvailable) return false;
            try {
                Object optional = curiosGetInventory.invoke(null, player);
                if (optional instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object handler = opt.get();
                    Method findFirst = findCurioMethod(handler);
                    if (findFirst == null) return false;

                    Predicate<ItemStack> isTerminal = stack -> stack.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem;
                    Object resultOpt = findFirst.invoke(handler, isTerminal);
                    return resultOpt instanceof java.util.Optional<?> slotOpt && slotOpt.isPresent();
                }
            } catch (Throwable t) {
                curiosAvailable = false;
            }
            return false;
        }

        /** Checks vanilla inventory + Curios slots for any wireless terminal item. */
        private boolean playerHasWirelessTerminal(Player player) {
            // 1. Check vanilla inventory
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem) {
                    return true;
                }
            }
            // 2. Check Curios slots (cached reflection)
            return curiosHasTerminal(player);
        }

        // ---- Curios caching ------------------------------------------------

        // Once resolved, these are the Method handles for the Curios API.
        // If Curios isn't installed, curiosAvailable stays false and we never retry.
        private static boolean curiosInitialized = false;
        private static boolean curiosAvailable = false;
        @Nullable private static Method curiosGetInventory;
        @Nullable private static Method curiosFindFirstCurio;
        @Nullable private static Method curiosStackMethod;

        /** Tries to resolve Curios reflection handles once. Called from constructor. */
        private static synchronized void tryInitCurios() {
            if (curiosInitialized) return;
            curiosInitialized = true;
            try {
                Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                curiosGetInventory = curiosApiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                curiosAvailable = true;
            } catch (Throwable t) {
                curiosAvailable = false;
            }
        }

        /** Resolves the findFirstCurio method from the handler's actual class. */
        @Nullable
        private Method findCurioMethod(Object handler) {
            if (curiosFindFirstCurio != null) return curiosFindFirstCurio;
            try {
                curiosFindFirstCurio = handler.getClass().getMethod("findFirstCurio", Predicate.class);
            } catch (NoSuchMethodException e) {
                curiosAvailable = false;
            }
            return curiosFindFirstCurio;
        }

        /** Resolves the stack() method from a SlotResult implementation. */
        @Nullable
        private Method stackMethod(Object slotResult) {
            if (curiosStackMethod != null) return curiosStackMethod;
            try {
                curiosStackMethod = slotResult.getClass().getMethod("stack");
            } catch (NoSuchMethodException e) {
                // should never happen
            }
            return curiosStackMethod;
        }

        /** Gets the ME storage interface from a grid. */
        private appeng.api.storage.MEStorage getStorage(appeng.api.networking.IGrid grid) {
            return grid.getStorageService().getInventory();
        }

        /** Creates an AE2 action source representing the player. */
        private appeng.api.networking.security.IActionSource playerSource(Player player) {
            return new appeng.me.helpers.PlayerSource(player);
        }

        // ---- counting -------------------------------------------------------

        int countItems(Player player, Item item) {
            appeng.api.networking.IGrid grid = findGrid(player);
            if (grid == null) return 0;

            appeng.api.storage.MEStorage storage = getStorage(grid);
            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(item);

            return (int) storage.extract(key, Long.MAX_VALUE,
                    appeng.api.config.Actionable.SIMULATE, playerSource(player));
        }

        // ---- extraction (for building — items are consumed, not spawned) ----

        int extractItems(Player player, Item item, int count) {
            appeng.api.networking.IGrid grid = findGrid(player);
            if (grid == null) return 0;

            appeng.api.storage.MEStorage storage = getStorage(grid);
            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(item);

            return (int) storage.extract(key, count,
                    appeng.api.config.Actionable.MODULATE, playerSource(player));
        }

        // ---- restock --------------------------------------------------------

        int restockMainHand(Player player) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) return 0;

            int maxStack = held.getMaxStackSize();
            int currentCount = held.getCount();
            int needed = maxStack - currentCount;
            if (needed <= 0) return 0;

            appeng.api.networking.IGrid grid = findGrid(player);
            if (grid == null) return 0;

            appeng.api.storage.MEStorage storage = getStorage(grid);
            appeng.api.stacks.AEItemKey key = appeng.api.stacks.AEItemKey.of(held);

            // Extract from network
            long extracted = storage.extract(key, needed,
                    appeng.api.config.Actionable.MODULATE, playerSource(player));

            if (extracted > 0) {
                // Give the extracted items to the player
                int remaining = (int) extracted;
                while (remaining > 0) {
                    int batch = Math.min(remaining, maxStack);
                    ItemStack extractedStack = key.toStack(batch);
                    if (!player.getInventory().add(extractedStack)) {
                        // Inventory full — drop at feet
                        player.drop(extractedStack, false);
                    }
                    remaining -= batch;
                }
            }

            return (int) extracted;
        }
    }
}
