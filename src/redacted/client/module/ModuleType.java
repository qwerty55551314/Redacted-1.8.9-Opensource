package redacted.client.module;

/**
 * Classifies a {@link Module} beyond its {@link Category}. The module manager
 * (and config manager) use this to decide whether a module may be registered
 * on the current build: {@code DEVELOPER} modules only exist when
 * {@code Client.DEV_BUILD} is {@code true} (release builds never register them).
 *
 * <p>This is the declarative counterpart to {@link CommandType}: a module
 * declares its type once and every manager that consumes modules gates on it
 * automatically - no per-call {@code if (Client.DEV_BUILD)} sprinkled around.
 */
public enum ModuleType {
    /** A normal end-user module. Registered on every build. */
    NORMAL,
    /** A developer/diagnostic module. Registered only when {@code Client.DEV_BUILD} is true. */
    DEVELOPER
}
