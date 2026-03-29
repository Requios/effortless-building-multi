# Effortless Building Multi-Loader

This is a Minecraft mod being rebuilt for **1.21.1** targeting **NeoForge** and **Fabric** (with a Forge subproject kept for reference/compatibility).

## Project structure

- `common/` — shared code compiled into all loaders
- `neoforge/` — NeoForge-specific entry points and event handlers
- `fabric/` — Fabric-specific entry points
- `forge/` — Forge subproject (present but secondary; the primary targets are NeoForge and Fabric)

## Context

The mod is being rebuilt **bit by bit** based on an older version of the mod. The old version:

- Only works on **Forge**
- Targets **Minecraft 1.20.1**

When porting code from the old version, APIs will frequently differ. Common things to watch for:

- Rendering API changes (e.g. `Tesselator.getBuilder()` → `Tesselator.getInstance().begin()`, `tessellator.end()` → `BufferUploader.drawWithShader(buffer.buildOrThrow())`, `buffer.vertex().color().endVertex()` → `buffer.addVertex().setColor()`)
- `GameRenderer` shader method renames (e.g. `getPositionColorTexShader` → `getPositionTexColorShader`)
- `Screen`-level helper methods that moved to `GuiGraphics` (e.g. `renderComponentTooltip`)
- `SoundEvents` fields changed from `SoundEvent` to `Holder<SoundEvent>` — use `SimpleSoundInstance.forUI()` instead of the raw constructor
- Platform-specific classes (`ClientEvents`, `EffortlessBuildingClient` as a common class, etc.) that existed in the old Forge mod but do not exist here — use `BuildModes.CLIENT` for build mode access
- Create Foundation utilities (`Components`, `Lang`, `TooltipHelper`) from the old mod do not exist here — use vanilla `Component` equivalents directly
