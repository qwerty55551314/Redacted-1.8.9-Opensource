package redacted.client.command;

/**
 * Classifies a {@link Command} by its intended audience. {@code CommandManager}
 * consults this to decide whether a command may be registered on the current
 * build: {@code DEVELOPER} commands only exist when {@code Client.DEV_BUILD} is
 * {@code true} (release builds never register them).
 *
 * <p>This is the command-side counterpart to {@link redacted.client.module.ModuleType}:
 * a command declares its type once in its constructor and the manager gates on it
 * automatically - no per-call {@code if (Client.DEV_BUILD)} sprinkled around.
 */
public enum CommandType {
    /** A normal end-user command. Registered on every build. */
    NORMAL,
    /** A developer/diagnostic command. Registered only when {@code Client.DEV_BUILD} is true. */
    DEVELOPER
}
