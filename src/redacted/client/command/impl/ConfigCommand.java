package redacted.client.command.impl;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import redacted.client.Client;
import redacted.client.command.Argument;
import redacted.client.command.Command;

import java.util.Arrays;
import java.util.List;

public class ConfigCommand extends Command {

    // flag tokens recognized in execute and stripped from the name; they appear in
    // help/usage as append markers but are never runnable on their own
    private static final String[] PR_TOKENS = {"-pr", "--pr", "--prevent-render"};
    private static final String[] RENDER_TOKENS = {"-r", "--render", "render"};
    private static final String[] YES_TOKENS = {"-y", "--yes"};

    public ConfigCommand() {
        super("config", "Save/load config profiles", new String[]{"cfg"},
                Argument.literal("action", "what to do with a config profile",
                        new Argument.Option("save", "save the current settings to a profile"),
                        new Argument.Option("load", "apply a saved profile"),
                        new Argument.Option("list", "show every saved profile"),
                        new Argument.Option("delete", "remove a saved profile"),
                        new Argument.Option("folder", "open the config folder in your file manager")),
                Argument.optionalGreedy("name", "profile name; quote it if it contains spaces",
                        () -> Arrays.asList(Client.getInstance().getConfigManager().list())),
                Argument.optionalLiteral("flags", "append flags (they are stripped from the name)",
                        new Argument.Option("-pr", "never touch RENDER+COSMETICS"),
                        new Argument.Option("-r", "also save RENDER+COSMETICS"),
                        new Argument.Option("-y", "skip the overwrite confirmation")));
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        final boolean pr = hasFlag(args, PR_TOKENS);
        final boolean render = hasFlag(args, RENDER_TOKENS);
        final boolean yes = hasFlag(args, YES_TOKENS);
        final boolean includeRender;

        switch (args[0].toLowerCase()) {
            case "load":
                // load applies RENDER+COSMETICS from the file by default; -pr forces them out
                includeRender = !pr;
                break;
            case "save":
                includeRender = render && !pr;
                break;
            default:
                includeRender = !pr;
        }

        switch (args[0].toLowerCase()) {
            case "save": {
                final String name = stripFlags(args, 1);
                if (name.isEmpty()) {
                    sendMessage("§cWhich name? Usage: §r" + getUsage());
                    return;
                }
                if (!yes && Client.getInstance().getConfigManager().profileFile(name) != null
                        && Client.getInstance().getConfigManager().profileFile(name).isFile()) {
                    confirmOverwrite(name, includeRender);
                    return;
                }
                Client.getInstance().getConfigManager().save(name, includeRender);
                sendMessage(configName("§aSaved config ", name, includeRender, "reload"));
                break;
            }
            case "load": {
                if (args.length < 2) {
                    sendMessage("§cWhich config? Usage: §r" + getUsage());
                    return;
                }
                final String name = stripFlags(args, 1);
                if (name.isEmpty()) {
                    sendMessage("§cWhich config? Usage: §r" + getUsage());
                    return;
                }
                if (Client.getInstance().getConfigManager().loadProfile(name, includeRender)) {
                    sendMessage(configName("§aLoaded config ", name, includeRender, "reload"));
                } else {
                    sendMessage("§cConfig §e" + name + "§c not found.");
                }
                break;
            }
            case "list": {
                final String[] configs = Client.getInstance().getConfigManager().list();
                if (configs.length == 0) {
                    sendMessage("§cNo configs found.");
                } else {
                    final IChatComponent msg = new ChatComponentText("§aConfigs §7(§e"
                            + configs.length + "§7)§a: ");
                    for (int i = 0; i < configs.length; i++) {
                        if (i > 0) {
                            msg.appendSibling(new ChatComponentText("§7, "));
                        }
                        msg.appendSibling(clickCommand("§e" + configs[i], ".config load " + configs[i],
                                "§7Click to load §e" + configs[i]));
                    }
                    sendMessage(msg);
                }
                break;
            }
            case "delete": {
                if (args.length < 2) {
                    sendMessage("§cWhich config? Usage: §r" + getUsage());
                    return;
                }
                final String name = stripFlags(args, 1);
                if (name.isEmpty()) {
                    sendMessage("§cWhich config? Usage: §r" + getUsage());
                    return;
                }
                if (Client.getInstance().getConfigManager().deleteProfile(name)) {
                    sendMessage("§aDeleted config §e" + name + "§a.");
                } else {
                    sendMessage("§cConfig §e" + name + "§c not found.");
                }
                break;
            }
            case "folder": {
                final java.io.File dir = Client.getInstance().getConfigManager().getConfigsDir();
                if (dir == null) {
                    sendMessage("§cConfig folder unavailable.");
                    break;
                }
                if (!dir.exists()) dir.mkdirs();
                try {
                    final java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                    if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                        desktop.open(dir);
                        sendMessage("§aOpened config folder: §e" + dir.getAbsolutePath());
                    } else {
                        sendMessage("§cCannot open folders on this OS.");
                    }
                } catch (final Exception e) {
                    sendMessage("§cFailed to open folder: " + e.getMessage());
                }
                break;
            }
            default:
                sendMessage("§cUnknown action §e" + args[0] + "§c. Usage: §r" + getUsage());
        }
    }

    /** Prints a clickable overwrite confirmation; Yes re-runs the save with -y, No does nothing. */
    private void confirmOverwrite(final String name, final boolean includeRender) {
        final String yesCmd = ".config save " + name + " -y" + (includeRender ? " -r" : "");
        final IChatComponent msg = new ChatComponentText("§e" + name
                + " §calready exists. Overwrite? ");
        msg.appendSibling(clickCommand("§aYes", yesCmd, "§7Click to overwrite §e" + name));
        msg.appendSibling(new ChatComponentText(" "));
        msg.appendSibling(clickCommand("§cNo", ".config list", "§7Click to cancel"));
        sendMessage(msg);
    }

    private IChatComponent configName(final String prefix, final String name,
                                      final boolean includeRender, final String hoverHint) {
        final IChatComponent msg = new ChatComponentText(prefix);
        // color lives ON the name so names with spaces stay fully yellow (the code in
        // the prefix component would not carry across the sibling boundary)
        msg.appendSibling(clickCommand("§e" + name, ".config load " + name,
                "§7Click to " + hoverHint + " §e" + name));
        if (!includeRender) {
            msg.appendSibling(new ChatComponentText(" §7(§8no render§7)"));
        }
        msg.appendSibling(new ChatComponentText("§a."));
        return msg;
    }

    private static boolean hasFlag(final String[] args, final String[] tokens) {
        for (final String arg : args) {
            if (arg == null) continue;
            for (final String token : tokens) {
                if (arg.equalsIgnoreCase(token)) return true;
            }
        }
        return false;
    }

    /**
     * Joins args[from..end] and drops every flag token so flags can never become part
     * of the profile name, wherever they were typed.
     */
    private static String stripFlags(final String[] args, final int from) {
        final StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            final String arg = args[i];
            if (arg == null || isFlagToken(arg)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(arg);
        }
        return sb.toString();
    }

    private static boolean isFlagToken(final String arg) {
        for (final String token : PR_TOKENS) {
            if (arg.equalsIgnoreCase(token)) return true;
        }
        for (final String token : RENDER_TOKENS) {
            if (arg.equalsIgnoreCase(token)) return true;
        }
        return arg.equalsIgnoreCase("-y") || arg.equalsIgnoreCase("--yes");
    }

    @Override
    public List<String> getArgCompletions(String[] args, String token) {
        if (args != null && args.length == 1
                && ("load".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0])
                || "save".equalsIgnoreCase(args[0]))) {
            return filter(Arrays.asList(Client.getInstance().getConfigManager().list()), token);
        }
        return super.getArgCompletions(args, token);
    }
}
