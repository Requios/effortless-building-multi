package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import nl.requios.effortlessbuilding.modifier.ModifierPersistence;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.modifier.ArrayModifier;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.MirrorModifier;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.modifier.RadialMirrorModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public class ModifiersScreen extends Screen {

    // ---- layout ----
    private static final int PANEL_W  = 390;
    private static final int PANEL_H  = 246;
    /** Width of the left modifier-list column. */
    private static final int LIST_W   = 175;
    /** X-offset of the vertical divider from the panel's left edge. */
    private static final int DIV_OX   = LIST_W + 8;
    /** X-offset where settings content begins (from panel left edge). */
    private static final int SET_OX   = DIV_OX + 6;
    private static final int ROW_H    = 22;
    /** Horizontal space reserved for label text before the – / field / + cluster. */
    private static final int LABEL_W  = 62;
    /** EditBox width (between the – and + buttons). */
    private static final int EDIT_W   = 60;
    private static final int FIELD_H  = 16;
    /** Vertical step between successive settings rows. */
    private static final int ROW_GAP  = 22;

    // Tracks which modifier row is selected for the settings panel.
    private int selectedIndex = -1;

    /**
     * Each int field row holds the EditBox and its value-setter so that
     * {@link #mouseScrolled} can increment/decrement without re-building widgets.
     */
    private record IntFieldEntry(EditBox field, IntConsumer setter) {}
    private final List<IntFieldEntry> intFields = new ArrayList<>();

    public ModifiersScreen() {
        super(Component.literal("Modifiers"));
    }

    // =========================================================================
    // Widget construction
    // =========================================================================

    @Override
    protected void init() {
        intFields.clear();

        int px = panelX(), py = panelY();
        buildListWidgets(px, py);
        buildSettingsWidgets(px, py);

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(px + PANEL_W - 74, py + PANEL_H - 22, 70, 16)
                .build());
    }

    // ---- left: modifier list ----

    private void buildListWidgets(int px, int py) {
        List<IModifier> modifiers = ModifierSystem.CLIENT.getModifiers();
        int lx = px + 4;
        int rowBase = py + 24;
        int count = modifiers.size();

        for (int i = 0; i < count; i++) {
            final int idx = i;
            IModifier modifier = modifiers.get(i);
            int ry = rowBase + i * ROW_H;

            // Enable/disable toggle
            addRenderableWidget(Button.builder(
                            Component.literal(modifier.isEnabled() ? "ON" : "OFF"),
                            btn -> { modifier.setEnabled(!modifier.isEnabled()); rebuildWidgets(); })
                    .bounds(lx, ry + 4, 28, 14)
                    .build());

            // ↑ / ↓ reorder buttons
            Button upBtn = addRenderableWidget(Button.builder(
                            Component.literal("↑"),
                            btn -> { ModifierSystem.CLIENT.moveModifier(idx, -1); selectedIndex = idx - 1; rebuildWidgets(); })
                    .bounds(lx + LIST_W - 44, ry + 4, 12, 14)
                    .build());
            if (idx == 0) upBtn.active = false;

            Button downBtn = addRenderableWidget(Button.builder(
                            Component.literal("↓"),
                            btn -> { ModifierSystem.CLIENT.moveModifier(idx, +1); selectedIndex = idx + 1; rebuildWidgets(); })
                    .bounds(lx + LIST_W - 30, ry + 4, 12, 14)
                    .build());
            if (idx == count - 1) downBtn.active = false;

            // Remove button
            addRenderableWidget(Button.builder(
                            Component.literal("X"),
                            btn -> {
                                ModifierSystem.CLIENT.removeModifier(idx);
                                int size = ModifierSystem.CLIENT.getModifiers().size();
                                if (selectedIndex >= size) selectedIndex = size - 1;
                                rebuildWidgets();
                            })
                    .bounds(lx + LIST_W - 16, ry + 4, 14, 14)
                    .build());
        }

        // Add buttons — bottom of list panel
        int addY = py + PANEL_H - 22;
        addRenderableWidget(Button.builder(Component.literal("+ Mirror"), btn -> {
            ModifierSystem.CLIENT.addModifier(new MirrorModifier());
            selectedIndex = ModifierSystem.CLIENT.getModifiers().size() - 1;
            rebuildWidgets();
        }).bounds(px + 4, addY, 58, 16).build());

        addRenderableWidget(Button.builder(Component.literal("+ Array"), btn -> {
            ModifierSystem.CLIENT.addModifier(new ArrayModifier());
            selectedIndex = ModifierSystem.CLIENT.getModifiers().size() - 1;
            rebuildWidgets();
        }).bounds(px + 66, addY, 52, 16).build());

        addRenderableWidget(Button.builder(Component.literal("+ Radial"), btn -> {
            ModifierSystem.CLIENT.addModifier(new RadialMirrorModifier());
            selectedIndex = ModifierSystem.CLIENT.getModifiers().size() - 1;
            rebuildWidgets();
        }).bounds(px + 122, addY, 56, 16).build());
    }

    // ---- right: settings for selected modifier ----

    private void buildSettingsWidgets(int px, int py) {
        List<IModifier> modifiers = ModifierSystem.CLIENT.getModifiers();
        if (selectedIndex < 0 || selectedIndex >= modifiers.size()) return;

        IModifier modifier = modifiers.get(selectedIndex);
        int sx = px + SET_OX;
        int sy = py + 24;

        if (modifier instanceof MirrorModifier mirror) {
            // Axis toggles
            addRenderableWidget(Button.builder(Component.literal("X: " + onOff(mirror.mirrorX)),
                            btn -> { mirror.mirrorX = !mirror.mirrorX; rebuildWidgets(); })
                    .bounds(sx, sy, 54, 14).build());
            addRenderableWidget(Button.builder(Component.literal("Y: " + onOff(mirror.mirrorY)),
                            btn -> { mirror.mirrorY = !mirror.mirrorY; rebuildWidgets(); })
                    .bounds(sx + 58, sy, 54, 14).build());
            addRenderableWidget(Button.builder(Component.literal("Z: " + onOff(mirror.mirrorZ)),
                            btn -> { mirror.mirrorZ = !mirror.mirrorZ; rebuildWidgets(); })
                    .bounds(sx + 116, sy, 54, 14).build());
            // Origin fields
            addIntField(sx, sy + ROW_GAP,     String.valueOf(mirror.originX), v -> mirror.originX = v);
            addIntField(sx, sy + ROW_GAP * 2, String.valueOf(mirror.originY), v -> mirror.originY = v);
            addIntField(sx, sy + ROW_GAP * 3, String.valueOf(mirror.originZ), v -> mirror.originZ = v);
            // Set-to-player button
            addRenderableWidget(Button.builder(Component.literal("Set origin to player pos"),
                            btn -> {
                                var pos = playerPos();
                                mirror.originX = pos.getX(); mirror.originY = pos.getY(); mirror.originZ = pos.getZ();
                                rebuildWidgets();
                            })
                    .bounds(sx, sy + ROW_GAP * 4, 165, 14).build());

        } else if (modifier instanceof ArrayModifier array) {
            addIntField(sx, sy,               String.valueOf(array.count),   v -> array.count   = Math.max(0, v));
            addIntField(sx, sy + ROW_GAP,     String.valueOf(array.offsetX), v -> array.offsetX = v);
            addIntField(sx, sy + ROW_GAP * 2, String.valueOf(array.offsetY), v -> array.offsetY = v);
            addIntField(sx, sy + ROW_GAP * 3, String.valueOf(array.offsetZ), v -> array.offsetZ = v);

        } else if (modifier instanceof RadialMirrorModifier radial) {
            addIntField(sx, sy, String.valueOf(radial.slices), v -> radial.slices = Math.max(2, v));
            addRenderableWidget(Button.builder(Component.literal("Mirror slices: " + onOff(radial.mirrorSlices)),
                            btn -> { radial.mirrorSlices = !radial.mirrorSlices; rebuildWidgets(); })
                    .bounds(sx, sy + ROW_GAP, 152, 14).build());
            addIntField(sx, sy + ROW_GAP * 2, String.valueOf(radial.originX), v -> radial.originX = v);
            addIntField(sx, sy + ROW_GAP * 3, String.valueOf(radial.originZ), v -> radial.originZ = v);
            // Set-to-player button
            addRenderableWidget(Button.builder(Component.literal("Set origin to player pos"),
                            btn -> {
                                var pos = playerPos();
                                radial.originX = pos.getX(); radial.originZ = pos.getZ();
                                rebuildWidgets();
                            })
                    .bounds(sx, sy + ROW_GAP * 4, 165, 14).build());
        }
    }

    /**
     * Adds a labeled int field row: [–] [EditBox] [+].
     * The text label is rendered separately in {@link #renderSettingsLabels}.
     */
    private void addIntField(int x, int y, String value, IntConsumer setter) {
        // Create the EditBox first so the –/+ lambdas can reference it.
        EditBox field = new EditBox(font, x + LABEL_W + 16, y, EDIT_W, FIELD_H, Component.empty());
        field.setValue(value);
        field.setFilter(s -> s.matches("-?\\d*"));
        field.setResponder(s -> {
            try { setter.accept(Integer.parseInt(s)); }
            catch (NumberFormatException ignored) {}
        });

        addRenderableWidget(Button.builder(Component.literal("−"),
                        btn -> stepField(field, setter, -1))
                .bounds(x + LABEL_W + 2, y, 12, FIELD_H).build());
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.literal("+"),
                        btn -> stepField(field, setter, +1))
                .bounds(x + LABEL_W + 80, y, 12, FIELD_H).build());

        intFields.add(new IntFieldEntry(field, setter));
    }

    private void stepField(EditBox field, IntConsumer setter, int delta) {
        int cur;
        try { cur = Integer.parseInt(field.getValue()); }
        catch (NumberFormatException e) { cur = 0; }
        int next = cur + delta;
        field.setValue(String.valueOf(next));
        setter.accept(next);
    }

    // =========================================================================
    // Input handling
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking the name area of a modifier row selects it.
        int px = panelX(), py = panelY();
        int lx = px + 4;
        int rowBase = py + 24;
        List<IModifier> modifiers = ModifierSystem.CLIENT.getModifiers();

        for (int i = 0; i < modifiers.size(); i++) {
            int ry = rowBase + i * ROW_H;
            // Name click zone: after enable toggle, before ↑ button
            if (mouseX >= lx + 32 && mouseX < lx + LIST_W - 47
                    && mouseY >= ry && mouseY < ry + ROW_H) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? 1 : -1;
        for (IntFieldEntry entry : intFields) {
            EditBox field = entry.field();
            // Hover area covers the – button, the field itself, and the + button.
            if (mouseX >= field.getX() - 14 && mouseX <= field.getX() + field.getWidth() + 14
                    && mouseY >= field.getY() && mouseY <= field.getY() + field.getHeight()) {
                stepField(field, entry.setter(), delta);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int px = panelX(), py = panelY();
        List<IModifier> modifiers = ModifierSystem.CLIENT.getModifiers();

        // Panel backdrop + border
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xD0101010);
        graphics.renderOutline(px, py, PANEL_W, PANEL_H, 0xFFAAAAAA);

        // Divider
        int divX = px + DIV_OX;
        graphics.fill(divX, py + 2, divX + 1, py + PANEL_H - 2, 0xFF555555);

        // Headers
        graphics.drawString(font, "MODIFIERS", px + 5, py + 8, 0xFFFFFF);

        String settingsHeader = (selectedIndex >= 0 && selectedIndex < modifiers.size())
                ? modifiers.get(selectedIndex).getDisplayName().getString().toUpperCase(Locale.ROOT) + " SETTINGS"
                : "SETTINGS";
        graphics.drawString(font, settingsHeader, divX + 5, py + 8, 0xFFFFFF);

        // Modifier list rows
        int lx = px + 4;
        int rowBase = py + 24;

        if (modifiers.isEmpty()) {
            graphics.drawString(font, "Add a modifier below.", lx + 2, rowBase + 7, 0x888888);
        } else {
            for (int i = 0; i < modifiers.size(); i++) {
                int ry = rowBase + i * ROW_H;
                if (i == selectedIndex) {
                    graphics.fill(lx, ry, lx + LIST_W, ry + ROW_H - 1, 0x40FFFFFF);
                }
                graphics.drawString(font, modifiers.get(i).getDisplayName().getString(),
                        lx + 34, ry + 7, 0xEEEEEE);
            }
        }

        // Settings labels + hint
        renderSettingsLabels(graphics, px, py, modifiers);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSettingsLabels(GuiGraphics graphics, int px, int py, List<IModifier> modifiers) {
        int sx   = px + SET_OX;
        int sy   = py + 24;
        int divX = px + DIV_OX;

        if (selectedIndex < 0 || selectedIndex >= modifiers.size()) {
            if (!modifiers.isEmpty()) {
                graphics.drawString(font, "Select a modifier", divX + 6, py + 32, 0x888888);
            }
            return;
        }

        IModifier modifier = modifiers.get(selectedIndex);

        if (modifier instanceof MirrorModifier) {
            graphics.drawString(font, "Origin X:", sx, sy + ROW_GAP     + 4, 0xCCCCCC);
            graphics.drawString(font, "Origin Y:", sx, sy + ROW_GAP * 2 + 4, 0xCCCCCC);
            graphics.drawString(font, "Origin Z:", sx, sy + ROW_GAP * 3 + 4, 0xCCCCCC);
        } else if (modifier instanceof ArrayModifier) {
            graphics.drawString(font, "Count:",    sx, sy                + 4, 0xCCCCCC);
            graphics.drawString(font, "Offset X:", sx, sy + ROW_GAP     + 4, 0xCCCCCC);
            graphics.drawString(font, "Offset Y:", sx, sy + ROW_GAP * 2 + 4, 0xCCCCCC);
            graphics.drawString(font, "Offset Z:", sx, sy + ROW_GAP * 3 + 4, 0xCCCCCC);
        } else if (modifier instanceof RadialMirrorModifier) {
            graphics.drawString(font, "Slices:",   sx, sy                + 4, 0xCCCCCC);
            graphics.drawString(font, "Origin X:", sx, sy + ROW_GAP * 2 + 4, 0xCCCCCC);
            graphics.drawString(font, "Origin Z:", sx, sy + ROW_GAP * 3 + 4, 0xCCCCCC);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int panelX() { return (width  - PANEL_W) / 2; }
    private int panelY() { return (height - PANEL_H) / 2; }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    private static net.minecraft.core.BlockPos playerPos() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.blockPosition() : net.minecraft.core.BlockPos.ZERO;
    }

    @Override
    public void onClose() {
        ModifierPersistence.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
