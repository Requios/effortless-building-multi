# AGENTS.md

## Quick Orientation
- Target: Minecraft `26.2`, Java `25`, primary loaders are `fabric` and `neoforge`.
- Put shared gameplay logic in `common/`; loader projects should only contain bootstrap/events/platform glue.
- Read first: `BuildPipeline.java`, `BuildPipelineClient.java`, `PacketHandler.java`.
- Rendering entry points: `RenderHandler.java`, `BlockPreviewRenderer.java`, `ModifierRenderer.java`, `VertexConsumerProvider.java`.

## Project Structure
- `common/` — shared code compiled into all loaders
- `neoforge/` — NeoForge-specific entry points and event handlers
- `fabric/` — Fabric-specific entry points

## How It Works

**Build modes** are shapes the player selects (wall, floor, line, cube, etc.) and builds with a few clicks. For example, a wall mode lets you click two corners and fills the rectangle between them.

**Modifiers** transform the resulting block set — mirrors duplicate blocks across an axis, arrays repeat them in a grid, radials copy them around a center point.

### Client Flow
1. Player clicks → `BuildPipelineClient` receives the click via mixin/loader hooks.
2. The **build mode** processes the click (multi-click sequence: first click sets one corner, second click completes the shape). On the final click, the build mode populates a `BlockSet` with all positions in the shape.
3. The `BlockSet` runs through the **client pipeline** (`ModifierSystem` → `ConstraintSystem`), which multiplies positions via modifiers and marks invalid ones.
4. A packet is sent to the server containing the build mode, click positions, and all context needed to reproduce the result.

### Server Flow
1. `PacketHandler` receives the packet and calls `BuildPipeline.SERVER.runServerPipeline(...)`.
2. The **server pipeline** (`BuildModeSystem` → `ModifierSystemServer` → `ConstraintSystem`) regenerates the same `BlockSet` from scratch using the packet data — the server never trusts the client's block positions.
3. `PacketHandler` iterates `blockSet.validEntries()` and applies the actual world changes.

### Preview
While the player is mid-sequence (between clicks), `BuildPipelineClient.getPreviewBlocks()` runs each frame to show what would be built. The preview also runs through the pipeline so modifiers and constraints are visualized live.

## Pipeline Stages
- `BuildModeSystem` — generates initial positions from the build mode (server only; client populates BlockSet before pipeline)
- `ModifierSystem` / `ModifierSystemServer` — mirrors/arrays/radials the block set
- `ConstraintSystem` — marks entries with `BlockStatus` rejection reasons (not removal). Checks: max blocks, only-placed-blocks, max-hardness, require-tools, breaking-disabled. Runs for both breaking and replacing existing blocks during placement.

## Minecraft 26.2 Migration Notes

### Rendering (Vibrant Visual)
Minecraft 26.2 removed `MultiBufferSource` and migrated to a feature-based rendering system. All custom rendering must use `SubmitNodeCollector.submitCustomGeometry()`:

- **`VertexConsumerProvider`** — Replacement for `MultiBufferSource`. Buffers `BakedQuad` calls per `RenderType`, then submits them via `submitCustomGeometry()` with alpha/tint wrappers. Never uses `StagedVertexBuffer` directly.
- **`BlockPreviewRenderer`** — All three passes (block models, bounding box faces, edge quads) use `submitCustomGeometry()`.
- **`ModifierRenderer`** — Mirror planes and radial boundaries use `submitCustomGeometry()`.
- **`RenderHandler.onRenderLevel()`** — Signature accepts `SubmitNodeCollector` instead of `VertexConsumerProvider`.

### Loader-specific render hooks
- **Fabric**: `EffortlessBuildingClient` uses `LevelRenderEvents.COLLECT_SUBMITS` (accesses `SubmitNodeCollector` from the event context).
- **NeoForge**: `NeoForgeClientSetup` uses `RenderLevelStageEvent.AfterTranslucentFeatures` + `LevelRendererAccessor` mixin to expose `LevelRenderer.submitNodeStorage`.

### Deprecated API removals
- `ChatFormatting.getColor()` → hardcoded hex values (e.g. `ChatFormatting.RED` → `0xFFFF5555`)
- `I18n.exists()` → `I18n.get(key)` + key comparison
- `Minecraft.getInstance().setScreen()` → `Minecraft.getInstance().gui.setScreen()`
- `Minecraft.getInstance().screen` → `Minecraft.getInstance().gui.screen()`

## BlockEntry Status
- `BlockEntry.status` (default `VALID`) carries rejection reasons from `ConstraintSystem`.
- `BlockSet.validEntries()` / `validPositions()` filter to valid entries.
- Server skips non-valid entries; client renderer shows rejection visuals (red tint + outline via `BlockPreviewRenderer`, item stacks via `BreakDisplayTracker`).

## Gradle Commands
```powershell
.\gradlew.bat :common:compileJava
.\gradlew.bat :fabric:compileJava :neoforge:compileJava
.\gradlew.bat :fabric:build :neoforge:build -x javadoc -x test
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```
