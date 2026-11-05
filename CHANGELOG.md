# Changelog

Notable changes to RTX Remix for Minecraft, newest first. Changes that a
player, a map author, or a contributor would notice go here; internal
refactoring does not.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
The version in a release is the `mod_version` in `gradle.properties`, and it is
also part of every mod file name.

## Community Edition 0.1.0-ce.6 - Unreleased

### Fixed

- Distant leaves are no longer darker than near ones: the holes in cutout
  textures carry their sprite's colour, so the runtime's mips stop averaging
  black into them.
- Trees in shade are no longer near-black. Leaves seen directly trace their
  indirect light instead of reading the radiance cache, which averaged the dark
  inside of the canopy, at the cost of some leaf shimmer.

### Changed

- Foliage lets more light through: a 0.4-block transmission distance instead of
  0.25, and 0.7 of the authored single-scattering albedo instead of half.
- The iron block is smoother polished metal (Auto PBR roughness 0.16 with a
  faint bump), so its reflection is no longer a smeared band and a torch beside
  it no longer clips into a white blob.

## Community Edition 0.1.0-ce.5 - Unreleased

### Added

- Volumetric clouds, on by default: real-scale cumulus fields to the horizon,
  storm towers with anvils in thunder, overcast in rain, and thin, fibrous
  cirrus high above in fair weather. Lightning lights the storm clouds from inside. Every
  cloud is ray-marched into the sky as a volume with the same sun, moon, and atmosphere as
  the sky and fades into the horizon haze; near clouds are also meshed, out of
  sight, so they cast shadows and light shafts. Distant clouds in front of the sun dim the daylight, and
  clouds hide the stars and the Milky Way behind them. Clouds → Volumetric
  Clouds, Volumetric Cloud Detail, and Cloud Cover; a saved Procedural cloud
  style is kept.

### Fixed

- A single frame of the raw raster sky now and then flashed over the whole
  screen: the sky's compute passes restored vanilla's shader through its
  bind cache, which could leave their own program bound for the next draw.

## Community Edition 0.1.0-ce.4 - Unreleased

### Added

- Glowing block lights: glowstone, sea lanterns, froglights, shroomlights, jack
  o'lanterns, lit redstone lamps and copper bulbs, lanterns, and end rods now
  cast light, calibrated to a torch's power at their light level.
- An Ultra motion blur step with 48 samples.
- Auto PBR, on by default: every block without a PBR pack gets surface relief
  from its texture and a roughness that suits it; gold, iron, netherite, and
  copper blocks reflect as metal, and diamond blocks, ice, and polished stone
  are glossy.
- Shooting stars on clear nights, about one every seven seconds, and a meteor
  shower one night in five.

### Changed

- Natural Daylight is a physical sky: one atmosphere with ozone and multiple
  scattering lights both the sky and the sun and moon, in their real ratio. The
  sky is deep blue instead of grey-cyan, shade has contrast, golden hour is
  warm, and the noon sun and the clouds are no longer tinted orange.
- The sun is a small round disc with a tight glow instead of a large white blot.
- Nights are lit by the moon, three times upstream's and following its phase,
  instead of a second cyan sky light that turned moonlit grass green. The night
  ambient boost defaults to 0.
- Motion blur takes a shutter speed (1/250 s to 1/24 s, default 1/48 s) and
  draws the same streak at any frame rate; the maximum blur size goes up to
  0.25, and streaks are two-sided at every quality step. The old shutter
  setting is not carried over.
- A richer Milky Way: clumpy star clouds, a great rift and thin dust lanes, a
  warm core with bluish arms, faint nebulae, and a grain of unresolved stars,
  in a wider, meandering band; 6000 stars instead of 4000.
- LabPBR ambient occlusion is applied in linear space.
- Leaves and grass absorb more light per scattering event (half the authored
  single-scattering albedo), so torch-lit foliage no longer glows green.
- An untouched 0.1.0-ce.3 night conf is upgraded.

## Community Edition 0.1.0-ce.3 - Unreleased

### Added

- Golden-hour light shafts: a low sun lights the dawn mist and dusk haze far more
  strongly than surfaces, and the sky ambient scatters less, so shafts read.
- Cloud-gap shafts in rain, drifting rain curtains with clearer gaps, and
  lightning that lights the rain haze and casts brief shadows.
