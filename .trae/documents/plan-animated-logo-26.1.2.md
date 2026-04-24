## Summary

Port `animated-mojang-logo` so it runs on Minecraft `26.1.2` while still supporting the existing `1.21.11` (1.21.x) line. The crash log is explained by a namespace/mappings mismatch (the mod jar references intermediary classes like `net/minecraft/class_2960`, but Minecraft `26.1.2` runs in Mojang “official” names) and by the startup overlay class changing (old mixin target no longer exists).

## Current State Analysis (Grounded)

- The repo is already a multi-project build:
  - [settings.gradle](file:///workspace/settings.gradle) includes `mc121` and `mc26`.
  - Root [build.gradle](file:///workspace/build.gradle) is an aggregator.
  - `mc121` uses Yarn v2 mappings in [mc121/build.gradle](file:///workspace/mc121/build.gradle); `mc26` uses `loom.officialMojangMappings()` in [mc26/build.gradle](file:///workspace/mc26/build.gradle).
- The repo still contains the legacy single-module sources/resources under:
  - [src/main/java](file:///workspace/src/main/java) and [src/main/resources](file:///workspace/src/main/resources)
  - plus a `bin/` folder that looks like IDE output ([bin/main](file:///workspace/bin/main))
  - These are not referenced by the root build, but they are confusing and easy to accidentally ship/maintain.
- The startup overlay implementation currently exists only for `mc121` via [SplashOverlayMixin.java](file:///workspace/mc121/src/main/java/dev/codeitsduckydev/animatedlogo/mixin/SplashOverlayMixin.java) (Yarn names). `mc26` has no mixins configured yet ([animated-logo.mc26.mixins.json](file:///workspace/mc26/src/main/resources/animated-logo.mc26.mixins.json) has an empty `mixins` list).
- Shared game assets (textures/sounds) currently live under [src/main/resources/assets](file:///workspace/src/main/resources/assets) (legacy path), and both subprojects attempt to include them via:
  - `sourceSets.main.resources.srcDir rootProject.file('src/main/resources/assets')`
  - This is incorrect for Minecraft resource layout: it would place `animated-mojang-logo/**` at the jar root, but Minecraft expects `assets/animated-mojang-logo/**`. As-is, builds would either miss resources in-game or require a different packaging shape.
- The `mc121` mixin uses `com.llamalad7.mixinextras.sugar.Local` but no Gradle dependency currently provides MixinExtras on the compile classpath, so `mc121` compilation is expected to fail until that dependency is added.

## Goal & Success Criteria

- **Primary:** A built jar that loads on Minecraft `26.1.2` without crash and shows the animated logo (or an equivalent loading overlay) at startup.
- **Compatibility:** Still provide a jar that works on Minecraft `1.21.11` and later within the `1.21.x` line.
- **Packaging:** Two separate jars (or two branches) so users don’t accidentally load the wrong build on the wrong Minecraft line.
- **Verification:** Both jars start in a dev run (`runClient`) and also do not crash when placed in a real launcher instance.

## Proposed Changes (Decision-Complete)

### 1) Stabilize the build toolchain (wrapper + plugin resolution)

**Why:** Porting requires reliable `genSources` / `runClient` on both subprojects. Loom/plugin resolution and wrapper download issues block progress.

**Change:**
- Update [settings.gradle](file:///workspace/settings.gradle) `pluginManagement` to add an explicit resolution strategy for Loom so it can resolve directly from Fabric Maven even if the Gradle Plugin Portal marker lookup fails:
  - if plugin id is `fabric-loom`, map to module `net.fabricmc:fabric-loom:${version}`
- Pin the Gradle wrapper to a Loom-compatible Gradle 8.x release (not 9.x) by editing [gradle-wrapper.properties](file:///workspace/gradle/wrapper/gradle-wrapper.properties).
- Add conservative download timeouts via [gradle.properties](file:///workspace/gradle.properties) `systemProp.org.gradle.internal.http.connectionTimeout` and `systemProp.org.gradle.internal.http.socketTimeout` so users with slow connections can still build.

### 2) Fix shared assets packaging so both jars contain `assets/...`

**Why:** Both subprojects must ship textures/sounds in the correct in-jar path (`assets/animated-mojang-logo/...`) or the animation/sound will fail at runtime.

**Change:**
- Create a dedicated shared directory that contains only the correct resource tree, e.g.:
  - `/workspace/shared-resources/assets/animated-mojang-logo/**`
- Update both [mc121/build.gradle](file:///workspace/mc121/build.gradle) and [mc26/build.gradle](file:///workspace/mc26/build.gradle):
  - `sourceSets.main.resources.srcDir rootProject.file('shared-resources')`
  - (remove the current `src/main/resources/assets` inclusion)
- Keep each module’s own `src/main/resources` for its `fabric.mod.json` and mixins json.

### 3) mc121: make the 1.21.x jar buildable and self-contained

**Why:** `mc121` is the known-good implementation line, but currently lacks build-time dependencies and shared assets inclusion.

**Change (mc121):**
- Use Yarn mappings (as today) and the current `SplashOverlayMixin` approach.
- Add MixinExtras to [mc121/build.gradle](file:///workspace/mc121/build.gradle) so `com.llamalad7.mixinextras.sugar.Local` compiles and runs on loader versions that don’t bundle it.
- Ensure `mc121` jar includes the shared `assets/...` resources from step 2.
- Keep the Minecraft dependency cap in [mc121 fabric.mod.json](file:///workspace/mc121/src/main/resources/fabric.mod.json): `"minecraft": ">=1.21.11 <1.22"`.
- Ensure the produced jar name clearly indicates the line (e.g. `animated-logo-mc1.21.x-<modVersion>.jar`).

### 4) mc26 subproject: port to Minecraft 26.1.2 using Mojang mappings

**Why:** Yarn mappings aren’t available for 26.1.2; Mojang official mappings match the 26.1.2 runtime namespace, eliminating `class_2960` / `class_425` failures.

**Change (mc26 build):**
- Set dependencies:
  - `minecraft "com.mojang:minecraft:26.1.2"`
  - `mappings loom.officialMojangMappings()` (Fabric Loom 1.13.3 supports this)
  - `modImplementation "net.fabricmc:fabric-loader:0.19.2"`
  - `modImplementation "net.fabricmc.fabric-api:fabric-api:0.146.1+26.1.2"`
- Keep Java target at 21 (runs fine on Java 25), unless compilation reveals 26.1.2 requires a higher `--release`.

**Change (mc26 code):**
- Generate sources for 26.1.2 (`:mc26:genSources`) and locate the startup/loading overlay entrypoint in Mojang names by searching the generated sources for:
  - `SplashOverlay`, `LoadingOverlay`, `Reload`, `progress`, and the class that draws the Mojang logo during startup
- Implement a new mixin under `mc26/src/main/java/dev/codeitsduckydev/animatedlogo/mixin/` targeting the discovered class/method and re-implement the animation rendering there (likely replacing the entire `SplashOverlay` hook).
- Port sound + id usage in [mc26 AnimatedLogo.java](file:///workspace/mc26/src/main/java/dev/codeitsduckydev/animatedlogo/AnimatedLogo.java):
  - Replace Yarn’s `Identifier`/`Registry`/`Registries` with Mojang equivalents (e.g., `ResourceLocation` + built-in registries). Compilation against official mappings determines the exact classes.

**Change (mc26 metadata):**
- In `mc26/src/main/resources/fabric.mod.json` set:
  - `"minecraft": ">=26.1.2 <27"` (or a tighter cap if desired)
  - Include only the mc26 mixin json.

### 5) Make mixins fail-safe where appropriate (to avoid hard crashes on minor updates)

**Why:** Minor patch changes in 26.1.x can rename/move overlay classes. A hard-required mixin can crash the game at launch.

**Change:**
- For mc26 mixins json, consider `"required": false` and guard behavior so the mod still loads (even if the animation is skipped) rather than crashing.
- Keep mc121 `"required": true` (optional) since it targets a stable known line.

## Assumptions & Decisions

- Decision: Support both lines via **two artifacts** (mc121 + mc26) in one repository (multi-project Gradle).
- Decision: Use **Mojang mappings** for mc26 because Yarn 26.1.2 mappings are not currently available.
- Assumption: Fabric Loom `1.13.3` + Fabric Loader `0.19.2` is the correct baseline for `26.1.2`.
- Decision: Fix shared assets by creating a `shared-resources/assets/...` directory and having both subprojects include it as a resources source root.

## Verification Plan

1. `./gradlew :mc121:clean :mc121:build` and confirm the jar contains `assets/animated-mojang-logo/**`.
2. `./gradlew :mc26:clean :mc26:build` and confirm the jar contains `assets/animated-mojang-logo/**`.
3. `./gradlew :mc121:runClient` and confirm:
   - no mixin-target-missing warnings
   - animation + sound work on `1.21.11`
4. `./gradlew :mc26:runClient` and confirm:
   - no `class_2960` / `class_425` missing class errors
   - animation works (or gracefully skips without crash if the overlay target changed)
5. Manual launcher test:
   - Drop mc26 jar into a `26.1.2` instance and verify startup no longer crashes.
