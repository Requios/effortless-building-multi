package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Reusable UI field builders for screens. Holds field/checkbox state and
 * provides input-handling and rendering helpers.
 */
public class ScreenWidgets {

    // ---- layout constants ----
    public static final int LABEL_W = 62;
    public static final int EDIT_W = 40;
    public static final int VEC_EDIT_W = 38;
    public static final int FIELD_H = 16;
    public static final int ROW_GAP = 22;
    public static final int VEC3_EXTRA_Y = 8;

    // ---- records ----
    public record IntFieldEntry(EditBox field, IntConsumer setter) {}
    public record DoubleFieldEntry(EditBox field, DoubleConsumer setter) {}
    public record CheckboxEntry(int x, int y, int w, int h, String label, boolean value, Runnable toggle) {}

    // ---- state ----
    private final List<IntFieldEntry> intFields = new ArrayList<>();
    private final List<DoubleFieldEntry> doubleFields = new ArrayList<>();
    private final List<CheckboxEntry> checkboxes = new ArrayList<>();

    private final Font font;
    private final Consumer<AbstractWidget> widgetAdder;

    /**
     * @param font       the screen's font
     * @param widgetAdder typically {@code screen::addRenderableWidget}
     */
    public ScreenWidgets(Font font, Consumer<AbstractWidget> widgetAdder) {
        this.font = font;
        this.widgetAdder = widgetAdder;
    }

    /** Clears all tracked fields/checkboxes. Call at the start of {@code init()}. */
    public void clear() {
        intFields.clear();
        doubleFields.clear();
        checkboxes.clear();
    }

    // =========================================================================
    // Int field with –/+ buttons
    // =========================================================================

