package redacted.client.command;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import redacted.client.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every "." chat command. A command declares its name, a short
 * description, optional aliases and the {@link Argument}s it accepts. The base
 * class turns those declarations into a colored usage string, default tab
 * completion and a per-argument help block, so concrete commands only write
 * their {@link #execute} logic (plus an override here and there for unusual
 * completion).
 */
public abstract class Command {

    protected static final String PREFIX = "§7[§8Redacted§7] §f";

    private final String name;
    private final String description;
    private final List<String> aliases;
    private final List<Argument> arguments;
    private final CommandType type;

    protected Command(final String name, final String description) {
        this(name, description, new String[0], CommandType.NORMAL);
    }

    protected Command(final String name, final String description, final Argument... arguments) {
        this(name, description, new String[0], CommandType.NORMAL, arguments);
    }

    protected Command(final String name, final String description, final String[] aliases,
                      final Argument... arguments) {
        this(name, description, aliases, CommandType.NORMAL, arguments);
    }

    protected Command(final String name, final String description, final String[] aliases,
                      final CommandType type, final Argument... arguments) {
        this.name = name;
        this.description = description;
        this.aliases = aliases == null ? Collections.<String>emptyList() : Arrays.asList(aliases);
        this.arguments = arguments == null ? Collections.<Argument>emptyList() : Arrays.asList(arguments);
        this.type = type == null ? CommandType.NORMAL : type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    /** The command's {@link CommandType} (see {@link CommandType#DEVELOPER}). */
    public CommandType getType() {
        return this.type;
    }

    /** Whether this is a developer-only command ({@link CommandType#DEVELOPER}). */
    public boolean isDeveloperOnly() {
        return this.type == CommandType.DEVELOPER;
    }

    public boolean isNameOrAlias(final String input) {
        if (name.equalsIgnoreCase(input)) {
            return true;
        }
        for (final String alias : aliases) {
            if (alias.equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }

    public abstract void execute(String[] args);

    /**
     * Colored usage line built from the declared arguments, e.g.
     * {@code ".config §a<save, load, list, delete, folder> §b[name]"}.
     */
    public String getUsage() {
        final StringBuilder sb = new StringBuilder("§e.").append(name);
        for (final Argument argument : arguments) {
            sb.append(' ').append(render(argument));
        }
        return sb.toString();
    }

    private String render(final Argument argument) {
        final String inner;
        if (argument.hasChoices()) {
            final StringBuilder choices = new StringBuilder();
            int i = 0;
            for (final Argument.Option option : argument.getOptions()) {
                if (i++ > 0) {
                    choices.append("§7, §a");
                }
                choices.append(option.value());
            }
            inner = "§a" + choices;
        } else {
            inner = "§b" + argument.getName();
        }
        return (argument.isRequired() ? "§7<" : "§7[") + inner + (argument.isRequired() ? "§7>" : "§7]");
    }

    /**
     * Default completion: the values of the argument at the position currently
     * being typed, filtered by the partial token. Override for commands whose
     * completion does not line up 1:1 with their declared arguments (e.g.
     * {@code .toggle all off}).
     */
    public List<String> getArgCompletions(final String[] args, final String token) {
        final int index = args == null ? 0 : args.length;
        if (index >= arguments.size()) {
            return Collections.emptyList();
        }
        return filter(arguments.get(index).getValues(), token);
    }

    protected static List<String> filter(final List<String> values, final String token) {
        final List<String> out = new ArrayList<String>();
        if (token == null || token.isEmpty()) {
            return values == null ? Collections.<String>emptyList() : values;
        }
        for (final String value : values) {
            if (value != null && value.regionMatches(true, 0, token, 0, token.length())) {
                out.add(value);
            }
        }
        return out;
    }

    /**
     * Joins {@code args[from..end]} back into one string. This is how greedy
     * name arguments accept names with spaces: quoted or not, the remainder of
     * the line is the value.
     */
    protected static String joinArgs(final String[] args, final int from) {
        if (args == null || from >= args.length) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(args[from]);
        for (int i = from + 1; i < args.length; i++) {
            sb.append(' ').append(args[i]);
        }
        return sb.toString();
    }

    protected boolean requireArgs(final String[] args, final int min, final int max) {
        if (args.length < min || args.length > max) {
            printUsage();
            return false;
        }
        return true;
    }

    protected void printUsage() {
        sendMessage("§cUsage: §r" + getUsage());
    }

    /**
     * Detailed help for this command: description, colored usage, aliases and one
     * line per declared argument/choice. Option values are clickable and drop the
     * matching invocation into the chat input. Shown by {@code .help <command>}.
     */
    public void printHelp() {
        sendMessage("§e." + name + " §7- §f" + description);
        sendMessage("§7Usage: §r" + getUsage());
        if (!aliases.isEmpty()) {
            sendMessage("§7Aliases: §e" + String.join("§7, §e", aliases));
        }
        for (final Argument argument : arguments) {
            sendMessage("§8  §b" + argument.getName() + "§8: §f" + argument.getDescription());
            if (argument.hasChoices()) {
                for (final Argument.Option option : argument.getOptions()) {
                    final IChatComponent line = new ChatComponentText("§8  §e" + option.value());
                    // append-marker flags (dash-prefixed) are hints, not commands
                    // never make them clickable, or the user could click ".config -pr"
                    if (!option.value().startsWith("-")) {
                        line.setChatStyle(new ChatStyle()
                                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                        "." + name + " " + option.value() + " "))
                                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        new ChatComponentText("§7Click to type §e." + name + " " + option.value()))));
                    }
                    if (option.description() != null) {
                        line.appendSibling(new ChatComponentText(" §7- §f" + option.description()));
                    }
                    sendMessage(line);
                }
            }
        }
    }

    /**
     * Builds a clickable (RUN_COMMAND) component: clicking runs {@code command}
     * through the client command system, exactly like typing it.
     */
    protected static IChatComponent clickCommand(final String text, final String command, final String hover) {
        final IChatComponent comp = new ChatComponentText(text);
        comp.setChatStyle(new ChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(hover == null ? "§7Click to run §e" + command : hover))));
        return comp;
    }

    /**
     * Builds a clickable (SUGGEST_COMMAND) component: clicking drops {@code text}
     * into the chat input for the user to finish.
     */
    protected static IChatComponent suggestCommand(final String text, final String suggest, final String hover) {
        final IChatComponent comp = new ChatComponentText(text);
        comp.setChatStyle(new ChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(hover == null ? "§7Click to type §e" + suggest : hover))));
        return comp;
    }

    // not ChatUtil.say -- that stamps [Client.NAME], commands keep their own literal prefix
    protected void sendMessage(final String message) {
        ChatUtil.print(PREFIX + message);
    }

    protected void sendMessage(final IChatComponent message) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX).appendSibling(message));
        }
    }
}