- Natural Daylight: a believable sun-to-sky ratio, blue aerial perspective, a
  tighter sun halo, and a smaller sun.
- Haze Detail setting for the volumetric froxel grid, Ultra by default.
- `/rtx ce` for live tuning of the new looks.

### Changed

- Untouched 0.1.0-ce.2 dawn, dusk, rain, and thunder confs are upgraded.

## Community Edition 0.1.0-ce.1 - Unreleased

The first Community Edition build, on upstream `a518eff`. Every change below
has its own switch; COMMUNITY.md lists them.

### Added

- Rain-wet ground: rain-exposed upward faces darken and gain a water sheen by
  LabPBR porosity, then dry slowly.
- Shore foam, biome water colours, and Remix animated water on water surfaces.
- A night sky of 4000 stars and a procedural Milky Way, with cooler, stronger
  moonlight.
- Optional motion blur in four quality steps.
- A Simplified Chinese translation.

### Changed

- LabPBR smoothness converts to the perceptual roughness Remix expects, so
  terrain is no longer glossier than authored.
- Rain and thunder bring a denser, drifting haze, an overcast sky, and no
  exposure lift; clouds follow the weather and time of day.
- Night brings a thin haze; the night sky ambient defaults to 1.4.
- Shipped confs that an install still has unedited are upgraded to the new
  defaults.

## Unreleased

### Fixed

- The installer runs on PCs that give every program administrator rights, such
  as with User Account Control turned off or in the built-in Administrator
  account. It still refuses administrator rights as an account other than the
  one signed in, whose folders it would otherwise install into.
- The installer can install into a Prism Launcher it does not detect, such as
  a portable copy kept anywhere: the launcher question asks for its folder,
  and later runs remember it. A portable Prism the installer does detect, on
  the PATH or behind a Start menu shortcut, now gets the instance in its own
  folder rather than in `%APPDATA%\PrismLauncher`.

## 0.1.0 - 2026-10-23

The first public release, a beta.

### Added

- Path-traced rendering of Minecraft Java Edition 1.21.1 through RTX Remix:
  terrain, entities and block entities, particles, the sky, and clouds, with
  baseline LabPBR textures built into the mod JAR through Fabric's `fabric` pack.
- Lighting that follows the game: time of day, weather, being underwater, and
  per-dimension skies, with the End's constant dark dome and a black sky with
  no ambient where a dimension has none. Map authors can give zones and biomes
  their own looks through gameplay-driven conf profiles installed beside the
  mod, with an authoring guide.
- Block lights from resource-pack rules, a Light Reach setting and an Auto
  light cull distance, flames and lights on hand-held items, and the `G` key
  to put out and relight what the local player holds.
- The Copper Beacon, a placeable and craftable directional lamp: dye and
  saturation, intensity, cone angle, redstone control, and static, rotating,
  oscillating, or patrolling motion that every player sees the same way.
- An RTX Remix settings screen in Options, a button into Remix's own Graphics
  Settings, the `/rtx` command tree, `F8` to toggle path tracing, and
  session-only quality presets through `/rtx preset <name>` or
  `-Drtxremix.preset=<name>`.
- Camera paths: record a flythrough by walking it and replay it on wall-clock
  time or one fixed step per frame. Still shots keep a pose together with its
  time of day, weather, path-tracing state, and cloud style.
- Procedural clouds, selectable in place of vanilla's.
- Steadier frames at high render distance: paced chunk delivery and unloads
  in single-player, a hold behind the loading screen after joining a world,
  and allocation-free hot paths that keep garbage collection out of the frame.
- A self-contained mod JAR that any Fabric launcher can install on its own,
  and a Windows installer for Prism Launcher and the Microsoft launcher that
  sets up Java, Fabric, the mod, and tuned JVM arguments, and repairs,
  upgrades, uninstalls, or exports an install.
- Developer tools under `tools/` through the `rtx` CLI: launching, benchmarks
  on the pinned demo world, built by [BlockWorks](https://www.blockworks.uk/),
  and the LabPBR art pack, created by an art team at NVIDIA, or on locally
  staged fixtures, JFR and Tracy profiling, and the contributor checks.
- Developer tools launch Minecraft through a Prism Launcher data folder for
  each checkout, signed in to a Microsoft account that owns Minecraft: Java
  Edition. `tools\rtx.bat prism signin` signs a checkout in, and
  `launch.jvmArgs` carries developer JVM options.
