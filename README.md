# Redacted

A Minecraft 1.8.9 MCP client, tuned mainly for Polar-AC and Hypixel. Development has stopped, so the full source is being released as-is.

The menu shows the client as **Redacted**; the tree is at the version it was left at.

## What's inside

- Full module suite: combat, movement, render, world, utility, plus the developer modules.
- Click GUIs (dropdown and the custom Redacted one), HUD editor, themes, notifications.
- Alt manager: cracked, Microsoft (device code or refresh token) and Altening accounts, with per-alt proxy support.
- ReplayMod-based replay and rewind support, wired into the main menu.
- Baritone pathing included, with the client's own bridge and auto-bridge controller on top.
- Anticheat-aware modules. Reach, WTap, BedAura and the rest have Polar/Grim/Vulcan/Intave/AGC paths, and there's a Hypixel bed-break mode.
- Developer tooling: packet logger with its own GUI, debug packet dumps on the debug classpath, and a built-in MCP (Model Context Protocol) bridge for AI agents. `.mcp on` starts it on port 25585, `.mcp off` stops it.
- The 1.8.9 client runs on LWJGL 3, not the old LWJGL 2 stack.

## Building

JDK 21 and the Gradle wrapper are all you need; the toolchain handles the rest.

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

Debug build (packet dumper and extra diagnostics):

```bash
./gradlew debugRunClient
```

IntelliJ imports it as a Gradle project without any setup beyond picking the JDK.

## Notes

- Discontinued: no support and no updates planned.
- Apache 2.0, see `LICENSE`.