    public void addIntField(int x, int y, String value, IntConsumer setter) {
        EditBox field = new EditBox(font, x + LABEL_W + 12, y, EDIT_W, FIELD_H, Component.empty());
        field.setValue(value);
        field.setFilter(s -> s.matches("-?\\d*"));
        field.setResponder(s -> {
            try { setter.accept(Integer.parseInt(s)); }
            catch (NumberFormatException ignored) {}
        });
        field.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));

        widgetAdder.accept(Button.builder(Component.literal("−"),
                        btn -> stepInt(field, setter, -1))
                .bounds(x + LABEL_W, y, 12, FIELD_H).build());
        widgetAdder.accept(field);
        widgetAdder.accept(Button.builder(Component.literal("+"),
                        btn -> stepInt(field, setter, +1))
                .bounds(x + LABEL_W + 12 + EDIT_W, y, 12, FIELD_H).build());

        intFields.add(new IntFieldEntry(field, setter));
    }

    public void stepInt(EditBox field, IntConsumer setter, int delta) {
        int cur;
        try { cur = Integer.parseInt(field.getValue()); }
        catch (NumberFormatException e) { cur = 0; }
        int next = cur + delta;
        field.setValue(String.valueOf(next));
        setter.accept(next);
    }

    // =========================================================================
    // Double field with –/+ buttons
    // =========================================================================

    public void addDoubleField(int x, int y, String value, DoubleConsumer setter) {
        EditBox field = new EditBox(font, x + LABEL_W + 16, y, EDIT_W, FIELD_H, Component.empty());
        field.setValue(value);
        field.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        field.setResponder(s -> {
            try { setter.accept(Double.parseDouble(s)); }
            catch (NumberFormatException ignored) {}
        });

        widgetAdder.accept(Button.builder(Component.literal("−"),
                        btn -> stepDouble(field, setter, -0.5))
                .bounds(x + LABEL_W + 2, y, 12, FIELD_H).build());
        widgetAdder.accept(field);
        widgetAdder.accept(Button.builder(Component.literal("+"),
                        btn -> stepDouble(field, setter, +0.5))
                .bounds(x + LABEL_W + 80, y, 12, FIELD_H).build());

        doubleFields.add(new DoubleFieldEntry(field, setter));
    }

    public void stepDouble(EditBox field, DoubleConsumer setter, double delta) {
        double cur;
        try { cur = Double.parseDouble(field.getValue()); }
        catch (NumberFormatException e) { cur = 0; }
        double next = cur + delta;
        field.setValue(formatDouble(next));
        setter.accept(next);
    }

    // =========================================================================
    // Vector3 fields (compact, no –/+ buttons)
    // =========================================================================

    public void addVec3DoubleField(int x, int y, double valX, double valY, double valZ,
                                   DoubleConsumer setX, DoubleConsumer setY, DoubleConsumer setZ) {
        int fieldStart = x + LABEL_W;
        int spacing = VEC_EDIT_W + 4;
        addSmallDoubleField(fieldStart, y, formatDouble(valX), setX);
        addSmallDoubleField(fieldStart + spacing, y, formatDouble(valY), setY);
        addSmallDoubleField(fieldStart + spacing * 2, y, formatDouble(valZ), setZ);
    }

    public void addVec3IntField(int x, int y, int valX, int valY, int valZ,
                                IntConsumer setX, IntConsumer setY, IntConsumer setZ) {
        int fieldStart = x + LABEL_W;
        int spacing = VEC_EDIT_W + 4;
        addSmallIntField(fieldStart, y, String.valueOf(valX), setX);
        addSmallIntField(fieldStart + spacing, y, String.valueOf(valY), setY);
        addSmallIntField(fieldStart + spacing * 2, y, String.valueOf(valZ), setZ);
    }

    private void addSmallDoubleField(int x, int y, String value, DoubleConsumer setter) {
        EditBox field = new EditBox(font, x, y, VEC_EDIT_W, FIELD_H, Component.empty());
        field.setValue(value);
        field.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        field.setResponder(s -> {
            try { setter.accept(Double.parseDouble(s)); }
            catch (NumberFormatException ignored) {}
        });
        field.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
        widgetAdder.accept(field);
        doubleFields.add(new DoubleFieldEntry(field, setter));
    }

    private void addSmallIntField(int x, int y, String value, IntConsumer setter) {
        EditBox field = new EditBox(font, x, y, VEC_EDIT_W, FIELD_H, Component.empty());
        field.setValue(value);
        field.setFilter(s -> s.matches("-?\\d*"));
        field.setResponder(s -> {
            try { setter.accept(Integer.parseInt(s)); }
            catch (NumberFormatException ignored) {}
        });
        field.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
        widgetAdder.accept(field);
        intFields.add(new IntFieldEntry(field, setter));
    }

    // =========================================================================
    // Set-to-player button
    // =========================================================================

    public void addSetToPlayerButton(int x, int y, Runnable action) {
        int fieldStart = x + LABEL_W;
        int spacing = VEC_EDIT_W + 4;
        int btnX = fieldStart + spacing * 3 + 2;
        widgetAdder.accept(Button.builder(Component.literal("\u2316"),
                        b -> action.run())
                .bounds(btnX, y, FIELD_H, FIELD_H)
                .tooltip(Tooltip.create(Component.literal("Set to player position")))
                .build());
    }

    // =========================================================================
    // Checkbox (plain text, no widget background)
    // =========================================================================

    public void addCheckbox(int x, int y, String label, boolean value, Runnable toggle) {
        String text = (value ? "\u2611" : "\u2610") + (label.isEmpty() ? "" : " " + label);
        int w = font.width(text) + 2;
        int h = 11;
        checkboxes.add(new CheckboxEntry(x, y, w, h, label, value, toggle));
    }

    // =========================================================================
    // Input handling (delegate from Screen)
    // =========================================================================

    /** Returns true if a checkbox was clicked. Call before super in mouseClicked. */
    public boolean handleCheckboxClick(double mouseX, double mouseY) {
        for (CheckboxEntry cb : checkboxes) {
            if (mouseX >= cb.x() && mouseX < cb.x() + cb.w()
                    && mouseY >= cb.y() && mouseY < cb.y() + cb.h()) {
                cb.toggle().run();
                return true;
            }
        }
        return false;
    }

    /** Returns true if a field was scrolled. Call before super in mouseScrolled. */
    public boolean handleScroll(double mouseX, double mouseY, double scrollY) {
        int delta = scrollY > 0 ? 1 : -1;
        for (IntFieldEntry entry : intFields) {
            EditBox field = entry.field();
            if (mouseX >= field.getX() && mouseX <= field.getX() + field.getWidth()
                    && mouseY >= field.getY() && mouseY <= field.getY() + field.getHeight()) {
                stepInt(field, entry.setter(), delta);
                return true;
            }
        }
        double halfDelta = scrollY > 0 ? 0.5 : -0.5;
        for (DoubleFieldEntry entry : doubleFields) {
            EditBox field = entry.field();
            if (mouseX >= field.getX() && mouseX <= field.getX() + field.getWidth()
                    && mouseY >= field.getY() && mouseY <= field.getY() + field.getHeight()) {
                stepDouble(field, entry.setter(), halfDelta);
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    /** Renders all checkboxes as plain text with hover highlight. */
    public void renderCheckboxes(GuiGraphics graphics, int mouseX, int mouseY) {
        for (CheckboxEntry cb : checkboxes) {
            String text = (cb.value() ? "\u2611" : "\u2610") + (cb.label().isEmpty() ? "" : " " + cb.label());
            boolean hovered = mouseX >= cb.x() && mouseX < cb.x() + cb.w()
                    && mouseY >= cb.y() && mouseY < cb.y() + cb.h();
            int color = hovered ? 0xFFFFFF : 0xCCCCCC;
            graphics.drawString(font, text, cb.x(), cb.y(), color);
        }
    }

    /** Renders X/Y/Z sub-labels above a vector3 field row. */
    public void renderVec3Labels(GuiGraphics graphics, int sx, int y) {
        int fieldStart = sx + LABEL_W;
        int spacing = VEC_EDIT_W + 4;
        int labelY = y - 9;
        graphics.drawString(font, "X", fieldStart + VEC_EDIT_W / 2 - 2, labelY, 0xCCCCCC);
        graphics.drawString(font, "Y", fieldStart + spacing + VEC_EDIT_W / 2 - 2, labelY, 0xCCCCCC);
        graphics.drawString(font, "Z", fieldStart + spacing * 2 + VEC_EDIT_W / 2 - 2, labelY, 0xCCCCCC);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    public static String formatDouble(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}

