# AGENTS.md
## Quick Orientation
- Target: Minecraft `1.21.1`, Java `21`, primary loaders are `fabric` and `neoforge`; `forge` is legacy reference.
- Put shared gameplay logic in `common/`; loader projects should only contain bootstrap/events/platform glue.
- Read first: `CLAUDE.md`, `common/src/main/java/nl/requios/effortlessbuilding/buildchain/BuildChain.java`, `common/src/main/java/nl/requios/effortlessbuilding/network/PacketHandler.java`.

## Architecture That Matters
- Placement/break flow is client preview + server authority: input -> `BuildChain.handleRightClick/handleLeftClick` -> mode computes blocks -> client sends packet -> server recomputes via `getServerBlocks(...)` -> `BuildChain.SERVER` applies edits.
- Keep client/server behavior aligned by including mode options in packets and applying them with `ModeOptions.applyForCalculation(...)` in `PacketHandler`.
- Do not bypass `BuildChain` from loader ticks; both loaders intentionally route through it (`fabric/.../EffortlessBuildingClient.java`, `neoforge/.../NeoForgeClientSetup.java`).

## Loader Boundary Pattern
- Platform abstractions live in `common/platform/services/*`, resolved via `ServiceLoader` in `common/platform/Services.java`.
- Loader-specific implementations + registration files:
  - Fabric: `fabric/src/main/java/.../platform/*`, `fabric/src/main/resources/META-INF/services/*`
  - NeoForge: `neoforge/src/main/java/.../platform/*`, `neoforge/src/main/resources/META-INF/services/*`
- Client network send path should stay `PacketHandler.sendToServer(...)` -> `Services.NETWORK` implementation.

## Build Modes, Modifiers, Rendering Conventions
- `BuildModeEnum` stores singleton `IBuildMode` instances; modes are stateful across click sequences.
- Use `BuildChain.getPlayerLookVec(player)` (not raw look vector) to avoid divide-by-zero in bound math.
- Modifier transforms run as ordered `IBuildSystem` stages through `ModifierSystem.CLIENT` and are shared by preview + execution.
- Modifier persistence is client-side JSON at `<gameDir>/config/effortlessbuilding_modifiers.json` (`ModifierPersistence`).
- Keep loader render hooks thin; preview visuals and action-bar UX live in `BlockPreviewRenderer`.

## Gradle + Dev Workflows (verified)
- Compile common:
```powershell
.\gradlew.bat :common:compileJava
```
- Compile active loaders:
```powershell
.\gradlew.bat :fabric:compileJava :neoforge:compileJava
```
- Run dev environments:
```powershell
.\gradlew.bat :fabric:runClient
.\gradlew.bat :fabric:runServer
.\gradlew.bat :neoforge:runClient
.\gradlew.bat :neoforge:runServer
.\gradlew.bat :neoforge:runData
```

## Porting Notes
- This is an incremental port from Forge `1.20.1`; prefer existing `common` implementations over old Forge-only patterns.
- Use `BuildModes.CLIENT` (avoid legacy common singletons from older Forge code).
- Follow API drift mappings in `CLAUDE.md` (`GuiGraphics`, shader renames, `SimpleSoundInstance.forUI`, etc.).
