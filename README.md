# Redacted Client

Minecraft 1.8.9 client base updated for LWJGL 3 with a small client event/module layer.

## Requirements

- IntelliJ IDEA
- JDK 21 installed and selected as the Gradle JVM
- Minecraft assets from a local Minecraft installation

The Gradle build uses a Java 21 toolchain while compiling Java 8 bytecode for the client sources.

## IntelliJ Setup

1. Clone the repository.
2. Open IntelliJ IDEA and select `File > Open`.
3. Select the repository folder and open it as a Gradle project.
4. When IntelliJ asks about Gradle settings, use:
   - Gradle JVM: JDK 21
   - Build and run using: Gradle
   - Run tests using: Gradle
5. Wait for Gradle sync to finish.
6. Create the local run assets:

   Windows PowerShell:

   ```powershell
   New-Item -ItemType Directory -Force run
   Copy-Item -Recurse -Force "$env:APPDATA\.minecraft\assets" "run\assets"
   ```

   Windows cmd:

   ```bat
   mkdir run
   xcopy /E /I "%APPDATA%\.minecraft\assets" "run\assets"
   ```

   Linux or macOS:

   ```bash
   mkdir -p run
   cp -R ~/.minecraft/assets run/assets
   ```

7. In the Gradle tool window, run `Tasks > application > runClient`.

## Command Line

Build the client:

```bash
./gradlew build
```

Run the client:

```bash
./gradlew runClient
```

Copy resolved LWJGL API and native jars to `build/lwjgl3-libraries`:

```bash
./gradlew syncLwjglLibraries
```

On Windows cmd, use `gradlew.bat` instead of `./gradlew`.

## Repository Hygiene

Generated files and local machine state are intentionally ignored:

- `.idea/`
- `.gradle/`
- `build/`
- `out/`
- `run/`
- `.env*`

Do not commit Minecraft runtime saves, logs, copied assets, IDE workspace state, or local environment files.
