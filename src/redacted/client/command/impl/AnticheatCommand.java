package redacted.client.command.impl;

import redacted.client.Client;
import redacted.client.command.Argument;
import redacted.client.command.Command;
import redacted.client.command.CommandType;
import redacted.client.module.impl.utility.AnticheatDetector;
import redacted.client.module.impl.utility.FlagDetector;

/**
 * Query the anticheat detection state and flag count.
 * <ul>
 *   <li>{@code .ac} - show the last detected anticheat ("Vanilla" when nothing was detected)</li>
 *   <li>{@code .ac flags} - show the flag count since FlagDetector enabled / last reset</li>
 *   <li>{@code .ac reset} - reset the flag count</li>
 * </ul>
 * Developer-only ({@link CommandType#DEVELOPER}): never registered on release builds.
 */
public class AnticheatCommand extends Command {

    public AnticheatCommand() {
        super("ac", "Query anticheat detection and flag info", new String[]{"anticheat"},
                CommandType.DEVELOPER,
                Argument.optionalLiteral("action", "what to query",
                        new Argument.Option("info", "show the last detected anticheat"),
                        new Argument.Option("flags", "show the flag count this session"),
                        new Argument.Option("reset", "reset the flag count")));
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("detect")) {
            final String ac = AnticheatDetector.getLastDetected();
            final String details = AnticheatDetector.getLastDetails();
            final AnticheatDetector module = Client.getInstance().getModuleManager().getModule(AnticheatDetector.class);
            final boolean running = module != null && module.isEnabled();

            if ("Unknown".equals(ac) || ac == null) {
                sendMessage("Anticheat: §aVanilla");
            } else {
                sendMessage("Anticheat: " + ac);
                if (details != null && !details.isEmpty()) {
                    sendMessage("Details: " + details);
                }
            }
            if (!running) {
                sendMessage("Note: AnticheatDetector is not enabled. Use §e.toggle AnticheatDetector");
            }
            return;
        }

        if (args[0].equalsIgnoreCase("flags") || args[0].equalsIgnoreCase("flag")) {
            final FlagDetector fd = Client.getInstance().getModuleManager().getModule(FlagDetector.class);
            if (fd == null) {
                sendMessage("FlagDetector module not found.");
                return;
            }
            if (!fd.isEnabled()) {
                sendMessage("FlagDetector is not enabled. Use §e.toggle FlagDetector");
                return;
            }
            sendMessage("Flags detected this session: " + getFlagCount(fd));
            return;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            final FlagDetector fd = Client.getInstance().getModuleManager().getModule(FlagDetector.class);
            if (fd == null || !fd.isEnabled()) {
                sendMessage("FlagDetector is not enabled.");
                return;
            }
            fd.disable();
            fd.setEnabled(true);
            sendMessage("FlagDetector reset.");
            return;
        }

        sendMessage("§cUnknown action §e" + args[0] + "§c. Usage: §r" + getUsage());
    }

    private int getFlagCount(FlagDetector fd) {
        try {
            return fd.getClass().getDeclaredField("flagCount").getInt(fd);
        } catch (Exception e) {
            return -1;
        }
    }
}
