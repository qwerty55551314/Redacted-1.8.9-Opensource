package redacted.client.command.impl;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import redacted.client.Client;
import redacted.client.command.Argument;
import redacted.client.command.Command;

import java.util.Arrays;
import java.util.List;

public class ThemeCommand extends Command {

    public ThemeCommand() {
        super("theme", "Save/load GUI layout themes",
                Argument.literal("action", "what to do with a theme",
                        new Argument.Option("save", "save the current GUI layout"),
                        new Argument.Option("load", "apply a saved theme"),
                        new Argument.Option("list", "show every saved theme"),
                        new Argument.Option("delete", "remove a saved theme")),
                Argument.optionalGreedy("name", "theme name; quote it if it contains spaces",
                        () -> Arrays.asList(Client.getInstance().getThemeManager().list())));
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "save": {
                final String name = args.length > 1
                        ? joinArgs(args, 1)
                        : Client.getInstance().getThemeManager().getCurrentTheme();
                Client.getInstance().getThemeManager().save(name);
                sendMessage(themeName("§aSaved theme ", name, "reload"));
                break;
            }
            case "load": {
                if (args.length < 2) {
                    sendMessage("§cWhich theme? Usage: §r" + getUsage());
                    return;
                }
                final String name = joinArgs(args, 1);
                Client.getInstance().getThemeManager().load(name);
                sendMessage(themeName("§aLoaded theme ", name, "reload"));
                break;
            }
            case "list": {
                final String[] themes = Client.getInstance().getThemeManager().list();
                if (themes.length == 0) {
                    sendMessage("§cNo themes found.");
                } else {
                    final IChatComponent msg = new ChatComponentText("§aThemes §7(§e"
                            + themes.length + "§7)§a: ");
                    for (int i = 0; i < themes.length; i++) {
                        if (i > 0) {
                            msg.appendSibling(new ChatComponentText("§7, "));
                        }
                        msg.appendSibling(clickCommand("§e" + themes[i], ".theme load " + themes[i],
                                "§7Click to load §e" + themes[i]));
                    }
                    sendMessage(msg);
                }
                break;
            }
            case "delete": {
                if (args.length < 2) {
                    sendMessage("§cWhich theme? Usage: §r" + getUsage());
                    return;
                }
                final String name = joinArgs(args, 1);
                Client.getInstance().getThemeManager().delete(name);
                sendMessage("§aDeleted theme §e" + name + "§a.");
                break;
            }
            default:
                sendMessage("§cUnknown action §e" + args[0] + "§c. Usage: §r" + getUsage());
        }
    }

    private IChatComponent themeName(final String prefix, final String name, final String hoverHint) {
        final IChatComponent msg = new ChatComponentText(prefix);
        msg.appendSibling(clickCommand("§e" + name, ".theme load " + name,
                "§7Click to " + hoverHint + " §e" + name));
        msg.appendSibling(new ChatComponentText("§a."));
        return msg;
    }

    @Override
    public List<String> getArgCompletions(String[] args, String token) {
        if (args != null && args.length == 1
                && ("load".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]))) {
            return filter(Arrays.asList(Client.getInstance().getThemeManager().list()), token);
        }
        return super.getArgCompletions(args, token);
    }
}
