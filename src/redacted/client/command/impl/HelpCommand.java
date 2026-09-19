package redacted.client.command.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import redacted.client.Client;
import redacted.client.command.Argument;
import redacted.client.command.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * Baritone-style clickable help. {@code .help} prints one page of commands; the
 * {@code <<}, {@code >>} buttons flip pages and each command opens its detailed
 * help, which links back to the menu. Pages are weighted so a page holds roughly
 * {@link #PAGE_WEIGHT} "rows" -- about 5 simple commands or 3 argument-heavy ones.
 */
public class HelpCommand extends Command {

    private static final int PAGE_WEIGHT = 5;

    public HelpCommand() {
        super("help", "Show command help (clickable pages)", new String[]{"h", "?"},
                Argument.optionalText("command",
                        "page number to open, or a command name for its detailed help",
                        () -> {
                            final List<String> names = new ArrayList<String>();
                            for (final Command cmd : Client.getInstance().getCommandManager().getCommands()) {
                                names.add(cmd.getName());
                            }
                            return names;
                        }));
    }

    @Override
    public void execute(String[] args) {
        if (args.length > 0) {
            final String arg = args[0].trim();
            if (arg.matches("\\d+")) {
                showPage(Integer.parseInt(arg));
                return;
            }
            final Command target = Client.getInstance().getCommandManager().getCommand(arg);
            if (target != null) {
                showCommandHelp(target);
                return;
            }
            sendMessage("§cUnknown command §e" + arg + "§c. Use §e.help§c.");
            return;
        }
        showPage(1);
    }

    // command list (paged)

    /** A command takes 2 rows when it is argument-heavy so it doesn't cram a page. */
    private static int rowWeight(final Command cmd) {
        int options = 0;
        for (final Argument argument : cmd.getArguments()) {
            options += argument.getOptions().size();
        }
        return (cmd.getArguments().size() >= 2 || options >= 4) ? 2 : 1;
    }

    private static List<List<Command>> buildPages() {
        final List<List<Command>> pages = new ArrayList<List<Command>>();
        List<Command> current = new ArrayList<Command>();
        int weight = 0;
        for (final Command cmd : Client.getInstance().getCommandManager().getCommands()) {
            final int w = rowWeight(cmd);
            if (!current.isEmpty() && weight + w > PAGE_WEIGHT) {
                pages.add(current);
                current = new ArrayList<Command>();
                weight = 0;
            }
            current.add(cmd);
            weight += w;
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
        return pages;
    }

    private void showPage(final int requested) {
        final List<List<Command>> pages = buildPages();
        if (pages.isEmpty()) {
            sendMessage("§cNo commands registered.");
            return;
        }
        if (requested < 1 || requested > pages.size()) {
            sendMessage("§cNo page §e" + requested + "§c. Pages: §e1§7-§e" + pages.size());
            return;
        }

        // the total-command-count header only belongs on the first page
        if (requested == 1) {
            printLine(new ChatComponentText("§aCommands §7(§e"
                    + Client.getInstance().getCommandManager().getCommands().size() + "§7)§a: §7click one for details"));
        }

        final List<Command> page = pages.get(requested - 1);
        for (final Command cmd : page) {
            printLine(commandRow(cmd));
        }

        printLine(buildPager(requested, pages.size()));
    }

    private static IChatComponent commandRow(final Command cmd) {
        final IChatComponent name = new ChatComponentText("§e." + cmd.getName());
        final StringBuilder hover = new StringBuilder("§6" + cmd.getDescription());
        if (!cmd.getAliases().isEmpty()) {
            hover.append("\n§7Aliases: §e").append(String.join("§7, §e", cmd.getAliases()));
        }
        hover.append("\n\n§7").append(cmd.getUsage()).append("\n\n§7Click to view full help");
        name.setChatStyle(new ChatStyle()
                .setColor(EnumChatFormatting.YELLOW)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ".help " + cmd.getName()))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hover.toString()))));

        final IChatComponent rest = new ChatComponentText(" §7- §f" + cmd.getDescription());

        final IChatComponent row = new ChatComponentText("");
        row.appendSibling(name);
        row.appendSibling(rest);
        return row;
    }

    private static IChatComponent buildPager(final int page, final int maxPage) {
        final IChatComponent pager = new ChatComponentText("§7");
        pager.appendSibling(pagerButton("<<", page - 1, maxPage, "previous page"));
        pager.appendText("§7, ");
        pager.appendSibling(pagerButton(">>", page + 1, maxPage, "next page"));
        pager.appendText("§7  §e" + page + "§7/§e" + maxPage);
        return pager;
    }

    private static IChatComponent pagerButton(final String label, final int target, final int maxPage, final String hint) {
        final IChatComponent comp = new ChatComponentText(label);
        if (target >= 1 && target <= maxPage) {
            comp.setChatStyle(new ChatStyle()
                    .setColor(EnumChatFormatting.YELLOW)
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ".help " + target))
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("§7Click to view " + hint))));
        } else {
            comp.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
        }
        return comp;
    }

    // single-command detail

    private void showCommandHelp(final Command cmd) {
        cmd.printHelp();
        final IChatComponent back = new ChatComponentText("§7[§e« §7back to help§7]");
        back.setChatStyle(new ChatStyle()
                .setColor(EnumChatFormatting.GRAY)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ".help"))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("§7Click to return to the help menu"))));
        printLine(back);
    }

    private static void printLine(final IChatComponent line) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(Command.PREFIX).appendSibling(line));
        }
    }
}
