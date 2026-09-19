# Redacted

A Minecraft 1.8.9 client with an MCP (Model Context Protocol) bridge built in, aimed at Polar-AC and Hypixel. It's discontinued. The source is out as-is: no support, no updates, no promises.

## Modules

Combat: AimAssist, SilentAim, SilentAura, AutoClicker, AutoBlock, AutoThrow, BackTrack, BedAura (with a Hypixel mode), BowAimAssist, Criticals, FakeLag, NoHitDelay, Reach, TargetFilter, Team, Triggerbot, Velocity, WTap, AntiBot, AntiFireBall, AntiMiss.

Movement: Sprint, NoSlow, NoFall, NoJumpDelay, KeepSprint, InventoryMove, BridgeAssist.

Render: EntityESP (ESP, tracers, nametags, target HUD), BlockESP (beds, chests, item counts, place effect), Crosshair, FullBright, Freecam, FreeLook, Mirror, Notifications, SuperheroFX, TNTTimer, Trajectories, Tweaks, ClickGUI, EntityCulling, ParticleCulling.

World: Scaffold, BedProtection, ChestStealer, Clutch, FastPlace, AutoPearl, Baritone.

Utility: AutoHeal, AutoTool, InvManager, AnticheatDetector, FlagDetector, MiddleClickFriend, ClientSpoof.

Cosmetics: Capes, Hats, Wings, Particles, HitSounds.

Exploit: Disabler.

Developer: PacketLogger, with its own packet log GUI.

## Anticheat work

Reach gates on Grim, Polar and Vulcan separately instead of sharing one number. WTap is tuned around Polar-class reaction offsets. BedAura has a Hypixel adjacent-then-bed break mode. AnticheatDetector fingerprints Polar, Intave and AGC by transaction timing and windowId patterns, and FlagDetector watches setbacks, velocity and transactions live.

## GUIs

Three click GUI styles: Dropdown, Exhibition and Redacted. Opens with RightShift. There's also a drag-and-drop HUD editor (positions survive resizes), a theme manager, a config manager, an alt manager, a Bed Wars team manager and the packet log viewer.

## Alt manager

Cracked, Microsoft (device code flow and refresh tokens) and Altening accounts. Per-alt proxy support, a history of every session, and auto login of the last used account on startup.

## Replays

ReplayMod is built in. Recording, replay and rewind work, and the viewer opens from the main menu.

## Baritone

The full Baritone pathing source ships with the client, plus a bridge that routes Baritone's rotations through the client's silent aim and an auto bridge controller for bridging.

## Commands

Prefix is `.`:

`.help`, `.toggle <module>`, `.config`, `.theme`, `.mcp`, `.packetlog`, `.anticheat`, `.reset`, `.settimer`.

## MCP bridge

`.mcp on` starts it on port 25585, `.mcp off` stops it and `.mcp status` shows what's running. It binds to 127.0.0.1 and exposes modules, configs, client info, baritone, world, player, containers, render, chat and debug tools. It also starts with the client by default; flip `AUTO_START_MCP` in `DebugSystem` if you don't want that.

## Debug

`./gradlew debugRunClient` puts the packet dumper on the classpath (set `DEBUG_PACKETS=true` to turn it on). The rest of the debug logging lives in `DebugSystem`: per-category flags, one file per category under `logs/debug-<category>.log`.

## Under the hood

LWJGL 3 instead of the old 1.8.9 LWJGL 2 stack, a Celeritas/Sodium-style chunk renderer, and the whole Minecraft 1.8.9 source tree in one project so everything builds together.

## Building

JDK 21 is all you need; the Gradle toolchain and wrapper handle the rest.

```bash
./gradlew runClient
```

If `run/assets` doesn't exist yet, point it at a normal 1.8.9 install once:

```bash
cp -R ~/.minecraft/assets run/assets
```

Standalone jar:

```bash
./gradlew shadowJar        # build/libs/RedactedClient.jar
```

IntelliJ imports it as a Gradle project without setup beyond picking the JDK.

## License

GPLv3. See `LICENSE`.
