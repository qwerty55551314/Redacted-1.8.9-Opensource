package redacted.client.command;

import redacted.client.Client;
import redacted.client.command.impl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandManager {
    private final List<Command> commands = new ArrayList<Command>();
    private final String prefix = ".";

    public void init() {
        this.commands.add(new ConfigCommand());
        this.commands.add(new ThemeCommand());
        this.commands.add(new HelpCommand());
        this.commands.add(new SettimerCommand());
        this.commands.add(new ToggleCommand());
        this.commands.add(new ResetCommand());
        this.commands.add(new McpCommand());
        this.commands.add(new PacketLogCommand());
        // Developer commands (CommandType.DEVELOPER) are gated automatically: they
        // are only registered on dev builds (Client.DEV_BUILD == true).
        registerDevCommands();
    }

    /** Registers commands whose {@link CommandType} is not {@link CommandType#NORMAL}. */
    private void registerDevCommands() {
        if (!Client.DEV_BUILD) return;
        this.commands.add(new AnticheatCommand());
    }

    public boolean handleInput(String input) {
        if (input == null || !input.startsWith(this.prefix)) return false;

        final String[] parts = tokenize(input.substring(this.prefix.length()));
        if (parts.length == 0 || parts[0].isEmpty()) return false;

        final String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        final Command cmd = getCommand(parts[0]);
        if (cmd == null) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        Command.PREFIX + "§cUnknown command. Use §e.help§c."));
            }
            return true;
        }

        try {
            cmd.execute(args);
        } catch (final Throwable t) {
            cmd.printUsage();
            cmd.sendMessage("§cError: §f" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
        return true;
    }

    /**
     * Tab-completion candidates for the word under the cursor of a client command
     * input (e.g. ".toggle Spr" -> [Sprint]). Mirrors how vanilla completes "/cmd":
     * the first token after the prefix matches command names, anything after that is
     * delegated to the command's own argument suggestions.
     *
     * @param inputSoFar the full text up to the cursor, including the leading prefix
     */
    public List<String> getCompletions(String inputSoFar) {
        if (inputSoFar == null) return Collections.emptyList();

        final String body = inputSoFar.startsWith(this.prefix)
                ? inputSoFar.substring(this.prefix.length())
                : inputSoFar;
        // trailing empty token must survive so ".toggle " suggests argument values
        final String[] parts = tokenizePreserveTrailing(body);
        final String token = parts[parts.length - 1];

        final List<String> completions = new ArrayList<String>();
        if (parts.length == 1) {
            for (final Command cmd : this.commands) {
                completions.add(cmd.getName());
            }
        } else {
            final Command cmd = getCommand(parts[0]);
            if (cmd != null) {
                final String[] args = Arrays.copyOfRange(parts, 1, parts.length - 1);
                for (final String suggestion : cmd.getArgCompletions(args, token)) {
                    if (suggestion != null && !suggestion.isEmpty()) completions.add(suggestion);
                }
            }
        }
        Collections.sort(completions, String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    public Command getCommand(String name) {
        if (name == null) return null;
        for (final Command cmd : this.commands) {
            if (cmd.isNameOrAlias(name)) return cmd;
        }
        return null;
    }

    public List<Command> getCommands() { return this.commands; }
    public String getPrefix() { return this.prefix; }

    /**
     * Splits on spaces but treats double-quoted runs as a single argument and
     * drops the quotes, so ".config load \"MineBerry SkyWars\"" and the raw
     * ".config load MineBerry SkyWars" both resolve to the name "MineBerry SkyWars".
     * Trailing empty tokens are dropped (execution path).
     */
    public static String[] tokenize(final String input) {
        final List<String> parts = new ArrayList<String>();
        final StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts.toArray(new String[parts.size()]);
    }

    /**
     * Same as {@link #tokenize} but keeps the trailing empty token so callers can
     * tell ".toggle" (no args) from ".toggle " (typing the first arg).
     */
    private static String[] tokenizePreserveTrailing(final String input) {
        final List<String> parts = new ArrayList<String>();
        final StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[parts.size()]);
    }
}
